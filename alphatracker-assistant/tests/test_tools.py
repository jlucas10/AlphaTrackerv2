from datetime import date

import pytest

from app.spring_client import ALLOWED_PATHS, SpringAuthError, SpringToolError
from app.tools import MAX_PAYLOAD_CHARS, TOOLS, ToolRunner

from tests.fakes import FakeSpring

TODAY = date(2026, 10, 14)


def runner(spring) -> ToolRunner:
    return ToolRunner(spring, today=lambda: TODAY)


def test_period_is_resolved_in_python_and_mapped_onto_springs_query():
    spring = FakeSpring(response={"groups": []})

    result = runner(spring).run("get_breakdown", {"by": "setup", "account_id": 5, "period": "last_month"}, "tok")

    assert result.ok
    assert spring.calls == [
        ("/analytics/breakdown", "tok",
         {"accountId": 5, "from": "2026-09-01", "to": "2026-09-30", "by": "setup"})
    ]
    # The model is told exactly which dates the period turned into, so it can quote them.
    assert result.payload["period_applied"] == {"period": "last_month", "from": "2026-09-01", "to": "2026-09-30"}

    spring.calls.clear()
    runner(spring).run("get_summary", {"from_date": "2026-09-18", "to_date": "2026-09-19"}, "tok")
    assert spring.calls[0][2] == {"accountId": None, "from": "2026-09-18", "to": "2026-09-19"}

    spring.calls.clear()
    runner(spring).run("list_accounts", {}, "tok")
    assert spring.calls == [("/accounts", "tok", {})]

    # Every tool must point at a path the Spring client will actually allow.
    assert {t.path for t in TOOLS.values()} <= ALLOWED_PATHS


def test_bad_calls_return_an_error_the_model_can_read_and_never_reach_spring():
    bad_calls = [
        ("get_everything", {}),                                                    # unknown tool
        ("get_summary", {"period": "this_month", "from_date": "2026-10-01"}),      # both period and dates
        ("get_summary", {"from_date": "2026-10-09", "to_date": "2026-10-01"}),     # reversed range
        ("get_summary", {"period": "next_decade"}),                                # not a real period
        ("get_summary", {"color": "red"}),                                         # invented parameter
        ("get_breakdown", {"period": "today"}),                                    # missing required `by`
        ("get_breakdown", {"by": "weather"}),                                      # bad enum value
        ("get_journal_entries", {"period": "all_time"}),                           # journal must be bounded
        ("get_journal_entries", {"from_date": "2025-01-01", "to_date": "2026-01-02"}),  # 367 days
    ]
    spring = FakeSpring()

    for tool, args in bad_calls:
        result = runner(spring).run(tool, args, "tok")

        assert result.ok is False, (tool, args)
        assert isinstance(result.payload["error"], str) and result.payload["error"], (tool, args)
    assert spring.calls == []


def test_spring_refusals_go_back_to_the_model_but_a_dead_token_ends_the_request():
    refused = FakeSpring(error=SpringToolError(403, "Unauthorized access: account not found or not yours."))
    result = runner(refused).run("get_summary", {"account_id": 99}, "tok")
    assert result.ok is False and "not yours" in result.payload["error"]

    with pytest.raises(SpringAuthError):  # not something the model can fix -> must propagate
        runner(FakeSpring(error=SpringAuthError())).run("get_summary", {}, "tok")

    # A year of long journal notes is cut, and the cut is announced, never silent.
    entries = [{"date": f"2026-01-{d:02d}", "notes": "x" * 2000, "htfBias": "Bullish"} for d in range(1, 29)]
    big = runner(FakeSpring(response=entries)).run("get_journal_entries", {"period": "last_30_days"}, "tok")
    assert big.ok and big.payload["truncated"] is True
    assert big.payload["count"] == 28          # the model is given the count, so it never has to count entries itself
    assert big.payload["returned"] < big.payload["total"] == 28
    assert len(str(big.payload["data"])) <= MAX_PAYLOAD_CHARS + 500

