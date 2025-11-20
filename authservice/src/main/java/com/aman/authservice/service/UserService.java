package com.aman.authservice.service;

import com.aman.authservice.dto.ChangePasswordDTO;
import com.aman.authservice.dto.UserInfoDTO;
import com.aman.authservice.entities.UserInfo;
import com.aman.authservice.eventProducer.UserEventProducer;
import com.aman.authservice.events.UserInfoEvent;
import com.aman.authservice.events.UserPasswordChangedEvent;
import com.aman.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserEventProducer userEventProducer;

    public String signupUser(UserInfoDTO userInfoDto) {
        // Validate if user exists by email
        if (userRepository.findByEmail(userInfoDto.getEmail()).isPresent()) {
            log.warn("Signup failed: User already exists with email {}", userInfoDto.getEmail());
            return null; // can also throw a custom exception instead
        }

        // Optional: Check username if you still need it
        if (userInfoDto.getUsername() != null &&
                userRepository.findByUsername(userInfoDto.getUsername()).isPresent()) {
            log.warn("Signup failed: User already exists with username {}", userInfoDto.getUsername());
            return null;
        }

        // Create new user entity
        UserInfo user = mapDtoToEntity(userInfoDto);

        // Save user in DB
        userRepository.save(user);

        // Publish signup event
        publishSignupEvent(user);

        log.info("User registered successfully: {}", user.getUserId());
        return user.getUserId();
    }

    private UserInfo mapDtoToEntity(UserInfoDTO dto) {
        return UserInfo.builder()
                .userId(UUID.randomUUID().toString())
                .firstName(dto.getFirstName())
                .phoneNumber(dto.getPhoneNumber())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .username(dto.getUsername())
                .password(passwordEncoder.encode(dto.getPassword()))
                .roles(new HashSet<>())
                .build();
    }

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

    public String getUserIdByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(UserInfo::getUserId)
                .orElse(null);
    }

    public void changePassword(String userId, ChangePasswordDTO request) {

        UserInfo user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("Old password does not match");
        }

        String encodedNewPassword = passwordEncoder.encode(request.getNewPassword());
        user.setPassword(encodedNewPassword);
        userRepository.save(user);

        // Publish Kafka event
        UserPasswordChangedEvent event = new UserPasswordChangedEvent(userId);
        userEventProducer.publish(event, userId);

        log.info("Password changed successfully for userId: {}", userId);
    }
}
