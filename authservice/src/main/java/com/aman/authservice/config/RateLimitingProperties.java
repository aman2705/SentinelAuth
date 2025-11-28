package com.aman.authservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for distributed rate limiting, brute-force protection and
 * supporting infrastructure.
 */
@Data
@ConfigurationProperties(prefix = "sentinel.rate-limit")
public class RateLimitingProperties {

    /**
     * Header name that carries tenant identifier (if any).
     */
    private String tenantHeader = "X-Tenant-ID";

    /**
     * Shared metric tag to correlate counters with this service.
     */
    private String metricTag = "authservice";

    private Window fixedWindow = Window.defaults(5, Duration.ofMinutes(1));
    private Window slidingWindow = Window.defaults(10, Duration.ofMinutes(5));
    private BruteForce bruteForce = BruteForce.defaults(3, Duration.ofMinutes(5), Duration.ofMinutes(15));

    @Data
    public static class Window {
        private int limit;
        private Duration duration;

        public static Window defaults(int limit, Duration duration) {
            Window window = new Window();
            window.setLimit(limit);
            window.setDuration(duration);
            return window;
        }
    }

    @Data
    public static class BruteForce {
        private int limit;
        private Duration duration;
        private Duration lockout;

        public static BruteForce defaults(int limit, Duration duration, Duration lockout) {
            BruteForce bruteForce = new BruteForce();
            bruteForce.setLimit(limit);
            bruteForce.setDuration(duration);
            bruteForce.setLockout(lockout);
            return bruteForce;
        }
    }
}

