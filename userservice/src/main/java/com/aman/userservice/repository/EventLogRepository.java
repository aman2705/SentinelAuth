package com.aman.userservice.repository;

import com.aman.userservice.domain.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for event logs with idempotency support.
 */
@Repository
public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    List<EventLog> findByUserIdOrderByEventTimestampDesc(String userId);
    
    /**
     * Checks if an event has already been processed (idempotency check).
     *
     * @param eventId Unique event identifier (format: topic-partition-offset)
     * @return Optional EventLog if event was already processed
     */
    Optional<EventLog> findByEventId(String eventId);
}