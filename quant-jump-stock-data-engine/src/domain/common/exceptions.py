"""
도메인 예외 정의

비즈니스 로직에서 발생하는 예외들을 정의합니다.
외부 라이브러리에 의존하지 않는 순수 Python 예외입니다.
"""


class DomainException(Exception):
    """도메인 예외 기본 클래스"""

    def __init__(self, message: str, code: str = "DOMAIN_ERROR"):
        self.message = message
        self.code = code
        super().__init__(self.message)


class ValidationError(DomainException):
    """검증 오류"""

    def __init__(self, message: str, field: str = None):
        self.field = field
        code = f"VALIDATION_ERROR:{field}" if field else "VALIDATION_ERROR"
        super().__init__(message, code)


class StrategyError(DomainException):
    """전략 관련 오류"""

    def __init__(self, message: str, strategy_id: str = None):
        self.strategy_id = strategy_id
        code = f"STRATEGY_ERROR:{strategy_id}" if strategy_id else "STRATEGY_ERROR"
        super().__init__(message, code)


class IndicatorError(DomainException):
    """지표 계산 오류"""

    def __init__(self, message: str, indicator_name: str = None):
        self.indicator_name = indicator_name
        code = f"INDICATOR_ERROR:{indicator_name}" if indicator_name else "INDICATOR_ERROR"
        super().__init__(message, code)


class InsufficientDataError(DomainException):
    """데이터 부족 오류"""

    def __init__(self, message: str, required: int = None, actual: int = None):
        self.required = required
        self.actual = actual
        super().__init__(message, "INSUFFICIENT_DATA")


class StrategyNotFoundError(DomainException):
    """전략을 찾을 수 없음"""

    def __init__(self, strategy_id: str):
        super().__init__(f"Strategy not found: {strategy_id}", f"STRATEGY_NOT_FOUND:{strategy_id}")


class InvalidConditionError(DomainException):
    """잘못된 조건"""

    def __init__(self, message: str, condition_type: str = None):
        self.condition_type = condition_type
        super().__init__(message, f"INVALID_CONDITION:{condition_type}" if condition_type else "INVALID_CONDITION")


class RiskLimitExceededError(DomainException):
    """리스크 한도 초과"""

    def __init__(self, message: str, limit_type: str = None, current_value: float = None, limit_value: float = None):
        self.limit_type = limit_type
        self.current_value = current_value
        self.limit_value = limit_value
        super().__init__(message, f"RISK_LIMIT_EXCEEDED:{limit_type}" if limit_type else "RISK_LIMIT_EXCEEDED")


class StrategyExecutionError(DomainException):
    """전략 실행 오류"""

    def __init__(self, message: str, strategy_id: str = None, symbol: str = None):
        self.strategy_id = strategy_id
        self.symbol = symbol
        code = f"STRATEGY_EXECUTION_ERROR:{strategy_id}:{symbol}" if strategy_id and symbol else "STRATEGY_EXECUTION_ERROR"
        super().__init__(message, code)


class InvalidStrategyError(DomainException):
    """잘못된 전략 정의"""

    def __init__(self, message: str, strategy_id: str = None):
        self.strategy_id = strategy_id
        super().__init__(message, f"INVALID_STRATEGY:{strategy_id}" if strategy_id else "INVALID_STRATEGY")


class BacktestError(DomainException):
    """백테스트 실행 오류"""

    def __init__(self, message: str, strategy_id: str = None, symbol: str = None):
        self.strategy_id = strategy_id
        self.symbol = symbol
        code = "BACKTEST_ERROR"
        if strategy_id and symbol:
            code = f"BACKTEST_ERROR:{strategy_id}:{symbol}"
        elif strategy_id:
            code = f"BACKTEST_ERROR:{strategy_id}"
        super().__init__(message, code)


class BacktestConfigError(DomainException):
    """백테스트 설정 오류"""

    def __init__(self, message: str, config_key: str = None):
        self.config_key = config_key
        super().__init__(message, f"BACKTEST_CONFIG_ERROR:{config_key}" if config_key else "BACKTEST_CONFIG_ERROR")


class InsufficientCapitalError(DomainException):
    """자본 부족 오류"""

    def __init__(self, message: str, required: float = None, available: float = None):
        self.required = required
        self.available = available
        super().__init__(message, "INSUFFICIENT_CAPITAL")
