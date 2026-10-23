import base64
import contextlib
import json
import sys
from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.llm import LLMTurn
from app.main import create_app
from app.ratelimit import RateLimitExceeded, RateLimiter, user_key
from app.spring_client import SpringAuthError
from tests.fakes import FakeLLM


class Clock:
    def __init__(self):
        self.now = 0.0

    def __call__(self) -> float:
        return self.now


def fake_jwt(sub: str, nonce: str = "") -> str:
    """Looks like a JWT (header.payload.signature); `nonce` makes two logins of the same user differ."""
    b64 = lambda obj: base64.urlsafe_b64encode(json.dumps(obj).encode()).rstrip(b"=").decode()
    return f"{b64({'alg': 'HS256'})}.{b64({'sub': sub, 'n': nonce})}.sig"


def test_the_limiter_enforces_minute_day_and_global_windows_and_never_counts_a_rejection():
    clock = Clock()
    limiter = RateLimiter(per_minute=3, per_day=5, global_per_day=7, clock=clock)

    for second in (0, 10, 20):
        clock.now = second
        limiter.admit("a")
    clock.now = 30
    with pytest.raises(RateLimitExceeded) as minute:
        limiter.admit("a")
    assert (minute.value.scope, minute.value.retry_after) == ("minute", 30)   # the t=0 request ages out at t=60

    limiter.admit("b")                                   # another user is untouched by a's limit
    clock.now = 61
    limiter.admit("a")                                   # a slot opened. The rejection at t=30 was NOT recorded
    clock.now = 125
    limiter.admit("a")                                   # a's 5th request in the day

    clock.now = 190
    with pytest.raises(RateLimitExceeded) as day:
        limiter.admit("a")
    assert (day.value.scope, day.value.retry_after) == ("day", 86_400 - 190)  # frees up when the t=0 request is a day old

    clock.now = 200
    limiter.admit("c")                                   # the global count reaches 7 (6 admitted so far, plus this one)
    clock.now = 210
    with pytest.raises(RateLimitExceeded) as everyone:
        limiter.admit("d")
    assert everyone.value.scope == "global"              # one user's rejection above did not use up the global budget


def admitted_under_contention(limiter: RateLimiter, rounds: int = 20) -> list[int]:
    """How many of 2000 simultaneous requests got through, repeated `rounds` times, with the
    interpreter told to switch threads as often as possible so a race has every chance to show."""
    def attempt(_):
        try:
            limiter.admit("x")
            return True
        except RateLimitExceeded:
            return False

    previous = sys.getswitchinterval()
    sys.setswitchinterval(1e-6)
    try:
        results = []
        for _ in range(rounds):
            limiter._events.clear()                    # a fresh minute window each round
            with ThreadPoolExecutor(max_workers=64) as pool:
                results.append(sum(pool.map(attempt, range(2000))))
        return results
    finally:
        sys.setswitchinterval(previous)


def test_concurrent_requests_cannot_slip_past_the_limit():
    limiter = RateLimiter(per_minute=50, per_day=100_000, global_per_day=100_000)

    assert set(admitted_under_contention(limiter)) == {50}      # always exactly the limit, never 51

    # Proof the test can fail: the same code with the lock removed over-admits in at least one round.
    # (A race is probabilistic, so this is a smoke detector, not a proof; the lock is the real guarantee.)
    unlocked = RateLimiter(per_minute=50, per_day=100_000, global_per_day=100_000)
    unlocked._lock = contextlib.nullcontext()
    assert max(admitted_under_contention(unlocked)) > 50


class SelectiveSpring:
    """Accepts any token except the literal 'bad'."""

    def get(self, path, token, params=None):
        if token == "bad":
            raise SpringAuthError()
        return [{"id": 1, "name": "Apex 50k #1", "firm": "Apex", "accountType": "EVALUATION"}]


def test_chat_is_limited_per_user_after_authentication_and_before_any_model_call_is_paid_for():
    llm = FakeLLM([LLMTurn("ok", [])] * 10)
    limiter = RateLimiter(per_minute=2, per_day=100, global_per_day=1000)
    client = TestClient(create_app(Settings(_env_file=None), spring=SelectiveSpring(), llm=llm, limiter=limiter))
    ask = lambda token: client.post("/chat", headers={"Authorization": f"Bearer {token}"}, json={"message": "hi"})

    # Junk tokens are rejected at the preflight and never touch the limiter, so spamming them
    # cannot use up a real user's allowance.
    assert [ask("bad").status_code for _ in range(5)] == [401] * 5

    first_login, second_login = fake_jwt("trader@x.com", "1"), fake_jwt("trader@x.com", "2")
    assert [ask(first_login).status_code, ask(first_login).status_code] == [200, 200]

    blocked = ask(second_login)                           # logging in again does not reset the allowance
    assert blocked.status_code == 429
    assert int(blocked.headers["Retry-After"]) >= 1
    assert "too quickly" in blocked.json()["detail"]

    assert ask(fake_jwt("someone.else@x.com")).status_code == 200      # a different user has their own allowance
    assert len(llm.calls) == 3                            # exactly the 3 admitted requests reached the model

    assert user_key(first_login) == user_key(second_login) == "sub:trader@x.com"
    assert user_key("not-a-jwt") == user_key("not-a-jwt") != user_key("another-garbage")   # unreadable: stable, per token
