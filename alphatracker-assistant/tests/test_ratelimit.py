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
from app.ratelimit import RateLimitExceeded, RateLimiter, is_demo, user_key
from app.spring_client import SpringAuthError
from tests.fakes import FakeLLM


class Clock:
    def __init__(self):
        self.now = 0.0

    def __call__(self) -> float:
        return self.now


def fake_jwt(sub: str, nonce: str = "", **claims) -> str:
    """Looks like a JWT (header.payload.signature); `nonce` makes two logins of the same user differ."""
    b64 = lambda obj: base64.urlsafe_b64encode(json.dumps(obj).encode()).rstrip(b"=").decode()
    return f"{b64({'alg': 'HS256'})}.{b64({'sub': sub, 'n': nonce, **claims})}.sig"


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

    # From a browser (a request carrying an Origin), the wait time must be READABLE by the page's JavaScript.
    cross_origin = client.post("/chat", json={"message": "hi"},
                               headers={"Authorization": f"Bearer {second_login}", "Origin": "http://localhost:5173"})
    assert cross_origin.status_code == 429
    assert "retry-after" in cross_origin.headers["access-control-expose-headers"].lower()

    assert ask(fake_jwt("someone.else@x.com")).status_code == 200      # a different user has their own allowance
    assert len(llm.calls) == 3                            # exactly the 3 admitted requests reached the model

    assert user_key(first_login) == user_key(second_login) == "sub:trader@x.com"
    assert user_key("not-a-jwt") == user_key("not-a-jwt") != user_key("another-garbage")   # unreadable: stable, per token


def test_demo_users_get_tighter_per_user_limits_and_normal_users_are_unaffected():
    clock = Clock()
    limiter = RateLimiter(per_minute=10, per_day=100, global_per_day=1000, clock=clock,
                          demo_per_minute=2, demo_per_day=3)

    limiter.admit("demo", demo=True)
    limiter.admit("demo", demo=True)
    with pytest.raises(RateLimitExceeded) as minute:
        limiter.admit("demo", demo=True)
    assert minute.value.scope == "minute"

    clock.now = 61
    limiter.admit("demo", demo=True)                      # the 3rd of the day
    clock.now = 122
    with pytest.raises(RateLimitExceeded) as day:
        limiter.admit("demo", demo=True)
    assert day.value.scope == "day"                       # the demo's daily budget, far below the normal 100

    for _ in range(10):                                   # a normal user still gets the normal 10 a minute
        limiter.admit("trader")
    with pytest.raises(RateLimitExceeded):
        limiter.admit("trader")


def test_a_limiter_with_no_demo_limits_treats_demo_like_everyone_else():
    limiter = RateLimiter(per_minute=2, per_day=100, global_per_day=1000)
    limiter.admit("demo", demo=True)
    limiter.admit("demo", demo=True)
    with pytest.raises(RateLimitExceeded):
        limiter.admit("demo", demo=True)


def test_demo_tokens_are_recognised_from_the_claim_and_garbage_is_not_demo():
    assert is_demo(fake_jwt("demo@alphatracker.app", demo=True)) is True
    assert is_demo(fake_jwt("trader@x.com")) is False
    assert is_demo(fake_jwt("trader@x.com", demo="true")) is False      # only a real boolean true counts
    assert is_demo("not-a-jwt") is False


def test_chat_applies_the_tighter_demo_limit_to_demo_tokens_only():
    llm = FakeLLM([LLMTurn("ok", [])] * 10)
    limiter = RateLimiter(per_minute=5, per_day=100, global_per_day=1000, demo_per_minute=1, demo_per_day=10)
    client = TestClient(create_app(Settings(_env_file=None), spring=SelectiveSpring(), llm=llm, limiter=limiter))
    ask = lambda token: client.post("/chat", headers={"Authorization": f"Bearer {token}"}, json={"message": "hi"})

    demo = fake_jwt("demo@alphatracker.app", "1", demo=True)
    assert ask(demo).status_code == 200
    assert ask(fake_jwt("demo@alphatracker.app", "2", demo=True)).status_code == 429   # 1 a minute, shared by every visitor

    trader = fake_jwt("trader@x.com")
    assert [ask(trader).status_code for _ in range(3)] == [200, 200, 200]
