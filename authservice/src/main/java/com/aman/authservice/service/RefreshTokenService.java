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
     * Creates and persists a new refresh token for the given username.
     *
     * @param username the username for which the refresh token is generated
     * @return the saved RefreshToken
     * @throws UsernameNotFoundException if the user is not found
     */
    public RefreshToken createRefreshToken(String username) {
        UserInfo user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        RefreshToken refreshToken = RefreshToken.builder()
                .userInfo(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(REFRESH_TOKEN_DURATION_MS))
                .build();

        log.info("Creating refresh token for user: {}", username);
        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Retrieves a refresh token by its token string.
     *
     * @param token the refresh token string
     * @return an Optional containing the RefreshToken if found
     */
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Verifies that the refresh token has not expired.
     * If expired, deletes the token and throws an exception.
     *
     * @param token the RefreshToken to verify
     * @return the token if valid
     * @throws RuntimeException if the token is expired
     */
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            log.warn("Refresh token expired for user: {}", token.getUserInfo().getUsername());
            throw new RuntimeException("Refresh token expired. Please login again.");
        }
        return token;
    }
}
