"""ScoringPolicy — 점수 산식 단일 진실원 (SSoT, PR 1).

리뷰 반영:
  - SCORING_SPEC_PATH env 우선 (C1)
  - raw % vs component score Public API 분리 (C2)
  - private method 호출 금지 — public API 만 노출 (H6)
  - ROUND_HALF_UP quantize 명시 (M10)
"""
from __future__ import annotations

import os
from decimal import Decimal, ROUND_HALF_UP
from functools import lru_cache
from pathlib import Path
from typing import Optional, Dict, Any

import yaml

from domain.recommendation.exceptions import SpecValidationError, SpecNotFoundError
from domain.recommendation.score import Score


# 기본 경로: backend repo 루트의 scoring_spec.yaml
# src/domain/recommendation -> src/domain -> src -> data-engine -> backend
_FALLBACK_DEFAULT = Path(__file__).resolve().parents[4] / "scoring_spec.yaml"
_QTZ_2 = Decimal("0.01")


class ScoringPolicy:
    """spec 파일로부터 로드된 점수 정책 (immutable after load)."""

    def __init__(self, spec: Dict[str, Any]):
        self._spec = spec
        self._validate_invariants(spec)

    # ── factory ─────────────────────────────────────────
    @classmethod
    def load(cls, path: str) -> "ScoringPolicy":
        p = Path(path)
        if not p.exists():
            raise SpecNotFoundError(f"scoring spec not found: {path}")
        with open(p, "r", encoding="utf-8") as f:
            spec = yaml.safe_load(f)
        return cls(spec)

    @classmethod
    @lru_cache(maxsize=1)
    def _cached_default(cls) -> "ScoringPolicy":
        path = os.environ.get("SCORING_SPEC_PATH") or str(_FALLBACK_DEFAULT)
        return cls.load(path)

    @classmethod
    def load_default(cls) -> "ScoringPolicy":
        """Cached default (SCORING_SPEC_PATH 우선 → fallback 경로)."""
        return cls._cached_default()

    # ── invariant 검증 ─────────────────────────────────
    @staticmethod
    def _validate_invariants(spec: Dict[str, Any]) -> None:
        axes = spec.get("axes") or {}
        if set(axes.keys()) != {"ai", "technical", "sentiment"}:
            raise SpecValidationError(
                f"axes keys must be {{ai, technical, sentiment}}; got {set(axes.keys())}"
            )

        w_sum = sum(Decimal(str(axes[k]["weight"])) for k in axes)
        if abs(w_sum - Decimal("1.0")) > Decimal("0.001"):
            raise SpecValidationError(f"axes weights sum must be 1.0; got {w_sum}")

        derived = sum(
            Decimal(str(axes[k]["weight"])) * Decimal(str(axes[k]["max"])) for k in axes
        )
        declared = Decimal(str(spec.get("composite_max", 0)))
        if abs(derived - declared) > Decimal("0.001"):
            raise SpecValidationError(
                f"composite_max mismatch: declared {declared} vs derived {derived}"
            )

        grades = spec.get("grades") or {}
        order = ["S", "A", "B", "C", "D"]
        thresholds = [Decimal(str(grades[g]["min"])) for g in order if g in grades]
        if thresholds != sorted(thresholds, reverse=True):
            raise SpecValidationError(
                f"grade thresholds must be monotonic decreasing; got {thresholds}"
            )

    # ── 속성 ─────────────────────────────────────────
    @property
    def formula_version(self) -> str:
        return self._spec["formula_version"]

    @property
    def composite_max(self) -> Decimal:
        return Decimal(str(self._spec["composite_max"]))

    @property
    def axes(self) -> Dict[str, Dict[str, Any]]:
        return self._spec["axes"]

    @property
    def rsi_threshold(self) -> Decimal:
        return Decimal(str(self._spec["axes"]["technical"]["rsi_threshold"]))

    @property
    def ai_cap_pct(self) -> Decimal:
        return Decimal(str(self._spec["axes"]["ai"]["cap_threshold_pct"]))

    @property
    def min_composite_score(self) -> Decimal:
        return Decimal(str(self._spec["recommendation_filter"]["min_composite_score"]))

    # ── Public: raw % → AI 점수 변환 (리뷰 C2) ──────────
    def normalize_rise_pct_to_score(self, rise_pct: Optional[Decimal]) -> Decimal:
        """raw rise percentage (%, 예: 20.0) → AI score (0~max_ai).

        현행 산식 100% 보존:
          normalized = clip(0.5 + rise_pct / (2 * cap), 0, 1)
          if normalized < 0.5 → 0  (음수 예측은 0점. PR 3 에서 veto 로 변경)
          else → (normalized - 0.5) * 2 * max_ai
        """
        if rise_pct is None:
            return Decimal("0")
        cap = self.ai_cap_pct
        normalized = Decimal("0.5") + rise_pct / (Decimal("2") * cap)
        normalized = max(Decimal("0"), min(Decimal("1"), normalized))
        if normalized < Decimal("0.5"):
            return Decimal("0")
        ai_max = Decimal(str(self.axes["ai"]["max"]))
        v = (normalized - Decimal("0.5")) * Decimal("2") * ai_max
        return v.quantize(_QTZ_2, rounding=ROUND_HALF_UP)

    # ── Public: tech indicators → tech score ─────────────
    def tech_score_from_indicators(self, indicators: Optional[Dict[str, Any]]) -> Decimal:
        if not indicators:
            return Decimal("0")
        cfg = self.axes["technical"]
        comps = cfg["components"]
        s = Decimal("0")
        if indicators.get("golden_cross"):
            s += Decimal(str(comps["golden_cross"]))
        rsi = indicators.get("rsi")
        if rsi is not None and Decimal(str(rsi)) < self.rsi_threshold:
            s += Decimal(str(comps["rsi_below_threshold"]))
        if indicators.get("macd_buy_signal"):
            s += Decimal(str(comps["macd_buy_signal"]))
        return s.quantize(_QTZ_2, rounding=ROUND_HALF_UP)

    def count_tech_signals(self, indicators: Optional[Dict[str, Any]]) -> int:
        if not indicators:
            return 0
        cnt = 0
        if indicators.get("golden_cross"):
            cnt += 1
        rsi = indicators.get("rsi")
        if rsi is not None and Decimal(str(rsi)) < self.rsi_threshold:
            cnt += 1
        if indicators.get("macd_buy_signal"):
            cnt += 1
        return cnt

    # ── Public: normalized AI probability (0~1) → AI score ──────
    def ai_score_from_normalized(self, normalized: Optional[Decimal]) -> Decimal:
        """이미 normalized 된 rise_probability (0~1) → AI score (0~max_ai).

        sync_service 가 이미 normalized 한 값을 가지고 있는 경우 사용.
        raw rise_pct 가 있다면 normalize_rise_pct_to_score() 사용.
        """
        if normalized is None:
            return Decimal("0")
        v = Decimal(str(normalized))
        v = max(Decimal("0"), min(Decimal("1"), v))
        if v < Decimal("0.5"):
            return Decimal("0")
        ai_max = Decimal(str(self.axes["ai"]["max"]))
        return ((v - Decimal("0.5")) * Decimal("2") * ai_max).quantize(_QTZ_2, rounding=ROUND_HALF_UP)

    # ── Public: sentiment (-1~+1) → sentiment score ─────
    def sentiment_score_from_raw(self, sentiment: Optional[Decimal]) -> Decimal:
        if sentiment is None:
            return Decimal("0")
        v = Decimal(str(sentiment))
        if v <= Decimal("0"):
            return Decimal("0")
        sent_max = Decimal(str(self.axes["sentiment"]["max"]))
        return (v * sent_max).quantize(_QTZ_2, rounding=ROUND_HALF_UP)

    # ── Public: compose components → Score ──────────────
    def compose_components(
        self,
        ai_score: Decimal,
        tech_score: Decimal,
        sentiment_score: Decimal,
        has_ai: bool,
        has_tech: bool,
        has_sentiment: bool,
        tech_signal_count: int,
    ) -> Score:
        """이미 normalized 된 component score 들로부터 Score 계산.

        본 PR 은 현행 buy_criteria 동작 보존: missing 시 0점 처리 (재분배 없음).
        """
        ax = self.axes
        w_ai = Decimal(str(ax["ai"]["weight"]))
        w_tech = Decimal(str(ax["technical"]["weight"]))
        w_sent = Decimal(str(ax["sentiment"]["weight"]))

        effective_ai = ai_score if has_ai else Decimal("0")
        effective_tech = tech_score if has_tech else Decimal("0")
        effective_sent = sentiment_score if has_sentiment else Decimal("0")

        composite = (
            w_ai * effective_ai + w_tech * effective_tech + w_sent * effective_sent
        )
        composite_max = self.composite_max
        confidence = (
            (composite / composite_max).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP)
            if composite_max > 0
            else Decimal("0")
        )

        grade = self.grade_for_composite(composite)
        label = self._label_from_confidence_and_signals(confidence, tech_signal_count)

        missing = tuple(
            name
            for name, present in [
                ("ai", has_ai),
                ("tech", has_tech),
                ("sentiment", has_sentiment),
            ]
            if not present
        )

        return Score(
            ai_score=effective_ai.quantize(_QTZ_2, rounding=ROUND_HALF_UP),
            tech_score=effective_tech.quantize(_QTZ_2, rounding=ROUND_HALF_UP),
            sentiment_score=effective_sent.quantize(_QTZ_2, rounding=ROUND_HALF_UP),
            composite_score=composite.quantize(_QTZ_2, rounding=ROUND_HALF_UP),
            composite_max=composite_max.quantize(_QTZ_2, rounding=ROUND_HALF_UP),
            confidence=confidence,
            grade=grade,
            recommendation_label=label,
            missing_axes=missing,
            veto_reasons=(),  # PR 3 에서 채워짐
            warnings=(),  # PR 5 에서 VIX 경고
        )

    # ── Public: raw signals → Score (one-shot) ──────────
    def score_from_raw_signals(
        self,
        rise_pct: Optional[Decimal],
        sentiment_raw: Optional[Decimal],
        tech_indicators: Optional[Dict[str, Any]],
    ) -> Score:
        """raw input (rise % / sentiment -1~+1 / tech indicators) → Score 단일 호출."""
        ai_s = self.normalize_rise_pct_to_score(rise_pct)
        tech_s = self.tech_score_from_indicators(tech_indicators)
        sent_s = self.sentiment_score_from_raw(sentiment_raw)
        return self.compose_components(
            ai_score=ai_s,
            tech_score=tech_s,
            sentiment_score=sent_s,
            has_ai=rise_pct is not None,
            has_tech=bool(tech_indicators),
            has_sentiment=sentiment_raw is not None,
            tech_signal_count=self.count_tech_signals(tech_indicators),
        )

    # ── Grade / Label ───────────────────────────────────
    def grade_for_composite(self, composite: Decimal) -> str:
        for g in ["S", "A", "B", "C"]:
            if composite >= Decimal(str(self._spec["grades"][g]["min"])):
                return g
        return "D"

    def _label_from_confidence_and_signals(
        self, confidence: Decimal, tech_signals: int
    ) -> str:
        """본 PR 은 현행 RecommendationGrade.from_scores() 매핑 100% 보존."""
        labels = self._spec["recommendation_labels"]
        for key in ["STRONG", "RECOMMEND", "WATCH"]:
            cfg = labels.get(key) or {}
            min_c = Decimal(str(cfg.get("min_confidence", 0)))
            min_s = cfg.get("min_tech_signals", 0)
            if confidence >= min_c and tech_signals >= min_s:
                return key
        return "NONE"

    def label_metadata(self, label_key: str) -> Dict[str, str]:
        cfg = self._spec["recommendation_labels"].get(label_key) or {}
        return {"label": cfg.get("label", ""), "emoji": cfg.get("emoji", "⚪")}
