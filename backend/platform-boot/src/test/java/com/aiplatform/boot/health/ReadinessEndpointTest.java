package com.aiplatform.boot.health;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        classes = ReadinessEndpointTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.function.definition=",
                "spring.cloud.stream.enabled=false"
        }
)
@AutoConfigureTestRestTemplate
class ReadinessEndpointTest {
    private static final List<String> CRITICAL_DEPENDENCIES = List.of(
            "db",
            "redis",
            "minio",
            "binders",
            "rocketmq"
    );
    private static final String SENSITIVE_DETAIL = "jdbc:postgresql://internal-db/aip?password=never-expose";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DependencyState dependencyState;

    @Autowired
    private JsonMapper objectMapper;

    @BeforeEach
    void setUp() {
        dependencyState.allUp();
    }

    @Test
    void reportsReadyOnlyWhenAllCriticalDependenciesAreUpAndHidesDetails() throws Exception {
        ResponseEntity<String> response = readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(objectMapper.readTree(response.getBody()))
                .isEqualTo(objectMapper.createObjectNode().put("status", "UP"));
        assertThat(response.getBody())
                .doesNotContain(SENSITIVE_DETAIL)
                .doesNotContain("credential")
                .doesNotContain("components");
    }

    @ParameterizedTest
    @ValueSource(strings = {"db", "redis", "minio", "binders", "rocketmq"})
    void reportsNotReadyWhenAnyCriticalDependencyIsDown(String dependency) throws Exception {
        dependencyState.down(dependency);

        ResponseEntity<String> response = readiness();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(objectMapper.readTree(response.getBody()))
                .isEqualTo(objectMapper.createObjectNode().put("status", "DOWN"));
    }

    @Test
    void reportsReadyAgainAfterDependencyRecovers() {
        dependencyState.down("rocketmq");
        assertThat(readiness().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        dependencyState.up("rocketmq");

        assertThat(readiness().getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readiness().getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void keepsLivenessUpWhenEveryCriticalDependencyIsDown() {
        CRITICAL_DEPENDENCIES.forEach(dependencyState::down);

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    private ResponseEntity<String> readiness() {
        return restTemplate.getForEntity("/actuator/health/readiness", String.class);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataRedisAutoConfiguration.class,
            DataRedisReactiveAutoConfiguration.class,
            DataRedisRepositoriesAutoConfiguration.class
    }, excludeName = {
            "cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate",
            "org.springframework.cloud.stream.config.BindersHealthIndicatorAutoConfiguration"
    })
    @Import(HealthController.class)
    static class TestApplication {
        @Bean
        DependencyState dependencyState() {
            return new DependencyState();
        }

        @Bean(name = "dbHealthIndicator")
        HealthIndicator dbHealthIndicator(DependencyState state) {
            return () -> state.health("db");
        }

        @Bean(name = "redisHealthIndicator")
        HealthIndicator redisHealthIndicator(DependencyState state) {
            return () -> state.health("redis");
        }

        @Bean(name = "minioHealthIndicator")
        HealthIndicator minioHealthIndicator(DependencyState state) {
            return () -> state.health("minio");
        }

        @Bean(name = "rocketmqHealthIndicator")
        HealthIndicator rocketmqHealthIndicator(DependencyState state) {
            return () -> state.health("rocketmq");
        }

        @Bean(name = "bindersHealthIndicator")
        HealthIndicator bindersHealthIndicator(DependencyState state) {
            return () -> state.health("binders");
        }
    }

    static final class DependencyState {
        private final Map<String, Boolean> availability = new ConcurrentHashMap<>();

        private DependencyState() {
            allUp();
        }

        void allUp() {
            CRITICAL_DEPENDENCIES.forEach(this::up);
        }

        void up(String dependency) {
            availability.put(dependency, true);
        }

        void down(String dependency) {
            availability.put(dependency, false);
        }

        Health health(String dependency) {
            Health.Builder builder = Boolean.TRUE.equals(availability.get(dependency))
                    ? Health.up()
                    : Health.down();
            return builder
                    .withDetail("credential", SENSITIVE_DETAIL)
                    .withDetail("dependency", dependency)
                    .build();
        }
    }
}
