package com.aman.userservice.deserializer;

import com.aman.userservice.domain.UserInfoDTO;
import com.aman.userservice.events.UserEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import java.util.Map;

/**
 * Production-ready unified deserializer that handles all event types.
 * Properly deserializes UserInfoEvent (signup) and UserEvent (auth events).
 * Handles deserialization errors gracefully with proper logging.
 */
@Slf4j
public class UserEventUnifiedDeserializer implements Deserializer<Object> {

    private final ObjectMapper mapper;

    public UserEventUnifiedDeserializer() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // Configuration is handled in constructor
    }

    /**
     * Deserializes Kafka message bytes to appropriate event type.
     * Handles:
     * - UserInfoDTO (signup events without eventType field)
     * - UserEvent (auth events with eventType field)
     *
     * @param topic Topic name (for logging)
     * @param bytes Serialized event bytes
     * @return Deserialized event object
     * @throws RuntimeException if deserialization fails (will trigger retry/DLQ)
     */
    @Override
    public Object deserialize(String topic, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            log.warn("Received null or empty bytes for topic: {}", topic);
            return null;
        }

        try {
            JsonNode node = mapper.readTree(bytes);

            if (node == null || node.isNull()) {
                log.warn("Parsed JSON node is null for topic: {}", topic);
                return null;
            }

            // Check if event has eventType field to determine event type
            if (!node.has("eventType") || node.get("eventType").isNull()) {
                // SIGNUP event (UserInfoDTO payload without eventType)
                log.debug("Deserializing SIGNUP event as UserInfoDTO | topic={}", topic);
                try {
                    UserInfoDTO signupEvent = mapper.treeToValue(node, UserInfoDTO.class);
                    log.debug("Successfully deserialized SIGNUP event | topic={} | userId={}",
                            topic, signupEvent != null ? signupEvent.getUserId() : "null");
                    return signupEvent;
                } catch (Exception e) {
                    log.error("Failed to deserialize SIGNUP event (UserInfoDTO) | topic={} | error={}",
                            topic, e.getMessage(), e);
                    throw new RuntimeException("Failed to deserialize SIGNUP event: " + e.getMessage(), e);
                }
            }

            // Auth event with eventType field
            String eventType = node.get("eventType").asText();
            log.debug("Deserializing AUTH event | topic={} | eventType={}", topic, eventType);

            try {
                UserEvent authEvent = mapper.treeToValue(node, UserEvent.class);
                log.debug("Successfully deserialized AUTH event | topic={} | eventType={} | userId={}",
                        topic, eventType, authEvent != null ? authEvent.getUserId() : "null");
                return authEvent;
            } catch (Exception e) {
                log.error("Failed to deserialize AUTH event (UserEvent) | topic={} | eventType={} | error={}",
                        topic, eventType, e.getMessage(), e);
                throw new RuntimeException("Failed to deserialize AUTH event: " + e.getMessage(), e);
            }

        } catch (RuntimeException e) {
            // Re-throw runtime exceptions (they contain the original error)
            throw e;
        } catch (Exception e) {
            log.error("Failed to deserialize event | topic={} | error={} | bytesLength={}",
                    topic, e.getMessage(), bytes.length, e);
            // Throw runtime exception to trigger error handler and retry mechanism
            throw new RuntimeException("Failed to deserialize event from topic " + topic + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        // Cleanup if needed
    }
}
