//package com.aman.authservice.ratelimit;
//
//import com.aman.authservice.config.RateLimitingConfiguration;
//import com.aman.authservice.config.RateLimitingProperties;
//import com.aman.authservice.ratelimit.exception.BruteForceLockoutException;
//import com.aman.authservice.ratelimit.model.RateLimitKeyStrategy;
//import com.aman.authservice.ratelimit.model.RateLimiterDefinition;
//import com.aman.authservice.ratelimit.resolver.RateLimitKeyResolver;
//import com.aman.authservice.ratelimit.service.BruteForceProtectionService;
//import com.aman.authservice.ratelimit.service.RedisRateLimiterService;
//import com.aman.authservice.util.ClientRequestMetadataExtractor;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
//import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
//import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
//import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.context.annotation.Import;
//import org.springframework.test.context.ActiveProfiles;
//import org.springframework.test.context.DynamicPropertyRegistry;
//import org.springframework.test.context.DynamicPropertySource;
//import org.testcontainers.containers.GenericContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;
//
//import java.time.Duration;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//
//@Testcontainers
//@SpringBootTest(classes = RateLimitingIntegrationTest.Config.class)
//@ActiveProfiles("test")
//class RateLimitingIntegrationTest {
//
//    @Container
//    static GenericContainer<?> redis =
//            new GenericContainer<>("redis:7.2.4-alpine").withExposedPorts(6379);
//
//    @DynamicPropertySource
//    static void redisProperties(DynamicPropertyRegistry registry) {
//        registry.add("spring.data.redis.host", redis::getHost);
//        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
//        registry.add("spring.data.redis.cluster.nodes", () -> "");
//    }
//
//    @Autowired
//    private RedisRateLimiterService redisRateLimiterService;
//
//    @Autowired
//    private RateLimitKeyResolver rateLimitKeyResolver;
//
//    @Autowired
//    private BruteForceProtectionService bruteForceProtectionService;
//
//    @MockBean
//    private com.aman.authservice.service.RefreshTokenService refreshTokenService;
//
//    @Test
//    void fixedWindowBlocksAfterThreshold() {
//        var metadata = rateLimitKeyResolver.buildMetadata("192.168.0.1", "tenant-a", "alice");
//        RateLimiterDefinition definition = RateLimiterDefinition.builder()
//                .name("fixed-test")
//                .fixedWindowKeyStrategy(RateLimitKeyStrategy.IP)
//                .fixedWindowLimit(2)
//                .fixedWindowDuration(Duration.ofSeconds(30))
//                .slidingWindowKeyStrategy(RateLimitKeyStrategy.IP_USERNAME)
//                .slidingWindowLimit(0)
//                .slidingWindowDuration(Duration.ZERO)
//                .build();
//
//        assertThat(redisRateLimiterService.allow(definition, metadata)).isTrue();
//        assertThat(redisRateLimiterService.allow(definition, metadata)).isTrue();
//        assertThat(redisRateLimiterService.allow(definition, metadata)).isFalse();
//    }
//
//    @Test
//    void slidingWindowPreventsBurstAcrossWindow() {
//        var metadata = rateLimitKeyResolver.buildMetadata("10.0.0.10", "tenant-b", "bob");
//        RateLimiterDefinition definition = RateLimiterDefinition.builder()
//                .name("sliding-test")
//                .fixedWindowKeyStrategy(RateLimitKeyStrategy.IP)
//                .fixedWindowLimit(0)
//                .fixedWindowDuration(Duration.ZERO)
//                .slidingWindowKeyStrategy(RateLimitKeyStrategy.IP_USERNAME)
//                .slidingWindowLimit(3)
//                .slidingWindowDuration(Duration.ofSeconds(120))
//                .build();
//
//        assertThat(redisRateLimiterService.allow(definition, metadata)).isTrue();
//        assertThat(redisRateLimiterService.allow(definition, metadata)).isTrue();
//        assertThat(redisRateLimiterService.allow(definition, metadata)).isTrue();
//        assertThat(redisRateLimiterService.allow(definition, metadata)).isFalse();
//    }
//
//    @Test
//    void bruteForceLockoutTriggersAfterThreeFailures() {
//        String ip = "203.0.113.15";
//        String user = "charlie";
//
//        bruteForceProtectionService.reset(ip, user);
//        bruteForceProtectionService.ensureAllowed(ip, user);
//
//        bruteForceProtectionService.recordFailure(ip, user);
//        bruteForceProtectionService.recordFailure(ip, user);
//        assertThatThrownBy(() -> bruteForceProtectionService.recordFailure(ip, user))
//                .isInstanceOf(BruteForceLockoutException.class);
//
//        assertThatThrownBy(() -> bruteForceProtectionService.ensureAllowed(ip, user))
//                .isInstanceOf(BruteForceLockoutException.class);
//
//        bruteForceProtectionService.reset(ip, user);
//        bruteForceProtectionService.ensureAllowed(ip, user);
//    }
//
//    @SpringBootApplication
//    @EnableAutoConfiguration(exclude = {
//            DataSourceAutoConfiguration.class,
//            HibernateJpaAutoConfiguration.class,
//            KafkaAutoConfiguration.class
//    })
//    @ComponentScan(basePackageClasses = {
//            RedisRateLimiterService.class,
//            BruteForceProtectionService.class,
//            RateLimitKeyResolver.class,
//            RateLimitingConfiguration.class,
//            ClientRequestMetadataExtractor.class
//    })
//    @EnableConfigurationProperties(RateLimitingProperties.class)
//    static class Config {
//    }
//}
//
