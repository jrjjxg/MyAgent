---
name: docs.web-research
description: Combine local document analysis with external search when search tools are enabled.
summary: Use this when uploaded documents need external validation, enrichment, or current web context.
preferredTools:
  - web_search
  - web_fetch
allowedTools:
  - web_search
  - web_fetch
  - load_skill
invocation: workflow
execution: inline
requiresDocuments: true
requiresWeb: true
agent: general-agent
---
# Web Research Skill

Use this only when external search tools are available.

Deliver:
- what comes from uploaded documents
- what comes from external search
- clear separation between local evidence and external context

Rules:
- do not pretend web evidence exists if search tools are unavailable
- keep citations explicit
