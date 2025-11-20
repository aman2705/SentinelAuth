package com.aman.authservice.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class UserEvent {
    private String userId;
    private String eventType;
    private Instant eventTimestamp;
}
