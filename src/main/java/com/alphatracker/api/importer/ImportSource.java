package com.alphatracker.api.importer;

// Where an import file came from. The client sends this as the "source" field and the server picks the
// matching TradeImportParser. Adding Tradovate later means a new constant here plus a new adapter class,
// and nothing else in the importer changes.
public enum ImportSource {
    TOPSTEPX;

    // Parses the client's "source" text. Case-insensitive; anything unknown is a 400 that lists what works.
    public static ImportSource fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Choose an import source. Supported: " + supported() + ".");
        }
        for (ImportSource source : values()) {
            if (source.name().equalsIgnoreCase(value.trim())) {
                return source;
            }
        }
        throw new IllegalArgumentException(
                "Unsupported import source '" + value.trim() + "'. Supported: " + supported() + ".");
    }

    private static String supported() {
        return String.join(", ", java.util.Arrays.stream(values()).map(Enum::name).toList());
    }
}
