import os
from pathlib import Path

from dotenv import load_dotenv
from sqlalchemy import create_engine, text
from sqlalchemy.engine import URL


BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")


def required_setting(name: str) -> str:
    value = os.getenv(name)
    if not value:
        raise RuntimeError(f"缺少配置项：{name}")
    return value


database_url = URL.create(
    drivername="mysql+pymysql",
    username=required_setting("DB_USER"),
    password=required_setting("DB_PASSWORD"),
    host=required_setting("DB_HOST"),
    port=int(os.getenv("DB_PORT", "3306")),
    database=required_setting("DB_NAME"),
    query={"charset": "utf8mb4"},
)

engine = create_engine(
    database_url,
    pool_pre_ping=True,
    pool_recycle=1800,
    connect_args={"connect_timeout": 10},
)


def check_connection() -> str:
    with engine.connect() as connection:
        return connection.execute(
            text("SELECT DATABASE()")
        ).scalar_one()

INSERT_NEWS = text(
    """
    INSERT INTO news_pool (
        source_row_id,
        news_date,
        title,
        original_content,
        content,
        industry,
        area,
        content_hash
    )
    VALUES (
        :source_row_id,
        :news_date,
        :title,
        :original_content,
        :content,
        :industry,
        :area,
        :content_hash
    )
    ON DUPLICATE KEY UPDATE id = id
    """
)


def save_articles(articles: list[dict]) -> dict:
    if not articles:
        return {"total": 0, "inserted": 0, "duplicates": 0}

    dates = {article["news_date"] for article in articles}
    if len(dates) != 1:
        raise ValueError("一次只能写入同一天的资讯")

    news_date = dates.pop()

    rows = [
        {
            "source_row_id": article["source_row_id"],
            "news_date": article["news_date"],
            "title": article["title"],
            "original_content": article["original_content"],
            "content": article["content"],
            "industry": article["industry"],
            "area": article["area"],
            "content_hash": article["content_hash"],
        }
        for article in articles
    ]

    with engine.begin() as connection:
        before = connection.execute(
            text(
                """
                SELECT COUNT(*)
                FROM news_pool
                WHERE news_date = :news_date
                """
            ),
            {"news_date": news_date},
        ).scalar_one()

        connection.execute(INSERT_NEWS, rows)

        after = connection.execute(
            text(
                """
                SELECT COUNT(*)
                FROM news_pool
                WHERE news_date = :news_date
                """
            ),
            {"news_date": news_date},
        ).scalar_one()

    inserted = after - before

    return {
        "date": str(news_date),
        "total": len(rows),
        "inserted": inserted,
        "duplicates": len(rows) - inserted,
    }