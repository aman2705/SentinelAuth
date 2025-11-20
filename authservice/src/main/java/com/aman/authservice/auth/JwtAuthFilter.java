package com.aman.authservice.auth;

import com.aman.authservice.exception.TokenValidationException;
import com.aman.authservice.service.JwtService;
import com.aman.authservice.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter.
 * Intercepts requests to extract and validate JWT tokens from the Authorization header.
 * Sets the authentication context if a valid token is found.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsServiceImpl;

    /**
     * Filters incoming requests to extract and validate JWT tokens.
     * Sets authentication context if token is valid.
     *
     * @param request HTTP servlet request
     * @param response HTTP servlet response
     * @param chain Filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;
        String username = null;

        // Extract token from Authorization header
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            try {
                token = authHeader.substring(BEARER_PREFIX_LENGTH).trim();
                
                if (token.isEmpty()) {
                    log.debug("Empty token in Authorization header");
                } else {
                    // Extract username from token (validates token format)
                    try {
                        username = jwtService.extractUsername(token);
                    } catch (TokenValidationException e) {
                        log.debug("Token validation failed: {}", e.getMessage());
                        // Continue without authentication - don't set auth context
                    }
                }
            } catch (StringIndexOutOfBoundsException e) {
                log.debug("Invalid Authorization header format");
                // Continue without authentication
            }
        }

        // Validate token and set authentication context
        if (username != null && token != null && 
            SecurityContextHolder.getContext().getAuthentication() == null) {

            try {
                UserDetails userDetails = userDetailsServiceImpl.loadUserByUsername(username);

                if (jwtService.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authentication set for user: {}", username);
                } else {
                    log.debug("Token validation failed for user: {}", username);
                }
            } catch (UsernameNotFoundException e) {
                log.debug("User not found: {}", username);
                // Don't set authentication context
            } catch (TokenValidationException e) {
                log.debug("Token validation exception for user: {} - {}", username, e.getMessage());
                // Don't set authentication context
            } catch (Exception e) {
                log.error("Unexpected error during authentication for user: {}", username, e);
                // Don't set authentication context on unexpected errors
            }
        }

        chain.doFilter(request, response);
    }
}