package com.aman.authservice.exception;

/**
 * Exception thrown when token validation fails (expired, invalid, malformed).
 */
public class TokenValidationException extends RuntimeException {
    public TokenValidationException(String message) {
        super(message);
    }

    public TokenValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

