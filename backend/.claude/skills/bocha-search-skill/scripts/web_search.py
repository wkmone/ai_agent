#!/usr/bin/env python3
"""Web search script using DuckDuckGo API"""

import sys
import urllib.request
import urllib.parse
import json


def web_search(query: str, num_results: int = 5) -> str:
    """Search the web using DuckDuckGo"""
    try:
        # Use DuckDuckGo HTML search
        url = f"https://html.duckduckgo.com/html/?q={urllib.parse.quote(query)}"
        
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        
        with urllib.request.urlopen(req, timeout=10) as response:
            html = response.read().decode('utf-8')
        
        # Simple parsing (in production, use proper HTML parser)
        results = []
        import re
        
        # Find result blocks
        result_pattern = r'<a class="result__a" href="([^"]+)"[^>]*>([^<]+)</a>'
        snippet_pattern = r'<a class="result__snippet"[^>]*>([^<]+)</a>'
        
        result_matches = re.findall(result_pattern, html)
        snippet_matches = re.findall(snippet_pattern, html)
        
        for i, (url, title) in enumerate(result_matches[:num_results], 1):
            snippet = snippet_matches[i-1] if i-1 < len(snippet_matches) else ""
            results.append(f"{i}. {title}\n   URL: {url}\n   {snippet}\n")
        
        if not results:
            return "No results found."
        
        return "Search Results:\n\n" + "\n".join(results)
    
    except Exception as e:
        return f"Search failed: {str(e)}"


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python web_search.py '<query>'")
        print("Example: python web_search.py 'Python programming'")
        sys.exit(1)
    
    query = sys.argv[1]
    print(web_search(query))
