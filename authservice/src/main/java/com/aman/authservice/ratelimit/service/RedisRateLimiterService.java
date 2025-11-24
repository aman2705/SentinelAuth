package com.aman.authservice.ratelimit.service;

import com.aman.authservice.config.RateLimitingProperties;
import com.aman.authservice.ratelimit.model.RateLimitKeyStrategy;
import com.aman.authservice.ratelimit.model.RateLimiterDefinition;
import com.aman.authservice.ratelimit.resolver.FixedWindowBucketResolver;
import com.aman.authservice.ratelimit.resolver.RateLimitKeyResolver;
import com.aman.authservice.ratelimit.resolver.SlidingWindowResolver;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
public class RedisRateLimiterService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> fixedWindowScript;
    private final DefaultRedisScript<Long> slidingWindowScript;
    private final FixedWindowBucketResolver fixedWindowBucketResolver;
    private final SlidingWindowResolver slidingWindowResolver;
    private final RateLimitKeyResolver rateLimitKeyResolver;
    private final CircuitBreaker redisCircuitBreaker;
    private final Counter rateLimitHitCounter;

    public RedisRateLimiterService(StringRedisTemplate stringRedisTemplate,
                                   FixedWindowBucketResolver fixedWindowBucketResolver,
                                   SlidingWindowResolver slidingWindowResolver,
                                   RateLimitKeyResolver rateLimitKeyResolver,
                                   CircuitBreaker redisCircuitBreaker,
                                   RateLimitingProperties properties,
                                   MeterRegistry meterRegistry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.fixedWindowBucketResolver = fixedWindowBucketResolver;
        this.slidingWindowResolver = slidingWindowResolver;
        this.rateLimitKeyResolver = rateLimitKeyResolver;
        this.redisCircuitBreaker = redisCircuitBreaker;

        this.fixedWindowScript = new DefaultRedisScript<>();
        this.fixedWindowScript.setLocation(new org.springframework.core.io.ClassPathResource("lua/fixed_window.lua"));
        this.fixedWindowScript.setResultType(Long.class);

        this.slidingWindowScript = new DefaultRedisScript<>();
        this.slidingWindowScript.setLocation(new org.springframework.core.io.ClassPathResource("lua/sliding_window.lua"));
        this.slidingWindowScript.setResultType(Long.class);

        this.rateLimitHitCounter = Counter.builder("rate_limit_hit_total")
                .description("Total number of requests blocked by distributed rate limiting")
                .tag("service", properties.getMetricTag())
                .register(meterRegistry);
    }

    public boolean allow(RateLimiterDefinition definition, RateLimitKeyResolver.RateLimitRequestMetadata metadata) {
        boolean fixedAllowed = !definition.isFixedWindowEnabled()
                || allowFixedWindow(definition, metadata);
        boolean slidingAllowed = !definition.isSlidingWindowEnabled()
                || allowSlidingWindow(definition, metadata);

        boolean allowed = fixedAllowed && slidingAllowed;
        if (!allowed) {
            rateLimitHitCounter.increment();
            log.warn("Rate limit triggered | limiter={} | metadata={}", definition.getName(), metadata);
        }
        return allowed;
    }

    public boolean allowFixedWindow(String baseKey, int limit, Duration window) {
        String bucketKey = fixedWindowBucketResolver.resolveBucketKey(baseKey, window);
        return executeRedisCommand(() -> {
            Long result = stringRedisTemplate.execute(
                    fixedWindowScript,
                    List.of(bucketKey),
                    String.valueOf(limit),
                    String.valueOf(window.toMillis())
            );
            return result == null || result == 1L;
        });
    }

    public boolean allowSlidingWindow(String baseKey, int limit, Duration window) {
        String slidingKey = slidingWindowResolver.resolveKey(baseKey);
        return executeRedisCommand(() -> {
            Long result = stringRedisTemplate.execute(
                    slidingWindowScript,
                    List.of(slidingKey),
                    String.valueOf(limit),
                    String.valueOf(window.toMillis()),
                    String.valueOf(slidingWindowResolver.nowMillis())
            );
            return result == null || result == 1L;
        });
    }

    private boolean allowFixedWindow(RateLimiterDefinition definition, RateLimitKeyResolver.RateLimitRequestMetadata metadata) {
        String baseKey = resolveBaseKey(definition.getFixedWindowKeyStrategy(), metadata);
        return allowFixedWindow(baseKey, definition.getFixedWindowLimit(), definition.getFixedWindowDuration());
    }

    private boolean allowSlidingWindow(RateLimiterDefinition definition, RateLimitKeyResolver.RateLimitRequestMetadata metadata) {
        String baseKey = resolveBaseKey(definition.getSlidingWindowKeyStrategy(), metadata);
        return allowSlidingWindow(baseKey, definition.getSlidingWindowLimit(), definition.getSlidingWindowDuration());
    }

    private String resolveBaseKey(RateLimitKeyStrategy strategy, RateLimitKeyResolver.RateLimitRequestMetadata metadata) {
        return rateLimitKeyResolver.resolve(strategy, metadata);
    }

    private boolean executeRedisCommand(Supplier<Boolean> supplier) {
        try {
            return CircuitBreaker
                    .decorateSupplier(redisCircuitBreaker, supplier)
                    .get();
        } catch (Exception ex) {
            log.error("Redis rate limiter fallback - allowing request due to Redis/CircuitBreaker state", ex);
            return true;
        }
    }
}

