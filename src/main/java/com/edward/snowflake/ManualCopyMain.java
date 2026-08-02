package com.edward.snowflake;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

public final class ManualCopyMain {
    private static final String COPY_SQL = """
            COPY INTO EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_LANDING (
              BILL_LINE_ID, BILL_ID, BILLING_ACCOUNT_TOKEN, PRODUCT_ID, SERVICE_ID,
              CHARGE_TYPE_CODE, BILLING_CYCLE_START, BILLING_CYCLE_END, CHARGE_DATE,
              QUANTITY, UNIT_PRICE, CHARGE_AMOUNT, TAX_AMOUNT, CREDIT_AMOUNT, NET_AMOUNT,
              CURRENCY_CODE, CHARGE_STATUS, SOURCE_UPDATED_AT,
              SOURCE_FILENAME, SOURCE_ROW_NUMBER
            )
            FROM (
              SELECT
                t.$1::VARCHAR, t.$2::VARCHAR, t.$3::VARCHAR, t.$4::VARCHAR,
                t.$5::VARCHAR, t.$6::VARCHAR, TRY_TO_DATE(t.$7), TRY_TO_DATE(t.$8),
                TRY_TO_DATE(t.$9), TRY_TO_DECIMAL(t.$10, 18, 4),
                TRY_TO_DECIMAL(t.$11, 18, 4), TRY_TO_DECIMAL(t.$12, 18, 2),
                TRY_TO_DECIMAL(t.$13, 18, 2), TRY_TO_DECIMAL(t.$14, 18, 2),
                TRY_TO_DECIMAL(t.$15, 18, 2), t.$16::VARCHAR, t.$17::VARCHAR,
                TRY_TO_TIMESTAMP_NTZ(t.$18), METADATA$FILENAME, METADATA$FILE_ROW_NUMBER
              FROM @EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_STAGE t
            )
            PATTERN = '.*[.]csv'
            ON_ERROR = 'ABORT_STATEMENT'
            """;

    private ManualCopyMain() {
    }

    public static void main(String[] args) throws Exception {
        try (Connection connection = DriverManager.getConnection(
                required("SNOWFLAKE_URL"),
                required("SNOWFLAKE_USER"),
                required("SNOWFLAKE_PASSWORD"));
             Statement statement = connection.createStatement()) {

            System.out.println("Files visible in the Azure SAS stage:");
            print(statement.executeQuery(
                    "LIST @EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_STAGE"));

            System.out.println("COPY INTO result:");
            print(statement.executeQuery(COPY_SQL));

            System.out.println("Landing rows by source file:");
            print(statement.executeQuery("""
                    SELECT SOURCE_FILENAME, COUNT(*) AS LOADED_ROWS,
                           MAX(INGESTED_AT) AS LAST_INGESTED_AT
                    FROM EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_LANDING
                    GROUP BY SOURCE_FILENAME
                    ORDER BY LAST_INGESTED_AT DESC
                    """));
        }
    }

    private static void print(ResultSet result) throws Exception {
        try (result) {
            ResultSetMetaData metadata = result.getMetaData();
            int columns = metadata.getColumnCount();
            boolean found = false;
            while (result.next()) {
                found = true;
                StringBuilder row = new StringBuilder();
                for (int column = 1; column <= columns; column++) {
                    if (column > 1) {
                        row.append(" | ");
                    }
                    row.append(metadata.getColumnLabel(column))
                            .append('=')
                            .append(result.getString(column));
                }
                System.out.println(row);
            }
            if (!found) {
                System.out.println("(no rows)");
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }
}
