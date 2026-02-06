# API Migration Status

## Overview
Status of the legacy Python FastAPI to Spring Boot Kotlin migration.

## Migrated Endpoints (quantiq-core)

### Stocks (`/api/stocks`)
- `GET /`: List stocks
- `GET /{ticker}`: Detail
- `POST /`: Create/Update
- `DELETE /{ticker}`: Soft delete

### Users (`/api/users`)
- `GET /`: List users
- `GET /{userId}`: User + Portfolio
- `POST /`: Registration
- `POST /{userId}/stocks`: Add to portfolio
- `DELETE /{userId}/stocks/{ticker}`: Remove from portfolio

### Analysis (`/stocks/analysis`)
- `POST /sentiment`: Trigger Kafka event
- `POST /technical`: Trigger Kafka event

### Financials (`/api/balance`, `/api/auto-trading`)
- `GET /balance/overseas`: Current holdings
- `GET /balance/profit`: P&L data
- `GET /auto-trading/config`: Settings
- `POST /auto-trading/execute/buy`: Manual trigger

## Retired Features
- **Legacy AI Prediction Trigger (`/api/colab`)**: Retired per user request.
- **Legacy GCS Package Upload**: Removed in favor of direct CI/CD or internal management.
