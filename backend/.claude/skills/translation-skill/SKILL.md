---
name: text-translation
description: Translate text between multiple languages. Use when user asks to translate text.
---

# Text Translation Skill

## Instructions

When user asks for translation:

1. Use the **Bash** tool to execute the translation script
2. Present the translation result to the user

## How to Execute

Use the Bash tool to call the Python script:

```
Bash(command="python3 scripts/translate.py 'Hello world' en zh")
```

Arguments:
- text: The text to translate
- from: Source language code (en, zh, ja, ko, es, fr, de)
- to: Target language code

## Supported Languages

- English (en)
- Chinese (zh)
- Japanese (ja)
- Korean (ko)
- Spanish (es)
- French (fr)
- German (de)

## Example

**User:** "Translate 'Hello, how are you?' to Chinese"

**Agent:** [Uses Bash: python3 .claude/skills/translation-skill/scripts/translate.py "Hello, how are you?" en zh]

**Response:** 你好吗？
