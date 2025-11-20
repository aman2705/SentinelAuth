package com.aman.userservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Handles graceful shutdown of Kafka consumers.
 * Ensures in-flight messages are processed before shutdown.
 */
@Slf4j
@Component
public class KafkaGracefulShutdown implements ApplicationListener<ContextClosedEvent> {

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    public KafkaGracefulShutdown(KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry) {
        this.kafkaListenerEndpointRegistry = kafkaListenerEndpointRegistry;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        log.info("Graceful shutdown initiated - stopping Kafka consumers...");

        kafkaListenerEndpointRegistry.getAllListenerContainers().forEach(container -> {
            String containerId = container.getListenerId();
            log.info("Stopping Kafka listener container: {}", containerId);

            try {
                // Stop accepting new messages but allow in-flight processing
                container.stop(() -> {
                    log.info("Kafka listener container stopped: {}", containerId);
                });

                // Wait for graceful shutdown (max 30 seconds)
                if (container instanceof MessageListenerContainer) {
                    ((MessageListenerContainer) container).getContainerProperties().setShutdownTimeout(30000);
                }
            } catch (Exception e) {
                log.error("Error stopping Kafka listener container: {}", containerId, e);
            }
        });

        // Wait for containers to stop
        try {
            Thread.sleep(5000); // Give containers 5 seconds to finish processing
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Graceful shutdown wait interrupted", e);
        }

        log.info("Kafka consumers stopped gracefully");
    }
}

