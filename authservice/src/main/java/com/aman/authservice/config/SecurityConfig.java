package com.aman.authservice.config;

import com.aman.authservice.auth.JwtAuthFilter;
import com.aman.authservice.service.JwtService;
import com.aman.authservice.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 * Configures authentication, authorization, and JWT filter chain.
 * 
 * Security notes:
 * - CSRF is disabled because we're using stateless JWT authentication
 * - CORS is disabled by default - should be configured for production with allowed origins
 * - Session management is STATELESS to support JWT-based authentication
 * - Public endpoints: /auth/v1/** (authentication endpoints) and /health
 * - All other endpoints require authentication
 */
@Slf4j
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsServiceImpl;

    /**
     * Password encoder bean using BCrypt.
     * BCrypt is a secure hashing algorithm with salt generation.
     *
     * @return BCrypt password encoder instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authentication provider that uses UserDetailsService and PasswordEncoder.
     *
     * @return DaoAuthenticationProvider instance
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsServiceImpl);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Authentication manager for handling authentication requests.
     *
     * @param config Authentication configuration
     * @return AuthenticationManager instance
     * @throws Exception if configuration fails
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * JWT authentication filter bean.
     * This filter extracts and validates JWT tokens from the Authorization header.
     *
     * @param jwtService JWT service for token validation
     * @return JwtAuthFilter instance
     */
    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService) {
        return new JwtAuthFilter(jwtService, userDetailsServiceImpl);
    }

    /**
     * Security filter chain configuration.
     * 
     * Security configuration:
     * - CSRF disabled for stateless JWT authentication
     * - CORS disabled (TODO: Configure CORS for production with specific allowed origins)
     * - Public endpoints: /auth/v1/** and /health
     * - All other endpoints require authentication
     * - Stateless session management (no session cookies)
     * - JWT filter added before UsernamePasswordAuthenticationFilter
     *
     * @param http HttpSecurity configuration builder
     * @param jwtAuthFilter JWT authentication filter
     * @return SecurityFilterChain instance
     * @throws Exception if configuration fails
     */
    @Bean
    @Primary
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        http
                // Disable CSRF for stateless JWT authentication
                .csrf(AbstractHttpConfigurer::disable)
                // TODO: Configure CORS for production with specific allowed origins
                // Example: .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .cors(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(
                                "/auth/v1/**",  // Authentication endpoints
                                "/health"       // Health check
                        ).permitAll()
                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )
                // Stateless session management (no session cookies)
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Use custom authentication provider
                .authenticationProvider(authenticationProvider())
                // Add JWT filter before UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(Customizer.withDefaults());

        SecurityFilterChain chain = http.build();
        log.info("Security filter chain configured");
        return chain;
    }
}
