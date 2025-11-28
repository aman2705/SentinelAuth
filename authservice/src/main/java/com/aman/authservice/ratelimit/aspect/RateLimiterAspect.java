package com.aman.authservice.ratelimit.aspect;

import com.aman.authservice.config.RateLimitingProperties;
import com.aman.authservice.ratelimit.annotation.RateLimiter;
import com.aman.authservice.ratelimit.context.RateLimitContext;
import com.aman.authservice.ratelimit.context.RateLimitContextHolder;
import com.aman.authservice.ratelimit.exception.RateLimitExceededException;
import com.aman.authservice.ratelimit.model.RateLimiterDefinition;
import com.aman.authservice.ratelimit.resolver.RateLimitKeyResolver;
import com.aman.authservice.ratelimit.service.RedisRateLimiterService;
import com.aman.authservice.util.ClientRequestMetadataExtractor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

@Slf4j
@Aspect
@Component("customRateLimiterAspect")
@RequiredArgsConstructor
public class RateLimiterAspect {

    private final RedisRateLimiterService redisRateLimiterService;
    private final RateLimitingProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final ClientRequestMetadataExtractor metadataExtractor;

    @Around("@annotation(rateLimiter)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimiter rateLimiter) throws Throwable {
        RateLimitKeyResolver.RateLimitRequestMetadata metadata = buildMetadata(joinPoint);
        RateLimiterDefinition definition = buildDefinition(rateLimiter);

        boolean allowed = redisRateLimiterService.allow(definition, metadata);
        if (!allowed) {
            throw new RateLimitExceededException(
                    String.format("Too many requests for limiter '%s'. Please retry later.", rateLimiter.name())
            );
        }

        return joinPoint.proceed();
    }

    private RateLimitKeyResolver.RateLimitRequestMetadata buildMetadata(ProceedingJoinPoint joinPoint) {
        RateLimitContext context = RateLimitContextHolder.get();
        HttpServletRequest request = currentRequest();
        String ip = context != null ? context.ipAddress() : metadataExtractor.extractClientIp(request);
        String tenant = context != null ? context.tenant()
                : metadataExtractor.resolveTenant(request, properties.getTenantHeader(), "public");
        String username = keyResolver.extractUsername(joinPoint.getArgs())
                .orElseGet(() -> resolveUsernameFromSecurity());

        return keyResolver.buildMetadata(ip, tenant, username);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes != null ? attributes.getRequest() : null;
    }

    private RateLimiterDefinition buildDefinition(RateLimiter rateLimiter) {
        RateLimitingProperties.Window fixed = properties.getFixedWindow();
        RateLimitingProperties.Window sliding = properties.getSlidingWindow();

        int fixedLimit = rateLimiter.fixedWindowLimit() > 0 ? rateLimiter.fixedWindowLimit() : fixed.getLimit();
        Duration fixedDuration = rateLimiter.fixedWindowSeconds() > 0
                ? Duration.ofSeconds(rateLimiter.fixedWindowSeconds())
                : fixed.getDuration();

        int slidingLimit = rateLimiter.slidingWindowLimit() > 0 ? rateLimiter.slidingWindowLimit() : sliding.getLimit();
        Duration slidingDuration = rateLimiter.slidingWindowSeconds() > 0
                ? Duration.ofSeconds(rateLimiter.slidingWindowSeconds())
                : sliding.getDuration();

        return RateLimiterDefinition.builder()
                .name(rateLimiter.name())
                .fixedWindowKeyStrategy(rateLimiter.fixedWindowKey())
                .slidingWindowKeyStrategy(rateLimiter.slidingWindowKey())
                .fixedWindowLimit(fixedLimit)
                .fixedWindowDuration(fixedDuration)
                .slidingWindowLimit(slidingLimit)
                .slidingWindowDuration(slidingDuration)
                .build();
    }

    private String resolveUsernameFromSecurity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "anonymous";
    }
}

