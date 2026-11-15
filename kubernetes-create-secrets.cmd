@echo off
kubectl create secret generic opsmind-secrets ^
  --namespace opsmind ^
  --from-env-file=.env ^
  --dry-run=client -o yaml > opsmind-secret.yaml

if errorlevel 1 (
  echo Secret generation failed.
  exit /b 1
)

kubectl apply -f opsmind-secret.yaml

if errorlevel 1 (
  echo Secret creation failed.
  exit /b 1
)

del opsmind-secret.yaml

echo Kubernetes Secret created successfully.