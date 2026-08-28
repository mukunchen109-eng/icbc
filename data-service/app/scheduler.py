import os
from datetime import datetime, timedelta, timezone
from pathlib import Path

from dotenv import load_dotenv
from apscheduler.schedulers.blocking import BlockingScheduler

from app.collector import run_collection


BASE_DIR = Path(__file__).resolve().parent.parent
BEIJING_TIME = timezone(timedelta(hours=8))

load_dotenv(BASE_DIR / ".env")


def read_collection_time() -> tuple[int, int]: #读取预定的时间
    value = os.getenv("COLLECTION_TIME", "09:00")

    try:
        hour, minute = map(int, value.split(":"))
    except ValueError as error:
        raise ValueError("COLLECTION_TIME必须使用HH:MM格式") from error

    if not 0 <= hour <= 23 or not 0 <= minute <= 59:
        raise ValueError("COLLECTION_TIME不是有效时间")

    return hour, minute


def scheduled_collection():
    today = datetime.now(BEIJING_TIME).date() #运行时日期
    run_collection(today, "SCHEDULED")


def main():
    hour, minute = read_collection_time()
    now = datetime.now(BEIJING_TIME)  #得到当前北京时间

    scheduler = BlockingScheduler(timezone=BEIJING_TIME)
    scheduler.add_job(  #每日任务
        scheduled_collection,
        "cron",
        hour=hour,
        minute=minute,
    )

    print(f"当前北京时间：{now:%Y-%m-%d %H:%M:%S}")
    print(f"每日采集时间：{hour:02d}:{minute:02d}")

    if (now.hour, now.minute) >= (hour, minute): #若程序在当天九点后启动，补跑当日信息采集
        run_collection(now.date(), "CATCH_UP")

    print("定时采集程序已启动")

    try:
        scheduler.start()
    except (KeyboardInterrupt, SystemExit):
        scheduler.shutdown(wait=False)
        print("定时采集程序已停止")

if __name__ == "__main__":
    main()
