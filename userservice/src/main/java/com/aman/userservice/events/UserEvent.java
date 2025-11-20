package com.aman.userservice.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserEvent {

    private String userId;
    private String eventType;
    private Instant eventTimestamp;
}
