from datetime import date, datetime, timedelta
from typing import Literal, get_args
from zoneinfo import ZoneInfo

# The model never computes dates ("last month" is not something to get wrong
# by one day). It names a period; this module turns it into exact calendar
# dates. Both ends are INCLUSIVE, matching the Spring analytics endpoints.
#
# Periods that are still running ("this week", "this month") end TODAY, not at
# the end of the period: "so far", with a `to` that never lies about the future.
# Weeks run Monday to Sunday.
PeriodName = Literal[
    "today",
    "this_week",
    "last_week",
    "this_month",
    "last_month",
    "last_30_days",
    "all_time",
]
PERIOD_NAMES: tuple[str, ...] = get_args(PeriodName)


def today_in(timezone: str, now: datetime | None = None) -> date:
    """The trader's calendar date. Trade dates carry no timezone, so a single
    configured one decides what 'today' means (and where midnight falls)."""
    tz = ZoneInfo(timezone)
    return (now.astimezone(tz) if now else datetime.now(tz)).date()


def resolve_period(period: str, today: date) -> tuple[date | None, date | None]:
    """Returns (from, to), both inclusive; (None, None) means no date filter."""
    if period == "today":
        return today, today
    if period == "this_week":
        return today - timedelta(days=today.weekday()), today
    if period == "last_week":
        this_monday = today - timedelta(days=today.weekday())
        return this_monday - timedelta(days=7), this_monday - timedelta(days=1)
    if period == "this_month":
        return today.replace(day=1), today
    if period == "last_month":
        last_day = today.replace(day=1) - timedelta(days=1)
        return last_day.replace(day=1), last_day
    if period == "last_30_days":
        return today - timedelta(days=29), today  # 30 calendar days, today included
    if period == "all_time":
        return None, None
    raise ValueError(f"Unknown period {period!r}. Use one of: {', '.join(PERIOD_NAMES)}.")
