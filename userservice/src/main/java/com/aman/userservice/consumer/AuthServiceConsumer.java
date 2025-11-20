package com.aman.userservice.consumer;

import com.aman.userservice.domain.EventLog;
import com.aman.userservice.domain.UserInfoDTO;
import com.aman.userservice.events.UserEvent;
import com.aman.userservice.repository.EventLogRepository;
import com.aman.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Production-ready Kafka consumer with:
 * - Error handling and retry logic
 * - Idempotency checks to prevent duplicate processing
 * - Structured logging with correlation IDs
 * - Manual acknowledgment for exactly-once processing
 * - Dead-letter queue (DLQ) support via error handler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceConsumer {

    private final UserService userService;
    private final EventLogRepository eventLogRepository;

    @Value("${spring.kafka.topic-json.name:user_service}")
    private String expectedTopic;

    /**
     * Consumes events from Kafka with idempotency checks and error handling.
     * 
     * CRITICAL FIX: Changed topic from "user-events" to match producer topic "user_service"
     * 
     * @param record Kafka consumer record with metadata
     * @param acknowledgment Manual acknowledgment for exactly-once processing
     */
    @KafkaListener(
            topics = "${spring.kafka.topic-json.name:user_service}", // FIXED: Now matches producer topic
            groupId = "${spring.kafka.consumer.group-id:user-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(ConsumerRecord<String, Object> record, Acknowledgment acknowledgment) {
        String correlationId = UUID.randomUUID().toString();
        String eventId = EventLog.createEventId(record.topic(), record.partition(), record.offset());
        
        log.info("Event received | correlationId={} | eventId={} | topic={} | partition={} | offset={} | key={}",
                correlationId, eventId, record.topic(), record.partition(), record.offset(), record.key());

        // Idempotency check: Skip if event was already processed
        if (eventLogRepository.findByEventId(eventId).isPresent()) {
            log.warn("Duplicate event detected, skipping | correlationId={} | eventId={} | topic={} | partition={} | offset={}",
                    correlationId, eventId, record.topic(), record.partition(), record.offset());
            acknowledgment.acknowledge();
            return;
        }

        try {
            Object event = record.value();
            
            if (event == null) {
                log.warn("Received null event, skipping | correlationId={} | eventId={} | topic={} | partition={} | offset={}",
                        correlationId, eventId, record.topic(), record.partition(), record.offset());
                acknowledgment.acknowledge();
                return;
            }

            String eventType = event.getClass().getSimpleName();
            log.info("Processing event | correlationId={} | eventId={} | eventType={} | topic={} | partition={} | offset={}",
                    correlationId, eventId, eventType, record.topic(), record.partition(), record.offset());

            // Process event based on type
            boolean processed = false;
            String processedEventType = null;
            
            if (event instanceof UserInfoDTO) {
                UserInfoDTO signupEvent = (UserInfoDTO) event;
                processedEventType = "USER_SIGNUP";
                log.info("Processing signup event | correlationId={} | eventId={} | userId={}",
                        correlationId, eventId, signupEvent.getUserId());
                userService.handleSignup(signupEvent);
                processed = true;
            } else if (event instanceof UserEvent) {
                UserEvent userEvent = (UserEvent) event;
                processedEventType = userEvent.getEventType();
                log.info("Processing auth event | correlationId={} | eventId={} | eventType={} | userId={}",
                        correlationId, eventId, userEvent.getEventType(), userEvent.getUserId());
                userService.handleAuthEvent(userEvent);
                processed = true;
            } else {
                log.warn("Unknown event type received | correlationId={} | eventId={} | eventType={} | topic={} | partition={} | offset={}",
                        correlationId, eventId, event.getClass().getName(), record.topic(), record.partition(), record.offset());
            }

            if (processed) {
                // Mark event as processed for idempotency
                EventLog eventLog = EventLog.builder()
                        .eventId(eventId)
                        .topic(record.topic())
                        .partition(record.partition())
                        .offset(record.offset())
                        .processedAt(Instant.now())
                        .build();
                
                // Store event metadata for idempotency
                if (event instanceof UserEvent) {
                    UserEvent userEvent = (UserEvent) event;
                    eventLog.setUserId(userEvent.getUserId());
                    eventLog.setEventType(userEvent.getEventType());
                    eventLog.setEventTimestamp(userEvent.getEventTimestamp());
                } else if (event instanceof UserInfoDTO) {
                    UserInfoDTO userInfoDTO = (UserInfoDTO) event;
                    eventLog.setUserId(userInfoDTO.getUserId());
                    eventLog.setEventType("USER_SIGNUP");
                    eventLog.setEventTimestamp(Instant.now());
                }
                
                try {
                    eventLogRepository.save(eventLog);
                    log.debug("Event marked as processed | correlationId={} | eventId={}", correlationId, eventId);
                } catch (Exception e) {
                    log.error("Failed to save event log (non-critical) | correlationId={} | eventId={} | error={}",
                            correlationId, eventId, e.getMessage(), e);
                    // Non-critical - event is still processed
                }

                // Acknowledge after successful processing
                acknowledgment.acknowledge();
                
                log.info("Event processed successfully | correlationId={} | eventId={} | eventType={} | processedEventType={} | topic={} | partition={} | offset={}",
                        correlationId, eventId, eventType, processedEventType, record.topic(), record.partition(), record.offset());
            } else {
                // Acknowledge even for unknown events to avoid infinite retries
                acknowledgment.acknowledge();
                log.warn("Event acknowledged but not processed | correlationId={} | eventId={} | eventType={}",
                        correlationId, eventId, eventType);
            }

        } catch (Exception ex) {
            log.error("Error processing event | correlationId={} | eventId={} | topic={} | partition={} | offset={} | error={}",
                    correlationId, eventId, record.topic(), record.partition(), record.offset(), ex.getMessage(), ex);
            
            // Don't acknowledge on error - let retry mechanism handle it
            // The error handler will retry and eventually send to DLQ after max retries
            throw new RuntimeException("Failed to process event: " + ex.getMessage(), ex);
        }
    }
}
