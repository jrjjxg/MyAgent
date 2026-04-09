# Ops

This directory stores local development and operations helpers.

## Run platform-api

Use the helper script below to keep local SNAPSHOT dependencies aligned before starting `platform-api`:

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\run-platform-api.ps1
```

To use a different HTTP port:

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\run-platform-api.ps1 --server.port=8081
```
