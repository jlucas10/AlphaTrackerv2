"""Does the Java API agree with the independent answer key?

Seeds the eval trader, then asks Spring the same questions the oracle answers from
the raw dataset - every period x account (all / A / B) for summary, the three
breakdowns and discipline, plus journal ranges and account balances - and reports
every difference.

Any mismatch means one side is wrong. That is the point: the oracle was written
separately from the Java code, so agreement is real evidence, and a disagreement
is either a Java bug or an oracle bug worth knowing about before the model is
graded against either of them.

    python -m evals.verify_oracle [--spring-url http://localhost:8080/api/v1]
"""

import argparse
import sys
from datetime import date
from typing import Any

from app.config import Settings
from app.periods import PERIOD_NAMES
from app.spring_client import SpringClient
from evals import oracle
from evals.dataset import ACCOUNTS, TODAY
from evals.seed import SeededEval, seed

_MISSING = object()
_TOLERANCE = 0.0051  # half a cent: Spring rounds money to cents, the oracle rounds exactly


def compare(label: str, got: Any, want: Any, problems: list[str]) -> None:
    """Record every place `got` differs from `want`. Extra keys in `got` are fine; missing ones are not.

    Strict about 'no data': None must equal None. A 0 where the answer key says None
    (or the reverse) is exactly the kind of bug that makes an assistant report a
    fake '0% win rate', so it must never pass.
    """
    if isinstance(want, dict):
        if not isinstance(got, dict):
            problems.append(f"{label}: expected an object, got {got!r}")
            return
        for key, expected in want.items():
            compare(f"{label}.{key}", got.get(key, _MISSING), expected, problems)
    elif isinstance(want, list):
        if not isinstance(got, list) or len(got) != len(want):
            problems.append(f"{label}: expected {len(want)} items, got {len(got) if isinstance(got, list) else got!r}")
            return
        for index, (g, w) in enumerate(zip(got, want)):
            compare(f"{label}[{index}]", g, w, problems)
    elif want is None:
        if got is not None:
            problems.append(f"{label}: expected null (no data), got {got!r}")
    elif isinstance(want, (int, float)) and not isinstance(want, bool):
        if got is _MISSING or not isinstance(got, (int, float)) or abs(got - want) > _TOLERANCE:
            problems.append(f"{label}: expected {want!r}, got {got!r}")
    elif got != want:
        problems.append(f"{label}: expected {want!r}, got {got!r}")


def run(seeded: SeededEval) -> tuple[int, list[str]]:
    spring = SpringClient(seeded.base_url)
    token = seeded.token
    problems: list[str] = []
    checks = 0

    def check(label: str, got: Any, want: Any) -> None:
        nonlocal checks
        checks += 1
        compare(label, got, want, problems)

    for period in PERIOD_NAMES:
        start, end = oracle.period_range(period, TODAY)
        for account in (None, *ACCOUNTS):
            trades = oracle.select(account=account, start=start, end=end)
            window = {
                "accountId": seeded.account_ids[account] if account else None,
                "from": start.isoformat() if start else None,
                "to": end.isoformat() if end else None,
            }
            where = f"[{period}, account={account or 'all'}]"

            summary = spring.get("/analytics/summary", token, window)
            check(f"summary{where}", {"trades": summary["trades"], "days": summary["days"]}, oracle.summary(trades))

            for by in ("instrument", "setup", "rating"):
                got = spring.get("/analytics/breakdown", token, {**window, "by": by})
                check(f"breakdown.{by}{where}", got["groups"], oracle.breakdown(trades, by))

            got = spring.get("/analytics/discipline", token, window)
            check(f"discipline{where}", got["discipline"], oracle.discipline(trades))

    for start, end in ((date(2026, 9, 1), date(2026, 9, 30)), (date(2026, 10, 1), date(2026, 10, 14)),
                       (date(2026, 9, 1), date(2026, 10, 14)), (date(2026, 10, 12), date(2026, 10, 13))):
        got = spring.get("/journal", token, {"from": start.isoformat(), "to": end.isoformat()})
        check(f"journal[{start}..{end}]", got, oracle.journal(start, end))

    balances = {a["name"]: a["currentBalance"] for a in spring.get("/accounts", token)}
    for key, spec in ACCOUNTS.items():
        check(f"balance[{spec.name}]", balances.get(spec.name), oracle.account_balance(key))

    return checks, problems


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare the Spring API with the independent answer key.")
    parser.add_argument("--spring-url", default=Settings().spring_api_base_url)
    args = parser.parse_args()

    checks, problems = run(seed(args.spring_url))
    if problems:
        print(f"MISMATCH: {len(problems)} difference(s) across {checks} comparisons. First {min(len(problems), 25)}:")
        for line in problems[:25]:
            print("  -", line)
        sys.exit(1)
    print(f"OK: Spring and the oracle agree on all {checks} comparisons.")


if __name__ == "__main__":
    main()
