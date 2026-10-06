package com.alphatracker.api.analytics;

// Day-level view: each calendar day's trades are netted together, then the
// day is a win day (net > 0), loss day (net < 0) or neutral day (net exactly
// 0). neutralDayRate = neutralDays / tradingDays - a high value means the
// trader keeps ending the day back at the start point.
public record DayStats(
        int tradingDays,
        int winDays,
        int lossDays,
        int neutralDays,
        Double neutralDayRate) {
}
