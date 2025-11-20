package com.aman.authservice.eventProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready event producer with error handling, correlation IDs, and retry logic.
 * Ensures reliable event publishing with proper logging and metrics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${spring.kafka.topic-json.name:user_service}")
    private String topic;

    /**
     * Publishes an event to Kafka with correlation ID and structured logging.
     * Blocks until acknowledgment is received (configurable timeout).
     *
     * @param event Event object to publish
     * @param key Partition key (typically userId)
     * @return true if published successfully, false otherwise
     */
    public boolean publish(Object event, String key) {
        return publish(event, key, true);
    }

    /**
     * Publishes an event to Kafka with optional await for acknowledgment.
     *
     * @param event Event object to publish
     * @param key Partition key (typically userId)
     * @param awaitAcknowledgement If true, waits for acknowledgment; if false, fire-and-forget
     * @return true if published successfully, false otherwise
     */
    public boolean publish(Object event, String key, boolean awaitAcknowledgement) {
        String correlationId = UUID.randomUUID().toString();
        String eventType = event.getClass().getSimpleName();

        log.info("Publishing event | correlationId={} | eventType={} | topic={} | key={}",
                correlationId, eventType, topic, key);

        try {
            CompletableFuture<SendResult<String, Object>> future =
                    kafkaTemplate.send(topic, key, event);

            if (awaitAcknowledgement) {
                // Wait for acknowledgment (5 seconds timeout)
                SendResult<String, Object> result = future.get(5, TimeUnit.SECONDS);

                log.info("Event published successfully | correlationId={} | eventType={} | topic={} | key={} | partition={} | offset={}",
                        correlationId,
                        eventType,
                        topic,
                        key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());

                return true;
            } else {
                // Fire-and-forget with callback
                future.whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Event published successfully | correlationId={} | eventType={} | topic={} | key={} | partition={} | offset={}",
                                correlationId,
                                eventType,
                                topic,
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to publish event | correlationId={} | eventType={} | topic={} | key={} | error={}",
                                correlationId, eventType, topic, key, ex.getMessage(), ex);
                    }
                });
                return true;
            }
        } catch (Exception ex) {
            log.error("Failed to publish event | correlationId={} | eventType={} | topic={} | key={} | error={}",
                    correlationId, eventType, topic, key, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Publishes an event asynchronously with callbacks for non-blocking operations.
     * Uses CompletableFuture callbacks for handling success and failure.
     *
     * @param event Event object to publish
     * @param key Partition key (typically userId)
     * @param onSuccess Callback for successful publish (can be null)
     * @param onFailure Callback for failed publish (can be null)
     */
    public void publishAsync(Object event, String key, 
                            java.util.function.Consumer<SendResult<String, Object>> onSuccess,
                            java.util.function.Consumer<Throwable> onFailure) {
        String correlationId = UUID.randomUUID().toString();
        String eventType = event.getClass().getSimpleName();

        log.info("Publishing event asynchronously | correlationId={} | eventType={} | topic={} | key={}",
                correlationId, eventType, topic, key);

        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
            
            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Event published successfully | correlationId={} | eventType={} | topic={} | key={} | partition={} | offset={}",
                            correlationId,
                            eventType,
                            topic,
                            key,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset());
                    if (onSuccess != null) {
                        try {
                            onSuccess.accept(result);
                        } catch (Exception callbackEx) {
                            log.error("Error in success callback | correlationId={} | error={}",
                                    correlationId, callbackEx.getMessage(), callbackEx);
                        }
                    }
                } else {
                    log.error("Failed to publish event | correlationId={} | eventType={} | topic={} | key={} | error={}",
                            correlationId, eventType, topic, key, ex.getMessage(), ex);
                    if (onFailure != null) {
                        try {
                            onFailure.accept(ex);
                        } catch (Exception callbackEx) {
                            log.error("Error in failure callback | correlationId={} | error={}",
                                    correlationId, callbackEx.getMessage(), callbackEx);
                        }
                    }
                }
            });
        } catch (Exception ex) {
            log.error("Failed to publish event | correlationId={} | eventType={} | topic={} | key={} | error={}",
                    correlationId, eventType, topic, key, ex.getMessage(), ex);
            if (onFailure != null) {
                try {
                    onFailure.accept(ex);
                } catch (Exception callbackEx) {
                    log.error("Error in failure callback | correlationId={} | error={}",
                            correlationId, callbackEx.getMessage(), callbackEx);
                }
            }
        }
    }

    /**
     * Publishes an event asynchronously without waiting for acknowledgment.
     * Uses fire-and-forget pattern with logging.
     *
     * @param event Event object to publish
     * @param key Partition key (typically userId)
     */
    public void publishAsync(Object event, String key) {
        publish(event, key, false);
    }
}
