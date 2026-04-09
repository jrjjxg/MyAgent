package com.xg.platform.api;

final class LocalPostgresIntegrationSupport {

    private LocalPostgresIntegrationSupport() {
    }

    static String jdbcUrl() {
        return required(
                "platform.test.postgres.url",
                "PLATFORM_TEST_POSTGRES_URL",
                "SPRING_DATASOURCE_URL"
        );
    }

    static String username() {
        return required(
                "platform.test.postgres.username",
                "PLATFORM_TEST_POSTGRES_USERNAME",
                "SPRING_DATASOURCE_USERNAME"
        );
    }

    static String password() {
        return required(
                "platform.test.postgres.password",
                "PLATFORM_TEST_POSTGRES_PASSWORD",
                "SPRING_DATASOURCE_PASSWORD"
        );
    }

    private static String required(String propertyName, String... envNames) {
        String value = System.getProperty(propertyName);
        if (value != null && !value.isBlank()) {
            return value;
        }

        for (String envName : envNames) {
            value = System.getenv(envName);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        throw new IllegalStateException(
                "Missing PostgreSQL test setting. Provide -D" + propertyName
                        + "=... or env " + envNames[0]
        );
    }
}
