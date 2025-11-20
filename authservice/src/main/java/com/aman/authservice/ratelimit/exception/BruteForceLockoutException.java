package com.aman.authservice.ratelimit.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class BruteForceLockoutException extends ResponseStatusException {
    public BruteForceLockoutException(String message) {
        super(HttpStatus.LOCKED, message);
    }
}

