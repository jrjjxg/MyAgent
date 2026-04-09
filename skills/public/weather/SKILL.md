---
name: weather
description: Get practical weather answers for a city or region using structured forecast data first.
summary: Use this for current weather, tomorrow's forecast, short outlooks, and travel weather checks.
triggers:
  - weather
  - forecast
  - temperature
  - tomorrow
  - today
  - 天气
  - 明天
preferredTools:
  - weather
  - web_search
  - web_fetch
allowedTools:
  - weather
  - web_search
  - web_fetch
invocation: auto
execution: inline
requiresDocuments: false
requiresWeb: true
agent: general-agent
---
# Weather Skill

Use this skill when the user asks for weather, temperature, forecast, or a quick travel-weather check.

Preferred workflow:
- First use the `weather` tool with a clear location.
- If the location is missing, ask for the city or region before continuing.
- If the structured weather result is incomplete, only then fall back to `web_search` and `web_fetch`.

Output rules:
- Give a user-readable answer, not a raw JSON dump.
- Include the key facts first: condition, temperature range, wind, and precipitation if available.
- Keep practical advice brief and tied to the forecast.

Do not use this skill for:
- historical weather
- severe-weather alerts
- professional meteorology analysis
- climate trend analysis

Evidence rules:
- Treat the `weather` tool result as the primary verified source.
- Do not treat search snippets from weather sites as confirmed facts unless fetched or otherwise verified.
