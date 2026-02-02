#!/bin/bash

# Kafka Topic 생성 스크립트 (Local)
# 사용법: bash scripts/create-kafka-topics.sh

set -e

KAFKA_BROKER="localhost:9092"
PARTITIONS=1
REPLICATION_FACTOR=1

echo "================================"
echo "Kafka Topics 생성 시작"
echo "================================"
echo "Broker: $KAFKA_BROKER"
echo ""

# 토픽 목록
topics=(
    "quantiq.analysis.request"
    "quantiq.analysis.completed"
    "economic.data.update.request"
    "economic.data.updated"
    "analysis.technical.request"
    "analysis.sentiment.request"
    "analysis.combined.request"
)

# Docker compose를 통해 실행하는 경우
if command -v docker &> /dev/null; then
    echo "Docker를 통해 Kafka 토픽 생성..."

    for topic in "${topics[@]}"; do
        echo ""
        echo "토픽 생성: $topic"

        docker exec quantiq-kafka kafka-topics \
            --bootstrap-server localhost:9092 \
            --create \
            --if-not-exists \
            --topic "$topic" \
            --partitions $PARTITIONS \
            --replication-factor $REPLICATION_FACTOR || true
    done

    echo ""
    echo "생성된 토픽 목록:"
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
