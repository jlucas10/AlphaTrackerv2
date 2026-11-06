package com.alphatracker.api.importer;

// Where an import file came from. The client sends this as the "source" field and the server picks the
// matching TradeImportParser. Adding Tradovate later means a new constant here plus a new adapter class,
// and nothing else in the importer changes.
public enum ImportSource {
    TOPSTEPX
}
