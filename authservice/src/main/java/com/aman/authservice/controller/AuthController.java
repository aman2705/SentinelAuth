package com.aman.authservice.controller;

import com.aman.authservice.entities.RefreshToken;
import com.aman.authservice.model.UserInfoDTO;
import com.aman.authservice.response.JwtResponseDTO;
import com.aman.authservice.service.JwtService;
import com.aman.authservice.service.RefreshTokenService;
import com.aman.authservice.service.UserDetailsServiceImpl;
import com.aman.authservice.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/auth/v1")
public class AuthController {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final UserDetailsServiceImpl userDetailsService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody UserInfoDTO userInfoDto) {
        try {
            String userId = userService.signupUser(userInfoDto);   // FIXED: use UserService

            if (userId == null) {
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body("User Already Exists");
            }

            // Generate refresh + access token
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(userInfoDto.getUsername());
            String jwtToken = jwtService.generateToken(userInfoDto.getUsername());

            return ResponseEntity.ok(
                    JwtResponseDTO.builder()
                            .accessToken(jwtToken)
                            .token(refreshToken.getToken())
                            .userId(userId)
                            .build()
            );

        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error during user signup");
        }
    }

    @GetMapping("/ping")
    public ResponseEntity<?> ping() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            String userId = userService.getUserIdByUsername(authentication.getName());

            if (userId != null) {
                return ResponseEntity.ok(userId);
            }
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
    }

    @GetMapping("/health")
    public ResponseEntity<Boolean> checkHealth() {
        return ResponseEntity.ok(true);
    }
}
