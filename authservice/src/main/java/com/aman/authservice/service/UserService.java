package com.aman.authservice.service;

import com.aman.authservice.dto.ChangePasswordDTO;
import com.aman.authservice.dto.UserInfoDTO;
import com.aman.authservice.entities.UserInfo;
import com.aman.authservice.eventProducer.UserEventProducer;
import com.aman.authservice.events.UserInfoEvent;
import com.aman.authservice.events.UserPasswordChangedEvent;
import com.aman.authservice.exception.InvalidCredentialsException;
import com.aman.authservice.exception.UserAlreadyExistsException;
import com.aman.authservice.exception.UserNotFoundException;
import com.aman.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.UUID;

/**
 * Service for user management operations including signup, password changes,
 * and user information retrieval. Handles business logic and validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventProducer userEventProducer;

    /**
     * Registers a new user in the system.
     * Validates that the user doesn't already exist (by email or username).
     * Publishes a signup event after successful registration.
     *
     * @param userInfoDto User information DTO
     * @return User ID of the newly created user
     * @throws UserAlreadyExistsException if user with email or username already exists
     * @throws IllegalArgumentException if input validation fails
     */
    @Transactional
    public String signupUser(UserInfoDTO userInfoDto) {
        log.debug("Attempting to sign up user with email: {}", userInfoDto.getEmail());

        // Sanitize input
        String email = sanitizeEmail(userInfoDto.getEmail());
        String username = userInfoDto.getUsername() != null 
            ? sanitizeUsername(userInfoDto.getUsername()) 
            : null;

        // Validate if user exists by email
        if (userRepository.findByEmail(email).isPresent()) {
            log.warn("Signup failed: User already exists with email {}", email);
            throw new UserAlreadyExistsException("User with email " + email + " already exists");
        }

        // Check username if provided
        if (username != null && !username.trim().isEmpty()) {
            if (userRepository.findByUsername(username).isPresent()) {
                log.warn("Signup failed: User already exists with username {}", username);
                throw new UserAlreadyExistsException("User with username " + username + " already exists");
            }
        } else {
            // Generate username from email if not provided
            username = generateUsernameFromEmail(email);
            // Check if generated username is taken
            int counter = 1;
            String originalUsername = username;
            while (userRepository.findByUsername(username).isPresent()) {
                username = originalUsername + counter;
                counter++;
            }
        }

        // Create new user entity
        UserInfo user = mapDtoToEntity(userInfoDto, email, username);

        // Save user in DB
        try {
            user = userRepository.save(user);
            log.info("User saved to database: {}", user.getUserId());
        } catch (Exception e) {
            log.error("Failed to save user to database", e);
            throw new RuntimeException("Failed to create user account", e);
        }

        // Publish signup event
        try {
            publishSignupEvent(user);
            log.debug("Signup event published for user: {}", user.getUserId());
        } catch (Exception e) {
            log.error("Failed to publish signup event for user: {}", user.getUserId(), e);
            // Non-critical error - user is created, just log the event failure
        }

        log.info("User registered successfully: {}", user.getUserId());
        return user.getUserId();
    }

    /**
     * Maps DTO to entity, applying sanitization and encoding.
     */
    private UserInfo mapDtoToEntity(UserInfoDTO dto, String email, String username) {
        return UserInfo.builder()
                .userId(UUID.randomUUID().toString())
                .firstName(sanitizeName(dto.getFirstName()))
                .lastName(sanitizeName(dto.getLastName()))
                .phoneNumber(parsePhoneNumber(dto.getPhoneNumber()))
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(dto.getPassword()))
                .roles(new HashSet<>())
                .build();
    }

    /**
     * Publishes user signup event to Kafka.
     */
    private void publishSignupEvent(UserInfo user) {
        userEventProducer.publish(
                UserInfoEvent.builder()
                        .userId(user.getUserId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .phoneNumber(user.getPhoneNumber())
                        .build(),
                user.getUserId()
        );
    }

    /**
     * Retrieves user ID by username.
     *
     * @param username Username to search for
     * @return User ID if found, null otherwise
     */
    public String getUserIdByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        
        return userRepository.findByUsername(username.trim())
                .map(UserInfo::getUserId)
                .orElse(null);
    }

    /**
     * Changes password for a user after validating the old password.
     *
     * @param userId User ID
     * @param request Password change request DTO
     * @throws UserNotFoundException if user is not found
     * @throws InvalidCredentialsException if old password doesn't match
     * @throws IllegalArgumentException if new password is invalid
     */
    @Transactional
    public void changePassword(String userId, ChangePasswordDTO request) {
        log.debug("Attempting to change password for user: {}", userId);

        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        // Validate new password is different from old password
        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new IllegalArgumentException("New password must be different from old password");
        }

        UserInfo user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Password change failed: User not found with ID {}", userId);
                    return new UserNotFoundException("User not found with ID: " + userId);
                });

        // Verify old password
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            log.warn("Password change failed: Old password mismatch for user {}", userId);
            throw new InvalidCredentialsException("Old password does not match");
        }

        // Encode and set new password
        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encodedNewPassword);
        
        try {
            userRepository.save(user);
            log.info("Password changed successfully for userId: {}", userId);
        } catch (Exception e) {
            log.error("Failed to save password change for user: {}", userId, e);
            throw new RuntimeException("Failed to update password", e);
        }

        // Publish Kafka event
        try {
            UserPasswordChangedEvent event = new UserPasswordChangedEvent(userId);
            userEventProducer.publish(event, userId);
            log.debug("Password changed event published for user: {}", userId);
        } catch (Exception e) {
            log.error("Failed to publish password changed event for user: {}", userId, e);
            // Non-critical error - password is changed, just log the event failure
        }
    }

    /**
     * Sanitizes email address by trimming and converting to lowercase.
     */
    private String sanitizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    /**
     * Sanitizes username by trimming whitespace.
     */
    private String sanitizeUsername(String username) {
        if (username == null) {
            return null;
        }
        return username.trim();
    }

    /**
     * Sanitizes name by trimming whitespace.
     */
    private String sanitizeName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim();
    }

    /**
     * Parses phone number string to Long, handling nulls.
     */
    private Long parsePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            return null;
        }
        try {
            // Remove any non-digit characters except leading +
            String cleaned = phoneNumber.trim().replaceAll("[^\\d+]", "");
            if (cleaned.isEmpty()) {
                return null;
            }
            // For now, store as Long - in production, consider storing as String
            // to preserve international format
            return Long.parseLong(cleaned.replaceAll("\\+", ""));
        } catch (NumberFormatException e) {
            log.warn("Invalid phone number format: {}", phoneNumber);
            return null;
        }
    }

    /**
     * Generates a username from email address (part before @).
     */
    private String generateUsernameFromEmail(String email) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        return email.substring(0, email.indexOf("@")).toLowerCase();
    }
}
