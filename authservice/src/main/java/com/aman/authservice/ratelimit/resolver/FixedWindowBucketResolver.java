package com.aman.authservice.ratelimit.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class FixedWindowBucketResolver {

    private final Clock clock;

    public String resolveBucketKey(String baseKey, Duration duration) {
        long bucket = computeBucket(duration);
        return String.format("%s:fw:%d", baseKey, bucket);
    }

    private long computeBucket(Duration duration) {
        long seconds = Math.max(1, duration.getSeconds());
        Instant now = Instant.now(clock);
        return now.getEpochSecond() / seconds;
    }
}

