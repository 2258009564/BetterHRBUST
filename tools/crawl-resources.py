"""收录教务处公开资料目录和附件链接，不下载附件，不使用登录 Cookie。"""
import html
import hashlib
import json
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urljoin, urlsplit, urlunsplit
from urllib.request import ProxyHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://jwzx.hrbust.edu.cn/homepage/"
SOURCE = BASE + "info.do?columnId=344"
OPENER = build_opener(ProxyHandler({}))


def clean_text(value):
    return " ".join(html.unescape(re.sub(r"<[^>]*>", " ", value)).split())


def school_url(value, page=SOURCE):
    url = urlsplit(urljoin(page, html.unescape(value)))
    if url.hostname != "jwzx.hrbust.edu.cn" or url.username or url.password:
        raise ValueError("资料链接不属于学校教务处")
    if url.scheme not in ("http", "https") or not url.path.startswith("/homepage/"):
        raise ValueError("资料链接不属于公开资料模块")
    path = re.sub(r";jsessionid=[^/?#]*", "", url.path, flags=re.I)
    return urlunsplit(("http", "jwzx.hrbust.edu.cn", path, url.query, ""))


def fetch(url):
    time.sleep(0.15)
    with OPENER.open(Request(school_url(url), headers={"User-Agent": "BetterHRBUST-ResourceCatalog/1.1"}), timeout=25) as response:
        school_url(response.geturl())
        if response.headers.get_content_type() not in ('text/html', 'text/plain', 'application/xhtml+xml'):
            raise ValueError('资料原文返回非文本内容，停止读取；附件只收录地址')
        charset = response.headers.get_content_charset() or "utf-8"
        data = response.read()
        for candidate in dict.fromkeys([charset, 'utf-8', 'gb18030']):
            try:
                return data.decode(candidate, errors='strict')
            except UnicodeDecodeError:
                pass
        raise ValueError(f'资料页面编码无法识别：{url}')


def rows(page, category):
    records = []
    for item in re.findall(r"<li\b[^>]*>(.*?)</li>", page, re.S | re.I):
        link = re.search(r'<a\b[^>]*href=["\']([^"\']+)["\'][^>]*>(.*?)</a>', item, re.S | re.I)
        date = re.search(r"\b\d{4}-\d{2}-\d{2}\b", item)
        if not link or not date:
            continue
        source = urlsplit(urljoin(BASE, html.unescape(link[1])))
        if source.hostname == 'jwzx.hrbust.edu.cn':
            url = school_url(link[1])
        elif source.hostname and source.hostname.endswith('.hrbust.edu.cn') and source.scheme in ('http', 'https') and not source.username:
            url = source.geturl()
        else:
            raise ValueError('资料列表存在无法验证的外部链接')
        article_id = parse_qs(urlsplit(url).query).get('articleId', [hashlib.sha256(url.encode()).hexdigest()[:16]])[0]
        records.append({"id": article_id,
                        "title": clean_text(link[2]), "category": category,
                        "date": date[0] if date else "", "url": url, "attachments": []})
    return records


def crawl():
    index = fetch(SOURCE)
    sections = re.split(r'<div\s+class="subColumnBar"\s*>', index)[1:]
    categories, records = [], []
    for section in sections:
        title = re.search(r'class="columnTitle".*?<span>(.*?)</span>', section, re.S)
        link = re.search(r'href="(infoArticleList\.do\?columnId=\d+)"', section)
        if not title or not link:
            raise ValueError("资料栏目结构改变，拒绝保存不完整索引")
        name, url = clean_text(title[1]), school_url(link[1])
        first = fetch(url)
        summary = re.search(r"共\s*<b>(\d+)</b>条，\s*<b>\d+</b>\s*/\s*<b>(\d+)</b>页", first)
        if not summary:
            raise ValueError(f"{name} 缺少分页统计")
        expected, pages = map(int, summary.groups())
        if pages > 100:
            raise ValueError("栏目分页异常，停止抓取")
        category_records = rows(first, name)
        column_id = parse_qs(urlsplit(url).query)["columnId"][0]
        for page in range(2, pages + 1):
            query = urlencode({"columnId": column_id, "pagingPage": page, "pagingNumberPer": 12,
                               "sortColumn": "publicationDate", "sortDirection": -1})
            category_records.extend(rows(fetch(BASE + "infoArticleList.do?" + query), name))
        unique = {item["id"]: item for item in category_records}
        if len(unique) != expected:
            raise ValueError(f"{name} 分页条数不匹配：期望 {expected}，实际 {len(unique)}")
        categories.append({"name": name, "url": url, "count": expected})
        records.extend(unique.values())
        print(f"栏目 {name}: {expected} 条，{pages} 页", flush=True)
    if len(categories) < 9:
        raise ValueError("未取得截图对应的完整公开栏目")
    for n, item in enumerate(records, 1):
        if urlsplit(item['url']).hostname != 'jwzx.hrbust.edu.cn':
            continue  # 学校栏目引用的其他学院页面保留原文链接，不扩大抓取范围。
        if not urlsplit(item['url']).path.endswith('infoSingleArticle.do'):
            item['attachments'].append({'title': item['title'], 'url': item['url']})
            item['url'] = next(category['url'] for category in categories if category['name'] == item['category'])
            continue  # 目录中的直接文件链接只收录地址，不下载或解析二进制文件。
        detail = fetch(item["url"])
        for href, title in re.findall(r'<a\b[^>]*href=["\']([^"\']*downloadTheolFile\.do[^"\']*)["\'][^>]*>(.*?)</a>', detail, re.S | re.I):
            attachment = {"title": clean_text(title), "url": school_url(href, item["url"])}
            if attachment not in item["attachments"]:
                item["attachments"].append(attachment)
        if n % 20 == 0:
            print(f"附件链接解析 {n}/{len(records)}", flush=True)
    records.sort(key=lambda item: (item["date"], item["id"]), reverse=True)
    data = {"source": SOURCE, "updatedAt": datetime.now(timezone.utc).isoformat(),
            "categories": categories, "items": records}
    content = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    for relative in ["shared/resources.json", "android/app/src/main/assets/resources.json"]:
        target = ROOT / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
    print(f"完成：{len(categories)} 个栏目，{len(records)} 条资料，{sum(len(r['attachments']) for r in records)} 个附件链接", flush=True)


if __name__ == "__main__":
    crawl()
