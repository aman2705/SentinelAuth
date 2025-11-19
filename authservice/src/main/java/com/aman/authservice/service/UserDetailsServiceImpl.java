package com.aman.authservice.service;

import com.aman.authservice.entities.UserInfo;
import com.aman.authservice.eventProducer.UserInfoEvent;
import com.aman.authservice.eventProducer.UserInfoProducer;
import com.aman.authservice.model.UserInfoDTO;
import com.aman.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserInfoProducer userInfoProducer;

    /**
     * Spring Security uses this method to load a user during authentication.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: {}", username);

        UserInfo user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found for username: {}", username);
                    return new UsernameNotFoundException("User not found with username: " + username);
                });

        log.debug("User found: {}", username);
        return new CustomUserDetails(user);
    }


    /**
     * Fetches userId for a given username.
     * Returns null if the user does not exist.
     */
    public String getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(UserInfo::getUserId)
                .orElse(null);
    }

    /**
     * Publishes a signup event to Kafka.
     */
    public void publishUserEvent(UserInfoDTO userInfoDto, String userId) {
        if (userInfoDto == null || userId == null) {
            log.warn("Cannot publish user event: userInfoDto or userId is null");
            return;
        }

        UserInfoEvent event = UserInfoEvent.builder()
                .userId(userId)
                .firstName(userInfoDto.getFirstName())
                .lastName(userInfoDto.getLastName())
                .email(userInfoDto.getEmail())
                .phoneNumber(userInfoDto.getPhoneNumber())
                .build();

        userInfoProducer.sendEventToKafka(event);
        log.info("Published user signup event for userId: {}", userId);
    }
}
