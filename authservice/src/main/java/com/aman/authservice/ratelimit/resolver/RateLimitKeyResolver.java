package com.aman.authservice.ratelimit.resolver;

import com.aman.authservice.dto.UserInfoDTO;
import com.aman.authservice.ratelimit.model.RateLimitKeyStrategy;
import com.aman.authservice.request.AuthRequestDTO;
import com.aman.authservice.request.RefreshTokenRequestDTO;
import com.aman.authservice.service.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Component
public class RateLimitKeyResolver {

    private static final String UNKNOWN = "anonymous";

    private final RefreshTokenService refreshTokenService;

    public RateLimitKeyResolver(@Autowired(required = false) @Nullable RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    public String resolve(String limiterName, RateLimitKeyStrategy strategy, RateLimitRequestMetadata metadata) {
        String ip = sanitize(metadata.ip());
        String username = sanitize(metadata.username());
        String tenant = sanitize(metadata.tenant());
        String safeLimiterName = sanitize(limiterName);

        return switch (strategy) {
            case IP -> String.format("rl:%s:ip:%s", safeLimiterName, ip);
            case IP_USERNAME -> String.format("rl:%s:ip-user:%s:%s", safeLimiterName, ip, username);
            case IP_USERNAME_TENANT -> String.format("rl:%s:ip-user-tenant:%s:%s:%s", safeLimiterName, ip, username, tenant);
        };
    }

    public RateLimitRequestMetadata buildMetadata(String ip, String tenant, String username) {
        return new RateLimitRequestMetadata(
                StringUtils.hasText(ip) ? ip : UNKNOWN,
                StringUtils.hasText(username) ? username : UNKNOWN,
                StringUtils.hasText(tenant) ? tenant : "public"
        );
    }

    public Optional<String> extractUsername(Object[] args) {
        return Arrays.stream(args)
                .map(this::resolveUsername)
                .filter(StringUtils::hasText)
                .findFirst()
                .map(name -> name.trim().toLowerCase(Locale.ROOT));
    }

    private String resolveUsername(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg instanceof AuthRequestDTO authRequestDTO) {
            return authRequestDTO.getUsername();
        }
        if (arg instanceof UserInfoDTO dto) {
            return StringUtils.hasText(dto.getUsername()) ? dto.getUsername() : dto.getEmail();
        }
        if (arg instanceof RefreshTokenRequestDTO refreshTokenRequestDTO) {
            return resolveFromRefreshToken(refreshTokenRequestDTO);
        }
        if (arg instanceof String s && s.contains("@")) {
            return s;
        }
        return null;
    }

    private String resolveFromRefreshToken(RefreshTokenRequestDTO refreshTokenRequestDTO) {
        if (refreshTokenService == null) {
            log.debug("RefreshTokenService unavailable - skipping username resolution for refresh token requests");
            return null;
        }
        return refreshTokenService.findByToken(refreshTokenRequestDTO.getToken())
                .map(refreshToken -> refreshToken.getUserInfo() != null
                        ? refreshToken.getUserInfo().getUsername()
                        : null)
                .orElse(null);
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return UNKNOWN;
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9:@\\-\\.]", "_");
    }

    public record RateLimitRequestMetadata(String ip, String username, String tenant) {
    }
}

