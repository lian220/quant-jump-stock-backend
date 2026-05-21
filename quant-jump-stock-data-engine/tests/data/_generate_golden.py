"""golden_v1_0_0.csv 생성 스크립트.

scoring_spec.yaml spec_version 1.0.0 에 대한 frozen 동작을 캡처.
실제 ScoringPolicy.compose_components 를 호출하여 expected 값을 산출.

사용법:
    cd quant-jump-stock-data-engine
    poetry run python tests/data/_generate_golden.py

재생성이 필요한 경우 (spec 변경 등):
    1. scoring_spec.yaml 변경
    2. 이 스크립트를 수정하여 변경 사항 반영
    3. 재실행 후 golden_v1_0_0.csv 갱신
    4. PR 에서 명시적으로 CSV diff 확인
"""
import csv
import sys
from decimal import Decimal
from pathlib import Path

# src 경로 추가
_REPO_ROOT = Path(__file__).resolve().parents[4]
_SRC = Path(__file__).resolve().parents[2] / "src"
sys.path.insert(0, str(_SRC))

from domain.recommendation.scoring_policy import ScoringPolicy

# ── 케이스 정의 ─────────────────────────────────────────────────────────────
# (case_id, scenario, ai_score, tech_score, sentiment_score,
#  has_ai, has_tech, has_sentiment, tech_signals)
#
# spec:
#   composite = 0.3*ai + 0.4*tech + 0.3*sent (missing → 0)
#   composite_max = 7.4
#   confidence = composite / 7.4
#   Grade: S>=6.0, A>=4.5, B>=3.0, C>=1.5, D>=0.0
#   Label: STRONG  conf>=0.65 AND signals>=3
#          RECOMMEND conf>=0.45 AND signals>=2
#          WATCH     conf>=0.30 AND signals>=1
#          NONE       otherwise

CASES = [
    # ══════════════════════════════════════════════════════════
    # A. Sanity / Boundaries (10 cases)
    # ══════════════════════════════════════════════════════════

    # A1 - 모두 최대값: 0.3*10 + 0.4*3.5 + 0.3*10 = 3.0 + 1.4 + 3.0 = 7.4 → S / STRONG
    (1, "all_max", "10.0", "3.5", "10.0", True, True, True, 3),

    # A2 - 모두 0: composite=0.0 → D / NONE
    (2, "all_zero", "0", "0", "0", True, True, True, 0),

    # A3 - S/A 경계: composite exactly 6.0 → grade=S
    # 0.3*ai + 0.4*3.5 + 0.3*0 = 0.3*ai + 1.4 = 6.0 → ai=15.333... (초과, 불가)
    # 대신: ai=10, sent=x: 3.0 + 0.4*3.5 + 0.3*x = 6.0 → 3.0+1.4+0.3x=6.0 → x=5.2
    # tech=0: 3.0 + 0 + 0.3*10 = 6.0 → ai=10, tech=0, sent=10: 3.0+0+3.0=6.0 ✓
    (3, "boundary_s_exactly_6", "10.0", "0", "10.0", True, True, True, 0),

    # A4 - A/B 경계: composite exactly 4.5
    # ai=10, tech=0, sent=x: 3.0 + 0 + 0.3x = 4.5 → x=5.0
    (4, "boundary_a_exactly_4p5", "10.0", "0", "5.0", True, True, True, 0),

    # A5 - B/C 경계: composite exactly 3.0
    # ai=0, tech=3.5, sent=x: 0 + 1.4 + 0.3x = 3.0 → x=16/3... 불가
    # ai=5, tech=0, sent=5: 1.5 + 0 + 1.5 = 3.0 ✓
    (5, "boundary_b_exactly_3", "5.0", "0", "5.0", True, True, True, 0),

    # A6 - C/D 경계: composite exactly 1.5
    # ai=5, tech=0, sent=0: 0.3*5 = 1.5 ✓
    (6, "boundary_c_exactly_1p5", "5.0", "0", "0", True, True, True, 0),

    # A7 - S 바로 아래: 5.99 → grade=A
    # ai=10, tech=0, sent=x: 3.0 + 0.3x = 5.99 → x=9.9666...
    # 정수로: ai=9, tech=3.5, sent=5: 2.7 + 1.4 + 1.5 = 5.6 → A
    # 정확히 5.99가 되게: ai=10.0, sent=9.967 (Decimal), tech=0 → 3.0+0.3*9.967=5.99
    # 간단히 round number: ai=9.97, tech=0, sent=10: 2.991+0+3.0=5.991 ≈ 5.99
    # tech=0이면 signal=0이므로 NONE. ai=10, tech=0, sent=(5.99-3.0)/0.3=9.9667 → 9.97
    (7, "just_below_s_5p99", "10.0", "0", "9.97", True, True, True, 0),

    # A8 - A 바로 아래: 4.49 → grade=B
    # ai=10, tech=0, sent=x: 3.0+0.3x=4.49 → x=4.97
    (8, "just_below_a_4p49", "10.0", "0", "4.97", True, True, True, 0),

    # A9 - B 바로 아래: 2.99 → grade=C
    # ai=0, tech=0, sent=x: 0.3x=2.99 → x=9.9667
    # 또는 ai=5, sent=4.93: 1.5+0+1.479=2.979 ≈ 2.98
    # ai=4.97, tech=0, sent=5: 1.491+0+1.5=2.991 → C
    # 정확히: ai=0.0, tech=0, sent=9.97: 0+0+0.3*9.97=2.991 → C (2.99 미만은 안됨)
    # 실제로 2.99가 되려면: 0.3*ai=2.99 → ai=9.9666... 이거나 혼합
    # ai=9.97, tech=0, sent=0: 0.3*9.97=2.991 ≈ 2.99 (반올림 후 2.99 → C)
    (9, "just_below_b_2p99", "9.97", "0", "0", True, True, True, 0),

    # A10 - C 바로 아래: 1.49 → grade=D
    # ai=4.97, tech=0, sent=0: 0.3*4.97=1.491 → C (1.49 아래 안됨)
    # ai=0, tech=3.5, sent=0: 0+0.4*3.5=1.4 → D ✓ (tech_max=3.5 이므로 정확히 1.4)
    # → 실제로 1.4는 D (1.5 미만). 근데 just_below_c_1p49라는 이름으로 1.4면 충분.
    (10, "just_below_c_1p4_grade_d", "0", "3.5", "0", True, True, True, 0),

    # ══════════════════════════════════════════════════════════
    # B. Missing Axes (10 cases)
    # ══════════════════════════════════════════════════════════

    # B1 - 세 축 모두 missing
    (11, "all_missing", "0", "0", "0", False, False, False, 0),

    # B2 - AI 만 missing (tech=3.5, sent=10)
    # composite = 0 + 0.4*3.5 + 0.3*10 = 1.4 + 3.0 = 4.4 → A
    # confidence=4.4/7.4≈0.595 → signals=3: STRONG? conf=0.595<0.65 → not STRONG
    # conf=0.595>=0.45 AND signals=3>=2 → RECOMMEND
    (12, "ai_missing_tech_sent_full", "0", "3.5", "10.0", False, True, True, 3),

    # B3 - Tech 만 missing (ai=10, sent=10)
    # composite = 0.3*10 + 0 + 0.3*10 = 6.0 → S
    # confidence=6.0/7.4≈0.811 → signals=0: STRONG(need 3)? No. RECOMMEND(need 2)? No. WATCH(need 1)? No → NONE
    (13, "tech_missing_ai_sent_full", "10.0", "0", "10.0", True, False, True, 0),

    # B4 - Sentiment 만 missing (ai=10, tech=3.5)
    # composite = 0.3*10 + 0.4*3.5 + 0 = 3.0 + 1.4 = 4.4 → A
    # confidence=4.4/7.4≈0.595 → signals=3: conf<0.65 → not STRONG; conf>=0.45 AND sig>=2 → RECOMMEND
    (14, "sent_missing_ai_tech_full", "10.0", "3.5", "0", True, True, False, 3),

    # B5 - AI+Tech missing (sent=10)
    # composite = 0 + 0 + 0.3*10 = 3.0 → B
    # confidence=3.0/7.4≈0.405 → signals=0 → NONE (need sig>=1 for WATCH)
    (15, "ai_tech_missing_sent_full", "0", "0", "10.0", False, False, True, 0),

    # B6 - AI+Sentiment missing (tech=3.5)
    # composite = 0 + 0.4*3.5 + 0 = 1.4 → D
    # confidence=1.4/7.4≈0.189 → NONE
    (16, "ai_sent_missing_tech_full", "0", "3.5", "0", False, True, False, 3),

    # B7 - Tech+Sentiment missing (ai=10)
    # composite = 0.3*10 + 0 + 0 = 3.0 → B
    # confidence=3.0/7.4≈0.405 → signals=0 → NONE
    (17, "tech_sent_missing_ai_full", "10.0", "0", "0", True, False, False, 0),

    # B8 - AI missing, tech=2.0, sent=5.0
    # composite = 0 + 0.4*2.0 + 0.3*5.0 = 0.8 + 1.5 = 2.3 → C
    (18, "ai_missing_tech_med_sent_med", "0", "2.0", "5.0", False, True, True, 2),

    # B9 - Sentiment missing, ai=5, tech=2.5
    # composite = 0.3*5 + 0.4*2.5 + 0 = 1.5 + 1.0 = 2.5 → C
    (19, "sent_missing_ai_med_tech_med", "5.0", "2.5", "0", True, True, False, 2),

    # B10 - Tech missing, ai=3, sent=3
    # composite = 0.3*3 + 0 + 0.3*3 = 0.9 + 0.9 = 1.8 → C
    (20, "tech_missing_ai_low_sent_low", "3.0", "0", "3.0", True, False, True, 0),

    # ══════════════════════════════════════════════════════════
    # C. Tech signal count variations (8 cases)
    # ══════════════════════════════════════════════════════════

    # C1 - 0 signals, label NONE
    # ai=8, tech=0 (score but 0 signals?), sent=8
    # NOTE: tech_score can be 0 with has_tech=True (e.g., golden_cross only contributes 1.5 but signals=0 means no cross etc)
    # Actually: tech_signals is passed separately. We can set tech_score>0 but signals=0 (edge: shouldn't happen normally but valid in API)
    # Let's use a realistic case: ai=8, tech=0, sent=8, signals=0
    # composite=0.3*8+0+0.3*8=4.8 → A
    # confidence=4.8/7.4≈0.649 → signals=0: STRONG need 3→No, RECOMMEND need 2→No, WATCH need 1→No → NONE
    (21, "tech_0signals_high_ai_sent", "8.0", "0", "8.0", True, True, True, 0),

    # C2 - 1 signal + low confidence → WATCH
    # Need: conf>=0.30 AND signals>=1 (and NOT conf>=0.45)
    # composite ≥ 0.3*7.4=2.22, composite < 0.45*7.4=3.33
    # ai=4, tech=1.0, sent=4: 1.2+0.4+1.2=2.8; conf=2.8/7.4≈0.378; signals=1 → WATCH ✓
    (22, "tech_1signal_low_conf_watch", "4.0", "1.0", "4.0", True, True, True, 1),

    # C3 - 2 signals + mid confidence → RECOMMEND
    # Need: conf>=0.45 AND signals>=2 (and NOT conf>=0.65)
    # composite ≥ 0.45*7.4=3.33, composite < 0.65*7.4=4.81
    # ai=7, tech=2.0, sent=5: 2.1+0.8+1.5=4.4; conf=4.4/7.4≈0.595; signals=2 → RECOMMEND ✓
    (23, "tech_2signals_mid_conf_recommend", "7.0", "2.0", "5.0", True, True, True, 2),

    # C4 - 3 signals + high confidence → STRONG
    # Need: conf>=0.65 AND signals>=3
    # composite ≥ 0.65*7.4=4.81
    # ai=10, tech=3.5, sent=5: 3.0+1.4+1.5=5.9; conf=5.9/7.4≈0.797; signals=3 → STRONG ✓
    (24, "tech_3signals_high_conf_strong", "10.0", "3.5", "5.0", True, True, True, 3),

    # C5 - confidence exactly at WATCH boundary (0.30)
    # composite = 0.30 * 7.4 = 2.22
    # Need signals>=1. ai=0, tech=2.0, sent=4.4: 0+0.8+1.32=2.12 ≈ not quite
    # ai=0.4, tech=2.0, sent=4.4: 0.12+0.8+1.32=2.24... Let's do ai=0.4, tech=2.0, sent=4.27: 0.12+0.8+1.281=2.201
    # Exact: 0.3*ai+0.4*tech+0.3*sent=2.22
    # tech=2.0→0.8; need 1.42 from ai+sent. ai=0, sent=4.7333: 0+0.3*4.73=1.42; let's say ai=0, tech=2.0, sent=4.73, sig=1
    (25, "conf_exactly_0p30_boundary_watch", "0", "2.0", "4.73", True, True, True, 1),

    # C6 - confidence exactly at RECOMMEND boundary (0.45)
    # composite = 0.45*7.4 = 3.33
    # tech=2.0→0.8; 0.3*ai+0.3*sent=2.53; ai=0, sent=8.43: 0+0.3*8.43=2.529≈2.53
    # ai=0, tech=2.0, sent=8.43, signals=2
    (26, "conf_exactly_0p45_boundary_recommend", "0", "2.0", "8.43", True, True, True, 2),

    # C7 - confidence exactly at STRONG boundary (0.65)
    # composite = 0.65*7.4 = 4.81
    # tech=3.5→1.4; 0.3*(ai+sent)=3.41; ai=5, sent=6.367: 1.5+1.4+1.91=4.81
    # ai=5.0, tech=3.5, sent=6.37, signals=3
    (27, "conf_exactly_0p65_boundary_strong", "5.0", "3.5", "6.37", True, True, True, 3),

    # C8 - 3 signals but low confidence → NONE (signals>=3 but conf<0.30)
    # composite < 0.30*7.4 = 2.22
    # ai=2, tech=1.0, sent=2: 0.6+0.4+0.6=1.6; conf=1.6/7.4≈0.216; signals=3 → NONE ✓
    (28, "tech_3signals_low_conf_none", "2.0", "1.0", "2.0", True, True, True, 3),

    # ══════════════════════════════════════════════════════════
    # D. Realistic production cases (12 cases)
    # ══════════════════════════════════════════════════════════

    # D1 - 전형적 S 등급 추천
    # ai=9.5, tech=3.5, sent=8.0: 2.85+1.4+2.4=6.65 → S
    (29, "realistic_s_grade_high_all", "9.5", "3.5", "8.0", True, True, True, 3),

    # D2 - 전형적 A 등급 추천
    # ai=7.5, tech=2.5, sent=6.0: 2.25+1.0+1.8=5.05 → A
    (30, "realistic_a_grade_balanced", "7.5", "2.5", "6.0", True, True, True, 2),

    # D3 - A 등급이지만 signals 부족 → WATCH
    # ai=8.0, tech=1.0, sent=6.0: 2.4+0.4+1.8=4.6 → A; conf=4.6/7.4≈0.622; signals=1 → WATCH
    (31, "realistic_a_grade_low_signals_watch", "8.0", "1.0", "6.0", True, True, True, 1),

    # D4 - B 등급 추천 (RECOMMEND)
    # ai=6.0, tech=2.0, sent=5.0: 1.8+0.8+1.5=4.1 → B? No, B>=3.0, A>=4.5; 4.1 → B
    # conf=4.1/7.4≈0.554; signals=2 → RECOMMEND ✓
    (32, "realistic_b_grade_recommend", "6.0", "2.0", "5.0", True, True, True, 2),

    # D5 - B 등급 WATCH
    # ai=5.0, tech=1.0, sent=5.0: 1.5+0.4+1.5=3.4 → B
    # conf=3.4/7.4≈0.459; signals=1 → WATCH ✓
    (33, "realistic_b_grade_watch", "5.0", "1.0", "5.0", True, True, True, 1),

    # D6 - C 등급 (NONE - signals 부족)
    # ai=3.0, tech=0, sent=3.0: 0.9+0+0.9=1.8 → C
    # conf=1.8/7.4≈0.243; signals=0 → NONE
    (34, "realistic_c_grade_none", "3.0", "0", "3.0", True, True, True, 0),

    # D7 - D 등급
    # ai=1.0, tech=0, sent=1.0: 0.3+0+0.3=0.6 → D
    (35, "realistic_d_grade_low", "1.0", "0", "1.0", True, True, True, 0),

    # D8 - sentiment=0 (유효한 0, missing 아님)
    # ai=9.0, tech=3.5, sent=0 (has_sentiment=True but 0)
    # composite=0.3*9+0.4*3.5+0=2.7+1.4=4.1 → B
    (36, "sentiment_zero_not_missing", "9.0", "3.5", "0", True, True, True, 3),

    # D9 - 기술 지표만 강한 케이스
    # ai=2.0, tech=3.5, sent=2.0: 0.6+1.4+0.6=2.6 → C
    # conf=2.6/7.4≈0.351; signals=3 → WATCH ✓
    (37, "tech_dominant_c_grade_watch", "2.0", "3.5", "2.0", True, True, True, 3),

    # D10 - 감성 지표만 강한 케이스
    # ai=2.0, tech=0, sent=9.0: 0.6+0+2.7=3.3 → B
    # conf=3.3/7.4≈0.446; signals=0 → NONE
    (38, "sentiment_dominant_b_grade_none", "2.0", "0", "9.0", True, True, True, 0),

    # D11 - 균형잡힌 중간 케이스
    # ai=5.0, tech=2.0, sent=5.0: 1.5+0.8+1.5=3.8 → B
    # conf=3.8/7.4≈0.514; signals=2 → RECOMMEND ✓
    (39, "balanced_mid_b_recommend", "5.0", "2.0", "5.0", True, True, True, 2),

    # D12 - 높은 AI 낮은 sentiment
    # ai=10.0, tech=2.0, sent=1.0: 3.0+0.8+0.3=4.1 → B
    # conf=4.1/7.4≈0.554; signals=2 → RECOMMEND ✓
    (40, "high_ai_low_sent_b_recommend", "10.0", "2.0", "1.0", True, True, True, 2),

    # ══════════════════════════════════════════════════════════
    # E. Edge: negative/zero paths & rounding (10 cases)
    # ══════════════════════════════════════════════════════════

    # E1 - ai_score=0 (음수 rise_pct 경로 결과, has_ai=True)
    # ai=0 (explicit zero), tech=3.5, sent=8.0
    # composite=0+1.4+2.4=3.8 → B; conf=3.8/7.4≈0.514; signals=3 → STRONG? conf<0.65→No; RECOMMEND✓
    (41, "ai_zero_explicit_has_ai_true", "0", "3.5", "8.0", True, True, True, 3),

    # E2 - Decimal rounding: 값이 .005 끝나는 경우 ROUND_HALF_UP 검증
    # 0.3*ai+0.4*tech+0.3*sent = x.xx5 형태
    # x.005: 0.3*1+0.4*0+0.3*0 = 0.3 (문제없음)
    # composite ending in .005: need special inputs
    # 0.3*5 + 0.4*1.5 + 0.3*0 = 1.5 + 0.6 = 2.1 (no .005)
    # 0.3*0 + 0.4*1.25 + 0.3*0 = 0.5 (no .005)
    # 0.3*1.5 + 0.4*0.5 + 0.3*2.5 = 0.45+0.2+0.75=1.4 (no .005)
    # Try: ai=1.0, tech=1.25, sent=1.0: 0.3+0.5+0.3=1.1 (no)
    # ai=0.5, tech=1.0, sent=0.5: 0.15+0.4+0.15=0.7 (no)
    # Composite .005: 0.3*a+0.4*b+0.3*c=n.005
    # Multiply by 1000: 300a+400b+300c = n*1000+5
    # 300a+400b+300c ≡ 5 (mod 10) → 400b ≡ 5 (mod 10) → 0*b ≡ 5 (mod 10) — impossible since 400b always ends in 0.
    # So composite sum can never end exactly in .005 with these weights (0.3, 0.4, 0.3).
    # Instead, test a case where intermediate Decimal arithmetic could cause banker's rounding vs ROUND_HALF_UP.
    # Specifically: composite = 2.225 → ROUND_HALF_UP → 2.23 vs ROUND_HALF_EVEN → 2.22
    # 0.3*ai+0.4*tech+0.3*sent = 2.225
    # tech=0: 0.3*(ai+sent)=2.225 → ai+sent=7.41666... (impossible exact)
    # tech=1.0→0.4: 0.3*(ai+sent)=1.825 → ai+sent=6.0833... (not clean)
    # tech=0.125→0.05: 0.3*(ai+sent)=2.175 → ai+sent=7.25 → ai=3.625, sent=3.625
    # 0.3*3.625+0.4*0.125+0.3*3.625 = 1.0875+0.05+1.0875=2.225 ✓
    # But tech=0.125 is not a realistic value (max 3.5, components are 1.5, 1.0, 1.0)
    # Use it as a direct decimal score input for the rounding test:
    (42, "rounding_half_up_2p225", "3.625", "0.125", "3.625", True, True, True, 1),

    # E3 - B/C 경계 WATCH: composite=3.0, confidence=0.405, signals=1 → WATCH
    # 정확히 3.0: ai=5, tech=0, sent=5: 0.3*5+0+0.3*5=3.0
    (43, "boundary_b_composite_3_watch", "5.0", "0", "5.0", True, True, True, 1),

    # E4 - grade=C but label=WATCH
    # composite = 1.6, conf=1.6/7.4≈0.216 → NONE (conf<0.30)
    # Need conf>=0.30 for WATCH. C grade min=1.5, B min=3.0.
    # composite=2.22=0.30*7.4: ai=3, tech=0, sent=4.4: 0.9+0+1.32=2.22; conf=0.30; sig=1 → WATCH
    (44, "c_grade_conf_0p30_watch", "3.0", "0", "4.4", True, True, True, 1),

    # E5 - 모든 축 최솟값 (0이 아닌 아주 작은 값)
    # ai=0.01, tech=0.01, sent=0.01: 0.003+0.004+0.003=0.01 → D
    (45, "all_min_nonzero", "0.01", "0.01", "0.01", True, True, True, 0),

    # E6 - tech_score > max (이론상 불가하지만 compose_components는 raw 값 그대로 사용)
    # ai=0, tech=4.0, sent=0: 0+0.4*4.0+0=1.6 → C (not B since 1.6<3.0)
    # Wait: 1.6 >= 1.5 → C ✓
    (46, "tech_over_max_input", "0", "4.0", "0", True, True, True, 3),

    # E7 - 종합점수 S이지만 signals=0이라 NONE (실전에서 드문 케이스)
    # ai=10, tech=0 (missing: has_tech=False), sent=10
    # composite=3.0+0+3.0=6.0 → S; conf=6.0/7.4≈0.811; signals=0 → NONE
    (47, "s_grade_no_signals_none", "10.0", "0", "10.0", True, False, True, 0),

    # E8 - Decimal precision: ai=3.333, tech=3.333, sent=3.333
    # 0.3*3.333+0.4*3.333+0.3*3.333 = 3.333*(0.3+0.4+0.3) = 3.333*1.0 = 3.333 → B
    (48, "repeating_decimal_3p333", "3.333", "3.333", "3.333", True, True, True, 2),

    # E9 - composite 정확히 7.4 (max): ai=10, tech=3.5, sent=10 (중복 A1이지만 다른 이름)
    # 실제로 A1과 동일하므로, 여기서는 다른 조합으로 동일 composite:
    # ai=10, tech=3.5, sent=10 = 7.4 (A1과 같으므로 다른 조합)
    # tech max contribution=1.4, ai+sent max=6.0. Can we get 7.4 another way?
    # 0.3*ai+0.4*3.5+0.3*sent=7.4 → 0.3*(ai+sent)=6.0 → ai+sent=20 (max가 ai=10,sent=10)
    # Only way is ai=10, tech=3.5, sent=10. A1과 완전 동일.
    # 대신 ai=10의 경우: 0.3*10=3.0 고정, tech=0, 0.3*sent=4.4→sent=14.67>max
    # 다른 접근: all_max에서 tech만 조금 낮춘 경우
    # ai=10.0, tech=3.0, sent=10.0: 3.0+1.2+3.0=7.2 → S
    # conf=7.2/7.4≈0.973; sig=3 → STRONG ✓
    (49, "near_max_7p2_strong", "10.0", "3.0", "10.0", True, True, True, 3),

    # E10 - composite=0, has_ai=True has_tech=True has_sent=True (모두 0점)
    # Separate from all_zero (which has same values but different semantic: all have=True here too)
    # Let's use all_zero with has=True but ai_score=0 from negative path:
    # ai=0 (negative AI), tech=0 (no signals), sent=0 (negative sentiment)
    # This IS A1 again. Let's make it distinct: tech_signals=1 but tech_score=0 (unusual edge)
    # composite=0; conf=0; signals=1 → NONE (conf<0.30)
    (50, "all_zero_scores_1signal_none", "0", "0", "0", True, True, True, 1),
]


def main():
    out_path = Path(__file__).parent / "golden_v1_0_0.csv"
    policy = ScoringPolicy.load_default()

    rows = []
    for tup in CASES:
        (cid, scenario, ai, tech, sent, has_ai, has_tech, has_sent, signals) = tup

        score = policy.compose_components(
            ai_score=Decimal(ai),
            tech_score=Decimal(tech),
            sentiment_score=Decimal(sent),
            has_ai=has_ai,
            has_tech=has_tech,
            has_sentiment=has_sent,
            tech_signal_count=signals,
        )

        rows.append({
            "case_id": cid,
            "scenario": scenario,
            "ai_score": ai,
            "tech_score": tech,
            "sentiment_score": sent,
            "has_ai": has_ai,
            "has_tech": has_tech,
            "has_sentiment": has_sent,
            "tech_signals": signals,
            "expected_composite": str(score.composite_score),
            "expected_grade": score.grade,
            "expected_label": score.recommendation_label,
        })

    fieldnames = [
        "case_id", "scenario",
        "ai_score", "tech_score", "sentiment_score",
        "has_ai", "has_tech", "has_sentiment", "tech_signals",
        "expected_composite", "expected_grade", "expected_label",
    ]

    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    print(f"Generated {len(rows)} cases → {out_path}")

    # 샘플 출력 (boundary 케이스 확인)
    print("\n--- Boundary cases preview ---")
    boundary_ids = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 42}
    for r in rows:
        if r["case_id"] in boundary_ids:
            print(
                f"  [{r['case_id']:2d}] {r['scenario']:<40s} "
                f"composite={r['expected_composite']:>5s}  "
                f"grade={r['expected_grade']}  label={r['expected_label']}"
            )

    # Grade 분포 확인
    from collections import Counter
    grade_dist = Counter(r["expected_grade"] for r in rows)
    label_dist = Counter(r["expected_label"] for r in rows)
    print(f"\nGrade distribution: {dict(grade_dist)}")
    print(f"Label distribution: {dict(label_dist)}")


if __name__ == "__main__":
    main()
