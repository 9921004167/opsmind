# Places real orders through the Order Service so Prometheus has requests to compute a 5xx rate from.
# A first "baseline" order is placed and must succeed, otherwise the script stops and prints the server's reason.
# During a Payment fault, orders normally still return HTTP 201 but with status PAYMENT_FAILED - the 5xx is recorded
# by the payment-service itself, which is what the Prometheus alert watches.
param([int]$Count = 90, [int]$DelayMs = 500)

function Get-ErrBody($err) {
  try { $s = $err.Exception.Response.GetResponseStream(); try { $s.Position = 0 } catch {}; (New-Object IO.StreamReader($s)).ReadToEnd() }
  catch { $err.Exception.Message }
}
function Place-Order($body) {
  try {
    $r = Invoke-WebRequest -Method Post -Uri http://localhost:8093/api/orders -ContentType 'application/json' -Body $body -UseBasicParsing
    $status = ''; try { $status = ($r.Content | ConvertFrom-Json).status } catch {}
    return @{ ok = $true; code = [int]$r.StatusCode; status = $status; body = $r.Content }
  } catch {
    $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    return @{ ok = $false; code = $code; status = ''; body = (Get-ErrBody $_) }
  }
}

# Fetch products as raw text and extract the first product's id/name with a regex (no PowerShell JSON object handling).
try { $resp = Invoke-WebRequest -Uri http://localhost:8090/api/products -UseBasicParsing }
catch { Write-Host "Could not call the catalog: $($_.Exception.Message)" -ForegroundColor Red; exit 1 }
$raw = [string]$resp.Content
Write-Host "Catalog answered HTTP $($resp.StatusCode)"
$m = [regex]::Match($raw, '"id":"([0-9a-fA-F-]{36})".*?"name":"([^"]*)"')
if (-not $m.Success) {
  Write-Host 'No product id found. First 300 characters of the raw response:' -ForegroundColor Red
  Write-Host $raw.Substring(0, [Math]::Min(300, $raw.Length))
  exit 1
}
$id = $m.Groups[1].Value
Write-Host "Using product: $($m.Groups[2].Value) ($id)"

$body = @{ customerId = 'demo-customer'; items = @(, @{ productId = [string]$id; quantity = 1 }) } | ConvertTo-Json -Depth 5
Write-Host "Request body: $body"

$pre = Place-Order $body
if (-not $pre.ok) {
  Write-Host ''
  Write-Host "Baseline order FAILED with HTTP $($pre.code). Server said:" -ForegroundColor Red
  Write-Host $pre.body
  Write-Host 'Fix this first; the fault test cannot work until a normal order succeeds.'
  exit 1
}
Write-Host "Baseline order OK: HTTP $($pre.code), order status $($pre.status)"

1..$Count | ForEach-Object {
  $r = Place-Order $body
  "{0,3}  HTTP {1}  {2}" -f $_, $r.code, $(if ($r.ok) { $r.status } else { $r.body })
  Start-Sleep -Milliseconds $DelayMs
}