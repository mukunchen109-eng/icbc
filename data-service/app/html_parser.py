import hashlib
import re
from bs4 import BeautifulSoup
import unicodedata
from rapidfuzz import fuzz

NOISE_PREFIX_PATTERN = re.compile(
    r"^(广告|推广|商业推广)\s*[:：]"
)
PROMOTION_MARKERS = (
    "扫码关注","关注公众号","点击链接","客服热线","联系电话","微信号","长按识别二维码","扫描二维码"
)
SIMILARITY_THRESHOLD = 85


def clean_line(text: str) -> str:
    text = text.replace("\xa0", " ")
    text = re.sub(r"\s+", " ", text).strip()
    text = re.sub(r"(?<=[\u4e00-\u9fff])\s+(?=[\u4e00-\u9fff])","", text)  # 删除中文字符之间的空格
    text = re.sub(r"\s+([，。；：！？、）”])",r"\1",text)  # 删除中文标点前后的空格
    text = re.sub(r"([（“])\s+", r"\1",text,)

    return text


def is_noise_paragraph(text: str) -> bool:
    if NOISE_PREFIX_PATTERN.match(text):
        return True

    if len(text) <= 120:
        return any(
            marker in text
            for marker in PROMOTION_MARKERS
        )

    return False


def make_article(
    batch: dict,
    title: str,
    paragraphs: list[str],
) -> dict | None:
    original_content = "\n".join(paragraphs).strip()

    cleaned_paragraphs = [
        text
        for text in paragraphs
        if not is_noise_paragraph(text)
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


#输入一条excel批次，返回多篇文章
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


def normalize_for_dedup(title: str, content: str) -> str:
    text = f"{title}\n{content}"
    text = unicodedata.normalize("NFKC", text).lower()
    return re.sub(r"\s+", "", text)


def calculate_hash(article: dict) -> str:
    text = normalize_for_dedup(
        article["title"],
        article["content"],
    )
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def remove_duplicates(articles: list[dict]) -> list[dict]:
    seen_hashes = set()
    result = []
    normalized_texts = []

    for article in articles:
        content_hash = calculate_hash(article)
        hash_key = (article["news_date"], content_hash)

        if hash_key in seen_hashes:
            continue
        seen_hashes.add(hash_key)

        current_text = normalize_for_dedup(
            article["title"],
            article["content"],
        )

        is_similar = False
        for saved_article, saved_text in zip(result, normalized_texts):
            if saved_article["news_date"] != article["news_date"]:
                continue
            if fuzz.ratio(current_text, saved_text) >= SIMILARITY_THRESHOLD:
                is_similar = True
                break
        if is_similar:
            continue

        article_with_hash = article.copy()
        article_with_hash["content_hash"] = content_hash

        result.append(article_with_hash)
        normalized_texts.append(current_text)

    return result