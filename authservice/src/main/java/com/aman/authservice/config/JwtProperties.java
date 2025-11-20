package com.aman.authservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for JWT token management.
 * Values should be set via environment variables or application.yaml.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    
    /**
     * Secret key for JWT signing (base64 encoded).
     * Should be set via JWT_SECRET environment variable.
     * Minimum recommended length: 256 bits (32 bytes base64 = 44 characters).
     */
    private String secret = "357638792F423F4428472B4B6250655368566D597133743677397A2443264629";
    
    /**
     * Access token expiration time in milliseconds.
     * Default: 100 minutes (6,000,000 ms).
     */
    private long accessTokenExpirationMs = 6_000_000L;
    
    /**
     * Refresh token expiration time in milliseconds.
     * Default: 100 minutes (6,000,000 ms).
     */
    private long refreshTokenExpirationMs = 6_000_000L;
}

