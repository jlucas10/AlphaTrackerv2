package com.alphatracker.api.analytics;

import java.time.LocalDate;
import java.util.List;

// GET /api/v1/analytics/breakdown?by=instrument|setup|rating. `note` is set
// only where the numbers are easy to misread (setup tags); it travels with the
// data so the assistant doesn't have to remember the caveat.
public record BreakdownResponse(
        String by,
        Long accountId,
        LocalDate from,
        LocalDate to,
        List<StatGroup> groups,
        String note) {
}
