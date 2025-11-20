package com.aman.authservice.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

/**
 * Production-ready serializer that handles all event types (UserEvent, UserInfoEvent, etc.).
 * Handles serialization errors gracefully with proper logging.
 */
@Slf4j
public class UserEventSerializer implements Serializer<Object> {

    private final ObjectMapper objectMapper;

    public UserEventSerializer() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // Configuration is handled in constructor
    }

    /**
     * Serializes any event object to JSON bytes.
     * Handles UserEvent, UserInfoEvent, and all other event types.
     *
     * @param topic Topic name (for logging/debugging)
     * @param event Event object to serialize
     * @return Serialized byte array
     * @throws RuntimeException if serialization fails
     */
    @Override
    public byte[] serialize(String topic, Object event) {
        if (event == null) {
            log.warn("Attempted to serialize null event for topic: {}", topic);
            return null;
        }

        try {
            String eventType = event.getClass().getSimpleName();
            byte[] serialized = objectMapper.writeValueAsBytes(event);
            
            log.debug("Serialized event | topic={} | eventType={} | size={} bytes",
                    topic, eventType, serialized.length);
            
            return serialized;
        } catch (Exception e) {
            String eventType = event != null ? event.getClass().getSimpleName() : "null";
            log.error("Failed to serialize event | topic={} | eventType={} | error={}",
                    topic, eventType, e.getMessage(), e);
            throw new RuntimeException("Error serializing event of type: " + eventType, e);
        }
    }

    @Override
    public void close() {
        // Cleanup if needed
    }
}
