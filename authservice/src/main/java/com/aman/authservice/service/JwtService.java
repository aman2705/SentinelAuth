package com.aman.authservice.service;

import com.aman.authservice.config.JwtProperties;
import com.aman.authservice.exception.TokenValidationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Service for JWT token generation, parsing, and validation.
 * Handles all JWT-related operations including token creation, extraction of claims,
 * and validation against user details.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    /**
     * Extracts the username (subject) from a JWT token.
     *
     * @param token JWT token string
     * @return Username extracted from token
     * @throws TokenValidationException if token is invalid or malformed
     */
    public String extractUsername(String token) {
        try {
            return extractClaim(token, Claims::getSubject);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Failed to extract username from token: {}", e.getMessage());
            throw new TokenValidationException("Invalid token: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the expiration date from a JWT token.
     *
     * @param token JWT token string
     * @return Expiration date
     * @throws TokenValidationException if token is invalid or malformed
     */
    public Date extractExpiration(String token) {
        try {
            return extractClaim(token, Claims::getExpiration);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Failed to extract expiration from token: {}", e.getMessage());
            throw new TokenValidationException("Invalid token: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts a specific claim from a JWT token.
     *
     * @param token JWT token string
     * @param claimsResolver Function to extract the desired claim
     * @param <T> Type of the claim
     * @return The extracted claim value
     * @throws TokenValidationException if token is invalid or malformed
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        try {
            final Claims claims = extractAllClaims(token);
            return claimsResolver.apply(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Failed to extract claim from token: {}", e.getMessage());
            throw new TokenValidationException("Invalid token: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts all claims from a JWT token.
     *
     * @param token JWT token string
     * @return Claims object containing all token claims
     * @throws TokenValidationException if token is invalid, expired, or malformed
     */
    private Claims extractAllClaims(String token) {
        try {
            return Jwts
                    .parser()
                    .setSigningKey(getSignKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("Token expired: {}", e.getMessage());
            throw new TokenValidationException("Token has expired", e);
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT: {}", e.getMessage());
            throw new TokenValidationException("Unsupported token format", e);
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT: {}", e.getMessage());
            throw new TokenValidationException("Malformed token", e);
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
            throw new TokenValidationException("Invalid token signature", e);
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
            throw new TokenValidationException("Token claims are empty", e);
        }
    }

    /**
     * Checks if a token is expired.
     *
     * @param token JWT token string
     * @return true if token is expired, false otherwise
     */
    private Boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            return expiration.before(new Date());
        } catch (TokenValidationException e) {
            log.debug("Token expiration check failed: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Validates a JWT token against user details.
     *
     * @param token JWT token string
     * @param userDetails User details to validate against
     * @return true if token is valid for the user, false otherwise
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            boolean isExpired = isTokenExpired(token);
            boolean isValid = username.equals(userDetails.getUsername()) && !isExpired;
            
            if (!isValid) {
                log.debug("Token validation failed for user: {}", username);
            }
            
            return isValid;
        } catch (TokenValidationException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Generates a new JWT access token for a user.
     *
     * @param username Username to generate token for
     * @return JWT token string
     * @throws IllegalArgumentException if username is null or empty
     */
    public String generateToken(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, username);
    }

    /**
     * Creates a JWT token with the specified claims and username.
     *
     * @param claims Additional claims to include in the token
     * @param username Username (subject) to include in the token
     * @return JWT token string
     */
    private String createToken(Map<String, Object> claims, String username) {
        long currentTimeMillis = System.currentTimeMillis();
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(currentTimeMillis))
                .setExpiration(new Date(currentTimeMillis + jwtProperties.getAccessTokenExpirationMs()))
                .signWith(getSignKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Gets the signing key for JWT tokens.
     *
     * @return Secret key for signing JWTs
     * @throws IllegalArgumentException if secret key is invalid
     */
    private Key getSignKey() {
        try {
            String secret = jwtProperties.getSecret();
            if (secret == null || secret.trim().isEmpty()) {
                throw new IllegalStateException("JWT secret key is not configured");
            }
            
            byte[] keyBytes = Decoders.BASE64.decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            log.error("Invalid JWT secret key format", e);
            throw new IllegalStateException("JWT secret key is invalid. Please check configuration.", e);
        }
    }
}
