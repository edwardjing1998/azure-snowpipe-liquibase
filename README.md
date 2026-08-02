# Azure Blob to Snowflake Snowpipe with Liquibase and GitHub Actions

This learning project provisions the Snowflake side of an Azure Blob auto-ingest
pipeline and automates Azure RBAC/Event Grid after the required Microsoft Entra
administrator consent checkpoint.

## Architecture

CSV -> Azure Blob -> Event Grid -> Storage Queue -> Snowflake notification
integration -> Snowpipe -> `EDU_AI_APP.STAGING.AZURE_BILL_LINE_LANDING`

## Fixed project values

| Setting | Value |
|---|---|
| Azure tenant | `c447f9fc-f702-4079-a2c7-061436bd0b88` |
| Resource group | `att_snowflake_billing_policy_sample_data` |
| Storage account | `attsnowflakedata` |
| Container | `snowflake-ingest` |
| Queue | `snowpipe-events` |
| Blob prefix | `billing/bill-line` |
| Event subscription | `snowpipe-billing-created` |
| Snowflake database | `EDU_AI_APP` |
| Changelog schema | `WEBAPP` |
| Landing schema | `STAGING` |

## Important automation boundary

GitHub Actions cannot click or bypass Microsoft administrator consent. Workflow
01 creates both Snowflake integrations and produces their consent URLs. An Azure
tenant administrator must open both URLs and select **Accept**. Workflow 02 may
then assign Azure roles to the resulting Enterprise Applications.

Do not store Snowflake passwords, Azure client secrets, SAS tokens, or storage
keys in source files.

## Repository variables

Create these under **Settings -> Secrets and variables -> Actions -> Variables**:

| Name | Value |
|---|---|
| `SNOWFLAKE_ACCOUNT` | `usbxmys-cm03603` |
| `AZURE_TENANT_ID` | `c447f9fc-f702-4079-a2c7-061436bd0b88` |
| `AZURE_RESOURCE_GROUP` | `att_snowflake_billing_policy_sample_data` |
| `AZURE_STORAGE_ACCOUNT` | `attsnowflakedata` |
| `AZURE_CONTAINER` | `snowflake-ingest` |
| `AZURE_QUEUE` | `snowpipe-events` |
| `AZURE_BLOB_PREFIX` | `billing/bill-line` |
| `AZURE_EVENT_SUBSCRIPTION` | `snowpipe-billing-created` |

## Repository secrets

Create these under **Actions -> Secrets**:

| Name | Purpose |
|---|---|
| `SNOWFLAKE_USER` | Snowflake deployment user |
| `SNOWFLAKE_PASSWORD` | Snowflake deployment password |
| `AZURE_CLIENT_ID` | GitHub OIDC Azure deployment application client ID |
| `AZURE_SUBSCRIPTION_ID` | Azure subscription ID |

The sample uses `ACCOUNTADMIN` for a learning account. Production should use a
dedicated Snowflake deployment role with `CREATE INTEGRATION`, integration
`USAGE`, and the required schema privileges.

## One-time GitHub OIDC preparation

Create or reuse an Azure application/service principal for GitHub Actions. Add
a federated credential whose issuer is `https://token.actions.githubusercontent.com`,
audience is `api://AzureADTokenExchange`, and subject is:

`repo:<GITHUB_OWNER>/<GITHUB_REPOSITORY>:ref:refs/heads/main`

The Azure deployment principal needs sufficient scope to:

- inspect the storage account;
- create the Event Grid subscription;
- assign `Storage Blob Data Reader` and `Storage Queue Data Contributor`.

Role assignment creation normally requires `Role Based Access Control
Administrator` or `User Access Administrator`. Grant only the narrowest practical
scope, preferably this resource group. This one-time bootstrap must be performed
by an authorized Azure administrator.

## Run phase 1

1. Push the repository to GitHub.
2. Open **Actions**.
3. Run **01 Bootstrap Snowflake Azure Integrations**.
4. Download the `azure-consent-information` artifact or read the job output.
5. Open both consent URLs and select **Accept**.

Phase 1 creates:

- `AZURE_BLOB_STORAGE_INT` (account-level);
- `AZURE_SNOWPIPE_NOTIFICATION_INT` (account-level);
- `EDU_AI_APP.STAGING.AZURE_BILL_LINE_LANDING`;
- `EDU_AI_APP.STAGING.BILL_LINE_CSV_FORMAT`;
- `EDU_AI_APP.STAGING.AZURE_BILL_LINE_STAGE`.

## Find the two Azure Enterprise Application Object IDs

After accepting both consent URLs:

1. Open Azure Portal -> Microsoft Entra ID -> Enterprise applications.
2. Search using each `AZURE_MULTI_TENANT_APP_NAME` shown by workflow 01.
3. Open the application.
4. Copy **Object ID**, not Application/Client ID.

The storage and notification integrations can create different service
principals; do not assume they have the same Object ID.

## Run phase 2

1. Open **Actions**.
2. Run **02 Authorize Azure and Activate Snowpipe**.
3. Enter the storage Enterprise Application Object ID.
4. Enter the notification Enterprise Application Object ID.

Phase 2:

- assigns Blob Reader to the storage service principal;
- assigns Queue Data Contributor to the notification service principal;
- creates the filtered Event Grid subscription if missing;
- creates `EDU_AI_APP.STAGING.BILL_LINE_AZURE_PIPE`;
- retries Snowflake Blob-list validation while Azure RBAC propagates;
- prints the Snowpipe status.

The activation changelog runs before the end-to-end access validation. This
ensures Liquibase records `azure-006-create-auto-ingest-pipe` even when Azure
RBAC needs additional propagation time. A later validation failure means the
pipe exists but Azure consent, role assignment, or networking still needs work.

## Upload a test file

```bash
az storage blob upload \
  --account-name attsnowflakedata \
  --container-name snowflake-ingest \
  --name "billing/bill-line/bill_lines_20260801_001.csv" \
  --file "sample-data/bill_lines_20260801_001.csv" \
  --auth-mode login
```

Use a new immutable filename for every later test because Snowpipe tracks file
load history.

## Verify in Snowflake

```sql
DESC STORAGE INTEGRATION AZURE_BLOB_STORAGE_INT;
DESC NOTIFICATION INTEGRATION AZURE_SNOWPIPE_NOTIFICATION_INT;
DESC STAGE EDU_AI_APP.STAGING.AZURE_BILL_LINE_STAGE;
LIST @EDU_AI_APP.STAGING.AZURE_BILL_LINE_STAGE;

SELECT SYSTEM$PIPE_STATUS(
  'EDU_AI_APP.STAGING.BILL_LINE_AZURE_PIPE'
);

SELECT *
FROM EDU_AI_APP.STAGING.AZURE_BILL_LINE_LANDING
ORDER BY INGESTED_AT DESC;
```

## Local validation

Export credentials only in your current terminal:

```bash
export SNOWFLAKE_USER="<user>"
export SNOWFLAKE_PASSWORD="<password>"
export SNOWFLAKE_URL="jdbc:snowflake://usbxmys-cm03603.snowflakecomputing.com/?db=EDU_AI_APP&schema=WEBAPP&warehouse=GLOBAL_FINANCE_WAREHOUSE&role=ACCOUNTADMIN"
```

Validate the XML and database connectivity:

```bash
mvn -B -ntp liquibase:validate \
  -Dliquibase.changeLogFile=src/main/resources/db/changelog/db.changelog-bootstrap.xml \
  -Dazure.tenant.id=c447f9fc-f702-4079-a2c7-061436bd0b88 \
  -Dazure.storage.account=attsnowflakedata \
  -Dazure.container=snowflake-ingest \
  -Dazure.queue=snowpipe-events \
  -Dazure.blob.prefix=billing/bill-line
```

## Rerun behavior

Liquibase records successful changesets in `EDU_AI_APP.WEBAPP.DATABASECHANGELOG`.
Rerunning a workflow does not rerun completed changesets. Do not edit an already
executed changeset; add a new changeset for later modifications.

The Azure role and Event Grid steps also check existing state before creating
resources. Do not replace a consented Snowflake integration casually because a
replacement can change its Azure identity and invalidate prior RBAC assignments.
