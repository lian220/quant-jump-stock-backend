"""ScoringPolicy 관련 예외 정의."""


class SpecValidationError(ValueError):
    """scoring_spec.yaml 형식/invariant 위반."""


class SpecNotFoundError(FileNotFoundError):
    """SCORING_SPEC_PATH 또는 default 경로에서 spec 미발견."""
