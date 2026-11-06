package com.alphatracker.api.importer;

import java.util.List;

// What a parser returns for a readable file. A bad ROW lands in `errors` and never stops the rest of the file.
// A bad FILE (empty, not a CSV, missing columns, over the limits) is not represented here at all: the parser
// throws IllegalArgumentException, which GlobalExceptionHandler already turns into a 400 with the message.
public record ParsedImport(List<ImportedTrade> trades, List<ImportRowError> errors) {
}
