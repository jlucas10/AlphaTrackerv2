"""Protects the model budget: per-user and global limits on chat requests.

Design, in one place:
  - Sliding-window LOG (exact timestamps), not a fixed window. A fixed window lets a client send
    double the limit across a window boundary; the log has no such seam, and at this scale
    (a few hundred timestamps) its cost is irrelevant.
  - Three limits checked together: per user per minute (stops loops and mashing), per user per
    day (a budget), and one GLOBAL daily cap (total spend is bounded however many users exist).
  - Check-and-record is ATOMIC under a lock. FastAPI runs these endpoints on worker threads;
    without the lock, many concurrent requests could all pass a "limit 10" check before any of
    them recorded itself.
  - A REJECTED request is not recorded. Counting rejections would let a client extend its own
    lockout by hammering, and would make the numbers disagree with what was actually spent.
  - Single process, in memory: counts reset on restart and are not shared across replicas.
    Correct for one container; a shared store (Redis) is the upgrade if the service scales out.
"""

import base64
import hashlib
import json
import math
import threading
import time
from collections import deque
from typing import Callable

MINUTE = 60.0
DAY = 86_400.0
_PURGE_EVERY = 500  # admits between sweeps that drop users who have gone quiet


class RateLimitExceeded(Exception):
    def __init__(self, scope: str, retry_after: int):
        super().__init__(f"rate limit exceeded ({scope})")
        self.scope = scope              # "minute" | "day" | "global"
        self.retry_after = retry_after  # whole seconds until a slot frees up (always >= 1)


class RateLimiter:
    def __init__(self, per_minute: int, per_day: int, global_per_day: int,
                 clock: Callable[[], float] = time.monotonic):
        self._per_minute, self._per_day, self._global = per_minute, per_day, global_per_day
        self._clock = clock  # injectable, so tests move time instead of sleeping
        self._events: dict[tuple[str, str], deque[float]] = {}  # (scope, user) -> admit timestamps
        self._lock = threading.Lock()
        self._admitted = 0

    def admit(self, user_key: str) -> None:
        """Let this user's request through, or raise RateLimitExceeded. Records it only if admitted."""
        now = self._clock()
        limits = (
            ("minute", (user_key, "minute"), self._per_minute, MINUTE),
            ("day", (user_key, "day"), self._per_day, DAY),
            ("global", ("*", "day"), self._global, DAY),
        )
        with self._lock:
            slots: list[deque[float]] = []
            for scope, key, limit, window in limits:
                events = self._events.setdefault(key, deque())
                while events and events[0] <= now - window:  # forget what has aged out of the window
                    events.popleft()
                if len(events) >= limit:
                    # The oldest event in the window decides when the next slot opens.
                    raise RateLimitExceeded(scope, max(1, math.ceil(events[0] + window - now)))
                slots.append(events)
            for events in slots:  # every limit passed: record the request in all of them at once
                events.append(now)

            self._admitted += 1
            if self._admitted % _PURGE_EVERY == 0:
                self._purge(now)

    def _purge(self, now: float) -> None:
        """Drop users with nothing left in their window, so idle users don't pile up forever."""
        for key in [k for k, events in self._events.items()
                    if not events or events[-1] <= now - (MINUTE if k[1] == "minute" else DAY)]:
            del self._events[key]


def user_key(token: str) -> str:
    """A stable identity for limiting: the JWT's `sub` (the email), NOT the token.

    A re-login mints a new token; keying on the token would hand out a fresh allowance each
    time. Decoding the payload without checking the signature is safe ONLY because this runs
    after the preflight GET /accounts succeeded, which proves Spring verified the signature.
    Never use this on a token that has not been through that check.
    """
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        sub = json.loads(base64.urlsafe_b64decode(payload))["sub"]
        if isinstance(sub, str) and sub:
            return "sub:" + sub.lower()
    except (IndexError, ValueError, KeyError, TypeError):
        pass
    return "tok:" + hashlib.sha256(token.encode()).hexdigest()[:16]  # unreadable token: still stable, still per-token
