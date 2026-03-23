---
name: terminal-execution
description: Execute terminal commands and scripts. Use when user asks to run a command, build project, or interact with file system.
---

# Terminal Execution Skill

## Instructions

Use this skill when you need to execute terminal commands.

## How to Execute

Use the **Bash** tool directly to execute commands:

```
Bash(command="ls -la")
```

## Common Commands

### List files
```
Bash(command="ls -la")
```

### Run Maven build
```
Bash(command="mvn clean compile -DskipTests")
```

### Git operations
```
Bash(command="git status")
Bash(command="git log --oneline -10")
```

### Run Python scripts
```
Bash(command="python3 scripts/some_script.py")
```

## Safety Guidelines

⚠️ **CRITICAL SAFETY RULES:**

1. **NEVER** execute commands that could:
   - Delete files (`rm -rf`, `del /F`)
   - Modify system configurations
   - Install/uninstall packages without explicit permission

2. **ALWAYS** inform the user before executing:
   - Commands that modify files
   - Commands that install software

3. **ASK FOR CONFIRMATION** for:
   - Any destructive operation

## Example

**User:** "List all files in the current directory"

**Agent:** [Uses Bash: command="ls -la"]
