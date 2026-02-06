#!/bin/bash

# ============================================================
# Kafka Topic 생성 스크립트 (Local)
# ============================================================
# 사용법: bash scripts/setup/create-kafka-topics.sh
#
# 토픽 네이밍 규칙:
#   {prefix}.{도메인}.{상태}
#   예: quantiq.backtest.request, quantiq.backtest.completed
# ============================================================

set -e

KAFKA_BROKER="localhost:9092"
PARTITIONS=1
REPLICATION_FACTOR=1

echo "================================"
echo "Kafka Topics 생성 시작"
echo "================================"
echo "Broker: $KAFKA_BROKER"
echo ""

# ============================================================
# 토픽 정의 (토픽명|설명)
# ============================================================

declare -a topic_definitions=(
    # ----------------------------------------------------------
    # 📊 분석 (Analysis)
    # ----------------------------------------------------------
    "quantiq.analysis.request|Spring → Data Engine: 종합 분석 요청"
    "quantiq.analysis.completed|Data Engine → Spring: 종합 분석 완료"
    "analysis.technical.request|Spring → Data Engine: 기술적 분석 요청 (SMA, RSI, MACD)"
    "analysis.sentiment.request|Spring → Data Engine: 감성 분석 요청"
    "analysis.combined.request|Spring → Data Engine: 복합 분석 요청"

    # ----------------------------------------------------------
    # 📈 경제 지표 (Economic Data)
    # ----------------------------------------------------------
    "economic.data.update.request|Spring → Data Engine: 경제 지표 업데이트 요청 (FRED)"
    "economic.data.updated|Data Engine → Spring: 경제 지표 업데이트 완료"

    # ----------------------------------------------------------
    # 🧪 백테스트 (Backtest) - SCRUM-162
    # EventSchema.kt / schema.py 참조
    # ----------------------------------------------------------
    "quantiq.backtest.request|Spring → Data Engine: 백테스트 실행 요청"
    "quantiq.backtest.completed|Data Engine → Spring: 백테스트 완료 (Kotlin EventSchema)"
    "quantiq.backtest.failed|Data Engine → Spring: 백테스트 실패 (Kotlin EventSchema)"
    "backtest.results.completed|Data Engine → Spring: 백테스트 완료 (Python EventTopics)"
    "backtest.results.failed|Data Engine → Spring: 백테스트 실패 (Python EventTopics)"

    # ----------------------------------------------------------
    # 📡 실시간 신호 (Trading Signals)
    # ----------------------------------------------------------
    "signal.strategy.generated|Data Engine → Spring: 전략 신호 생성 (BUY/SELL/HOLD)"
)

# ============================================================
# 토픽 생성
# ============================================================

# 토픽명만 추출
topics=()
for definition in "${topic_definitions[@]}"; do
    topic="${definition%%|*}"
    topics+=("$topic")
done

# Docker compose를 통해 실행하는 경우
if command -v docker &> /dev/null; then
    echo "Docker를 통해 Kafka 토픽 생성..."
    echo ""

    # 토픽 설명 출력 및 생성
    echo "📋 토픽 목록:"
    echo "------------------------------------------------------------"
    for definition in "${topic_definitions[@]}"; do
        topic="${definition%%|*}"
        description="${definition#*|}"
        printf "  %-35s %s\n" "$topic" "# $description"
    done
    echo "------------------------------------------------------------"
    echo ""

    for topic in "${topics[@]}"; do
        echo "🔧 토픽 생성: $topic"

        docker exec quantiq-kafka kafka-topics \
            --bootstrap-server localhost:9092 \
            --create \
            --if-not-exists \
            --topic "$topic" \
            --partitions $PARTITIONS \
            --replication-factor $REPLICATION_FACTOR 2>/dev/null || true
    done

    echo ""
    echo "✅ 생성된 토픽 목록:"
    docker exec quantiq-kafka kafka-topics \
        --bootstrap-server localhost:9092 \
        --list
else
    echo "❌ Docker를 찾을 수 없습니다."
    echo "Docker를 설치하거나 kafka-topics.sh를 직접 실행하세요."
    exit 1
fi

echo ""
echo "================================"
echo "✅ Kafka Topics 생성 완료"
echo "================================"
echo ""
echo "📚 토픽 사용 가이드:"
echo "  - Spring Producer: KafkaTemplate.send(\"토픽명\", 메시지)"
echo "  - Python Consumer: AIOKafkaConsumer(\"토픽명\")"
echo "  - 문서: docs/kafka-architecture-detailed.md"
