package com.aman.authservice.ratelimit.interceptor;

import com.aman.authservice.config.RateLimitingProperties;
import com.aman.authservice.ratelimit.context.RateLimitContext;
import com.aman.authservice.ratelimit.context.RateLimitContextHolder;
import com.aman.authservice.util.ClientRequestMetadataExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitContextInterceptor implements HandlerInterceptor {

    private final ClientRequestMetadataExtractor metadataExtractor;
    private final RateLimitingProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RateLimitContext context = RateLimitContext.builder()
                .ipAddress(metadataExtractor.extractClientIp(request))
                .tenant(metadataExtractor.resolveTenant(request, properties.getTenantHeader(), "public"))
                .build();
        RateLimitContextHolder.set(context);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        RateLimitContextHolder.clear();
    }
}

