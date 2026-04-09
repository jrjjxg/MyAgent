package com.xg.platform.api.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.ArrayList;
import java.util.List;

public class DataSourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String URL_KEY = "spring.datasource.url";
    private static final String USERNAME_KEY = "spring.datasource.username";
    private static final String PASSWORD_KEY = "spring.datasource.password";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        List<String> missingKeys = new ArrayList<>();
        requireResolved(environment, URL_KEY, missingKeys);
        requireResolved(environment, USERNAME_KEY, missingKeys);
        requireResolved(environment, PASSWORD_KEY, missingKeys);

        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException("""
                    Missing required PostgreSQL datasource configuration: %s
                    Load the local env file before starting the API:
                      PowerShell: . .\\.local\\postgres-env.ps1
                    Or pass the values explicitly, for example:
                      mvnw.cmd -pl backend/platform-api spring-boot:run "-Dspring-boot.run.jvmArguments=-Dspring.datasource.url=jdbc:postgresql://localhost:5433/myagent -Dspring.datasource.username=myagent_app -Dspring.datasource.password=***"
                    """.formatted(String.join(", ", missingKeys)));
        }
    }

    private void requireResolved(ConfigurableEnvironment environment,
                                 String key,
                                 List<String> missingKeys) {
        String value;
        try {
            value = environment.getProperty(key);
        } catch (IllegalArgumentException exception) {
            missingKeys.add(key);
            return;
        }
        if (value == null || value.isBlank() || value.contains("${")) {
            missingKeys.add(key);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
