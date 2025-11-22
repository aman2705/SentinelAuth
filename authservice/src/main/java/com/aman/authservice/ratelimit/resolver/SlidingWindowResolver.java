package com.aman.authservice.ratelimit.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SlidingWindowResolver {

    private final Clock clock;

    public String resolveKey(String baseKey) {
        return String.format("%s:sw", baseKey);
    }

    public long nowMillis() {
        return Instant.now(clock).toEpochMilli();
    }
}

