#!/usr/bin/env python3
"""News fetch script using Hacker News API"""

import sys
import urllib.request
import json


def get_hackernews_top(stories: int = 10) -> str:
    """Fetch top stories from Hacker News"""
    try:
        # Get top story IDs
        url = "https://hacker-news.firebaseio.com/v0/topstories.json"
        with urllib.request.urlopen(url, timeout=10) as response:
            story_ids = json.loads(response.read().decode())
        
        result = ["📰 Hacker News Top Stories", ""]
        
        for i, story_id in enumerate(story_ids[:stories], 1):
            # Get story details
            story_url = f"https://hacker-news.firebaseio.com/v0/item/{story_id}.json"
            with urllib.request.urlopen(story_url, timeout=10) as story_response:
                story = json.loads(story_response.read().decode())
            
            title = story.get('title', 'N/A')
            score = story.get('score', 0)
            by = story.get('by', 'N/A')
            url = story.get('url', f"https://news.ycombinator.com/item?id={story_id}")
            
            result.append(f"{i}. {title}")
            result.append(f"   Score: {score} | By: {by}")
            result.append(f"   URL: {url}")
            result.append("")
        
        return "\n".join(result)
    except Exception as e:
        return f"获取新闻失败: {str(e)}"


if __name__ == "__main__":
    count = 10
    if len(sys.argv) > 1:
        try:
            count = int(sys.argv[1])
        except ValueError:
            pass
    print(get_hackernews_top(count))
