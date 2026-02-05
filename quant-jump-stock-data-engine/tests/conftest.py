"""
Pytest Configuration and Fixtures

Data Engine 테스트를 위한 공통 설정 및 픽스처.
"""

import pytest
import sys
from pathlib import Path
from unittest.mock import MagicMock, AsyncMock

# src 경로 추가
sys.path.insert(0, str(Path(__file__).parent.parent / "src"))


@pytest.fixture
def mock_mongodb():
    """MongoDB Mock 픽스처"""
    mock = MagicMock()
    mock.find = MagicMock(return_value=[])
    mock.find_one = MagicMock(return_value=None)
    mock.insert_one = MagicMock()
    mock.update_one = MagicMock()
    return mock


@pytest.fixture
def mock_kafka_producer():
    """Kafka Producer Mock 픽스처"""
    mock = AsyncMock()
    mock.send_and_wait = AsyncMock()
    return mock


@pytest.fixture
def mock_gcs_client():
    """GCS Client Mock 픽스처"""
    mock = MagicMock()
    mock.bucket = MagicMock()
    mock.bucket.return_value.blob = MagicMock()
    return mock


@pytest.fixture
def sample_strategy_definition():
    """샘플 전략 정의"""
    return {
        "strategy_id": "test_golden_cross",
        "name": "Test Golden Cross Strategy",
        "description": "테스트용 골든크로스 전략",
        "version": "1.0",
        "rules": [
            {
                "name": "golden_cross_buy",
                "signal_type": "buy",
                "conditions": [
                    {
                        "indicator": "sma",
                        "params": {"period": 20},
                        "operator": "crosses_above",
                        "value": "sma_50"
                    },
                    {
                        "indicator": "rsi",
                        "params": {"period": 14},
                        "operator": "lt",
                        "value": 70
                    }
                ],
                "logic": "and",
                "weight": 1.0
            },
            {
                "name": "death_cross_sell",
                "signal_type": "sell",
                "conditions": [
                    {
                        "indicator": "sma",
                        "params": {"period": 20},
                        "operator": "crosses_below",
                        "value": "sma_50"
                    }
                ],
                "logic": "and",
                "weight": 1.0
            }
        ],
        "risk_management": {
            "stop_loss_pct": 0.05,
            "take_profit_pct": 0.15,
            "max_position_pct": 0.1
        },
        "is_ai_generated": False,
        "tags": ["momentum", "trend-following"]
    }


@pytest.fixture
def sample_market_data():
    """샘플 시장 데이터"""
    return [
        {"date": "2024-01-01", "open": 100, "high": 105, "low": 99, "close": 104, "volume": 1000000},
        {"date": "2024-01-02", "open": 104, "high": 108, "low": 103, "close": 107, "volume": 1200000},
        {"date": "2024-01-03", "open": 107, "high": 110, "low": 106, "close": 109, "volume": 1100000},
    ]
