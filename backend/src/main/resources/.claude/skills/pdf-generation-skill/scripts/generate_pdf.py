#!/usr/bin/env python3
"""PDF generation script using fpdf"""

import sys
import os


def generate_pdf(filename: str, content: str, output_dir: str = ".") -> str:
    """Generate a PDF from text content"""
    try:
        from fpdf import FPDF

        pdf = FPDF()
        pdf.add_page()
        pdf.set_font("Arial", size=12)

        # Add content line by line
        for line in content.split('\n'):
            pdf.cell(0, 10, line, ln=True)

        # Ensure output directory exists
        os.makedirs(output_dir, exist_ok=True)

        # Full path
        filepath = os.path.join(output_dir, filename)
        if not filename.endswith('.pdf'):
            filepath += '.pdf'

        pdf.output(filepath)
        return f"PDF generated: {filepath}"

    except ImportError:
        return "Error: fpdf library not installed. Run: pip install fpdf2"
    except Exception as e:
        return f"Error generating PDF: {str(e)}"


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python generate_pdf.py <filename> '<content>'")
        print("Example: python generate_pdf.py hello.pdf 'Hello World'")
        sys.exit(1)

    filename = sys.argv[1]
    content = sys.argv[2]

    print(generate_pdf(filename, content))
