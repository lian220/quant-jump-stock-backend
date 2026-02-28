"""US 마켓 시간 기준 날짜 유틸리티"""
from datetime import datetime, date, timedelta

import pytz

ET = pytz.timezone("America/New_York")  # EDT/EST 자동 처리
KST = pytz.timezone("Asia/Seoul")

_MARKET_CLOSE_BUFFER_HOUR = 17  # 4PM 장마감 + 1시간 버퍼


def now_et() -> datetime:
    return datetime.now(ET)


def today_et() -> date:
    return now_et().date()


def today_et_str() -> str:
    return today_et().isoformat()


def latest_complete_bar_date() -> date:
    """마지막 완성된 미국 거래일 바 날짜.

    - 5PM ET 이후: 오늘 바 완성 -> 오늘 반환
    - 5PM ET 이전: 오늘 바 미완성 -> 어제 반환
    """
    now = now_et()
    if now.hour >= _MARKET_CLOSE_BUFFER_HOUR:
        return now.date()
    return (now - timedelta(days=1)).date()


def latest_complete_bar_date_str() -> str:
    return latest_complete_bar_date().isoformat()


def now_kst() -> datetime:
    return datetime.now(KST)
