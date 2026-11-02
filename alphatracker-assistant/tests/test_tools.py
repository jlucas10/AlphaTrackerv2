from datetime import date

import pytest

from app.grounding import KnownNumbers
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


def test_list_accounts_adds_a_percent_the_grounding_check_will_accept():
    accounts = [
        {"id": 1, "name": "Near", "targetProgress": 0.7133, "targetRemaining": 860.0},
        {"id": 2, "name": "Past", "targetProgress": 1.0333, "targetRemaining": 0.0},
        {"id": 3, "name": "Behind", "targetProgress": -0.7, "targetRemaining": 5100.0},
        {"id": 4, "name": "Funded, no target", "targetProgress": None, "targetRemaining": None},
    ]
    spring = FakeSpring(response=accounts)

    data = runner(spring).run("list_accounts", {}, "tok").payload["data"]

    assert [a.get("targetProgressPercent") for a in data] == [71.3, 103.3, -70.0, None]
    assert "targetProgressPercent" not in data[3]            # null stays "no data": no invented 0%
    assert accounts[0].keys() == {"id", "name", "targetProgress", "targetRemaining"}   # the input is not mutated

    # The reason it exists: 103.3% (past the target) is not a number the check would otherwise know,
    # because it only derives a percent from values up to 1.
    known = KnownNumbers()
    known.add_payload(data)
    assert known.unknown_in("Past is at 103.3% of target with $0 left. Near has $860 left (71.3%).") == []

    only_ratio = KnownNumbers()
    only_ratio.add_payload(accounts)
    assert only_ratio.unknown_in("Past is at 103.3% of target.") == [103.3]


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

