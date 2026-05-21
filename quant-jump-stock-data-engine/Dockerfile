FROM python:3.11-slim

WORKDIR /app

# Install system dependencies (gcc for compilation if needed)
RUN apt-get update && apt-get install -y \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Install Poetry
RUN pip install poetry

# Layer 1: deps (cached unless pyproject/lock changes)
# Build context is now backend repo root → data-engine paths need explicit prefix
COPY quant-jump-stock-data-engine/pyproject.toml quant-jump-stock-data-engine/poetry.lock* ./

RUN poetry config virtualenvs.create false \
    && poetry install --no-interaction --no-ansi --no-root

# Layer 2: data-engine source
COPY quant-jump-stock-data-engine/ ./

# Layer 3: scoring SSoT spec (backend repo root)
COPY scoring_spec.yaml /app/scoring_spec.yaml

# Python path + scoring spec path
ENV PYTHONPATH=/app/src
ENV SCORING_SPEC_PATH=/app/scoring_spec.yaml

# Cloud Run은 PORT 환경변수 사용 (기본 8080), VM은 10020
EXPOSE ${PORT:-10020}

# Run the application
CMD ["python", "-m", "src.main"]
