package com.aman.authservice.service;

import com.aman.authservice.entities.RefreshToken;
import com.aman.authservice.entities.UserInfo;
import com.aman.authservice.repository.RefreshTokenRepository;
import com.aman.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    private static final long REFRESH_TOKEN_DURATION_MS = 6_000_000L; // 100 minutes

    /**
     * Create or update refresh token for a user.
     */
    public RefreshToken createRefreshToken(String username) {

        UserInfo user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        // Check if refresh token exists for user
        Optional<RefreshToken> existingToken = refreshTokenRepository.findByUserInfo(user);

        RefreshToken refreshToken;

        if (existingToken.isPresent()) {
            // UPDATE existing token
            refreshToken = existingToken.get();
            refreshToken.setToken(UUID.randomUUID().toString());
            refreshToken.setExpiryDate(Instant.now().plusMillis(REFRESH_TOKEN_DURATION_MS));

            log.info("Updating existing refresh token for user: {}", username);
        } else {
            // CREATE new token (first login)
            refreshToken = RefreshToken.builder()
                    .userInfo(user)
                    .token(UUID.randomUUID().toString())
                    .expiryDate(Instant.now().plusMillis(REFRESH_TOKEN_DURATION_MS))
                    .build();

            log.info("Creating new refresh token for user: {}", username);
        }

        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            log.warn("Refresh token expired for user: {}", token.getUserInfo().getUsername());
            throw new RuntimeException("Refresh token expired. Please login again.");
        }
        return token;
    }
}
