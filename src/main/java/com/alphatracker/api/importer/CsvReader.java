package com.alphatracker.api.importer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// A small RFC 4180 reader (quoted fields, "" escapes, CRLF or LF, a leading UTF-8 BOM), plus the file-level
// checks every CSV source needs. Hand-written instead of adding a dependency: it is ~60 lines, the sample
// export needs exactly this much, and the file-level rules below are ours either way.
//
// The BOM matters: TopstepX files start with one, so without stripping it the first header reads as
// "﻿Id" and the Id column looks missing.
final class CsvReader {

    private CsvReader() {
    }

    // A record and the line it STARTED on (a quoted field may span lines).
    record Record(int line, List<String> fields) {
        boolean isBlank() {
            return fields.stream().allMatch(String::isBlank);
        }
    }

    static List<Record> read(InputStream in) {
        String text = decode(readBounded(in));
        List<Record> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int recordLine = 1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                fields.add(field.toString());
                field.setLength(0);
                records.add(new Record(recordLine, fields));
                fields = new ArrayList<>();
                line++;
                recordLine = line;
            } else {
                field.append(c);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException(
                    "The file has an unclosed quote starting near line " + recordLine + "; it is not a valid CSV.");
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            records.add(new Record(recordLine, fields));
        }
        return records;
    }

    // Reads at most the limit + 1 byte, so an oversized upload is refused without buffering all of it.
    private static byte[] readBounded(InputStream in) {
        try {
            byte[] bytes = in.readNBytes(ImportRules.MAX_FILE_BYTES + 1);
            if (bytes.length > ImportRules.MAX_FILE_BYTES) {
                throw new IllegalArgumentException("The file is larger than the " + (ImportRules.MAX_FILE_BYTES / 1_000_000)
                        + " MB import limit. Export a shorter date range and try again.");
            }
            return bytes;
        } catch (IOException e) {
            throw new IllegalArgumentException("The file could not be read.");
        }
    }

    // Rejects anything that is not UTF-8 text. A NUL byte is the cheap tell for binary files (PDF, xlsx, images)
    // that happen to be renamed to .csv.
    private static String decode(byte[] bytes) {
        for (byte b : bytes) {
            if (b == 0) {
                throw new IllegalArgumentException("This does not look like a CSV file. Upload the .csv exported from your broker.");
            }
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("This does not look like a CSV file. Upload the .csv exported from your broker.");
        }
        return text.startsWith("﻿") ? text.substring(1) : text;
    }
}
