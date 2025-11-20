package com.aman.userservice.service;

import com.aman.userservice.domain.EventLog;
import com.aman.userservice.domain.UserInfo;
import com.aman.userservice.domain.UserInfoDTO;
import com.aman.userservice.events.UserEvent;
import com.aman.userservice.repository.EventLogRepository;
import com.aman.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final EventLogRepository eventLogRepository;

    /**
     * -------------------------
     * SIGNUP EVENT HANDLING
     * -------------------------
     * Signup sends full UserInfoDTO
     * → Save/Update user in database
     */
    public UserInfoDTO handleSignup(UserInfoDTO dto) {

        log.info("Processing signup for userId: {}", dto.getUserId());

        UserInfo user = userRepository.findByUserId(dto.getUserId())
                .map(existing -> {
                    log.info("Updating existing user {}", existing.getUserId());
                    return userRepository.save(dto.transformToUserInfo());
                })
                .orElseGet(() -> {
                    log.info("Creating new user {}", dto.getUserId());
                    return userRepository.save(dto.transformToUserInfo());
                });

        return mapToDTO(user);
    }


    /**
     * -------------------------
     * AUTH EVENTS HANDLING
     * -------------------------
     * Login, Logout, Refresh, Password Change
     * → Only log in auth_events table
     */
    public void handleAuthEvent(UserEvent event) {

        log.info("Saving auth event: {} for userId={}", event.getEventType(), event.getUserId());

        EventLog eventLog = EventLog.from(event);
        eventLogRepository.save(eventLog);
    }


    /**
     * Convert UserInfo → DTO
     */
    private UserInfoDTO mapToDTO(UserInfo userInfo) {
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
     * Fetch user by username
     */
    public UserInfoDTO getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(userInfo -> mapToDTO(userInfo))
                .orElseThrow(() -> new NoSuchElementException(
                        "User not found with username: " + username
                ));
    }
}
