package com.xg.platform.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceEnvironmentPostProcessorTest {

    private final DataSourceEnvironmentPostProcessor postProcessor = new DataSourceEnvironmentPostProcessor();

    @Test
    void failsWhenDatasourcePropertiesAreUnresolvedPlaceholders() {
        ConfigurableEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "${SPRING_DATASOURCE_URL}")
                .withProperty("spring.datasource.username", "${SPRING_DATASOURCE_USERNAME}")
                .withProperty("spring.datasource.password", "${SPRING_DATASOURCE_PASSWORD}");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, new SpringApplication()))
                .hasMessageContaining("Missing required PostgreSQL datasource configuration")
                .hasMessageContaining("spring.datasource.url")
                .hasMessageContaining(".\\.local\\postgres-env.ps1");
    }

    @Test
    void allowsStartupWhenDatasourcePropertiesAreResolved() {
        ConfigurableEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5433/myagent")
                .withProperty("spring.datasource.username", "myagent_app")
                .withProperty("spring.datasource.password", "secret");

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, new SpringApplication()))
                .doesNotThrowAnyException();
    }
}
