package com.aman.authservice.exception;

/**
 * Exception thrown when credentials are invalid (e.g., wrong password).
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}

