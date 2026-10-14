from app.grounding import KnownNumbers

# A trimmed tool result shaped like what Spring returns.
BREAKDOWN = {
    "data": {"groups": [{"key": "MNQ", "stats": {
        "tradeCount": 7, "wins": 3, "losses": 3, "neutral": 1,
        "winRate": 0.5, "neutralRate": 0.1429, "totalPnl": 38.0, "avgWin": 32.0, "avgLoss": -19.33}}]},
    "period_applied": {"period": "this_month", "from": "2026-10-01", "to": "2026-10-14"},
}


def test_only_figures_that_came_from_somewhere_are_accepted():
    known = KnownNumbers()
    known.add_text("TODAY is Wednesday, 2026-10-14. - id 3: Apex 50k #1")
    known.add_text("What's my MNQ win rate?")
    known.add_payload(BREAKDOWN)

    # Faithful answers, in the ways a model legitimately formats numbers.
    faithful = [
        "Your win rate is **50.0%** over 7 trades (3 wins, 3 losses, 1 neutral).",   # 0.5 shown as a percent
        "Neutral rate is 14.3% (about 14%). Total P&L is +$38, or $38.00.",           # 0.1429 -> 14.3 / 14
        "Average loss: -$19.33, average win $32 (October 1 to October 14, 2026).",   # dates from period_applied
        "On your Apex 50k #1 account.",                                               # numbers in account names
        "1. First point\n2. Second point\n3. Third point",                            # list numbering is not a figure
    ]
    for text in faithful:
        assert known.unknown_in(text) == [], text

    # The exact failure seen live: a follow-up answered with no tool call, figures invented.
    invented = "Your NQ win rate is 50.0%: 4 trades, total P&L -$140, average win $220, average loss -$290."
    assert known.unknown_in(invented) == [4.0, 140.0, 220.0, 290.0]

    # Arithmetic the model did itself is also caught: 3 + 3 + 1 would be 7, but 9 is a made-up total.
    assert known.unknown_in("That is 9 trades in total.") == [9.0]
