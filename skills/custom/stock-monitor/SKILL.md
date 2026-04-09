---
name: stock-monitor
description: Use this capability package to start, inspect, or stop the bundled stock monitoring daemon. Best for requests like "start stock monitoring", "check monitor status", "stop stock monitoring", or "run a one-off stock analysis" against the package's built-in watchlist.
summary: This skill is an internal capability package for the agent. Use packaged commands through tools. Do not instruct the user to open a terminal or run shell commands.
triggers:
  - stock monitoring
  - start monitor
  - stop monitor
  - monitor status
  - watchlist alerts
preferred_tools:
  - load_skill
  - run_skill_command
  - skill_process_status
  - stop_skill_process
allowed_tools:
  - load_skill
  - run_skill_command
  - skill_process_status
  - stop_skill_process
requires_web: false
---

# Stock Monitor

This skill is a capability package for managing the bundled stock-monitor scripts.

The user should describe the business goal in plain language. The agent should handle package loading and command execution internally.

## Product Contract

- Never tell the user to run `cd`, `.sh`, `python`, or any other terminal command unless they explicitly ask for manual CLI steps.
- Treat packaged commands as internal execution paths for the agent.
- Reply to the user with business status, not shell instructions.
- If a command fails, summarize the failure in product language and suggest the next user-facing step.

## What This Package Can Do

- Start the bundled background stock monitoring daemon using the package's built-in watchlist.
- Check whether the monitoring daemon is running.
- Stop the monitoring daemon.
- Run one-off analysis scripts included with the package.

## Current Limitation

This package currently uses a bundled, script-level watchlist and alert configuration.

- It is suitable for "start the existing monitor", "show status", "stop it", and basic package-level analysis.
- It is not yet a user-configurable web product workflow for editing watchlists or alert rules per user.

If the user asks to start monitoring and does not request custom configuration, use the package defaults and execute the command internally.

If the user asks for custom stock lists, cost prices, or thresholds, explain that this package currently runs the bundled preset configuration, then ask whether they want to proceed with the default monitor anyway.

## Internal Workflow

### 1. Start Monitoring

Use this when the user asks to start or enable stock monitoring.

Recommended flow:

1. Load the skill with `load_skill`.
2. Confirm whether the user wants the bundled default monitor if they asked for custom configuration.
3. Execute the package command internally.
4. Tell the user monitoring has started, that it uses the bundled watchlist, and that they can ask for status or stop it later.

Preferred command:

- `monitor_daemon` with `background=true`

Fallback:

- `control` with arguments equivalent to `start` only when the direct daemon command is unavailable in the current environment.

### 2. Check Status

Use this when the user asks whether monitoring is running, asks for recent logs, or asks for monitor status.

Recommended flow:

1. Load the skill if it is not already loaded.
2. Prefer checking active handles with `skill_process_status` when you already started the process in this conversation.
3. Otherwise run the package command internally to inspect status.
4. Summarize whether the monitor is running and include only concise relevant status/log details.

Preferred command:

- `skill_process_status` when you have a handle from `monitor_daemon`

Fallback:

- `control` with arguments equivalent to `status`

### 3. Stop Monitoring

Use this when the user asks to stop, disable, or shut down stock monitoring.

Recommended flow:

1. If you have a live handle from `run_skill_command` background execution, use `stop_skill_process`.
2. Otherwise run the package command internally to stop the daemon.
3. Confirm the monitor has been stopped.

Preferred command:

- `stop_skill_process` for a known daemon handle

Fallback:

- `control` with arguments equivalent to `stop`

### 4. One-Off Analysis

Use this when the user wants a package-provided one-time run or diagnostic.

Preferred commands:

- `monitor`
- `analyser`
- `test_suite`

Summarize the outcome in user language. Do not dump raw script output unless the user asks for logs or details.

## User-Facing Response Style

Good examples:

- "已为你启动股票监控，当前使用的是这个能力包内置的默认监控列表。你之后可以让我查看运行状态或停止监控。"
- "股票监控当前正在运行。我可以继续帮你查看最近状态。"
- "这个能力包目前使用内置监控配置，暂时不能直接在网页里改成你的自定义股票列表。要继续按默认配置启动吗？"

Bad examples:

- "请执行 `cd ~/workspace/skills/stock-monitor/scripts`"
- "请运行 `./control.sh start`"
- "先打开终端，再输入 python monitor_daemon.py"

## Packaged Commands

The package currently exposes commands such as:

- `control`
- `monitor`
- `monitor_daemon`
- `analyser`
- `test_suite`

Use them internally through the tooling system. Do not expose them as terminal instructions to the user by default.
