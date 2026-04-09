---
name: research.deep-research
description: Run staged deep research with explicit evidence gathering, reflection, and source-aware synthesis.
summary: Use this when the task needs multi-step evidence gathering, source verification, and a grounded report.
preferredTools:
  - web_search
  - web_fetch
  - research_reflect
allowedTools:
  - web_search
  - web_fetch
  - research_reflect
invocation: workflow
execution: inline
requiresDocuments: false
requiresWeb: true
agent: general-agent
---
# Deep Research Skill

Use this skill for research tasks that need more than a quick answer.

Workflow:
- Clarify the research goal, scope, constraints, and output shape before broad exploration.
- Gather evidence in stages: baseline context, focused follow-up, and validation of competing claims.
- Treat research as an evidence pipeline, not one long prompt. Keep the active working set small.
- Use `research_reflect` after initial evidence collection to identify missing evidence and plan the next focused search.

Evidence rules:
- Distinguish local documents, web search results, fetched web pages, and generated artifacts.
- Prefer primary sources, official docs, release notes, filings, or first-party statements when available.
- Do not collapse all evidence into one undifferentiated source list.
- Keep claims tied to typed citations such as `[W1]`, `[D2]`, or `[A1]`.

Report rules:
- Surface both findings and uncertainty.
- Call out where evidence is thin, stale, or conflicting.
- Keep the report grounded in collected sources only.
