FROM python:3.11-slim

WORKDIR /app

# Install system dependencies (gcc for compilation if needed)
RUN apt-get update && apt-get install -y \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Install Poetry
RUN pip install poetry

# Copy configuration
COPY pyproject.toml poetry.lock* ./

# Install dependencies
RUN poetry config virtualenvs.create false \
    && poetry install --no-interaction --no-ansi --no-root

# Copy source code
COPY . .

# Set Python path to include src directory
ENV PYTHONPATH=/app/src

# Cloud Run은 PORT 환경변수 사용 (기본 8080), VM은 10020
EXPOSE ${PORT:-10020}

# Run the application
CMD ["python", "-m", "src.main"]
