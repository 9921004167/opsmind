# One-time tenant onboarding so Prometheus alerts map to an OpsMind tenant.
# prometheus/alert_rules.yml hardcodes: organization=test-org, project=ecommerce-platform, environment=PRODUCTION,
# and the service label equals the scrape job name. Without these, alerts are rejected as "unknown organization".
# Goes through the real frontend nginx (http://localhost:3000), so it also exercises the /api proxy. Safe to re-run.
param([string]$Email = 'demo@opsmind.dev', [string]$Password = 'ChangeMe-Demo1!', [string]$Base = 'http://localhost:3000')
$ErrorActionPreference = 'Stop'
function Call($Method, $Path, $Body, $Token) {
  $h = @{}; if ($Token) { $h.Authorization = "Bearer $Token" }
  $a = @{ Method = $Method; Uri = "$Base$Path"; Headers = $h; ContentType = 'application/json'; UseBasicParsing = $true }
  if ($Body) { $a.Body = ($Body | ConvertTo-Json -Depth 5) }
  Invoke-RestMethod @a
}
try { Call POST /api/auth/register @{ organizationName = 'Test Org'; organizationSlug = 'test-org'; fullName = 'Demo Admin'; email = $Email; password = $Password } | Out-Null; 'Registered organization test-org' }
catch { "Register skipped (probably already exists): $($_.Exception.Message)" }
$auth = Call POST /api/auth/login @{ email = $Email; password = $Password }
$t = $auth.accessToken
$proj = @(Call GET /api/projects $null $t) | Where-Object { $_.slug -eq 'ecommerce-platform' } | Select-Object -First 1
if (-not $proj) { $proj = Call POST /api/projects @{ name = 'E-commerce Platform'; slug = 'ecommerce-platform' } $t; 'Created project ecommerce-platform' }
try { Call POST "/api/projects/$($proj.id)/environments" @{ type = 'PRODUCTION' } $t | Out-Null; 'Created environment PRODUCTION' } catch { 'Environment PRODUCTION already present (or not creatable): ' + $_.Exception.Message }
$have = @(Call GET "/api/projects/$($proj.id)/services" $null $t) | ForEach-Object { $_.slug }
foreach ($s in 'payment-service', 'order-service', 'inventory-service', 'product-catalog-service') {
  if ($have -notcontains $s) { Call POST "/api/projects/$($proj.id)/services" @{ name = $s; slug = $s; description = 'E-commerce reference service' } $t | Out-Null; "Created service $s" }
}
''
"Login:            $Email / (the password you passed)"
"Role:             $($auth.role)"
"Organization ID:  $($auth.organizationId)"
'To enable the Fault Lab add these two lines to .env, then run: docker compose up -d opsmind-core'
'  OPSMIND_FAULTLAB_ENABLED=true'
"  OPSMIND_FAULTLAB_ALLOWED_ORGS=$($auth.organizationId)"
