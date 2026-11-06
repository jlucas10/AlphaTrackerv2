package com.alphatracker.api;

import com.alphatracker.api.importer.ImportRowError;
import com.alphatracker.api.importer.ImportRules;
import com.alphatracker.api.importer.ImportSource;
import com.alphatracker.api.importer.ImportedTrade;
import com.alphatracker.api.importer.ParsedImport;
import com.alphatracker.api.importer.TopStepXParser;
import com.alphatracker.api.trade.Instrument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Pure unit tests - the parser has no Spring or DB dependency. The first group runs the REAL TopstepX sample;
public class TopStepXParserTest {

    private static final String HEADER = "Id,ContractName,EnteredAt,ExitedAt,EntryPrice,ExitPrice,Fees,PnL,Size,Type,TradeDay,TradeDuration,Commissions";

    private final TopStepXParser parser = new TopStepXParser();

    // ---- helpers ----

    // One data row in the real export's column order. Fees/Commissions/TradeDay/Duration are filler.
    private static String row(String id, String contract, String entered, String exited,
            String entry, String exit, String pnl, String size, String type) {
        return String.join(",", id, contract, entered, exited, entry, exit, "3.6000", pnl, size, type,
                "10/13/2026 00:00:00 -05:00", "00:01:00", "2.5000");
    }

    private static String goodRow(String id) {
        return row(id, "MNQZ6", "10/13/2026 07:45:11 -07:00", "10/13/2026 07:51:56 -07:00",
                "30756.75", "30723.75", "-330", "5", "Long");
    }

    private static InputStream csv(String... lines) {
        return new ByteArrayInputStream((String.join("\r\n", lines) + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private ParsedImport parse(String... lines) {
        return parser.parse(csv(lines));
    }

    private static InputStream sample() {
        InputStream in = TopStepXParserTest.class.getResourceAsStream("/imports/topstepx/trades_export.csv");
        assertNotNull(in, "sample fixture missing");
        return in;
    }

    // ---- the real sample ----

    @Test
    @DisplayName("source is TOPSTEPX")
    void sourceIsTopstepx() {
        assertEquals(ImportSource.TOPSTEPX, parser.source());
    }

    @Test
    @DisplayName("real sample: all 21 rows parse, none rejected (BOM + CRLF handled)")
    void realSampleParsesEveryRow() {
        ParsedImport result = parser.parse(sample());

        assertEquals(21, result.trades().size());
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(21, result.trades().stream().map(ImportedTrade::externalId).distinct().count());
    }

    @Test
    @DisplayName("real sample: first row is normalized field by field")
    void realSampleFirstRow() {
        ImportedTrade t = parser.parse(sample()).trades().get(0);

        assertEquals(2, t.line()); // header is line 1
        assertEquals("3198364077", t.externalId());
        assertEquals(Instrument.MNQ, t.instrument());
        assertEquals("LONG", t.direction());
        assertEquals(30756.75, t.entryPrice());
        assertEquals(30723.75, t.exitPrice());
        assertEquals(5, t.contracts());
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 45, 11), t.entryTime());
        assertEquals(LocalDateTime.of(2026, 10, 13, 7, 51, 56), t.exitTime());
        assertEquals(-330.0, t.sourcePnl());
    }

    @Test
    @DisplayName("real sample: the broker's own P&L matches the gross formula on every row, and sums to $495")
    void realSampleSourcePnlMatchesGrossFormula() {
        List<ImportedTrade> trades = parser.parse(sample()).trades();
        double sourceTotal = 0;
        for (ImportedTrade t : trades) {
            double move = t.direction().equals("LONG") ? t.exitPrice() - t.entryPrice() : t.entryPrice() - t.exitPrice();
            double derived = Math.round(move * t.instrument().getPointValue() * t.contracts() * 100.0) / 100.0;
            assertEquals(derived, t.sourcePnl(), 1e-9, "row " + t.externalId());
            sourceTotal += t.sourcePnl();
        }
        assertEquals(495.0, sourceTotal, 1e-9);
    }

    @Test
    @DisplayName("real sample: shorts are read as SHORT")
    void realSampleHasShorts() {
        List<ImportedTrade> trades = parser.parse(sample()).trades();
        assertEquals(7, trades.stream().filter(t -> t.direction().equals("SHORT")).count());
    }

    @Test
    @DisplayName("real sample: the two split exits (same entry, different Id) stay two separate trades")
    void realSampleSplitExitsStaySeparate() {
        List<ImportedTrade> trades = parser.parse(sample()).trades();
        ImportedTrade first = trades.stream().filter(t -> t.externalId().equals("3263153165")).findFirst().orElseThrow();
        ImportedTrade second = trades.stream().filter(t -> t.externalId().equals("3263385896")).findFirst().orElseThrow();

        assertEquals(first.entryTime(), second.entryTime());
        assertEquals(first.entryPrice(), second.entryPrice());
        assertNotEquals(first.exitTime(), second.exitTime());
    }

    // ---- rejected rows (the rest of the file still imports) ----

    @Test
    @DisplayName("unknown instrument: that row is rejected with a clear message, others survive")
    void unknownInstrumentIsRejectedPerRow() {
        ParsedImport result = parse(HEADER,
                goodRow("1"),
                row("2", "XYZZ6", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Long"),
                goodRow("3"));

        assertEquals(2, result.trades().size());
        assertEquals(1, result.errors().size());
        ImportRowError error = result.errors().get(0);
        assertEquals(3, error.line());
        assertEquals("2", error.externalId());
        assertTrue(error.message().contains("Unsupported contract 'XYZZ6'"), error.message());
        assertTrue(error.message().contains("MNQ"), "message should list what IS supported");
    }

    @Test
    @DisplayName("a contract without month/year is refused, not guessed (MNQ alone: the Q is not a month code)")
    void contractWithoutMonthIsRefused() {
        ParsedImport result = parse(HEADER,
                row("1", "MNQ", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Long"));

        assertTrue(result.trades().isEmpty());
        assertTrue(result.errors().get(0).message().contains("month and year"), result.errors().get(0).message());
    }

    @Test
    @DisplayName("month-coded contracts resolve to the right enum: MNQZ6, ESH7, M2KM6, MCLZ26, GCG7")
    void monthCodesMapToInstruments() {
        ParsedImport result = parse(HEADER,
                row("1", "MNQZ6", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Long"),
                row("2", "ESH7", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Long"),
                row("3", "M2KM6", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Long"),
                row("4", "MCLZ26", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Long"),
                row("5", "gcg7", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Long"));

        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(List.of(Instrument.MNQ, Instrument.ES, Instrument.M2K, Instrument.MCL, Instrument.GC),
                result.trades().stream().map(ImportedTrade::instrument).toList());
    }

    @Test
    @DisplayName("duplicate Id inside one file: the second occurrence is an error pointing at the first")
    void duplicateIdInsideFile() {
        ParsedImport result = parse(HEADER, goodRow("1"), goodRow("2"), goodRow("1"));

        assertEquals(2, result.trades().size());
        assertEquals(1, result.errors().size());
        assertEquals(4, result.errors().get(0).line());
        assertTrue(result.errors().get(0).message().contains("line 2"), result.errors().get(0).message());
    }

    @Test
    @DisplayName("bad values are row errors: direction, price, size, date, exit before entry, wrong column count")
    void badRowValues() {
        ParsedImport result = parse(HEADER,
                row("1", "MNQZ6", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Sideways"),
                row("2", "MNQZ6", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "abc", "101", "1", "1", "Long"),
                row("3", "MNQZ6", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "0", "Long"),
                row("4", "MNQZ6", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "2.5", "Long"),
                row("5", "MNQZ6", "13/10/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Long"),
                row("6", "MNQZ6", "10/13/2026 08:10:00 -07:00", "10/13/2026 08:05:00 -07:00", "100", "101", "1", "1", "Long"),
                row("7", "MNQZ6", "10/13/2026 08:00:00 -07:00", "10/13/2026 08:05:00 -07:00", "-5", "101", "1", "1", "Long"),
                "8,MNQZ6,too,few,columns");

        assertTrue(result.trades().isEmpty());
        assertEquals(8, result.errors().size());
        assertTrue(result.errors().get(0).message().contains("Long or Short"));
        assertTrue(result.errors().get(1).message().contains("EntryPrice 'abc'"));
        assertTrue(result.errors().get(2).message().contains("at least 1"));
        assertTrue(result.errors().get(3).message().contains("whole number"));
        assertTrue(result.errors().get(4).message().contains("EnteredAt"));
        assertTrue(result.errors().get(5).message().contains("before EnteredAt"));
        assertTrue(result.errors().get(6).message().contains("greater than zero"));
        assertTrue(result.errors().get(7).message().contains("columns"));
    }

    @Test
    @DisplayName("a file without a PnL column still parses (sourcePnl is null)")
    void missingPnlColumnIsFine() {
        ParsedImport result = parse("Id,ContractName,EnteredAt,ExitedAt,EntryPrice,ExitPrice,Size,Type",
                "1,MNQZ6,10/13/2026 08:00:00 -07:00,10/13/2026 08:05:00 -07:00,100,101,1,Long");

        assertEquals(1, result.trades().size());
        assertNull(result.trades().get(0).sourcePnl());
    }

    @Test
    @DisplayName("header names are matched case-insensitively and a quoted field parses")
    void headerCaseAndQuotes() {
        ParsedImport result = parse("id,contractname,enteredat,exitedat,entryprice,exitprice,size,type",
                "\"1\",\"MNQZ6\",\"10/13/2026 08:00:00 -07:00\",10/13/2026 08:05:00 -07:00,100,101,1,long");

        assertEquals(1, result.trades().size(), result.errors().toString());
        assertEquals("LONG", result.trades().get(0).direction());
    }

    // ---- unusable files (thrown, becomes a 400) ----

    @Test
    @DisplayName("empty file is refused")
    void emptyFile() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new ByteArrayInputStream(new byte[0])));
        assertEquals("The file is empty.", e.getMessage());
    }

    @Test
    @DisplayName("header only (no trades) is refused")
    void headerOnly() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(HEADER));
        assertEquals("The file has a header but no trades.", e.getMessage());
    }

    @Test
    @DisplayName("missing required columns are named in the message")
    void missingRequiredColumns() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("Id,ContractName,EnteredAt,EntryPrice,Size", "1,MNQZ6,x,1,1"));
        assertTrue(e.getMessage().contains("ExitedAt"), e.getMessage());
        assertTrue(e.getMessage().contains("ExitPrice"), e.getMessage());
        assertTrue(e.getMessage().contains("Type"), e.getMessage());
        assertFalse(e.getMessage().contains("Size"), "columns that ARE present must not be listed");
    }

    @Test
    @DisplayName("binary content renamed to .csv is refused")
    void binaryContent() {
        byte[] pdfLike = {'%', 'P', 'D', 'F', '-', '1', '.', '7', 0, 0, 1, 2, 3};
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new ByteArrayInputStream(pdfLike)));
        assertTrue(e.getMessage().contains("does not look like a CSV"), e.getMessage());
    }

    @Test
    @DisplayName("text from another source (wrong columns) is refused as not a TopstepX export")
    void wrongFormat() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("Symbol,Qty,Price", "MNQ,1,100"));
        assertTrue(e.getMessage().contains("TopstepX"), e.getMessage());
    }

    @Test
    @DisplayName("file over the size limit is refused")
    void oversizedFile() {
        byte[] big = new byte[ImportRules.MAX_FILE_BYTES + 1];
        java.util.Arrays.fill(big, (byte) 'a');
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parser.parse(new ByteArrayInputStream(big)));
        assertTrue(e.getMessage().contains("larger than"), e.getMessage());
    }

    @Test
    @DisplayName("more rows than the limit is refused")
    void tooManyRows() {
        String[] lines = new String[ImportRules.MAX_ROWS + 2];
        lines[0] = HEADER;
        for (int i = 1; i < lines.length; i++) {
            lines[i] = goodRow(String.valueOf(i));
        }
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse(lines));
        assertTrue(e.getMessage().contains("limit is " + ImportRules.MAX_ROWS), e.getMessage());
    }

    @Test
    @DisplayName("an unclosed quote is refused as an invalid CSV")
    void unclosedQuote() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse(HEADER, "\"1,MNQZ6,x"));
        assertTrue(e.getMessage().contains("unclosed quote"), e.getMessage());
    }

    // ---- time zone: timestamps carry their own offset and land on the right journal day ----

    private LocalDateTime enteredAt(String entered, String exited) {
        ParsedImport result = parse(HEADER, row("1", "MNQZ6", entered, exited, "100", "101", "1", "1", "Long"));
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        return result.trades().get(0).entryTime();
    }

    @Test
    @DisplayName("midnight boundary: 23:59:59 stays on the day, 00:00:00 is the next day (journal zone)")
    void midnightBoundary() {
        // -08:00 is Los Angeles standard time (after the 11/01/2026 change), so the wall clock is unchanged.
        LocalDateTime lastSecond = enteredAt("11/03/2026 23:59:59 -08:00", "11/04/2026 00:10:00 -08:00");
        LocalDateTime firstSecond = enteredAt("11/04/2026 00:00:00 -08:00", "11/04/2026 00:10:00 -08:00");

        assertEquals(LocalDateTime.of(2026, 11, 3, 23, 59, 59), lastSecond);
        assertEquals(LocalDateTime.of(2026, 11, 4, 0, 0, 0), firstSecond);
        assertNotEquals(lastSecond.toLocalDate(), firstSecond.toLocalDate());
    }

    @Test
    @DisplayName("a trade that STARTS before midnight and ENDS after it belongs to the entry day")
    void tradeSpanningMidnightUsesEntryDay() {
        ParsedImport result = parse(HEADER,
                row("1", "MNQZ6", "11/03/2026 23:50:00 -08:00", "11/04/2026 00:20:00 -08:00", "100", "101", "1", "1", "Long"));

        ImportedTrade t = result.trades().get(0);
        assertEquals(LocalDateTime.of(2026, 11, 3, 23, 50), t.entryTime());
        assertEquals(LocalDateTime.of(2026, 11, 4, 0, 20), t.exitTime());
    }

    @Test
    @DisplayName("an export in another zone is converted by instant: 01:30 -05:00 (Chicago) is the previous evening in LA")
    void otherOffsetIsConvertedByInstant() {
        // 01:30 at -05:00 is 06:30 UTC = 23:30 the previous day in Los Angeles (PDT, -07:00).
        LocalDateTime converted = enteredAt("10/14/2026 01:30:00 -05:00", "10/14/2026 02:00:00 -05:00");
        assertEquals(LocalDateTime.of(2026, 10, 13, 23, 30), converted);
    }

    @Test
    @DisplayName("daylight saving: -07:00 and -08:00 rows around the 11/01/2026 change both convert correctly")
    void daylightSavingChange() {
        // PDT -07:00 until 11/01 02:00; PST -08:00 after. 08:00 on either side stays 08:00 in LA.
        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 0),
                enteredAt("10/30/2026 08:00:00 -07:00", "10/30/2026 08:05:00 -07:00"));
        assertEquals(LocalDateTime.of(2026, 11, 2, 8, 0),
                enteredAt("11/02/2026 08:00:00 -08:00", "11/02/2026 08:05:00 -08:00"));
    }

    @Test
    @DisplayName("the repeated 01:30 hour: an exit at 01:30 -08:00 after an entry at 01:30 -07:00 is valid (compared as instants)")
    void repeatedHourExitIsNotBeforeEntry() {
        ParsedImport result = parse(HEADER,
                row("1", "MNQZ6", "11/01/2026 01:30:00 -07:00", "11/01/2026 01:30:00 -08:00", "100", "101", "1", "1", "Long"));

        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(1, result.trades().size());
    }
}
