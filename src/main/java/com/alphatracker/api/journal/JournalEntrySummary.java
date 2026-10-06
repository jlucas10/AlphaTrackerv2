package com.alphatracker.api.journal;

import java.time.LocalDate;

// One row of GET /api/v1/journal?from=&to= - just the day's reflection text.
// Deliberately NOT the full day bundle (no trades, no attachments): this is
// for scanning a stretch of days, and the assistant only needs the words.
public record JournalEntrySummary(LocalDate date, String notes, String htfBias) {

    public static JournalEntrySummary fromEntity(JournalEntry entry) {
        return new JournalEntrySummary(entry.getEntryDate(), entry.getNotes(), entry.getHtfBias());
    }
}
