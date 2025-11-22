package com.aman.authservice.ratelimit.model;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;

@Value
@Builder
public class RateLimiterDefinition {
    String name;
    RateLimitKeyStrategy fixedWindowKeyStrategy;
    RateLimitKeyStrategy slidingWindowKeyStrategy;
    int fixedWindowLimit;
    Duration fixedWindowDuration;
    int slidingWindowLimit;
    Duration slidingWindowDuration;

    public boolean isFixedWindowEnabled() {
        return fixedWindowLimit > 0 && fixedWindowDuration != null && !fixedWindowDuration.isZero();
    }

    public boolean isSlidingWindowEnabled() {
        return slidingWindowLimit > 0 && slidingWindowDuration != null && !slidingWindowDuration.isZero();
    }
}

