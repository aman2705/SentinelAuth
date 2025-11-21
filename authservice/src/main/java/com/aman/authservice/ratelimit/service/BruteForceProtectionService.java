package com.aman.authservice.ratelimit.service;

import com.aman.authservice.config.RateLimitingProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class BruteForceProtectionService {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> bruteForceScript;
    private final RateLimitingProperties properties;
    private final Counter lockoutCounter;

    public BruteForceProtectionService(StringRedisTemplate redisTemplate,
                                       RateLimitingProperties properties,
                                       MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;

        this.bruteForceScript = new DefaultRedisScript<>();
        this.bruteForceScript.setLocation(new ClassPathResource("lua/brute_force.lua"));
        this.bruteForceScript.setResultType(List.class);

        this.lockoutCounter = Counter.builder("brute_force_lockout_total")
                .description("Total number of brute force lockouts")
                .register(meterRegistry);
    }

    public void ensureAllowed(String ip, String username) {
        String lockKey = resolveLockKey(ip, username);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            throw new RuntimeException("Account locked due to too many failed attempts. Please try again later.");
        }
    }

    public void recordFailure(String ip, String username) {
        String failureKey = resolveFailureKey(ip, username);
        String lockKey = resolveLockKey(ip, username);

        List<Long> result = redisTemplate.execute(
                bruteForceScript,
                List.of(failureKey, lockKey),
                String.valueOf(properties.getBruteForceMaxFailures()),
                String.valueOf(properties.getBruteForceFailureWindowSeconds()),
                String.valueOf(properties.getBruteForceLockoutSeconds())
        );

        if (result != null && !result.isEmpty() && result.get(0) == 0) {
            lockoutCounter.increment();
            log.warn("Brute force lockout triggered for user: {} from IP: {}", username, ip);
        }
    }

    public void reset(String ip, String username) {
        String failureKey = resolveFailureKey(ip, username);
        redisTemplate.delete(failureKey);
    }

    private String resolveFailureKey(String ip, String username) {
        return String.format("bf:fail:%s:%s", ip, username);
    }

    private String resolveLockKey(String ip, String username) {
        return String.format("bf:lock:%s:%s", ip, username);
    }
}
