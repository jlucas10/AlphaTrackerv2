package com.alphatracker.api.demo;

// The shared demo account tried something it may not do. Mapped to HTTP 403 in GlobalExceptionHandler.
public class DemoForbiddenException extends RuntimeException {

    public DemoForbiddenException(String message) {
        super(message);
    }
}
