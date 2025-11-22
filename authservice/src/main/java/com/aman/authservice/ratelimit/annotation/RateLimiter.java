package com.aman.authservice.ratelimit.annotation;

import com.aman.authservice.ratelimit.model.RateLimitKeyStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    String name();

    RateLimitKeyStrategy fixedWindowKey() default RateLimitKeyStrategy.IP;

    RateLimitKeyStrategy slidingWindowKey() default RateLimitKeyStrategy.IP_USERNAME;

    int fixedWindowLimit() default -1;

    int fixedWindowSeconds() default -1;

    int slidingWindowLimit() default -1;

    int slidingWindowSeconds() default -1;
}

