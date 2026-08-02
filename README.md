# Simple Azure Blob SAS to Snowflake COPY Demo

This project intentionally avoids Microsoft Entra consent, Enterprise
Application Object IDs, Event Grid, Storage Queue, notification integration,
and Snowpipe. It uses a container-scoped SAS token and a manually triggered
`COPY INTO` workflow.

## Resources

| Resource | Value |
|---|---|
| Snowflake account | `usbxmys-cm03603` |
| Database | `EDU_AI_APP` |
| Changelog schema | `WEBAPP` |
| Data schema | `STAGING` |
| Warehouse | `GLOBAL_FINANCE_WAREHOUSE` |
| Azure storage account | `attsnowflakedata` |
| Container | `snowflake-ingest` |
| Prefix | `billing/bill-line` |

## 1. Generate the SAS token

In Azure Portal open:

`Storage accounts -> attsnowflakedata -> Containers -> snowflake-ingest -> Shared access tokens`

Select `Read` and `List`, HTTPS only, a start time slightly before the current
time, and an appropriate learning expiry. Generate and copy the SAS token,
including its leading `?`.

Never commit the SAS token. Rotate it after use or if it is exposed.

## 2. Configure GitHub

Create repository variables under **Settings -> Secrets and variables -> Actions
-> Variables**:

| Name | Value |
|---|---|
| `SNOWFLAKE_ACCOUNT` | `usbxmys-cm03603` |
| `AZURE_STORAGE_ACCOUNT` | `attsnowflakedata` |
| `AZURE_CONTAINER` | `snowflake-ingest` |
| `AZURE_BLOB_PREFIX` | `billing/bill-line` |

Create repository secrets:

| Name | Value |
|---|---|
| `SNOWFLAKE_USER` | Snowflake deployment user |
| `SNOWFLAKE_PASSWORD` | Snowflake password |
| `AZURE_BLOB_SAS_TOKEN` | Complete SAS token beginning with `?` |

## 3. Push the project

```bash
git init
git add .
git commit -m "Add Azure SAS Snowflake COPY demo"
git branch -M main
git remote add origin <YOUR_REPOSITORY_URL>
git push -u origin main
```

## 4. Deploy Snowflake objects

Run GitHub workflow **01 Deploy SAS Stage Objects**. It creates:

- `EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_LANDING`
- `EDU_AI_APP.STAGING.BILL_LINE_SAS_CSV_FORMAT`
- `EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_STAGE`

The stage changeset is `runAlways=true`, so rerunning workflow 01 refreshes the
stage when the SAS token is rotated.

Verify in Snowflake:

```sql
DESC STAGE EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_STAGE;
LIST @EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_STAGE;
```

## 5. Upload the sample CSV

Your local Azure user needs `Storage Blob Data Contributor` on the container.

```bash
az storage blob upload \
  --account-name attsnowflakedata \
  --container-name snowflake-ingest \
  --name "billing/bill-line/bill_lines_20260802_001.csv" \
  --file "sample-data/bill_lines_20260802_001.csv" \
  --auth-mode login
```

Verify:

```bash
az storage blob list \
  --account-name attsnowflakedata \
  --container-name snowflake-ingest \
  --prefix "billing/bill-line/" \
  --auth-mode login \
  --query "[].name" \
  --output table
```

## 6. Manually load the CSV

Run GitHub workflow **02 Manually Copy Azure CSV to Snowflake**. The Java helper
lists the stage, executes `COPY INTO`, and prints row counts grouped by source
filename.

## 7. Verify in Snowflake

```sql
SELECT BILL_LINE_ID, BILL_ID, CHARGE_TYPE_CODE, NET_AMOUNT,
       SOURCE_FILENAME, SOURCE_ROW_NUMBER, INGESTED_AT
FROM EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_LANDING
ORDER BY INGESTED_AT DESC;
```

The sample should insert two rows.

```sql
SELECT FILE_NAME, STATUS, ROW_COUNT, FIRST_ERROR_MESSAGE, LAST_LOAD_TIME
FROM TABLE(
  EDU_AI_APP.INFORMATION_SCHEMA.COPY_HISTORY(
    TABLE_NAME => 'EDU_AI_APP.STAGING.AZURE_BILL_LINE_SAS_LANDING',
    START_TIME => DATEADD('DAY', -1, CURRENT_TIMESTAMP())
  )
)
ORDER BY LAST_LOAD_TIME DESC;
```

## Reload behavior

Snowflake normally does not reload a filename already recorded in load history.
Upload new data with a unique immutable Blob name. Use `FORCE=TRUE` only for a
deliberate learning reload because it can create duplicates.

## SAS rotation

When the token expires, create a replacement SAS token, update the GitHub secret
`AZURE_BLOB_SAS_TOKEN`, and rerun workflow 01. Do not edit old executed
Liquibase changesets.
