# variables exports:

export AZ_RESOURCE_GROUP="att_snowflake_billing_policy_sample_data"
export AZ_STORAGE_ACCOUNT="attsnowflakedata"
export AZ_CONTAINER="snowflake-ingest"
export AZ_QUEUE="snowpipe-events"
export AZ_BLOB_PREFIX="billing/bill-line"
export AZ_EVENT_SUBSCRIPTION="snowpipe-billing-created"

# 

az resource show \
--ids "$AZ_STORAGE_ID" \
--api-version "2025-08-01" \
--query "{
name:name,
resourceGroup:resourceGroup,
location:location,
kind:kind,
status:properties.provisioningState
}" \
--output table

Name              ResourceGroup                             Location    Kind       Status
----------------  ----------------------------------------  ----------  ---------  ---------
attsnowflakedata  att_snowflake_billing_policy_sample_data  eastus      StorageV2  Succeeded


# Alternative using az rest

az rest \
--method get \
--url "https://management.azure.com${AZ_STORAGE_ID}?api-version=2025-08-01" \
--query "{
name:name,
resourceGroup:resourceGroup,
location:location,
kind:kind,
status:properties.provisioningState
}" \
--output table