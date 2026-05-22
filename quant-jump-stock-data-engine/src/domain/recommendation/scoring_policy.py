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
from typing import Any

import yaml

from domain.recommendation.exceptions import SpecValidationError, SpecNotFoundError
from domain.recommendation.score import Score


# 기본 경로: backend repo 루트의 scoring_spec.yaml
# src/domain/recommendation -> src/domain -> src -> data-engine -> backend
_FALLBACK_DEFAULT = Path(__file__).resolve().parents[4] / "scoring_spec.yaml"
_QTZ_2 = Decimal("0.01")


class ScoringPolicy:
    """spec 파일로부터 로드된 점수 정책 (immutable after load)."""

    def __init__(self, spec: dict[str, Any]):
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
    def _validate_invariants(spec: dict[str, Any]) -> None:
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

        # PR 3b: negative_policy ∈ {zero, veto}
        ai_negative_policy = (axes.get("ai") or {}).get("negative_policy", "zero")
        if ai_negative_policy not in {"zero", "veto"}:
            raise SpecValidationError(
                f"axes.ai.negative_policy must be 'zero' or 'veto'; got '{ai_negative_policy}'"
            )

        # PR 5: macro_gates.vix (선택 — 미정의 시 gate 비활성)
        vix_gate = (spec.get("macro_gates") or {}).get("vix") or {}
        if vix_gate:
            # enabled=True 일 때 threshold 필수 — 누락 시 runtime KeyError 방지
            enabled = vix_gate.get("enabled", True)
            threshold = vix_gate.get("threshold")
            if enabled and threshold is None:
                raise SpecValidationError(
                    "macro_gates.vix.threshold required when enabled=true"
                )
            if threshold is not None:
                try:
                    if Decimal(str(threshold)) <= 0:
                        raise SpecValidationError(
                            f"macro_gates.vix.threshold must be > 0; got {threshold}"
                        )
                except (ValueError, TypeError) as e:
                    raise SpecValidationError(
                        f"macro_gates.vix.threshold must be numeric; got {threshold!r}"
                    ) from e
            missing_policy = vix_gate.get("missing_policy", "skip")
            if missing_policy not in {"skip", "block"}:
                raise SpecValidationError(
                    f"macro_gates.vix.missing_policy must be 'skip' or 'block'; got '{missing_policy}'"
                )

    # ── 속성 ─────────────────────────────────────────
    @property
    def formula_version(self) -> str:
        return self._spec["formula_version"]

    @property
    def composite_max(self) -> Decimal:
        return Decimal(str(self._spec["composite_max"]))

    @property
    def axes(self) -> dict[str, dict[str, Any]]:
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

    # PR 3b: negative AI veto 게이트
    def is_negative_veto_enabled(self) -> bool:
        """spec.axes.ai.negative_policy == 'veto' 여부. False (zero) 면 PR 1 동작 보존."""
        return (self.axes.get("ai") or {}).get("negative_policy") == "veto"

    # PR 5: VIX 거시 gate
    @property
    def vix_gate(self) -> dict[str, Any] | None:
        """spec.macro_gates.vix dict 반환. 미정의 또는 enabled=False 면 None."""
        gate = (self._spec.get("macro_gates") or {}).get("vix") or {}
        if not gate or gate.get("enabled") is False:
            return None
        return gate

    def is_vix_gate_enabled(self) -> bool:
        """VIX gate 활성 여부."""
        return self.vix_gate is not None

    @property
    def vix_threshold(self) -> Decimal | None:
        """VIX > threshold 시 발동. gate 미활성 시 None."""
        gate = self.vix_gate
        if not gate:
            return None
        t = gate.get("threshold")
        return Decimal(str(t)) if t is not None else None

    def should_block_on_vix(self, vix_value: float | Decimal | None) -> bool:
        """VIX 값 → gate 발동 여부 (True 면 추천 전체 차단).

        - gate 미활성: 항상 False
        - vix_value=None: missing_policy 에 따름 ('skip' 정상 진행 / 'block' 차단)
        - 임계 초과: True
        - 비정상 값 (NaN, negative, > 200): None 으로 간주 → missing_policy 적용
        """
        threshold = self.vix_threshold  # invariant 가 enabled=True 시 None 보장
        if threshold is None:
            return False
        gate = self.vix_gate  # invariant 거친 dict
        if vix_value is None:
            return gate.get("missing_policy", "skip") == "block"
        # PR 5 review #7: sanity range 검증 (yfinance 비정상 값 방어)
        try:
            v = Decimal(str(vix_value))
        except Exception:
            return gate.get("missing_policy", "skip") == "block"
        # NaN 은 비교 자체가 InvalidOperation — 먼저 차단
        if v.is_nan() or not (Decimal("0") < v < Decimal("200")):
            return gate.get("missing_policy", "skip") == "block"
        return v > threshold

    # ── Private: clipped normalized → AI score (공통 tail) ──────
    def _score_from_clipped_normalized(self, normalized: Decimal) -> Decimal:
        """0~1 normalized probability → AI score (0~max_ai).

        공통 tail: 0.5 미만 → 0 (현행 보존, PR 3에서 veto). 이상 → (n-0.5)*2*max, quantize HALF_UP.
        """
        clipped = max(Decimal("0"), min(Decimal("1"), normalized))
        if clipped < Decimal("0.5"):
            return Decimal("0")
        ai_max = Decimal(str(self.axes["ai"]["max"]))
        return ((clipped - Decimal("0.5")) * Decimal("2") * ai_max).quantize(_QTZ_2, rounding=ROUND_HALF_UP)

    # ── Public: raw % → AI 점수 변환 (리뷰 C2) ──────────
    def normalize_rise_pct_to_score(self, rise_pct: Decimal | None) -> Decimal:
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
        return self._score_from_clipped_normalized(normalized)

    # ── Public: tech indicators → tech score ─────────────
    def tech_score_from_indicators(self, indicators: dict[str, Any] | None) -> Decimal:
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

    def count_tech_signals(self, indicators: dict[str, Any] | None) -> int:
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
    def ai_score_from_normalized(self, normalized: Decimal | None) -> Decimal:
        """이미 normalized 된 rise_probability (0~1) → AI score (0~max_ai).

        sync_service 가 이미 normalized 한 값을 가지고 있는 경우 사용.
        raw rise_pct 가 있다면 normalize_rise_pct_to_score() 사용.
        """
        if normalized is None:
            return Decimal("0")
        return self._score_from_clipped_normalized(Decimal(str(normalized)))

    # ── Public: sentiment (-1~+1) → sentiment score ─────
    def sentiment_score_from_raw(self, sentiment: Decimal | None) -> Decimal:
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
        veto_reasons: tuple[str, ...] = (),
        warnings: tuple[str, ...] = (),
    ) -> Score:
        """이미 normalized 된 component score 들로부터 Score 계산.

        PR 3b (2026-05-22): veto_reasons 비어있지 않고 spec.negative_policy=='veto' 면
        composite_score=0, grade='D', label='NONE' 강제. caller 가 raw 신호 부호 판단해서 전달.

        missing 시 0점 처리 (재분배 없음, PR 4 에서 검토).
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

        # PR 3b: veto 발동 시 composite 강제 0. spec 의 negative_policy 가 veto 인 경우만 실행 (rollback safety).
        veto_active = bool(veto_reasons) and self.is_negative_veto_enabled()
        if veto_active:
            composite = Decimal("0")

        confidence = (
            (composite / composite_max).quantize(Decimal("0.001"), rounding=ROUND_HALF_UP)
            if composite_max > 0
            else Decimal("0")
        )

        if veto_active:
            grade = "D"
            label = "NONE"
        else:
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

        # spec.negative_policy=='zero' 일 때는 caller 가 veto_reasons 전달해도 무시 (rollback safety).
        # 즉 zero 모드에선 항상 veto_reasons=() 반환.
        final_veto = veto_reasons if self.is_negative_veto_enabled() else ()

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
            veto_reasons=final_veto,
            warnings=warnings,
        )

    # score_from_raw_signals 헬퍼는 cleanup PR (2026-05-22) 에서 제거됨.
    # prod path (sync_service / buy_criteria) 는 자체 veto detection 후 compose_components 직접 호출 —
    # 호출처가 raw 신호의 부호를 알아야 일관 처리 가능. test 도 compose_components 직접 사용.

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

    # ── Public: label_metadata (PR 2, label/emoji SSoT) ──
    def label_metadata(self, key: str) -> dict[str, str]:
        """recommendation_labels[key] 의 label/emoji 를 반환. unknown key 는 NONE 매핑."""
        labels = self._spec.get("recommendation_labels") or {}
        cfg = labels.get(key) or labels.get("NONE") or {}
        return {
            "label": cfg.get("label", "추천 없음"),
            "emoji": cfg.get("emoji", "⚪"),
        }
