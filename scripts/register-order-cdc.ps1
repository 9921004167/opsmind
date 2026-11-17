# Registers (or updates) the Debezium connector "order-cdc". Safe to re-run (PUT is idempotent).
# Reads the database admin password from .env (ECOMMERCE_DB_ADMIN_PASSWORD), falling back to the compose default.
param([string]$Connect = 'http://localhost:8083')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pw = 'ecommerce_admin'
$line = Get-Content (Join-Path $root '.env') -ErrorAction SilentlyContinue | Where-Object { $_ -match '^ECOMMERCE_DB_ADMIN_PASSWORD=' } | Select-Object -First 1
if ($line) { $pw = ($line -split '=', 2)[1].Trim() }

$config = [ordered]@{
  'connector.class'                     = 'io.debezium.connector.postgresql.PostgresConnector'
  'plugin.name'                         = 'pgoutput'
  'database.hostname'                   = 'ecommerce-db'
  'database.port'                       = '5432'
  'database.user'                       = 'ecommerce_admin'
  'database.password'                   = $pw
  'database.dbname'                     = 'order_db'
  'topic.prefix'                        = 'ecom'
  'table.include.list'                  = 'public.orders'
  'slot.name'                           = 'opsmind_order_cdc'
  'publication.name'                    = 'opsmind_order_pub'
  'snapshot.mode'                       = 'initial'
  'decimal.handling.mode'               = 'string'
  'tombstones.on.delete'                = 'false'
  'key.converter'                       = 'org.apache.kafka.connect.json.JsonConverter'
  'value.converter'                     = 'org.apache.kafka.connect.json.JsonConverter'
  'key.converter.schemas.enable'        = 'false'
  'value.converter.schemas.enable'      = 'false'
}
$body = $config | ConvertTo-Json -Depth 3
Invoke-RestMethod -Method Put -Uri "$Connect/connectors/order-cdc/config" -ContentType 'application/json' -Body $body | Out-Null
Write-Host 'Connector order-cdc registered. Status:'
Start-Sleep -Seconds 5
Invoke-RestMethod -Uri "$Connect/connectors/order-cdc/status" | ConvertTo-Json -Depth 5
Write-Host ''
Write-Host 'Events go to Kafka topic: ecom.public.orders'
