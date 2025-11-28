package com.aman.authservice.ratelimit.context;

import lombok.Builder;

@Builder
public record RateLimitContext(
        String ipAddress,
        String tenant
) {
}

