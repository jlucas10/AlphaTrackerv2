"""The answer key: correct results worked out from the raw dataset.

Deliberately independent. It imports nothing from `app/` and never calls the Java
API, because an answer key derived from the system under test would call that
system's bugs "correct". It re-implements the rules in CONTEXT.md ("Win / Loss
Definition") from scratch:

    win     = P&L > 0      loss = P&L < 0      neutral = exactly 0
    winRate = wins / (wins + losses)            (neutral is left out)
    a day's trades are netted, then the day is a win / loss / neutral day

Two choices make it trustworthy rather than just "another float calculator":
  - Money is integer CENTS, so offsetting trades net to exactly 0 (no 5.5e-17).
  - Rounding is exact half-up on Fractions, so a result never depends on a float quirk.
The output dictionaries use the same field names as the Spring endpoints so the
two can be compared directly (see verify_oracle, step 2).
"""

import calendar
import math
from datetime import date, timedelta
from decimal import ROUND_HALF_UP, Decimal
from fractions import Fraction

from evals.dataset import JOURNAL, TODAY, TRADES, SeedTrade

# CME point values: dollars per 1.00 of price move, per contract. A second,
# independent copy of the table in Instrument.java.
POINT_VALUE = {
    "ES": 50, "MES": 5, "NQ": 20, "MNQ": 2, "YM": 5, "MYM": 0.5,
    "RTY": 50, "M2K": 5, "CL": 1000, "MCL": 100, "GC": 100, "MGC": 10,
}


def pnl_cents(trade: SeedTrade) -> int:
    move = Decimal(str(trade.exit)) - Decimal(str(trade.entry))
    if trade.direction == "SHORT":
        move = -move  # a short profits when price falls
    dollars = move * Decimal(str(POINT_VALUE[trade.ticker])) * trade.contracts
    return int((dollars * 100).to_integral_value(rounding=ROUND_HALF_UP))


# ---------------------------------------------------------------- rounding
def _half_up(value: Fraction) -> int:
    return math.floor(value + Fraction(1, 2))


def _rate(numerator: int, denominator: int) -> float:
    """numerator/denominator rounded half-up to 4 decimals."""
    return _half_up(Fraction(numerator, denominator) * 10_000) / 10_000


def _dollars(cents: int) -> float:
    return cents / 100


# ---------------------------------------------------------------- selection
def select(trades: list[SeedTrade] | None = None, *, account: str | None = None,
           start: date | None = None, end: date | None = None) -> list[SeedTrade]:
    """Trades in an account (None = all) and a date window (both ends inclusive)."""
    return [
        t for t in (TRADES if trades is None else trades)
        if (account is None or t.account == account)
        and (start is None or t.when.date() >= start)
        and (end is None or t.when.date() <= end)
    ]


def period_range(period: str, today: date = TODAY) -> tuple[date | None, date | None]:
    """Inclusive date range for a named period.

    Written differently from app/periods.py on purpose (ISO calendar weeks and
    calendar.monthrange rather than weekday() arithmetic), so a test comparing the
    two can catch an error in either.
    """
    if period == "today":
        return today, today
    year, week, _ = today.isocalendar()
    this_monday = date.fromisocalendar(year, week, 1)
    if period == "this_week":
        return this_monday, today
    if period == "last_week":
        start = this_monday - timedelta(days=7)
        return start, start + timedelta(days=6)
    if period == "this_month":
        return date(today.year, today.month, 1), today
    if period == "last_month":
        y, m = (today.year, today.month - 1) if today.month > 1 else (today.year - 1, 12)
        return date(y, m, 1), date(y, m, calendar.monthrange(y, m)[1])
    if period == "last_30_days":
        return today - timedelta(days=29), today
    if period == "all_time":
        return None, None
    raise ValueError(f"unknown period {period!r}")


# ---------------------------------------------------------------- statistics
def stats(trades: list[SeedTrade]) -> dict:
    pnls = [pnl_cents(t) for t in trades]
    wins = [p for p in pnls if p > 0]
    losses = [p for p in pnls if p < 0]
    neutral = len(pnls) - len(wins) - len(losses)
    decided = len(wins) + len(losses)
    return {
        "tradeCount": len(pnls),
        "wins": len(wins),
        "losses": len(losses),
        "neutral": neutral,
        "winRate": _rate(len(wins), decided) if decided else None,
        "neutralRate": _rate(neutral, len(pnls)) if pnls else None,
        "totalPnl": _dollars(sum(pnls)),
        "avgWin": _dollars(_half_up(Fraction(sum(wins), len(wins)))) if wins else None,
        "avgLoss": _dollars(_half_up(Fraction(sum(losses), len(losses)))) if losses else None,
    }


def day_stats(trades: list[SeedTrade]) -> dict:
    by_day: dict[date, int] = {}
    for t in trades:
        by_day[t.when.date()] = by_day.get(t.when.date(), 0) + pnl_cents(t)
    wins = sum(1 for c in by_day.values() if c > 0)
    losses = sum(1 for c in by_day.values() if c < 0)
    neutral = len(by_day) - wins - losses
    return {
        "tradingDays": len(by_day),
        "winDays": wins,
        "lossDays": losses,
        "neutralDays": neutral,
        "neutralDayRate": _rate(neutral, len(by_day)) if by_day else None,
    }


def summary(trades: list[SeedTrade]) -> dict:
    return {"trades": stats(trades), "days": day_stats(trades)}


def breakdown(trades: list[SeedTrade], by: str) -> list[dict]:
    groups: dict[str, list[SeedTrade]] = {}
    for t in trades:
        if by == "instrument":
            keys = [t.ticker]
        elif by == "setup":
            keys = list(dict.fromkeys(t.tags)) or ["(untagged)"]  # a repeated tag counts once
        elif by == "rating":
            keys = [str(t.rating) if t.rating is not None else "unrated"]
        else:
            raise ValueError(by)
        for key in keys:  # a multi-tag trade lands in each of its tags' groups
            groups.setdefault(key, []).append(t)
    ordered = sorted(groups.items(), key=lambda kv: (-len(kv[1]), kv[0]))
    return [{"key": key, "stats": stats(members)} for key, members in ordered]


def discipline(trades: list[SeedTrade]) -> dict:
    followed = [t for t in trades if t.followed_plan]
    broke = [t for t in trades if not t.followed_plan]
    flagged = len(followed) + len(broke)
    return {
        "planFollowedRate": _rate(len(followed), flagged) if flagged else None,
        "followedPlan": stats(followed),
        "brokePlan": stats(broke),
        "unspecified": stats([]),  # the API cannot create a null flag, so the dataset has none
    }


def journal(start: date, end: date) -> list[dict]:
    return [
        {"date": n.day.isoformat(), "notes": n.notes, "htfBias": n.htf_bias}
        for n in sorted(JOURNAL, key=lambda n: n.day)
        if start <= n.day <= end and (n.notes.strip() or n.htf_bias.strip())
    ]
