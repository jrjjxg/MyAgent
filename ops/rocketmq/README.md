# RocketMQ Local Dev

This folder contains the local baseline for running RocketMQ with this project.

## Why this exists

The application already supports `platform.async.mode=rocketmq`, but a plain RocketMQ zip on Windows is not enough by itself:

- the Spring starter needs `rocketmq.name-server` and `rocketmq.producer.group`
- RocketMQ topic names cannot contain `.`
- the default RocketMQ store path may land on `C:\Users\<user>\store`, which can trip disk protection on a low-space system drive

The scripts here fix those local setup problems by:

- pinning `brokerIP1=127.0.0.1`
- moving broker storage under the repo's `.local\rocketmq\store`
- using smaller JVM settings for a dev workstation
- pre-creating the default application topics after the broker boots

## Prerequisites

- Java 17 available locally
- RocketMQ 5.3.1 extracted to `.local\rocketmq\5.3.1\rocketmq-all-5.3.1-bin-release`
- PostgreSQL available for the application smoke test

## Start RocketMQ

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\rocketmq\start-local.ps1
```

To wipe the local broker store and start from a clean slate:

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\rocketmq\start-local.ps1 -CleanStore
```

## Stop RocketMQ

```powershell
powershell -ExecutionPolicy Bypass -File .\ops\rocketmq\stop-local.ps1
```

## Run the real RocketMQ smoke test

```powershell
. .\.local\postgres-env.ps1
mvn -pl backend/platform-api -am `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dplatform.test.rocketmq.enabled=true `
  -Dplatform.test.rocketmq.nameserver=127.0.0.1:9876 `
  -Dtest=RocketMqTaskDispatchIntegrationTest test
```

## Run the application in RocketMQ mode

```powershell
. .\.local\postgres-env.ps1
$env:PLATFORM_ASYNC_MODE = "rocketmq"
$env:ROCKETMQ_NAME_SERVER = "127.0.0.1:9876"
$env:ROCKETMQ_PRODUCER_GROUP = "platform-producer"
mvn -pl backend/platform-api spring-boot:run
```

## Current topic defaults

These defaults are now legal RocketMQ topic names:

- `platform_tasks_dispatch`
- `platform_memory_events`
- `platform_memory_long_term_jobs`

If you override them, stay with letters, digits, `_`, and `-`.
