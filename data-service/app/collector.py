from datetime import date
from pathlib import Path
import logging

from app.database import collection_job_exists, create_collection_job, finish_collection_job, save_articles
from app.excel_reader import read_batches
from app.html_parser import parse_articles, remove_duplicates

BASE_DIR = Path(__file__).resolve().parent.parent
DATA_FILE = BASE_DIR/"data"/"参阅信息.xlsx"
MAX_ATTEMPTS = 3

logging.basicConfig(
    filename=BASE_DIR/"collection.log",
    encoding="utf-8",
    level=logging.INFO,
    format="%(asctime)s | %(levelname)s | %(message)s",
)

logger = logging.getLogger(__name__)

def run_collection(target_date: date, trigger_type: str) -> None:
    if collection_job_exists(target_date):
        print(f"{target_date}的信息已被采集")
        return

    task_id = create_collection_job(target_date, trigger_type)

    logger.info(
        "任务开始：日期=%s，触发方式=%s",
        target_date,
        trigger_type,
    )

    for attempt in range(1, MAX_ATTEMPTS + 1):
        try:
            batches = read_batches(DATA_FILE, target_date)

            articles = []
            for batch in batches:
                articles.extend(parse_articles(batch))

            articles = remove_duplicates(articles)

            if not articles:
                finish_collection_job(
                    task_id,
                    status="NO_DATA",
                    processed_count=0,
                    retry_count=attempt - 1,
                    message="Excel中没有当天资讯",
                )
                logger.info("任务结束：日期=%s，没有当天资讯", target_date)

                print(f"{target_date}没有待处理资讯")
                return

            result = save_articles(articles)

            finish_collection_job(
                task_id,
                status="SUCCESS",
                processed_count=len(articles),
                retry_count=attempt - 1,
                message=f"成功写入{result['inserted']}条资讯",
            )

            logger.info(
                "任务成功：日期=%s，处理=%s，写入=%s",
                target_date,
                len(articles),
                result["inserted"],
            )

            print(
                f"{target_date}采集完成，"
                f"处理{len(articles)}条，"
                f"写入{result['inserted']}条"
            )
            return

        except Exception as error:
            if attempt < MAX_ATTEMPTS:
                logger.warning(
                    "第%s次执行失败：%s",
                    attempt,
                    error,
                )
                print(f"第{attempt}次采集失败，准备重试：{error}")
                continue

            logger.exception("任务失败：日期=%s", target_date)

            finish_collection_job(
                task_id,
                status="FAILED",
                processed_count=0,
                retry_count=attempt - 1,
                message=str(error)[:1000],
            )
            raise