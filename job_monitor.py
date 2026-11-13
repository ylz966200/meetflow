"""Collect explicitly supplied public job pages, respecting robots.txt.

Usage: python job_monitor.py https://example.com/job/123
"""
import csv
import datetime
import html
from html.parser import HTMLParser
import json
import re
import sys
import time
from urllib import error, parse, request, robotparser

AGENT = 'JobMonitor/1.0'

class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.parts = []
        self.hidden = 0

    def handle_starttag(self, tag, attrs):
        if tag in ('script', 'style', 'noscript'):
            self.hidden += 1

    def handle_endtag(self, tag):
        if tag in ('script', 'style', 'noscript') and self.hidden:
            self.hidden -= 1

    def handle_data(self, data):
        if not self.hidden and data.strip():
            self.parts.append(data.strip())

def fetch(url):
    parsed = parse.urlsplit(url)
    if parsed.scheme not in ('http', 'https') or not parsed.netloc:
        raise ValueError('Invalid URL')
    robots = robotparser.RobotFileParser(parse.urlunsplit((parsed.scheme, parsed.netloc, '/robots.txt', '', '')))
    robots.read()
    if not robots.can_fetch(AGENT, url):
        raise PermissionError('robots.txt disallows access')
    req = request.Request(url, headers={'User-Agent': AGENT})
    with request.urlopen(req, timeout=20) as response:
        final = response.geturl()
        if any(x in final.lower() for x in ('captcha', 'security', 'verify', 'login', 'passport')):
            raise PermissionError('Login or security page encountered')
        content = response.read(2_000_000).decode(response.headers.get_content_charset() or 'utf-8', errors='replace')
    page = Page()
    page.feed(content)
    text = re.sub(r'\s+', ' ', html.unescape(' '.join(page.parts)))
    if any(x in text for x in ('请完成安全验证', '访问过于频繁', '验证码', '请先登录')):
        raise PermissionError('Access restriction encountered')
    return final, text

def main(urls):
    today = datetime.date.today().isoformat()
    with open('job_monitor_results.jsonl', 'a', encoding='utf-8') as out:
        for url in dict.fromkeys(urls):
            row = {'date': today, 'source_url': url, 'status': '待核实'}
            try:
                row['final_url'], row['text'] = fetch(url)
                row['note'] = '公开页面文本；职位字段及有效性需核实'
            except (error.URLError, OSError, ValueError, PermissionError) as exc:
                row['error'] = str(exc)
            out.write(json.dumps(row, ensure_ascii=False) + '\n')
            print(json.dumps({k: v for k, v in row.items() if k != 'text'}, ensure_ascii=False))
            time.sleep(2)

if __name__ == '__main__':
    if len(sys.argv) < 2:
        sys.exit('Provide one or more public job page URLs.')
    main(sys.argv[1:])
