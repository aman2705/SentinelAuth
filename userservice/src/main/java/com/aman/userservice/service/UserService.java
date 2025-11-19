package com.aman.userservice.service;

import com.aman.userservice.domain.UserInfo;
import com.aman.userservice.domain.UserInfoDTO;
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

    public UserInfoDTO createOrUpdateUser(UserInfoDTO userInfoDto) {
        UserInfo userInfo = userRepository.findByUserId(userInfoDto.getUserId())
                .map(existingUser -> {
                    log.info("Updating existing user: {}", existingUser.getUserId());
                    return userRepository.save(userInfoDto.transformToUserInfo());
                })
                .orElseGet(() -> {
                    log.info("Creating new user with email: {}", userInfoDto.getEmail());
                    return userRepository.save(userInfoDto.transformToUserInfo());
                });

        return mapToDTO(userInfo);
    }

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

    public UserInfoDTO getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(userInfo -> new UserInfoDTO(
                        userInfo.getUserId(),
                        userInfo.getUsername(),
                        userInfo.getFirstName(),
                        userInfo.getLastName(),
                        userInfo.getPhoneNumber(),
                        userInfo.getEmail(),
                        userInfo.getProfilePic()
                ))
                .orElseThrow(() -> new NoSuchElementException("User not found with username: " + username));
    }
}
