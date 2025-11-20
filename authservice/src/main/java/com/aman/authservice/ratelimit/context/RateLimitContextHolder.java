package com.aman.authservice.ratelimit.context;

public final class RateLimitContextHolder {

    private static final ThreadLocal<RateLimitContext> CONTEXT = new ThreadLocal<>();

    private RateLimitContextHolder() {
    }

    public static void set(RateLimitContext context) {
        CONTEXT.set(context);
    }

    public static RateLimitContext get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}

