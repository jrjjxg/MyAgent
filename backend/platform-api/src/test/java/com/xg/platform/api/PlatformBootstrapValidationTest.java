package com.xg.platform.api;

import com.xg.platform.PlatformApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.AbstractDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlatformBootstrapValidationTest {

    @Test
    void failsToStartWithoutDataSourceProperties() {
        assertThatThrownBy(() -> runApplication(
                "missing-datasource",
                "--spring.datasource.url=${TEST_MISSING_SPRING_DATASOURCE_URL}",
                "--spring.datasource.username=${TEST_MISSING_SPRING_DATASOURCE_USERNAME}",
                "--spring.datasource.password=${TEST_MISSING_SPRING_DATASOURCE_PASSWORD}"))
                .hasMessageContaining("Missing required PostgreSQL datasource configuration")
                .hasMessageContaining("spring.datasource.url")
                .hasMessageContaining(".\\.local\\postgres-env.ps1");
    }

    @Test
    void failsToStartWhenCheckpointDatabaseIsNotPostgres() {
        assertThatThrownBy(() -> runApplication("non-postgres",
                NonPostgresDataSourceConfig.class,
                "--spring.flyway.enabled=false",
                "--spring.datasource.url=jdbc:ignored",
                "--spring.datasource.username=test",
                "--spring.datasource.password=test"))
                .hasMessageContaining("LangGraph4j checkpoint persistence requires PostgreSQL");
    }

    private void runApplication(String dataRootSuffix) {
        Path dataRoot = createDataRoot(dataRootSuffix);
        SpringApplicationBuilder builder = new SpringApplicationBuilder(PlatformApiApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "platform.data-root=" + normalize(Path.of("target", "bootstrap-tests", dataRoot.getFileName().toString())),
                        "platform.skills-root=" + normalize(Path.of("..", "..", "skills")),
                        "platform.extensions-config-path=" + normalize(Path.of("..", "..", "extensions.json")),
                        "platform.tools.document-script=" + normalize(Path.of("..", "..", "tools", "document_tools.py"))
                );
        try (ConfigurableApplicationContext ignored = builder.run()) {
        }
    }

    private void runApplication(String dataRootSuffix, String... additionalProperties) {
        Path dataRoot = createDataRoot(dataRootSuffix);
        String[] baseProperties = new String[]{
                "--platform.data-root=" + normalize(Path.of("target", "bootstrap-tests", dataRoot.getFileName().toString())),
                "--platform.skills-root=" + normalize(Path.of("..", "..", "skills")),
                "--platform.extensions-config-path=" + normalize(Path.of("..", "..", "extensions.json")),
                "--platform.tools.document-script=" + normalize(Path.of("..", "..", "tools", "document_tools.py"))
        };
        String[] allProperties = new String[baseProperties.length + additionalProperties.length];
        System.arraycopy(baseProperties, 0, allProperties, 0, baseProperties.length);
        System.arraycopy(additionalProperties, 0, allProperties, baseProperties.length, additionalProperties.length);
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(PlatformApiApplication.class)
                .web(WebApplicationType.NONE)
                .run(allProperties)) {
        }
    }

    private void runApplication(String dataRootSuffix, Class<?> extraSource, String... additionalProperties) {
        Path dataRoot = createDataRoot(dataRootSuffix);
        String[] baseProperties = new String[]{
                "--platform.data-root=" + normalize(Path.of("target", "bootstrap-tests", dataRoot.getFileName().toString())),
                "--platform.skills-root=" + normalize(Path.of("..", "..", "skills")),
                "--platform.extensions-config-path=" + normalize(Path.of("..", "..", "extensions.json")),
                "--platform.tools.document-script=" + normalize(Path.of("..", "..", "tools", "document_tools.py"))
        };
        String[] allProperties = new String[baseProperties.length + additionalProperties.length];
        System.arraycopy(baseProperties, 0, allProperties, 0, baseProperties.length);
        System.arraycopy(additionalProperties, 0, allProperties, baseProperties.length, additionalProperties.length);
        try (ConfigurableApplicationContext ignored = new SpringApplicationBuilder(PlatformApiApplication.class, extraSource)
                .web(WebApplicationType.NONE)
                .run(allProperties)) {
        }
    }

    private Path createDataRoot(String suffix) {
        try {
            Path path = Path.of("target", "bootstrap-tests", suffix + "-" + UUID.randomUUID());
            Files.createDirectories(path);
            return path;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create test data root", exception);
        }
    }

    private String normalize(Path path) {
        return path.normalize().toString().replace('\\', '/');
    }

    @Configuration(proxyBeanMethods = false)
    static class NonPostgresDataSourceConfig {

        @Bean
        @Primary
        DataSource nonPostgresDataSource() {
            return new AbstractDataSource() {
                @Override
                public Connection getConnection() {
                    return fakeConnection();
                }

                @Override
                public Connection getConnection(String username, String password) {
                    return fakeConnection();
                }

                private Connection fakeConnection() {
                    DatabaseMetaData metadata = (DatabaseMetaData) Proxy.newProxyInstance(
                            DatabaseMetaData.class.getClassLoader(),
                            new Class<?>[]{DatabaseMetaData.class},
                            (proxy, method, args) -> switch (method.getName()) {
                                case "getDatabaseProductName" -> "H2";
                                case "unwrap" -> null;
                                case "isWrapperFor" -> false;
                                default -> throw new UnsupportedOperationException(method.getName());
                            }
                    );
                    return (Connection) Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[]{Connection.class},
                            (proxy, method, args) -> switch (method.getName()) {
                                case "getMetaData" -> metadata;
                                case "close" -> null;
                                case "isClosed" -> false;
                                case "unwrap" -> null;
                                case "isWrapperFor" -> false;
                                default -> throw new UnsupportedOperationException(method.getName());
                            }
                    );
                }
            };
        }
    }
}
