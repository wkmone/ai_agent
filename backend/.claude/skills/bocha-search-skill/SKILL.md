---
name: web-search
description: Search the web for information. Use when user asks for information that requires web research.
---

# Web Search Skill

## Instructions

When user needs to search the web:

1. Use the **Bash** tool to execute a web search script
2. Present the results to the user

## How to Execute

Use the Bash tool to call the Python script:

```
Bash(command="python3 .claude/skills/bocha-search-skill/scripts/web_search.py 'search query'")
```

## Example

**User:** "What are the latest developments in AI?"

**Agent:** [Uses Bash: python3 .claude/skills/bocha-search-skill/scripts/web_search.py 'latest AI developments']

**Response:** Here are the search results:
1. [Result Title](URL)
   Description...
