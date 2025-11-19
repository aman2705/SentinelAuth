package com.aman.userservice.consumer;

import com.aman.userservice.domain.UserInfoDTO;
import com.aman.userservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceConsumer {

    private final UserService userService;

    @KafkaListener(
            topics = "${spring.kafka.topic-json.name}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(UserInfoDTO eventData) {
        try {
            log.info("Received UserInfoEvent from Kafka for email: {}", eventData.getEmail());
            userService.createOrUpdateUser(eventData);
            log.info("Successfully processed event for user: {}", eventData.getUserId());
        } catch (Exception ex) {
            log.error("Error processing UserInfoEvent for email {}: {}", eventData.getEmail(), ex.getMessage(), ex);
            // You can implement a dead-letter topic or retry mechanism here
        }
    }
}