package com.aman.userservice.service;

import com.aman.userservice.domain.EventLog;
import com.aman.userservice.domain.UserInfo;
import com.aman.userservice.domain.UserInfoDTO;
import com.aman.userservice.events.UserEvent;
import com.aman.userservice.repository.EventLogRepository;
import com.aman.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Production-ready user service with:
 * - Transaction management
 * - Error handling
 * - Structured logging
 * - Idempotency support
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final EventLogRepository eventLogRepository;

    /**
     * Handles user signup event.
     * Creates or updates user in database.
     *
     * @param dto User information DTO from signup event
     * @return Processed user DTO
     * @throws IllegalArgumentException if DTO is invalid
     */
    @Transactional
    public UserInfoDTO handleSignup(UserInfoDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("UserInfoDTO cannot be null");
        }

        if (dto.getUserId() == null || dto.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty");
        }

        String userId = dto.getUserId().trim();
        log.info("Processing signup event | userId={} | email={} | username={}",
                userId, dto.getEmail(), dto.getUsername());

        try {
            UserInfo user = userRepository.findByUserId(userId)
                    .map(existing -> {
                        log.info("Updating existing user | userId={} | email={}",
                                existing.getUserId(), existing.getEmail());
                        UserInfo updated = dto.transformToUserInfo();
                        updated.setUserId(existing.getUserId()); // Preserve existing ID
                        return userRepository.save(updated);
                    })
                    .orElseGet(() -> {
                        log.info("Creating new user | userId={} | email={} | username={}",
                                userId, dto.getEmail(), dto.getUsername());
                        return userRepository.save(dto.transformToUserInfo());
                    });

            log.info("User signup processed successfully | userId={} | email={} | username={}",
                    user.getUserId(), user.getEmail(), user.getUsername());

            return mapToDTO(user);
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation during signup | userId={} | error={}",
                    userId, e.getMessage(), e);
            // Try to fetch existing user if duplicate key error
            return userRepository.findByUserId(userId)
                    .map(this::mapToDTO)
                    .orElseThrow(() -> new RuntimeException("Failed to process signup: " + e.getMessage(), e));
        } catch (Exception e) {
            log.error("Failed to process signup | userId={} | error={}",
                    userId, e.getMessage(), e);
            throw new RuntimeException("Failed to process signup: " + e.getMessage(), e);
        }
    }

    /**
     * Handles authentication events (login, logout, token refresh, password change).
     * Note: EventLog is already saved in the consumer for idempotency tracking.
     * This method only processes the business logic for the event.
     *
     * @param event User event to process
     * @throws IllegalArgumentException if event is invalid
     */
    @Transactional
    public void handleAuthEvent(UserEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("UserEvent cannot be null");
        }

        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be null or empty in event");
        }

        String userId = event.getUserId().trim();
        String eventType = event.getEventType();

        log.info("Processing auth event | userId={} | eventType={} | timestamp={}",
                userId, eventType, event.getEventTimestamp());

        try {
            // Note: EventLog is already saved in the consumer (AuthServiceConsumer) 
            // for idempotency tracking. We only process business logic here.
            // If you need to log events from other sources, create EventLog with a generated eventId.
            
            // Business logic for auth events can be added here
            // For example: update user last login time, trigger notifications, etc.
            
            log.info("Auth event processed successfully | userId={} | eventType={} | timestamp={}",
                    userId, eventType, event.getEventTimestamp());
        } catch (Exception e) {
            log.error("Failed to process auth event | userId={} | eventType={} | error={}",
                    userId, eventType, e.getMessage(), e);
            throw new RuntimeException("Failed to process auth event: " + e.getMessage(), e);
        }
    }

    /**
     * Converts UserInfo entity to DTO.
     *
     * @param userInfo UserInfo entity
     * @return UserInfoDTO
     */
    private UserInfoDTO mapToDTO(UserInfo userInfo) {
        if (userInfo == null) {
            return null;
        }

        return new UserInfoDTO(
                userInfo.getUserId(),
                userInfo.getUsername(),
                userInfo.getFirstName(),
                userInfo.getLastName(),
                userInfo.getPhoneNumber(),
                userInfo.getEmail(),
                userInfo.getProfilePic()
        );
    }

    /**
     * Fetches user by username.
     *
     * @param username Username to search for
     * @return UserInfoDTO
     * @throws NoSuchElementException if user not found
     */
    public UserInfoDTO getUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }

        log.debug("Fetching user by username | username={}", username);

        return userRepository.findByUsername(username.trim())
                .map(this::mapToDTO)
                .orElseThrow(() -> {
                    log.warn("User not found | username={}", username);
                    return new NoSuchElementException("User not found with username: " + username);
                });
    }
}
