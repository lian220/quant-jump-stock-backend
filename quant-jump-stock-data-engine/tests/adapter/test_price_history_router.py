"""price_history_router 라우터 테스트.

NOTE: starlette 0.36.3 의 TestClient 는 httpx 0.28(app= 인자 제거)과 호환되지
않는다(프로젝트 lock 조합). 동일 검증을 httpx.ASGITransport 로 직접 ASGI 앱을
구동하여 수행한다 — TestClient 와 의미상 동일(상태코드/바디 검증).

라우터는 adapter/output 을 직접 참조하지 않고 set_price_history_service() 로
서비스를 주입받는다(헥사고날 경계). 테스트는 fake 서비스를 주입한다.
"""
import asyncio

import httpx
from fastapi import FastAPI

import adapter.input.rest.price_history_router as r


class _FakeService:
    def __init__(self, candles):
        self._candles = candles

    def build(self, ticker, period, today):
        return {"ticker": ticker, "period": period, "candles": self._candles}


def _get(candles, path):
    r.set_price_history_service(_FakeService(candles))
    app = FastAPI()
    app.include_router(r.router)

    async def _call():
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as c:
            return await c.get(path)

    return asyncio.run(_call())


def test_price_history_returns_candles():
    candles = [{"date": "2026-03-19", "open": 1, "high": 2, "low": 0.5,
                "close": 1.5, "volume": 10}]

    res = _get(candles, "/api/v1/stocks/AAPL/price-history?period=1m")

    assert res.status_code == 200
    body = res.json()
    assert body["ticker"] == "AAPL"
    assert body["period"] == "1m"
    assert isinstance(body["candles"], list)


def test_price_history_invalid_period_returns_422():
    # 잘못된 period 는 enum 검증에서 서비스 호출 전에 거부됨.
    res = _get([], "/api/v1/stocks/AAPL/price-history?period=2w")
    assert res.status_code == 422


def test_route_registered_in_main_app():
    import main
    paths = {route.path for route in main.app.routes if hasattr(route, "path")}
    assert "/api/v1/stocks/{ticker}/price-history" in paths
