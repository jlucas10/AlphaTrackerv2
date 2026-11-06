package com.alphatracker.api.importer;

import java.time.ZoneId;

// Limits and conventions shared by every source, in one place so the parsers, the endpoints and the tests
// all agree.
public final class ImportRules {

    private ImportRules() {
    }

    // A real export is ~200 bytes a row, so 1 MB is thousands of trades. Smaller than the 10 MB multipart cap
    // (application.yml), which exists for screenshots.
    public static final int MAX_FILE_BYTES = 1_000_000;

    public static final int MAX_ROWS = 2_000;

    // The journal's calendar, the drawdown replay and every manual entry use a zone-less LocalDateTime, so an
    // import has to pick ONE zone to express its instants in. Decided by the owner: Los Angeles. Export
    // timestamps carry their own offset, so converting by instant (not by copying the wall clock) puts a trade
    // on the right day even across the daylight-saving change.
    public static final ZoneId JOURNAL_ZONE = ZoneId.of("America/Los_Angeles");
}
