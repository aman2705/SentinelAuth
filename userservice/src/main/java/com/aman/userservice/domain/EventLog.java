package com.aman.userservice.domain;

import com.aman.userservice.events.UserEvent;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Event log entity for storing processed events with idempotency support.
 * Tracks event processing to prevent duplicate handling.
 */
@Entity
@Table(name = "auth_events", indexes = {
        @Index(name = "idx_user_id_timestamp", columnList = "userId,eventTimestamp"),
        @Index(name = "idx_event_id", columnList = "eventId", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", unique = true, nullable = false)
    private String eventId; // Unique identifier for idempotency (partition-offset or correlationId)

    private String userId;

    private String eventType;

    private Instant eventTimestamp;

    @Column(name = "topic")
    private String topic;

    @Column(name = "partition_id")
    private Integer partition;

    @Column(name = "offset_value")
    private Long offset;

    @Column(name = "processed_at")
    private Instant processedAt;

    public static EventLog from(UserEvent event) {
        return EventLog.builder()
                .userId(event.getUserId())
                .eventType(event.getEventType())
                .eventTimestamp(event.getEventTimestamp())
                .processedAt(Instant.now())
                .build();
    }

    /**
     * Creates event ID for idempotency check.
     * Format: {topic}-{partition}-{offset}
     */
    public static String createEventId(String topic, Integer partition, Long offset) {
        return String.format("%s-%d-%d", topic, partition, offset);
    }
}
