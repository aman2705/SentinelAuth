package com.aman.authservice.controller;

import com.aman.authservice.dto.ChangePasswordDTO;
import com.aman.authservice.dto.UserInfoDTO;
import com.aman.authservice.entities.RefreshToken;
import com.aman.authservice.response.JwtResponseDTO;
import com.aman.authservice.service.JwtService;
import com.aman.authservice.service.RefreshTokenService;
import com.aman.authservice.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication operations.
 * Handles user registration, health checks, and password changes.
 */
@Slf4j
@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    /**
     * Registers a new user and returns access and refresh tokens.
     *
     * @param userInfoDto User information DTO (validated)
     * @param request HTTP servlet request
     * @return JWT response with access token, refresh token, and user ID
     */
    @PostMapping("/signup")
    public ResponseEntity<JwtResponseDTO> signup(
            @Valid @RequestBody UserInfoDTO userInfoDto,
            HttpServletRequest request) {
        log.info("Signup request received for email: {}", userInfoDto.getEmail());

        String userId = userService.signupUser(userInfoDto);

        // Determine username for token generation (use username from DTO or generate from email)
        String username = userInfoDto.getUsername();
        if (username == null || username.trim().isEmpty()) {
            String email = userInfoDto.getEmail();
            username = email.substring(0, email.indexOf("@"));
        }

        // Generate refresh + access token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(username);
        String jwtToken = jwtService.generateToken(username);

        log.info("User signup successful: userId={}, username={}", userId, username);

        return ResponseEntity.ok(
                JwtResponseDTO.builder()
                        .accessToken(jwtToken)
                        .token(refreshToken.getToken())
                        .userId(userId)
                        .build()
        );
    }

    /**
     * Health check endpoint that validates authentication.
     * Returns the authenticated user's ID.
     *
     * @return User ID if authenticated, 401 otherwise
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        log.debug("Ping request received");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            log.debug("Authenticated user: {}", username);

            String userId = userService.getUserIdByUsername(username);

            if (userId != null) {
                return ResponseEntity.ok(userId);
            } else {
                log.warn("User ID not found for authenticated user: {}", username);
            }
        }

        return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED)
                .body("Unauthorized");
    }

    /**
     * Basic health check endpoint.
     * Always returns true to indicate service is up.
     *
     * @return true
     */
    @GetMapping("/health")
    public ResponseEntity<Boolean> checkHealth() {
        return ResponseEntity.ok(true);
    }

    /**
     * Changes password for the authenticated user.
     *
     * @param userId User ID from request header
     * @param request Password change request DTO (validated)
     * @return Success message
     * @throws IllegalArgumentException if userId is missing
     */
    @PostMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestHeader(value = "user-id", required = false) String userId,
            @Valid @RequestBody ChangePasswordDTO request) {

        if (userId == null || userId.trim().isEmpty()) {
            log.warn("Change password request missing user-id header");
            throw new IllegalArgumentException("User ID header is required");
        }

        log.info("Password change request received for user: {}", userId);
        userService.changePassword(userId.trim(), request);

        return ResponseEntity.ok("Password changed successfully");
    }
}
