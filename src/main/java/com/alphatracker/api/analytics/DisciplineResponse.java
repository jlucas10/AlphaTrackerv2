package com.alphatracker.api.analytics;

import java.time.LocalDate;

// GET /api/v1/analytics/discipline - trades split by the followedPlan flag.
public record DisciplineResponse(
        Long accountId,
        LocalDate from,
        LocalDate to,
        DisciplineStats discipline) {
}
