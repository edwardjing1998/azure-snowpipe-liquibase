package com.edward.snowflake;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class ValidateDeploymentMain {
    private ValidateDeploymentMain() {
    }

    public static void main(String[] args) throws Exception {
        String account = environment("AZURE_STORAGE_ACCOUNT", "attsnowflakedata");
        String container = environment("AZURE_CONTAINER", "snowflake-ingest");
        String prefix = environment("AZURE_BLOB_PREFIX", "billing/bill-line");
        String path = "azure://%s.blob.core.windows.net/%s/%s/"
                .formatted(account, container, prefix);

        try (Connection connection = IntegrationInfoMain.connection();
             Statement statement = connection.createStatement()) {
            String validationSql = "SELECT SYSTEM$VALIDATE_STORAGE_INTEGRATION(" +
                    "'AZURE_BLOB_STORAGE_INT', '" + path +
                    "', 'validation-placeholder.csv', 'list')";
            try (ResultSet result = statement.executeQuery(validationSql)) {
                if (!result.next()) {
                    throw new IllegalStateException("Storage integration returned no result");
                }
                String json = result.getString(1);
                System.out.println("Storage integration validation: " + json);
                if (json == null || !json.toLowerCase().contains("success")) {
                    throw new IllegalStateException("Storage integration validation failed");
                }
            }

            try {
                try (ResultSet result = statement.executeQuery(
                        "SELECT SYSTEM$PIPE_STATUS('EDU_AI_APP.STAGING.BILL_LINE_AZURE_PIPE')")) {
                    if (result.next()) {
                        System.out.println("Snowpipe status: " + result.getString(1));
                    }
                }
            } catch (SQLException exception) {
                System.out.println("Snowpipe is not created yet; storage validation succeeded.");
            }

            try (ResultSet result = statement.executeQuery(
                    "SELECT COUNT(*) FROM EDU_AI_APP.STAGING.AZURE_BILL_LINE_LANDING")) {
                result.next();
                System.out.println("Landing row count: " + result.getLong(1));
            }
        }
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
