package com.alphatracker.api.importer;

import java.io.InputStream;

// The seam between "a broker's file format" and the rest of AlphaTracker. One implementation per source,
// picked by ImportSource, the same shape as StorageService with its local and S3 adapters.
//
// An adapter's whole job is to turn a file into ImportedTrade rows. It must not touch the database, compute
// P&L or know about accounts: everything after parsing is source-independent.
public interface TradeImportParser {

    ImportSource source();

    // Throws IllegalArgumentException (message shown to the trader) when the whole file is unusable.
    ParsedImport parse(InputStream file);
}
