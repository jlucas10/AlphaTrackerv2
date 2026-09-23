package com.alphatracker.api.exception;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.alphatracker.api.storage.StorageException;

// Turns thrown exceptions into JSON the frontend can actually read.
//
// Spring's default error body omits the exception message unless
// server.error.include-message=always, so a rejected trade reached the modal as
// an opaque 500 and the UI could only show its generic fallback text. Every
// response here carries a "message" key, which is what the modal reads.
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Bad input from the client: unsupported ticker, missing price, contracts < 1.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    }

    // Ownership boundary violations from TradeService are a 403, not a 500.
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, String>> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }

    // Disk/S3 I/O failures underneath StorageService (or a corrupted storage
    // key escaping its base path) are an infrastructure problem, not
    // something the client did wrong - 500, unlike the two handlers above.
    // Logged at ERROR with the full cause chain: the client only ever gets
    // ex.getMessage() (deliberately, so internals like bucket names or file
    // paths never leak into a response body), so without this log line a
    // failure here is completely unobservable from the server side.
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<Map<String, String>> handleStorage(StorageException ex) {
        log.error("Storage operation failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", ex.getMessage()));
    }

    // Spring rejects an over-limit multipart body (application.yml,
    // spring.servlet.multipart.max-file-size) before TradeAttachmentService's
    // own size check ever runs - without this handler it surfaces as a bare
    // 500 with no "message" key, unlike every other rejection in this app.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("message", "Uploaded file exceeds the maximum allowed size."));
    }
}
