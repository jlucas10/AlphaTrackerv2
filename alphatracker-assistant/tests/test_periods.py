from datetime import date, datetime, timezone

import pytest

from app.periods import resolve_period, today_in


def test_periods_resolve_to_exact_inclusive_dates():
    wed = date(2026, 10, 14)  # a Wednesday
    assert resolve_period("today", wed) == (wed, wed)
    assert resolve_period("this_week", wed) == (date(2026, 10, 12), wed)       # Monday .. today
    assert resolve_period("last_week", wed) == (date(2026, 10, 5), date(2026, 10, 11))
    assert resolve_period("this_month", wed) == (date(2026, 10, 1), wed)
    assert resolve_period("last_month", wed) == (date(2026, 9, 1), date(2026, 9, 30))
    assert resolve_period("last_30_days", wed) == (date(2026, 9, 15), wed)      # 30 days, today included
    assert resolve_period("all_time", wed) == (None, None)

    # Week edges: on a Monday the week has just begun; on a Sunday it is complete.
    assert resolve_period("this_week", date(2026, 10, 12)) == (date(2026, 10, 12), date(2026, 10, 12))
    assert resolve_period("this_week", date(2026, 10, 11)) == (date(2026, 10, 5), date(2026, 10, 11))

    # Year boundary: Jan 1 2026 is a Thursday, so "last week" is entirely in 2025.
    jan1 = date(2026, 1, 1)
    assert resolve_period("last_month", jan1) == (date(2025, 12, 1), date(2025, 12, 31))
    assert resolve_period("last_week", jan1) == (date(2025, 12, 22), date(2025, 12, 28))

    # Leap year: last month from March 2024 includes Feb 29.
    assert resolve_period("last_month", date(2024, 3, 10)) == (date(2024, 2, 1), date(2024, 2, 29))

    with pytest.raises(ValueError):
        resolve_period("next_decade", wed)

    # "Today" follows the configured timezone, not UTC: 03:00 UTC on Oct 13
    # is still the evening of Oct 12 in Los Angeles.
    moment = datetime(2026, 10, 13, 3, 0, tzinfo=timezone.utc)

    assert today_in("America/Los_Angeles", moment) == date(2026, 10, 12)
    assert today_in("UTC", moment) == date(2026, 10, 13)
