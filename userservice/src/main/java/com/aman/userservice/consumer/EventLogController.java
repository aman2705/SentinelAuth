package com.aman.userservice.controller;

import com.aman.userservice.domain.EventLog;
import com.aman.userservice.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class EventLogController {

    private final EventLogRepository eventLogRepository;

    /**
     * Get all authentication events
     */
    @GetMapping
    public ResponseEntity<List<EventLog>> getAllEvents() {
        log.info("Fetching all auth events");
        return ResponseEntity.ok(eventLogRepository.findAll());
    }

    /**
     * Get events for a specific user
     */
    @GetMapping("/{userId}")
    public ResponseEntity<List<EventLog>> getEventsByUser(@PathVariable String userId) {
        log.info("Fetching events for userId={}", userId);
        List<EventLog> events = eventLogRepository.findByUserIdOrderByEventTimestampDesc(userId);
        return ResponseEntity.ok(events);
    }
}
