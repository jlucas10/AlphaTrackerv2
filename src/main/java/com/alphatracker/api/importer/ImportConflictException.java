package com.alphatracker.api.importer;

// Thrown when the unique key (account, source, external id) refuses an insert the preview said was new: the
// same trades were imported by a concurrent request between our duplicate check and our write. The whole
// import has rolled back; the trader can simply preview again.
public class ImportConflictException extends RuntimeException {
    public ImportConflictException(String message) {
        super(message);
    }
}
