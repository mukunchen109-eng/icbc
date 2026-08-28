import os
from datetime import date
from pathlib import Path
import requests
from dotenv import load_dotenv

BASE_DIR=Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / '.env')

def trigger_daily_report(
        report_date:date,
        report_title:str|None=None,
):
    api_url=os.getenv("REPORT_API_URL","http://localhost:8080/api/reports/daily-summary")

    try:
        timeout=int(os.getenv("REPORT_API_TIMEOUT","10"))
    except ValueError as error:
        raise ValueError("report_api_timeout需要是整数") from error

    payload={"reportDate":report_date.isoformat()}

    if report_title:
        payload["reportTitle"] = report_title

    try:
        response=requests.post(api_url, json=payload,timeout=(3,timeout))
        response.raise_for_status()
    except requests.RequestException as error:
        raise RuntimeError(f"接口调用失败:{error}") from error