package com.aman.userservice.repository;

import com.aman.userservice.domain.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    List<EventLog> findByUserIdOrderByEventTimestampDesc(String userId);
}