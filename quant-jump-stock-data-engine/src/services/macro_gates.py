"""Macro gate dispatcher — 산식 외부 pre-flight 게이트 추상화 (refactor 2026-05-22).

PR 5 가 도입한 VIX 거시 gate 는 handlers.py 의 단일 분기로 작성됨. PR 4+ 에서
다른 macro gate (DXY 환율 / 실업률 / FED 금리 등) 가 추가될 가능성이 있어 dispatcher
패턴으로 분리한다.

설계 원칙:
- ScoringPolicy 는 spec 검증 + threshold/policy 만 알고, **데이터 조회는 분리**
- 각 gate 가 fetcher (Mongo / 외부 API) + decision (ScoringPolicy.should_block_on_*) 으로 구성
- 결과는 `MacroGateResult` immutable — Slack/로그/저장 다운스트림이 균일 형태로 소비
- 새 gate 추가 시 `MacroGate` 서브클래스 + `MacroGateRunner._GATES` 에 등록만

호출처:
- `adapter/input/pubsub/handlers.py` `StockRecommendationHandler.handle()` 가
  `MacroGateRunner(policy, db).evaluate(analysis_date)` 호출
- 발동 gate 가 있으면 사용자/운영자 채널 양쪽 안내 + 송출 차단
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Optional, Protocol

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class MacroGateResult:
    """단일 gate 평가 결과.

    blocked=True 면 caller 가 추천 송출 차단 + Slack 알림. 다중 gate 가 발동해도 첫 발동
    사유로 사용자에게 안내 (혼란 방지).
    """
    gate_name: str  # "vix" — Slack 메시지 분기용
    blocked: bool
    value: Optional[float]  # 측정값 (예: 18.06). 결손 시 None
    threshold: Optional[float]  # spec 임계 (예: 35.0)
    reason: str = ""  # blocked=True 시 사용자 친화적 사유


class MacroGate(Protocol):
    """단일 macro gate strategy."""
    name: str

    def is_enabled(self) -> bool:
        """spec 에서 활성화 여부 확인."""
        ...

    def evaluate(self, db, analysis_date: str) -> MacroGateResult:
        """데이터 조회 + 임계 검사 + 결과 반환."""
        ...


class VixGate:
    """VIX 거시 변동성 gate (PR 5, spec.macro_gates.vix)."""
    name = "vix"

    def __init__(self, policy):
        self.policy = policy

    def is_enabled(self) -> bool:
        return self.policy.is_vix_gate_enabled()

    def evaluate(self, db, analysis_date: str) -> MacroGateResult:
        vix = _fetch_vix(db, analysis_date)
        threshold = float(self.policy.vix_threshold) if self.policy.vix_threshold else None
        blocked = self.policy.should_block_on_vix(vix)
        reason = ""
        if blocked:
            if vix is not None:
                reason = (
                    f"시장 변동성 지수(VIX)가 {vix:.1f}로 평소 범위를 벗어났습니다. "
                    f"변동성이 높은 날은 추천의 신뢰도가 낮아질 수 있어 자동으로 보류합니다."
                )
            else:
                reason = "VIX 데이터 결손 + 차단 정책. 운영자 확인 필요."
        return MacroGateResult(
            gate_name=self.name,
            blocked=blocked,
            value=vix,
            threshold=threshold,
            reason=reason,
        )


def _fetch_vix(db, analysis_date: str) -> Optional[float]:
    """daily_stock_data.yfinance_indicators.^VIX 값 조회. 없으면 None.

    Mongo 네트워크/timeout 오류 시 None 반환 + warning log (silent error 방지).
    """
    try:
        doc = db.daily_stock_data.find_one(
            {"date": analysis_date},
            {"_id": 0, "yfinance_indicators.^VIX": 1},
        )
    except Exception as e:
        logger.warning(
            "VIX 조회 실패 (date=%s, error=%s). gate missing_policy 적용.",
            analysis_date, e,
        )
        return None
    if not doc:
        return None
    vix = (doc.get("yfinance_indicators") or {}).get("^VIX")
    if vix is None:
        return None
    if isinstance(vix, (int, float)):
        return float(vix)
    if isinstance(vix, dict):
        val = vix.get("close_price") or vix.get("close")
        return float(val) if val is not None else None
    return None


@dataclass
class MacroGateRunner:
    """모든 활성 macro gate 를 순차 평가. 첫 차단 gate 가 결과 반환.

    PR 5 단계에서는 VIX 하나뿐이지만 PR 4+ 추가 시 _GATES 에 등록만 하면 됨.
    """
    policy: object
    db: object
    _gates: list = field(default_factory=list)

    def __post_init__(self):
        # 신규 gate 추가 위치 — VixGate 외에 DxyGate, UnemploymentGate 등을 여기 등록
        self._gates = [VixGate(self.policy)]

    def evaluate(self, analysis_date: str) -> Optional[MacroGateResult]:
        """활성 gate 들 평가. 첫 차단 결과 반환. 모두 통과 시 None."""
        for gate in self._gates:
            if not gate.is_enabled():
                continue
            result = gate.evaluate(self.db, analysis_date)
            if result.blocked:
                return result
        return None
