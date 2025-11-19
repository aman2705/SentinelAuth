package com.aman.authservice.controller;

import com.aman.authservice.entities.RefreshToken;
import com.aman.authservice.request.AuthRequestDTO;
import com.aman.authservice.request.RefreshTokenRequestDTO;
import com.aman.authservice.response.JwtResponseDTO;
import com.aman.authservice.service.JwtService;
import com.aman.authservice.service.RefreshTokenService;
import com.aman.authservice.service.UserDetailsServiceImpl;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth/v1")
@Slf4j
public class TokenController {

    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponseDTO> login(@RequestBody AuthRequestDTO authRequestDTO) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequestDTO.getUsername(), authRequestDTO.getPassword())
            );

            if (!authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String username = authRequestDTO.getUsername();
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(username);
            String userId = userDetailsService.getUserByUsername(username);

            if (userId == null || refreshToken == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(null);
            }

            String accessToken = jwtService.generateToken(username);
            JwtResponseDTO response = JwtResponseDTO.builder()
                    .accessToken(accessToken)
                    .token(refreshToken.getToken())
                    .userId(userId)
                    .build();

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException ex) {
            log.warn("Authentication failed for user: {}", authRequestDTO.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception ex) {
            log.error("Error during authentication", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/refreshToken")
    public ResponseEntity<JwtResponseDTO> refreshToken(@RequestBody RefreshTokenRequestDTO refreshTokenRequestDTO) {
        return refreshTokenService.findByToken(refreshTokenRequestDTO.getToken())
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUserInfo)
                .map(userInfo -> {
                    String accessToken = jwtService.generateToken(userInfo.getUsername());
                    JwtResponseDTO response = JwtResponseDTO.builder()
                            .accessToken(accessToken)
                            .token(refreshTokenRequestDTO.getToken())
                            .userId(userInfo.getUserId())
                            .build();
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
