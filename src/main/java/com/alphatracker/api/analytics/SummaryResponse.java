package com.alphatracker.api.analytics;

import java.time.LocalDate;

// GET /api/v1/analytics/summary. `from`/`to`/`accountId` echo the filter that
// was applied (null = unfiltered), so a caller - or the assistant reading the
// result - can state exactly what the numbers cover.
public record SummaryResponse(
        Long accountId,
        LocalDate from,
        LocalDate to,
        StatSummary trades,
        DayStats days) {
}
