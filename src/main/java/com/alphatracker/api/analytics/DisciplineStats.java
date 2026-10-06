package com.alphatracker.api.analytics;

// Trades split by the followedPlan flag. `unspecified` holds trades where the
// flag is null (logged before it defaulted to true) - they are reported
// separately rather than guessed into either side, and they are excluded from
// planFollowedRate.
//
// planFollowedRate = followedPlan.tradeCount / (followedPlan + brokePlan),
// null when no trade has the flag set.
public record DisciplineStats(
        Double planFollowedRate,
        StatSummary followedPlan,
        StatSummary brokePlan,
        StatSummary unspecified) {
}
