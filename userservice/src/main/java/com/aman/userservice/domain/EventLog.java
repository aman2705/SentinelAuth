package com.aman.userservice.domain;

import com.aman.userservice.events.UserEvent;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "auth_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userId;

    private String eventType;

    private Instant eventTimestamp;

    public static EventLog from(UserEvent event) {
        return EventLog.builder()
                .userId(event.getUserId())
                .eventType(event.getEventType())
                .eventTimestamp(event.getEventTimestamp())
                .build();
    }
}
