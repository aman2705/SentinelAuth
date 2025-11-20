package com.aman.userservice.deserializer;

import com.aman.userservice.domain.UserInfoDTO;
import com.aman.userservice.events.UserEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

@Slf4j
public class UserEventUnifiedDeserializer implements Deserializer<Object> {

    private final ObjectMapper mapper;

    public UserEventUnifiedDeserializer() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public Object deserialize(String topic, byte[] bytes) {
        try {
            if (bytes == null) return null;

            JsonNode node = mapper.readTree(bytes);

            if (!node.has("eventType")) {
                // → SIGNUP event (UserInfoDTO payload)
                log.info("Deserializing SIGNUP event as UserInfoDTO");
                return mapper.treeToValue(node, UserInfoDTO.class);
            }

            // Normal auth events
            String eventType = node.get("eventType").asText();
            log.info("Deserializing AUTH Event: {}", eventType);

            return mapper.treeToValue(node, UserEvent.class);

        } catch (Exception e) {
            log.error("Failed to deserialize event", e);
            return null;
        }
    }
}
