package com.alphatracker.api.importer;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.alphatracker.api.trade.Instrument;

// Adapter for the TopstepX "trades" CSV export. Everything here comes from the real sample in
// src/test/resources/imports/topstepx/, not from documentation:
//
//   Id,ContractName,EnteredAt,ExitedAt,EntryPrice,ExitPrice,Fees,PnL,Size,Type,TradeDay,TradeDuration,Commissions
//
// - Each row is a COMPLETED trade (entry and exit together), not a fill, so nothing has to be paired.
//   When TopstepX closes one position in pieces it reports one row per piece, each with its own Id;
//   those are imported as separate trades, exactly as the broker lists them.
// - Timestamps look like "10/13/2026 07:45:11 -07:00": the offset travels with each value, so they are
//   converted by instant into the journal zone rather than having the wall clock copied.
// - `Fees`, `Commissions`, `TradeDay` and `TradeDuration` are deliberately ignored (owner decision: no fees
//   in AlphaTracker). `PnL` is read only as a cross-check; our own derivation decides the real P&L.
@Component
public class TopStepXParser implements TradeImportParser {

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "Id", "ContractName", "EnteredAt", "ExitedAt", "EntryPrice", "ExitPrice", "Size", "Type");

    // uuuu + STRICT so 02/30/2026 is an error instead of being nudged to a real date.
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("MM/dd/uuuu HH:mm:ss xxx", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    // "MNQZ6" = root + month code (F G H J K M N Q U V X Z) + 1-2 digit year. Matched at the END of the
    // string and the root is then looked up EXACTLY in Instrument, so the Q in MNQ can never be mistaken
    // for a month code (MNQ alone has no month+year suffix and is refused as an incomplete contract).
    private static final Pattern CONTRACT_SUFFIX = Pattern.compile("[FGHJKMNQUVXZ]\\d{1,2}$");

    @Override
    public ImportSource source() {
        return ImportSource.TOPSTEPX;
    }

    @Override
    public ParsedImport parse(InputStream file) {
        List<CsvReader.Record> records = CsvReader.read(file);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("The file is empty.");
        }

        Map<String, Integer> columns = indexColumns(records.get(0));
        List<String> missing = REQUIRED_COLUMNS.stream()
                .filter(name -> !columns.containsKey(name.toLowerCase(Locale.ROOT)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("This does not look like a TopstepX trades export. Missing column(s): "
                    + String.join(", ", missing) + ".");
        }

        List<CsvReader.Record> rows = records.stream().skip(1).filter(r -> !r.isBlank()).toList();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The file has a header but no trades.");
        }
        if (rows.size() > ImportRules.MAX_ROWS) {
            throw new IllegalArgumentException("The file has " + rows.size() + " rows; the limit is "
                    + ImportRules.MAX_ROWS + ". Export a shorter date range and try again.");
        }

        List<ImportedTrade> trades = new ArrayList<>();
        List<ImportRowError> errors = new ArrayList<>();
        Map<String, Integer> firstLineOfId = new HashMap<>();

        for (CsvReader.Record row : rows) {
            String id = null;
            try {
                if (row.fields().size() != records.get(0).fields().size()) {
                    throw new IllegalArgumentException("Expected " + records.get(0).fields().size()
                            + " columns but found " + row.fields().size() + ".");
                }
                id = cell(row, columns, "Id");
                if (id.isEmpty()) {
                    throw new IllegalArgumentException("The Id is empty.");
                }
                Integer seenAt = firstLineOfId.putIfAbsent(id, row.line());
                if (seenAt != null) {
                    throw new IllegalArgumentException("Duplicate of the trade on line " + seenAt + " (same Id).");
                }
                trades.add(toTrade(row, columns, id));
            } catch (IllegalArgumentException e) {
                errors.add(new ImportRowError(row.line(), id, e.getMessage()));
            }
        }
        return new ParsedImport(trades, errors);
    }

    private ImportedTrade toTrade(CsvReader.Record row, Map<String, Integer> columns, String id) {
        Instrument instrument = parseInstrument(cell(row, columns, "ContractName"));
        String direction = parseDirection(cell(row, columns, "Type"));
        double entryPrice = parsePrice("EntryPrice", cell(row, columns, "EntryPrice"));
        double exitPrice = parsePrice("ExitPrice", cell(row, columns, "ExitPrice"));
        int contracts = parseContracts(cell(row, columns, "Size"));

        OffsetDateTime entered = parseTimestamp("EnteredAt", cell(row, columns, "EnteredAt"));
        OffsetDateTime exited = parseTimestamp("ExitedAt", cell(row, columns, "ExitedAt"));
        // Compared as instants: at the daylight-saving change two different moments can show the same
        // wall-clock time, so comparing the converted LocalDateTimes would be wrong.
        if (exited.isBefore(entered)) {
            throw new IllegalArgumentException("ExitedAt is before EnteredAt.");
        }

        return new ImportedTrade(row.line(), id, instrument, direction, entryPrice, exitPrice, contracts,
                toJournalTime(entered), toJournalTime(exited), parseOptionalPnl(row, columns));
    }

    private static LocalDateTime toJournalTime(OffsetDateTime value) {
        return value.atZoneSameInstant(ImportRules.JOURNAL_ZONE).toLocalDateTime();
    }

    // Lower-cased header -> position. Names are matched case-insensitively and trimmed.
    private static Map<String, Integer> indexColumns(CsvReader.Record header) {
        Map<String, Integer> columns = new HashMap<>();
        for (int i = 0; i < header.fields().size(); i++) {
            columns.putIfAbsent(header.fields().get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        return columns;
    }

    private static String cell(CsvReader.Record row, Map<String, Integer> columns, String name) {
        return row.fields().get(columns.get(name.toLowerCase(Locale.ROOT))).trim();
    }

    static Instrument parseInstrument(String contractName) {
        String normalized = contractName.trim().toUpperCase(Locale.ROOT);
        Matcher suffix = CONTRACT_SUFFIX.matcher(normalized);
        if (normalized.isEmpty() || !suffix.find()) {
            throw new IllegalArgumentException("Contract '" + contractName
                    + "' is not a contract with a month and year (for example MNQZ6).");
        }
        String root = normalized.substring(0, suffix.start());
        for (Instrument instrument : Instrument.values()) {
            if (instrument.name().equals(root)) {
                return instrument;
            }
        }
        throw new IllegalArgumentException("Unsupported contract '" + contractName + "' (instrument " + root
                + "). Supported: " + Instrument.supportedTickers() + ".");
    }

    private static String parseDirection(String type) {
        if (type.equalsIgnoreCase("Long")) {
            return "LONG";
        }
        if (type.equalsIgnoreCase("Short")) {
            return "SHORT";
        }
        throw new IllegalArgumentException("Type must be Long or Short, got '" + type + "'.");
    }

    private static double parsePrice(String name, String text) {
        double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " '" + text + "' is not a number.");
        }
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero.");
        }
        return value;
    }

    // Size is a whole number of contracts. "5" and "5.0000" are fine; "2.5" is not.
    private static int parseContracts(String text) {
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Size '" + text + "' is not a number.");
        }
        if (value.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("Size must be a whole number of contracts, got '" + text + "'.");
        }
        if (value.signum() < 1 || value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException("Size must be at least 1, got '" + text + "'.");
        }
        return value.intValueExact();
    }

    private static OffsetDateTime parseTimestamp(String name, String text) {
        try {
            return OffsetDateTime.parse(text, TIMESTAMP);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " '" + text
                    + "' is not a date like 10/13/2026 07:45:11 -07:00.");
        }
    }

    // PnL is optional context. A missing column or an unreadable value is not worth rejecting a row over:
    // it only feeds a warning later.
    private static Double parseOptionalPnl(CsvReader.Record row, Map<String, Integer> columns) {
        if (!columns.containsKey("pnl")) {
            return null;
        }
        String text = cell(row, columns, "PnL");
        if (text.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(text);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
