---
name: docs.paper-review
description: Structured paper review for a single uploaded document.
agent: general-agent
---
# Paper Review Skill

Use this workflow when the user asks for a deep read of one document.

Deliver:
- a concise summary
- explicit innovation points
- model or method details
- key experiment or metric takeaways
- limitations if evidence exists

Rules:
- rely on retrieved excerpts rather than filenames
- cite document evidence inline
- if evidence is missing, say so plainly
