from datetime import date, timedelta

from app.periods import PERIOD_NAMES, resolve_period
from evals import oracle
from evals.dataset import ACCOUNTS, JOURNAL, TODAY, TRADES


def test_oracle_matches_figures_worked_out_by_hand():
    # October 1-14, all accounts. I added these up on paper from the dataset's comments,
    # so the answer key is pinned to something other than its own code.
    october = oracle.select(*(), start=date(2026, 10, 1), end=date(2026, 10, 14))
    result = oracle.summary(october)

    # 17 trades: wins 40+14+30+14+40+20+40+20+16+6 = 240 (10 of them), losses -40-20-40-8-20 = -128 (5),
    # and two exactly-zero trades.
    assert result["trades"] == {
        "tradeCount": 17, "wins": 10, "losses": 5, "neutral": 2,
        "winRate": 0.6667,          # 10 / (10 + 5): the neutral trades are NOT in the denominator
        "neutralRate": 0.1176,      # 2 / 17
        "totalPnl": 112.0, "avgWin": 24.0, "avgLoss": -25.6,
    }
    # 10 trading days. Oct 1 (+40 -40) and Oct 6 (a single 0) both net to exactly 0.
    assert result["days"] == {
        "tradingDays": 10, "winDays": 5, "lossDays": 3, "neutralDays": 2, "neutralDayRate": 0.2,
    }

    everything = oracle.select()
    assert len(everything) == 40
    assert oracle.stats(everything)["neutral"] == 5

    groups = {g["key"]: g["stats"] for g in oracle.breakdown(everything, "setup")}
    assert groups["(untagged)"]["tradeCount"] == 10                  # counted by listing them: Sep 8, 9, 12, 17, 23; Oct 1, 6, 7, 13, 14
    assert sum(g["tradeCount"] for g in groups.values()) > 40        # multi-tag trades are counted per tag

    # MCL has one trade and it lost: a 0% win rate that must come with "tiny sample".
    mcl = {g["key"]: g["stats"] for g in oracle.breakdown(everything, "instrument")}["MCL"]
    assert (mcl["tradeCount"], mcl["winRate"], mcl["avgWin"], mcl["avgLoss"]) == (1, 0.0, None, -100.0)


def test_oracles_period_dates_agree_with_the_apps_across_a_year_boundary_and_a_leap_year():
    # Two different methods of date maths must agree on every day of these ranges.
    days = [date(2025, 12, 1) + timedelta(days=i) for i in range(0, 90)]            # Dec 2025 -> Feb 2026
    days += [date(2024, 2, 20) + timedelta(days=i) for i in range(0, 20)]           # leap February 2024
    days += [date(2026, 9, 25) + timedelta(days=i) for i in range(0, 25)]           # the eval's own month ends

    for today in days:
        for period in PERIOD_NAMES:
            assert oracle.period_range(period, today) == resolve_period(period, today), (period, today)


def test_the_dataset_has_no_typos_that_would_quietly_corrupt_every_answer():
    for t in TRADES:
        assert t.ticker in oracle.POINT_VALUE, t
        assert t.direction in ("LONG", "SHORT"), t
        assert t.account in ACCOUNTS, t
        assert t.rating is None or 1 <= t.rating <= 5, t
        assert t.contracts >= 1 and t.entry > 0 and t.exit > 0, t
        assert date(2026, 9, 1) <= t.when.date() <= TODAY, t
    assert all(date(2026, 9, 1) <= n.day <= TODAY for n in JOURNAL)
    assert len({(t.when, t.ticker, t.account) for t in TRADES}) == len(TRADES)       # no accidental duplicate rows
