---
name: research.github-repo
description: Investigate a GitHub repository through README, docs, releases, issues, and public web evidence.
agent: general-agent
---
# GitHub Repo Research Skill

Use this skill when the task is to evaluate a repository, project, or open source package.

Workflow:
- Identify the exact repository, owner, and project scope first.
- Review public entry points in order: README, official docs, release notes, changelog, and issue discussions.
- Separate project-maintainer claims from third-party commentary.
- Track missing answers explicitly, then run a focused follow-up search for those gaps.

Evaluation dimensions:
- Project purpose and target users
- Installation and operational complexity
- Release cadence and maintenance signals
- Ecosystem, integrations, and documentation quality
- Risks, limits, and adoption caveats

Output rules:
- Quote repository facts conservatively and tie them to sources.
- Highlight which findings come from the repo itself versus broader ecosystem evidence.
- Prefer a concise recommendation with tradeoffs over a feature dump.
