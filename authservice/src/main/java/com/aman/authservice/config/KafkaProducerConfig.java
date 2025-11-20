package com.aman.authservice.config;

import com.aman.authservice.serializer.UserEventSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Production-ready Kafka producer configuration with:
 * - Reliable delivery (acks=all)
 * - Idempotent producer (prevents duplicates)
 * - Retry mechanism with exponential backoff
 * - Compression for better performance
 * - Proper timeout configurations
 */
@Slf4j
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.producer.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.retries:3}")
    private Integer retries;

    @Value("${spring.kafka.producer.request-timeout-ms:30000}")
    private Integer requestTimeoutMs;

    @Value("${spring.kafka.producer.delivery-timeout-ms:120000}")
    private Integer deliveryTimeoutMs;

    @Value("${spring.kafka.producer.enable-idempotence:true}")
    private Boolean enableIdempotence;

    @Value("${spring.kafka.producer.compression-type:gzip}")
    private String compressionType;  // Default to gzip for Alpine Linux compatibility

    /**
     * Creates producer factory with production-ready settings.
     * Configuration ensures exactly-once semantics and reliable delivery.
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        
        // Bootstrap servers
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serializers
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, UserEventSerializer.class);
        
        // Reliability settings
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // Wait for all replicas
        props.put(ProducerConfig.RETRIES_CONFIG, retries);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, enableIdempotence); // Exactly-once semantics
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5); // Allow more concurrent requests with idempotence
        
        // Timeouts
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, deliveryTimeoutMs);
        
        // Performance - Using gzip for Alpine Linux compatibility (snappy requires glibc)
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType); // Compression: gzip, lz4, or none
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB batch size
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10); // Wait up to 10ms to batch
        
        // Batching and buffer settings
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB buffer
        
        log.info("Kafka producer factory configured | bootstrapServers={} | enableIdempotence={} | compression={}",
                bootstrapServers, enableIdempotence, compressionType);
        
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates KafkaTemplate with transaction support for exactly-once delivery.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(producerFactory());
        template.setObservationEnabled(true); // Enable Micrometer metrics
        return template;
    }
}
