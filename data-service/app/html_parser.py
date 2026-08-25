import hashlib
import re
from bs4 import BeautifulSoup


NOISE_PREFIXES = (
    "广告",
    "推广",
    "商业推广",
    "免责声明",
    "责任编辑",
)


def clean_line(text: str) -> str:
    text = text.replace("\xa0", " ")
    return re.sub(r"\s+", " ", text).strip()


def make_article(
    batch: dict,
    title: str,
    paragraphs: list[str],
) -> dict | None:
    original_content = "\n".join(paragraphs).strip()

    cleaned_paragraphs = [
        text
        for text in paragraphs
        if not text.startswith(NOISE_PREFIXES)
    ]
    content = "\n".join(cleaned_paragraphs).strip()

    if not title or not content:
        return None

    return {
        "source_row_id": batch["source_row_id"],
        "news_date": batch["news_date"],
        "title": title,
        "original_content": original_content,
        "content": content,
        "industry": batch["industry"],
        "area": batch["area"],
    }


def parse_articles(batch: dict) -> list[dict]:
    soup = BeautifulSoup(batch["raw_html"], "html.parser")

    for tag in soup.find_all(["script", "style"]):
        tag.decompose()

    articles = []
    title = None
    paragraphs = []

    for paragraph in soup.find_all("p"):
        strong = paragraph.find("strong")

        if strong:
            if title:
                article = make_article(batch, title, paragraphs)
                if article:
                    articles.append(article)

            title = clean_line(strong.get_text(" ", strip=True))
            paragraphs = []

            strong.extract()
            remaining = clean_line(
                paragraph.get_text(" ", strip=True)
            )
            if remaining:
                paragraphs.append(remaining)
        elif title:
            text = clean_line(paragraph.get_text(" ", strip=True))
            if text:
                paragraphs.append(text)

    if title:
        article = make_article(batch, title, paragraphs)
        if article:
            articles.append(article)

    return articles


def calculate_hash(article: dict) -> str:
    text = f"{article['title']}\n{article['content']}"
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def remove_duplicates(articles: list[dict]) -> list[dict]:
    seen = set()
    result = []

    for article in articles:
        content_hash = calculate_hash(article)
        duplicate_key = (
            article["news_date"],
            content_hash,
        )

        if duplicate_key in seen:
            continue

        seen.add(duplicate_key)

        article_with_hash = article.copy()
        article_with_hash["content_hash"] = content_hash
        result.append(article_with_hash)

    return result