"""
Application Ports (Hexagonal Architecture)

이 모듈은 애플리케이션 레이어와 외부 세계 간의 인터페이스를 정의합니다.
- Input Ports: 외부에서 애플리케이션으로의 진입점 (Use Cases)
- Output Ports: 애플리케이션에서 외부로의 출구점 (Repositories, Gateways)
"""

from .output_ports import (
    StrategyRepositoryPort,
    SignalOutputPort,
    NotificationPort,
)
from .input_ports import (
    StrategyExecutionUseCase,
    SignalGenerationUseCase,
)

__all__ = [
    # Output Ports (Driven)
    "StrategyRepositoryPort",
    "SignalOutputPort",
    "NotificationPort",
    # Input Ports (Driving)
    "StrategyExecutionUseCase",
    "SignalGenerationUseCase",
]
