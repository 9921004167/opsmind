@echo off
REM Creates the Prometheus/Alertmanager ConfigMaps from the repo's existing config files (single source of truth).
kubectl create configmap prometheus-config --namespace opsmind ^
  --from-file=prometheus.yml=monitoring\prometheus.yml ^
  --from-file=ecommerce-alerts.yml=monitoring\alerts\ecommerce-alerts.yml ^
  --from-file=alert_rules.yml=prometheus\alert_rules.yml ^
  --dry-run=client -o yaml | kubectl apply -f -
if errorlevel 1 exit /b 1
kubectl create configmap alertmanager-config --namespace opsmind ^
  --from-file=alertmanager.yml=alertmanager\alertmanager.yml ^
  --dry-run=client -o yaml | kubectl apply -f -
