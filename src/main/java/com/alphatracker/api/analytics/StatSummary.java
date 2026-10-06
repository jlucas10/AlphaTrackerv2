package com.alphatracker.api.analytics;

// One set of headline numbers over some group of trades.
//
// A trade is a win (> 0), a loss (< 0) or neutral (exactly 0) - see TradeStats.
// tradeCount includes neutral trades; winRate deliberately does not (it is
// wins / (wins + losses)), but neutralRate is reported alongside it because a
// trader who scratches a lot of trades is giving up profit or has a leaking
// system, and the assistant should be able to see that.
//
// The nullable Doubles are deliberate: with no decided trades there is no win
// rate, and with no losing trades there is no average loss. Returning 0 there
// would be a lie the assistant (or a chart) could read as "0% win rate".
//
// Rates are 0-1 fractions, not rounded percents - presentation rounding is
// the caller's job. avgLoss is negative (the mean of the stored profitLoss).
public record StatSummary(
        int tradeCount,
        int wins,
        int losses,
        int neutral,
        Double winRate,
        Double neutralRate,
        double totalPnl,
        Double avgWin,
        Double avgLoss) {
}
