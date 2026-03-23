#!/usr/bin/env python3
"""Translation script using DeepL API (free tier) or fallback to basic translation"""

import sys
import urllib.request
import urllib.parse
import json


def translate(text: str, from_lang: str, to_lang: str) -> str:
    """Translate text using LibreTranslate (free API)"""
    try:
        # Use LibreTranslate public API
        url = "https://libretranslate.com/translate"
        
        data = {
            "q": text,
            "source": from_lang if from_lang != "auto" else "auto",
            "target": to_lang,
            "format": "text"
        }
        
        req = urllib.request.Request(
            url,
            data=json.dumps(data).encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )
        
        with urllib.request.urlopen(req, timeout=10) as response:
            result = json.loads(response.read().decode())
        
        return result.get('translatedText', 'Translation failed')
    except Exception as e:
        # Fallback: return a message
        return f"Translation service unavailable: {str(e)}. Please try again later."


if __name__ == "__main__":
    if len(sys.argv) < 4:
        print("Usage: python translate.py <text> <from_lang> <to_lang>")
        print("Example: python translate.py 'Hello world' en zh")
        sys.exit(1)
    
    text = sys.argv[1]
    from_lang = sys.argv[2]
    to_lang = sys.argv[3]
    
    print(translate(text, from_lang, to_lang))
