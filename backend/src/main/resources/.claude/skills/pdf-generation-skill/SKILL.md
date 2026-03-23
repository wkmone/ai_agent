---
name: pdf-generation
description: Generate PDF documents from text. Use when user asks to create, save, or download a PDF document.
---

# PDF Generation Skill

## Instructions

When user wants to create a PDF:

1. Use the **Bash** tool to execute the PDF generation script
2. Inform the user where the PDF was saved

## How to Execute

Use the Bash tool to call the Python script:

```
Bash(command="python3 scripts/generate_pdf.py 'output.pdf' 'Content to put in PDF'")
```

Arguments:
- filename: Output PDF filename
- content: Text content for the PDF

## Example

**User:** "Create a PDF with 'Hello World'"

**Agent:** [Uses Bash: python3 scripts/generate_pdf.py hello.pdf 'Hello World']

**Response:** PDF generated: /path/to/hello.pdf
