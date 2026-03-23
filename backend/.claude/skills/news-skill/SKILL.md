---
name: news-fetch
description: Fetch latest news on a specific topic. Use when user asks for news or current events.
---

# News Fetch Skill

## Instructions

When user asks for news:

1. Use the **Bash** tool to execute the news script
2. Present the results in a clear format

## How to Execute

Use the Bash tool to call the Python script:

```
Bash(command="python3 .claude/skills/news-skill/scripts/news.py")
```

To get a specific number of stories:
```
Bash(command="python3 .claude/skills/news-skill/scripts/news.py 5")
```

## Response Format

Present news in a clear format:
- Title
- Score
- Author
- URL

## Example

**User:** "What's the latest tech news?"

**Agent:** [Uses Bash to run: python3 .claude/skills/news-skill/scripts/news.py]

**Response:**
```
📰 Hacker News Top Stories

1. New AI Model Released
   Score: 150 | By: johndoe
   URL: https://example.com/article

2. Cloud Computing Trends 2024
   Score: 89 | By: janedoe
   URL: https://example.com/article2
```
