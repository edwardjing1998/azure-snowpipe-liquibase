package com.edward.snowflake;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrationInfoMain {
    private IntegrationInfoMain() {
    }

    public static void main(String[] args) throws Exception {
        try (Connection connection = connection()) {
            Map<String, String> storage = describe(
                    connection, "DESC STORAGE INTEGRATION AZURE_BLOB_STORAGE_INT");
            Map<String, String> notification = describe(
                    connection, "DESC NOTIFICATION INTEGRATION AZURE_SNOWPIPE_NOTIFICATION_INT");

            String report = """
                    SNOWFLAKE AZURE CONSENT CHECKPOINT

                    STORAGE INTEGRATION
                    App name: %s
                    Consent URL: %s

                    NOTIFICATION INTEGRATION
                    App name: %s
                    Consent URL: %s

                    Manual action required: open each URL as an Azure tenant administrator
                    and select Accept. Then locate both Enterprise Applications and record
                    their Azure Object IDs for workflow 02.
                    """.formatted(
                    required(storage, "AZURE_MULTI_TENANT_APP_NAME"),
                    required(storage, "AZURE_CONSENT_URL"),
                    required(notification, "AZURE_MULTI_TENANT_APP_NAME"),
                    required(notification, "AZURE_CONSENT_URL"));

            System.out.println(report);
            Path target = Path.of("target", "azure-consent-information.txt");
            Files.createDirectories(target.getParent());
            Files.writeString(target, report, StandardCharsets.UTF_8);
        }
    }

    static Connection connection() throws SQLException {
        return DriverManager.getConnection(
                requiredEnvironment("SNOWFLAKE_URL"),
                requiredEnvironment("SNOWFLAKE_USER"),
                requiredEnvironment("SNOWFLAKE_PASSWORD"));
    }

    private static Map<String, String> describe(Connection connection, String sql)
            throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            while (result.next()) {
                values.put(result.getString("property"), result.getString("property_value"));
            }
        }
        return values;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing integration property: " + key);
        }
        return value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }
}
