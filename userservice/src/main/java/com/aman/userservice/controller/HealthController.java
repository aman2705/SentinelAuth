package com.aman.userservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.KafkaFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Health check controller for Kafka connectivity.
 * Provides endpoint to verify Kafka broker connection.
 */
@Slf4j
@RestController
@RequestMapping("/health")
@RequiredArgsConstructor
public class HealthController {

    private final ConsumerFactory<String, Object> consumerFactory;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Basic health check endpoint.
     *
     * @return true if service is up
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now());
        response.put("service", "userservice");
        return ResponseEntity.ok(response);
    }

    /**
     * Kafka connectivity health check.
     * Verifies connection to Kafka broker.
     *
     * @return Health status with Kafka connectivity information
     */
    @GetMapping("/kafka")
    public ResponseEntity<Map<String, Object>> kafkaHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", Instant.now());
        response.put("bootstrapServers", bootstrapServers);

        try {
            // Create admin client to check connectivity
            Map<String, Object> configs = new HashMap<>();
            configs.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            configs.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000); // 5 second timeout

            try (AdminClient adminClient = AdminClient.create(configs)) {
                DescribeClusterResult clusterResult = adminClient.describeCluster();
                KafkaFuture<String> clusterIdFuture = clusterResult.clusterId();

                String clusterId = clusterIdFuture.get(5, TimeUnit.SECONDS);

                response.put("status", "UP");
                response.put("clusterId", clusterId);
                response.put("connected", true);
                log.debug("Kafka health check successful | clusterId={}", clusterId);
            }

        } catch (Exception e) {
            log.error("Kafka health check failed | error={}", e.getMessage(), e);
            response.put("status", "DOWN");
            response.put("connected", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(503).body(response);
        }

        return ResponseEntity.ok(response);
    }
}

