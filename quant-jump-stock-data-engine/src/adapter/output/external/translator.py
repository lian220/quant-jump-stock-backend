"""
영한 번역 유틸리티

영어 텍스트를 한국어로 번역하는 헬퍼.
deep-translator (GoogleTranslator) 사용, API 키 불필요.
"""

import logging
import re
from typing import Optional

from deep_translator import GoogleTranslator

logger = logging.getLogger(__name__)

_HANGUL_PATTERN = re.compile(r"[가-힣]")
_translator: Optional[GoogleTranslator] = None


def _get_translator() -> GoogleTranslator:
    global _translator
    if _translator is None:
        _translator = GoogleTranslator(source="en", target="ko")
    return _translator


def _contains_korean(text: str) -> bool:
    """한글 포함 여부 확인"""
    return bool(_HANGUL_PATTERN.search(text))


def translate_if_english(text: Optional[str]) -> Optional[str]:
    """영어 텍스트면 한국어로 번역, 한글이 포함되어 있으면 그대로 반환.

    번역 실패 시 원본 반환 (번역 실패가 수집을 막지 않도록).
    """
    if not text or not text.strip():
        return text

    if _contains_korean(text):
        return text

    try:
        translated = _get_translator().translate(text)
        return translated if translated else text
    except Exception as e:
        logger.warning(f"번역 실패 (원본 유지): {e}")
        return text
