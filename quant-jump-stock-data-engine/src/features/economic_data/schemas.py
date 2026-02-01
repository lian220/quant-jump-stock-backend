"""Economic Data Pydantic Schemas (Read-Only API)"""
from pydantic import BaseModel


class StatusResponse(BaseModel):
    """상태 조회 응답 (Read-Only)"""
    service: str
    status: str
    timestamp: str
    supported_triggers: list[str]
