package com.aman.authservice.service;

import com.aman.authservice.config.JwtProperties;
import com.aman.authservice.entities.RefreshToken;
import com.aman.authservice.entities.UserInfo;
import com.aman.authservice.exception.TokenValidationException;
import com.aman.authservice.exception.UserNotFoundException;
import com.aman.authservice.repository.RefreshTokenRepository;
import com.aman.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing refresh tokens.
 * Handles creation, retrieval, and validation of refresh tokens.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;

    /**
     * Create or update refresh token for a user.
     * If a refresh token already exists for the user, it is updated with a new token and expiration.
     *
     * @param username Username to create/update refresh token for
     * @return The created or updated refresh token
     * @throws UserNotFoundException if user is not found
     */
    @Transactional
    public RefreshToken createRefreshToken(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        log.debug("Creating/updating refresh token for user: {}", username);

        UserInfo user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> {
                    log.warn("User not found for refresh token creation: {}", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        // Check if refresh token exists for user
        Optional<RefreshToken> existingToken = refreshTokenRepository.findByUserInfo(user);

        RefreshToken refreshToken;
        Instant expiryDate = Instant.now().plusMillis(jwtProperties.getRefreshTokenExpirationMs());

        if (existingToken.isPresent()) {
            // UPDATE existing token
            refreshToken = existingToken.get();
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setExpiryDate(expiryDate);

            log.info("Updating existing refresh token for user: {}", username);
        } else {
            // CREATE new token (first login)
            refreshToken = RefreshToken.builder()
                    .userInfo(user)
                    .token(UUID.randomUUID().toString())
                    .expiryDate(expiryDate)
                    .build();

            log.info("Creating new refresh token for user: {}", username);
        }

        try {
            return refreshTokenRepository.save(refreshToken);
        } catch (Exception e) {
            log.error("Failed to save refresh token for user: {}", username, e);
            throw new RuntimeException("Failed to create refresh token", e);
        }
    }

    /**
     * Finds a refresh token by token string.
     *
     * @param token Refresh token string
     * @return Optional containing the refresh token if found
     */
    public Optional<RefreshToken> findByToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByToken(token.trim());
    }

    /**
     * Verifies that a refresh token has not expired.
     * Deletes the token if it has expired.
     *
     * @param token Refresh token to verify
     * @return The token if valid
     * @throws TokenValidationException if token has expired
     */
    @Transactional
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Token cannot be null");
        }

        if (token.getExpiryDate() == null) {
            log.warn("Refresh token has no expiration date for user: {}", 
                token.getUserInfo() != null ? token.getUserInfo().getUsername() : "unknown");
            refreshTokenRepository.delete(token);
            throw new TokenValidationException("Refresh token is invalid: missing expiration date");
        }

        if (token.getExpiryDate().isBefore(Instant.now())) {
            String username = token.getUserInfo() != null 
                ? token.getUserInfo().getUsername() 
                : "unknown";
            
            refreshTokenRepository.delete(token);
            log.warn("Refresh token expired for user: {}", username);
            throw new TokenValidationException("Refresh token expired. Please login again.");
        }

        return token;
    }
}
