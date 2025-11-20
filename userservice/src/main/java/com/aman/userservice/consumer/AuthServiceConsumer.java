package com.aman.userservice.consumer;

import com.aman.userservice.domain.UserInfoDTO;
import com.aman.userservice.events.UserEvent;
import com.aman.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceConsumer {

    private final UserService userService;

    @KafkaListener(
            topics = "user-events",
            groupId = "user-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(Object event) {

        if (event instanceof UserInfoDTO) {
            log.info("📥 Received Signup Event: {}", event);
            userService.handleSignup((UserInfoDTO) event);
        } else if (event instanceof UserEvent) {
            log.info("📥 Received Auth Event: {}", event);
            userService.handleAuthEvent((UserEvent) event);
        } else {
            log.warn("⚠ Unknown event type received: {}", event.getClass());
        }
    }
}
