package com.aman.authservice.ratelimit.service;

import com.aman.authservice.config.RateLimitingProperties;
import com.aman.authservice.ratelimit.exception.BruteForceLockoutException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@Slf4j
@Service
public class BruteForceProtectionService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> bruteForceScript;
    private final RateLimitingProperties properties;
    private final CircuitBreaker redisCircuitBreaker;
    private final Counter lockoutCounter;

    public BruteForceProtectionService(StringRedisTemplate stringRedisTemplate,
                                       DefaultRedisScript<Long> bruteForceScript,
                                       RateLimitingProperties properties,
                                       CircuitBreaker redisCircuitBreaker,
                                       MeterRegistry meterRegistry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.bruteForceScript = bruteForceScript;
        this.properties = properties;
        this.redisCircuitBreaker = redisCircuitBreaker;
        this.lockoutCounter = Counter.builder("brute_force_lockout_total")
                .description("Total brute-force lockouts enforced")
                .tag("service", properties.getMetricTag())
                .register(meterRegistry);
    }

    public void ensureAllowed(String ip, String username) {
        if (!execute(ip, username, "check")) {
            lockoutCounter.increment();
            throw new BruteForceLockoutException("Account temporarily locked due to repeated failures. Try again later.");
        }
    }

    public void recordFailure(String ip, String username) {
        if (!execute(ip, username, "fail")) {
            lockoutCounter.increment();
            throw new BruteForceLockoutException("Account temporarily locked due to repeated failures. Try again later.");
        }
    }

    public void reset(String ip, String username) {
        execute(ip, username, "reset");
    }

    private boolean execute(String ip, String username, String mode) {
        String composite = compositeKey(ip, username);
        String attemptsKey = String.format("security:bf:{%s}:attempts", composite);
        String lockKey = String.format("security:bf:{%s}:lock", composite);

        RateLimitingProperties.BruteForce bf = properties.getBruteForce();

        Supplier<Boolean> supplier = () -> {
            Long response = stringRedisTemplate.execute(
                    bruteForceScript,
                    List.of(attemptsKey, lockKey),
                    String.valueOf(bf.getLimit()),
                    String.valueOf(bf.getDuration().toMillis()),
                    String.valueOf(bf.getLockout().toMillis()),
                    mode
            );
            return response == null || response == 1L;
        };

        Supplier<Boolean> decorated = io.github.resilience4j.decorators.Decorators
                .ofSupplier(supplier)
                .withCircuitBreaker(redisCircuitBreaker)
                .decorate();

        try {
            return decorated.get();
        } catch (Exception ex) {
            log.error("Redis brute-force protection fallback - allowing request", ex);
            return true;
        }
    }

    private String compositeKey(String ip, String username) {
        String safeIp = sanitize(ip);
        String safeUser = sanitize(username);
        return safeIp + ":" + safeUser;
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9:\\-\\.]", "_");
    }
}

