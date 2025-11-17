package com.aman.userservice.deserializer;

import com.aman.userservice.domain.UserInfoDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Deserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class UserInfoDeserializer implements Deserializer<UserInfoDTO> {

    private static final Logger log = LoggerFactory.getLogger(UserInfoDeserializer.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {
        // NO-OP
    }

    @Override
    public UserInfoDTO deserialize(String topic, byte[] data) {
        try {
            if (data == null) {
                return null;
            }
            return mapper.readValue(data, UserInfoDTO.class);
        } catch (Exception e) {
            log.error("Failed to deserialize UserInfoDTO from topic {}", topic, e);
            return null;
        }
    }

    @Override
    public void close() {
        // NO-OP
    }
}
