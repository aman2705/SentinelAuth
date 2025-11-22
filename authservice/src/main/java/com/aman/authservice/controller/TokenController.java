package com.aman.authservice.controller;

import com.aman.authservice.entities.RefreshToken;
import com.aman.authservice.entities.UserInfo;
import com.aman.authservice.eventProducer.UserEventProducer;
import com.aman.authservice.events.TokenRefreshedEvent;
import com.aman.authservice.events.UserLoggedInEvent;
import com.aman.authservice.events.UserLoginFailedEvent;
import com.aman.authservice.exception.TokenValidationException;
import com.aman.authservice.exception.UserNotFoundException;
import com.aman.authservice.ratelimit.annotation.RateLimiter;
import com.aman.authservice.ratelimit.model.RateLimitKeyStrategy;
import com.aman.authservice.ratelimit.service.BruteForceProtectionService;
import com.aman.authservice.request.AuthRequestDTO;
import com.aman.authservice.request.RefreshTokenRequestDTO;
import com.aman.authservice.response.JwtResponseDTO;
import com.aman.authservice.service.JwtService;
import com.aman.authservice.service.RefreshTokenService;
import com.aman.authservice.service.UserDetailsServiceImpl;
import com.aman.authservice.util.ClientRequestMetadataExtractor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * REST controller for token-related operations.
 * Handles user login and token refresh.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/v1")
public class TokenController {

    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;
    private final UserEventProducer userEventProducer;
    private final BruteForceProtectionService bruteForceProtectionService;
    private final ClientRequestMetadataExtractor metadataExtractor;

    /**
     * Authenticates a user and returns access and refresh tokens.
     * Extracts IP address and User-Agent from request for logging.
     *
     * @param authRequestDTO Authentication request DTO (validated)
     * @param request HTTP servlet request for extracting IP and User-Agent
     * @return JWT response with access token, refresh token, and user ID
     */
    @RateLimiter(
            name = "login-rate-limiter",
            fixedWindowKey = RateLimitKeyStrategy.IP,
            slidingWindowKey = RateLimitKeyStrategy.IP_USERNAME
    )
    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(
            @Valid @RequestBody AuthRequestDTO authRequestDTO,
            HttpServletRequest request) {
        String username = authRequestDTO.getUsername();
        log.info("Login attempt for user: {}", username);

        String ipAddress = metadataExtractor.extractClientIp(request);
        bruteForceProtectionService.ensureAllowed(ipAddress, username);

        try {
            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            username.trim(),
                            authRequestDTO.getPassword()
                    )
            );

            if (!authentication.isAuthenticated()) {
                log.warn("Authentication failed for user: {} - not authenticated", username);
                bruteForceProtectionService.recordFailure(ipAddress, username);
                publishLoginFailedEvent(username, "Invalid credentials");
                return ResponseEntity.<JwtResponseDTO>status(HttpStatus.UNAUTHORIZED).build();
            }

            // Get user ID
            String userId = userDetailsService.getUserByUsername(username);
            if (userId == null) {
                log.error("User ID not found for authenticated user: {}", username);
                publishLoginFailedEvent(username, "User ID not found");
                return ResponseEntity.<JwtResponseDTO>status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // Create refresh token
            RefreshToken refreshToken;
            try {
                refreshToken = refreshTokenService.createRefreshToken(username);
            } catch (Exception e) {
                log.error("Failed to create refresh token for user: {}", username, e);
                publishLoginFailedEvent(username, "Failed to create refresh token");
                return ResponseEntity.<JwtResponseDTO>status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            if (refreshToken == null) {
                log.error("Refresh token is null for user: {}", username);
                publishLoginFailedEvent(username, "Refresh token creation failed");
                return ResponseEntity.<JwtResponseDTO>status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // Generate access token
            String accessToken = jwtService.generateToken(username);

            // Build response
            JwtResponseDTO response = JwtResponseDTO.builder()
                    .accessToken(accessToken)
                    .token(refreshToken.getToken())
                    .userId(userId)
                    .build();

            // Extract IP and User-Agent for logging
            String userAgent = extractUserAgent(request);

            // Publish login success event
            try {
                userEventProducer.publish(
                        new UserLoggedInEvent(userId, ipAddress, userAgent),
                        userId
                );
                log.debug("Login success event published for user: {}", username);
            } catch (Exception e) {
                log.error("Failed to publish login success event for user: {}", username, e);
                // Non-critical error - login is successful, just log the event failure
            }

            log.info("Login successful for user: {} (userId: {})", username, userId);
            bruteForceProtectionService.reset(ipAddress, username);
            return ResponseEntity.ok(response);

        } catch (BadCredentialsException ex) {
            log.warn("Bad credentials for user: {}", username);
            bruteForceProtectionService.recordFailure(ipAddress, username);
            publishLoginFailedEvent(username, "Bad credentials");
            return ResponseEntity.<JwtResponseDTO>status(HttpStatus.UNAUTHORIZED).build();
        } catch (UserNotFoundException ex) {
            log.warn("User not found: {}", username);
            bruteForceProtectionService.recordFailure(ipAddress, username);
            publishLoginFailedEvent(username, "User not found");
            return ResponseEntity.<JwtResponseDTO>status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception ex) {
            log.error("Unexpected error during authentication for user: {}", username, ex);
            publishLoginFailedEvent(username, "Internal server error");
            return ResponseEntity.<JwtResponseDTO>status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Refreshes an access token using a valid refresh token.
     *
     * @param refreshTokenRequestDTO Refresh token request DTO (validated)
     * @param request HTTP servlet request
     * @return JWT response with new access token
     */
    @RateLimiter(
            name = "token-rate-limiter",
            fixedWindowKey = RateLimitKeyStrategy.IP,
            slidingWindowKey = RateLimitKeyStrategy.IP_USERNAME
    )
    @PostMapping({"/refreshToken", "/token"})
    public ResponseEntity<JwtResponseDTO> refreshToken(
            @Valid @RequestBody RefreshTokenRequestDTO refreshTokenRequestDTO,
            HttpServletRequest request) {

        String token = refreshTokenRequestDTO.getToken();
        log.debug("Token refresh request received");

        try {
            Optional<RefreshToken> optionalRefreshToken = refreshTokenService.findByToken(token);

            if (optionalRefreshToken.isEmpty()) {
                log.warn("Refresh token not found or invalid");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            RefreshToken refreshToken;

            try {
                refreshToken = refreshTokenService.verifyExpiration(optionalRefreshToken.get());
            } catch (TokenValidationException e) {
                log.warn("Token validation failed during refresh: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            UserInfo userInfo = refreshToken.getUserInfo();

            // Generate new access token
            String accessToken = jwtService.generateToken(userInfo.getUsername());

            JwtResponseDTO response = JwtResponseDTO.builder()
                    .accessToken(accessToken)
                    .token(token)  // returning the SAME refresh token
                    .userId(userInfo.getUserId())
                    .build();

            // Publish event (non-critical)
            try {
                userEventProducer.publish(
                        new TokenRefreshedEvent(userInfo.getUserId()),
                        userInfo.getUserId()
                );
                log.debug("Token refreshed event published for user: {}", userInfo.getUserId());
            } catch (Exception e) {
                log.error("Failed to publish token refresh event for user: {}", userInfo.getUserId(), e);
            }

            log.info("Token refreshed successfully for user: {} (userId: {})",
                    userInfo.getUsername(), userInfo.getUserId());

            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("Unexpected error during token refresh", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    /**
     * Extracts client IP address from request, handling proxy headers.
     *
     * @param request HTTP servlet request
     * @return Client IP address
     */
    /**
     * Extracts User-Agent from request.
     *
     * @param request HTTP servlet request
     * @return User-Agent string
     */
    private String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "unknown";
    }

    /**
     * Publishes login failed event.
     *
     * @param username Username that failed to login
     * @param reason Reason for login failure
     */
    private void publishLoginFailedEvent(String username, String reason) {
        try {
            userEventProducer.publish(
                    new UserLoginFailedEvent(username, reason),
                    username
            );
            log.debug("Login failed event published for user: {}", username);
        } catch (Exception e) {
            log.error("Failed to publish login failed event for user: {}", username, e);
            // Non-critical error - just log the event failure
        }
    }
}
