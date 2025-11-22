package com.aman.authservice.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Component
public class ClientRequestMetadataExtractor {

    private static final List<String> IP_HEADER_CHAIN = Arrays.asList(
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    );

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        for (String header : IP_HEADER_CHAIN) {
            String ip = request.getHeader(header);
            if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return sanitizeIp(ip);
            }
        }

        return sanitizeIp(request.getRemoteAddr());
    }

    public String resolveTenant(HttpServletRequest request, String tenantHeader, String defaultTenant) {
        if (request == null) {
            return defaultTenant;
        }
        String tenant = request.getHeader(tenantHeader);
        return StringUtils.hasText(tenant) ? tenant.trim().toLowerCase() : defaultTenant;
    }

    private String sanitizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        if (ip.contains(",")) {
            return ip.split(",")[0].trim();
        }
        return ip.trim();
    }
}

