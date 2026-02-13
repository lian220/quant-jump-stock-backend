# [Plan] Strategy Management System
**Plan Status**: 🚧 In Progress
**Reason**: To support full lifecycle management and marketplace for strategies, replacing basic manual execution.

---

# 시드 전략 관리 및 백테스트 시스템 구현 계획

> 📚 **개념 이해**: [백테스트 & 전략 시스템 아키텍처](./architecture/backtest-strategy-system.md) - 시드 전략 → 지표 → 포트폴리오 흐름 설명

## 📋 요구사항 정리

### 핵심 기능
1. **어드민 전략 관리**: 17개 시드 전략 개별 관리
2. **백테스트 실행**: 각 전략별 백테스트 실행 및 수익률 확인
3. **프론트엔드 노출**: 전략 마켓플레이스에 전략 카드 표시
4. **매매 기준 설정**: 전략별 매매 조건 커스터마이징

### ⚠️ 종목선정 이원화 (SCRUM-269)
전략은 `stock_selection_type`에 따라 두 가지 모델로 구분된다:
- **SCREENING**: 조건(conditions)으로 전체 유니버스를 필터링하여 종목 결정 (저PER, 골든크로스 등)
- **PORTFOLIO**: 사전 정의된 고정 포트폴리오 사용 (워런 버핏 13F, 올웨더 등)

구독 시 플로우가 분기됨 → PRD 5.5.1, ERD strategies 테이블 참조

---

## 🏗️ 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                  Backoffice (Admin)                      │
│  ┌────────────────────────────────────────────────────┐ │
│  │  전략 관리 대시보드                                 │ │
│  │  - 전략 목록 (17개)                                │ │
│  │  - 전략 상세 편집                                  │ │
│  │  - 백테스트 실행 버튼                              │ │
│  │  - 수익률 차트 확인                                │ │
│  └────────────────────────────────────────────────────┘ │
└─────────────────────┬───────────────────────────────────┘
                      │
         ┌────────────▼────────────┐
         │     Core API            │
         │  (Strategy Management)  │
         └────────┬────────────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
┌───▼────┐  ┌─────▼──────┐  ┌──▼────────┐
│Strategy│  │ Backtest   │  │ Data      │
│CRUD API│  │ Engine API │  │ Engine    │
└────────┘  └────────────┘  └───────────┘
                  │
         ┌────────▼────────┐
         │   PostgreSQL    │
         │  - strategies   │
         │  - backtest_    │
         │    results      │
         └─────────────────┘
```

---

## 📊 DB 스키마 확장

### strategies 테이블에 추가할 컬럼
```sql
-- 종목선정 이원화 (SCRUM-269)
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS
  stock_selection_type VARCHAR(20) NOT NULL DEFAULT 'SCREENING', -- SCREENING | PORTFOLIO
  investment_philosophy TEXT;                                     -- 투자 철학 (AI 참고)

ALTER TABLE strategies ADD CONSTRAINT check_stock_selection_type CHECK (
    stock_selection_type IN ('SCREENING', 'PORTFOLIO')
);

-- 백테스트 관련
ALTER TABLE strategies ADD COLUMN IF NOT EXISTS
  backtest_status VARCHAR(20) DEFAULT 'NOT_RUN',  -- 백테스트 상태
  last_backtest_at TIMESTAMP,                     -- 마지막 백테스트 실행 시간
  last_cagr NUMERIC(10,2),                        -- 최근 CAGR
  last_mdd NUMERIC(10,2),                         -- 최근 MDD
  last_sharpe_ratio NUMERIC(10,2),                -- 최근 샤프 비율
  last_win_rate NUMERIC(10,2);                    -- 최근 승률
```

---

## 🎯 구현 단계별 계획

### Phase 1: Backend API 구축 (3-4일)

#### 1.1 전략 관리 API
**파일**: `adapter/input/rest/strategy/StrategyAdminController.kt`

```kotlin
@RestController
@RequestMapping("/api/admin/strategies")
class StrategyAdminController(
    private val strategyService: StrategyService
) {
    // 전략 목록 조회 (필터링, 정렬)
    @GetMapping
    fun getStrategies(
        @RequestParam category: String?,
        @RequestParam status: String?,
        @RequestParam isPremium: Boolean?
    ): List<StrategyDto>

    // 전략 상세 조회
    @GetMapping("/{id}")
    fun getStrategy(@PathVariable id: Long): StrategyDetailDto

    // 전략 수정 (매매 조건, 설정)
    @PutMapping("/{id}")
    fun updateStrategy(
        @PathVariable id: Long,
        @RequestBody req: UpdateStrategyRequest
    ): StrategyDto

    // 전략 상태 변경 (ACTIVE/ARCHIVED)
    @PatchMapping("/{id}/status")
    fun updateStatus(
        @PathVariable id: Long,
        @RequestBody req: UpdateStatusRequest
    ): StrategyDto
}
```

#### 1.2 백테스트 실행 API
**파일**: `adapter/input/rest/backtest/BacktestAdminController.kt`

```kotlin
@RestController
@RequestMapping("/api/admin/backtest")
class BacktestAdminController(
    private val backtestService: BacktestService
) {
    // 백테스트 실행 (비동기)
    @PostMapping("/strategies/{strategyId}/run")
    fun runBacktest(
        @PathVariable strategyId: Long,
        @RequestBody req: BacktestRequest
    ): BacktestJobDto {
        return backtestService.runBacktestAsync(strategyId, req)
    }

    // 백테스트 상태 확인
    @GetMapping("/jobs/{jobId}")
    fun getBacktestJob(@PathVariable jobId: String): BacktestJobDto

    // 백테스트 결과 조회
    @GetMapping("/strategies/{strategyId}/results")
    fun getBacktestResults(
        @PathVariable strategyId: Long,
        @RequestParam limit: Int = 10
    ): List<BacktestResultDto>

    // 백테스트 결과 상세 (차트 데이터)
    @GetMapping("/results/{resultId}")
    fun getBacktestDetail(@PathVariable resultId: Long): BacktestDetailDto
}
```

**DTO 정의**:
```kotlin
data class BacktestRequest(
    val startDate: LocalDate,      // 백테스트 시작일
    val endDate: LocalDate,         // 백테스트 종료일
    val initialCapital: BigDecimal, // 초기 자본금
    val symbols: List<String>?      // 대상 종목 (null이면 전체)
)

data class UpdateStrategyRequest(
    val name: String?,
    val description: String?,
    val conditions: StrategyConditions?,
    val rebalanceFrequency: String?,
    val isPublic: Boolean?,
    val isPremium: Boolean?
)

data class StrategyConditions(
    val buyConditions: List<Condition>,
    val sellConditions: List<Condition>,
    val filters: List<Filter>?
)

data class Condition(
    val type: String,        // "SMA_CROSS", "RSI", "MACD", etc.
    val operator: String,    // ">", "<", "==", "CROSS_OVER", etc.
    val value: Any,          // 임계값 또는 비교 대상
    val parameters: Map<String, Any>? // 기술지표 파라미터
)
```

#### 1.3 백테스트 서비스
**파일**: `application/backtest/BacktestService.kt`

```kotlin
@Service
class BacktestService(
    private val strategyRepository: StrategyRepository,
    private val backtestResultRepository: BacktestResultRepository,
    private val kafkaProducer: KafkaBacktestProducer
) {
    // 백테스트 실행 (비동기)
    @Async
    fun runBacktestAsync(strategyId: Long, request: BacktestRequest): BacktestJobDto {
        val strategy = strategyRepository.findById(strategyId)
            .orElseThrow { NotFoundException("전략을 찾을 수 없습니다") }

        val jobId = UUID.randomUUID().toString()

        // Kafka로 백테스트 요청 발행
        kafkaProducer.sendBacktestRequest(
            BacktestJobMessage(
                jobId = jobId,
                strategyId = strategyId,
                conditions = strategy.conditions,
                startDate = request.startDate,
                endDate = request.endDate,
                initialCapital = request.initialCapital,
                symbols = request.symbols
            )
        )

        // 전략 백테스트 상태 업데이트
        strategy.backtestStatus = "RUNNING"
        strategy.lastBacktestAt = LocalDateTime.now()
        strategyRepository.save(strategy)

        return BacktestJobDto(
            jobId = jobId,
            strategyId = strategyId,
            status = "RUNNING",
            createdAt = LocalDateTime.now()
        )
    }

    // 백테스트 결과 수신 (Kafka Consumer)
    @KafkaListener(topics = ["backtest-results"])
    fun handleBacktestResult(message: BacktestResultMessage) {
        // 백테스트 결과 저장
        val result = backtestResultRepository.save(
            BacktestResult(
                strategyId = message.strategyId,
                jobId = message.jobId,
                cagr = message.cagr,
                mdd = message.mdd,
                sharpeRatio = message.sharpeRatio,
                winRate = message.winRate,
                totalReturn = message.totalReturn,
                equityCurve = message.equityCurve,
                trades = message.trades
            )
        )

        // 전략에 최신 백테스트 결과 업데이트
        val strategy = strategyRepository.findById(message.strategyId).get()
        strategy.backtestStatus = "COMPLETED"
        strategy.lastCagr = message.cagr
        strategy.lastMdd = message.mdd
        strategy.lastSharpeRatio = message.sharpeRatio
        strategy.lastWinRate = message.winRate
        strategyRepository.save(strategy)
    }
}
```

---

### Phase 2: Data Engine 백테스트 기능 (2-3일)

#### 2.1 백테스트 실행 모듈
**파일**: `quant-jump-stock-data-engine/src/services/backtest_service.py`

```python
from typing import Dict, List
import pandas as pd
from datetime import datetime
import asyncio

class BacktestService:
    def __init__(self):
        self.kafka_consumer = KafkaConsumer('backtest-requests')
        self.kafka_producer = KafkaProducer()

    async def consume_backtest_requests(self):
        """Kafka에서 백테스트 요청 수신"""
        async for message in self.kafka_consumer:
            request = BacktestRequest.parse(message.value)
            await self.run_backtest(request)

    async def run_backtest(self, request: BacktestRequest):
        """백테스트 실행"""
        try:
            # 1. 데이터 로드
            data = await self.load_historical_data(
                symbols=request.symbols,
                start_date=request.start_date,
                end_date=request.end_date
            )

            # 2. 전략 조건 적용
            signals = self.apply_strategy_conditions(
                data=data,
                conditions=request.conditions
            )

            # 3. 백테스트 실행
            result = self.execute_backtest(
                data=data,
                signals=signals,
                initial_capital=request.initial_capital
            )

            # 4. 결과 계산
            metrics = self.calculate_metrics(result)

            # 5. Kafka로 결과 발행
            await self.kafka_producer.send('backtest-results', {
                'job_id': request.job_id,
                'strategy_id': request.strategy_id,
                'status': 'COMPLETED',
                'cagr': metrics['cagr'],
                'mdd': metrics['mdd'],
                'sharpe_ratio': metrics['sharpe_ratio'],
                'win_rate': metrics['win_rate'],
                'total_return': metrics['total_return'],
                'equity_curve': metrics['equity_curve'],
                'trades': result['trades']
            })

        except Exception as e:
            # 에러 발생 시 실패 메시지 발행
            await self.kafka_producer.send('backtest-results', {
                'job_id': request.job_id,
                'strategy_id': request.strategy_id,
                'status': 'FAILED',
                'error': str(e)
            })

    def apply_strategy_conditions(self, data: pd.DataFrame, conditions: Dict) -> pd.DataFrame:
        """전략 조건을 데이터에 적용"""
        signals = data.copy()

        # 매수 조건 적용
        buy_signal = pd.Series(True, index=data.index)
        for condition in conditions['buy_conditions']:
            buy_signal &= self._evaluate_condition(data, condition)

        # 매도 조건 적용
        sell_signal = pd.Series(False, index=data.index)
        for condition in conditions['sell_conditions']:
            sell_signal |= self._evaluate_condition(data, condition)

        signals['buy_signal'] = buy_signal
        signals['sell_signal'] = sell_signal

        return signals

    def _evaluate_condition(self, data: pd.DataFrame, condition: Dict) -> pd.Series:
        """개별 조건 평가"""
        cond_type = condition['type']
        operator = condition['operator']
        value = condition.get('value')
        params = condition.get('parameters', {})

        if cond_type == 'SMA_CROSS':
            short_period = params['short_period']
            long_period = params['long_period']
            sma_short = data['close'].rolling(short_period).mean()
            sma_long = data['close'].rolling(long_period).mean()

            if operator == 'CROSS_OVER':
                return (sma_short > sma_long) & (sma_short.shift(1) <= sma_long.shift(1))
            elif operator == 'CROSS_UNDER':
                return (sma_short < sma_long) & (sma_short.shift(1) >= sma_long.shift(1))

        elif cond_type == 'RSI':
            rsi = self._calculate_rsi(data['close'], params.get('period', 14))
            if operator == '<':
                return rsi < value
            elif operator == '>':
                return rsi > value

        elif cond_type == 'MACD':
            macd, signal = self._calculate_macd(data['close'])
            if operator == 'CROSS_OVER':
                return (macd > signal) & (macd.shift(1) <= signal.shift(1))

        return pd.Series(False, index=data.index)

    def execute_backtest(self, data: pd.DataFrame, signals: pd.DataFrame, initial_capital: float) -> Dict:
        """백테스트 실행 및 거래 내역 생성"""
        portfolio = {
            'cash': initial_capital,
            'holdings': {},
            'equity': [initial_capital],
            'trades': []
        }

        for date, row in signals.iterrows():
            # 매수 신호
            if row['buy_signal'] and portfolio['cash'] > 0:
                shares = int(portfolio['cash'] / row['close'])
                if shares > 0:
                    cost = shares * row['close']
                    portfolio['cash'] -= cost
                    portfolio['holdings'][date] = shares
                    portfolio['trades'].append({
                        'date': str(date),
                        'action': 'BUY',
                        'shares': shares,
                        'price': float(row['close']),
                        'value': float(cost)
                    })

            # 매도 신호
            elif row['sell_signal'] and date in portfolio['holdings']:
                shares = portfolio['holdings'][date]
                revenue = shares * row['close']
                portfolio['cash'] += revenue
                del portfolio['holdings'][date]
                portfolio['trades'].append({
                    'date': str(date),
                    'action': 'SELL',
                    'shares': shares,
                    'price': float(row['close']),
                    'value': float(revenue)
                })

            # 자산 가치 계산
            holdings_value = sum(
                shares * data.loc[date, 'close']
                for d, shares in portfolio['holdings'].items()
            )
            portfolio['equity'].append(portfolio['cash'] + holdings_value)

        return portfolio

    def calculate_metrics(self, result: Dict) -> Dict:
        """백테스트 성과 지표 계산"""
        equity_curve = pd.Series(result['equity'])
        returns = equity_curve.pct_change().dropna()

        # CAGR (연평균 복리 수익률)
        total_return = (equity_curve.iloc[-1] / equity_curve.iloc[0]) - 1
        years = len(equity_curve) / 252  # 거래일 기준
        cagr = (1 + total_return) ** (1 / years) - 1 if years > 0 else 0

        # MDD (최대 낙폭)
        cumulative = (1 + returns).cumprod()
        running_max = cumulative.cummax()
        drawdown = (cumulative - running_max) / running_max
        mdd = drawdown.min()

        # Sharpe Ratio
        sharpe_ratio = returns.mean() / returns.std() * (252 ** 0.5) if returns.std() > 0 else 0

        # 승률 계산
        trades = result['trades']
        buy_trades = {t['date']: t['price'] for t in trades if t['action'] == 'BUY'}
        sell_trades = [t for t in trades if t['action'] == 'SELL']

        winning_trades = 0
        for sell_trade in sell_trades:
            if sell_trade['date'] in buy_trades:
                if sell_trade['price'] > buy_trades[sell_trade['date']]:
                    winning_trades += 1

        win_rate = (winning_trades / len(sell_trades) * 100) if sell_trades else 0

        return {
            'cagr': round(cagr * 100, 2),
            'mdd': round(abs(mdd) * 100, 2),
            'sharpe_ratio': round(sharpe_ratio, 2),
            'win_rate': round(win_rate, 2),
            'total_return': round(total_return * 100, 2),
            'equity_curve': [float(x) for x in equity_curve.tolist()]
        }
```

---

### Phase 3: Backoffice 어드민 UI (3-4일)

#### 3.1 전략 관리 페이지
**파일**: `quant-jump-stock-backoffice/src/app/admin/strategies/page.tsx`

```tsx
'use client';

import { useState, useEffect } from 'react';
import { StrategyTable } from '@/components/admin/StrategyTable';
import { StrategyFilters } from '@/components/admin/StrategyFilters';

export default function StrategiesPage() {
  const [strategies, setStrategies] = useState([]);
  const [filters, setFilters] = useState({});

  useEffect(() => {
    fetchStrategies();
  }, [filters]);

  const fetchStrategies = async () => {
    const response = await fetch('/api/admin/strategies?' + new URLSearchParams(filters));
    const data = await response.json();
    setStrategies(data);
  };

  return (
    <div className="p-6">
      <h1 className="text-2xl font-bold mb-6">전략 관리</h1>

      <StrategyFilters onChange={setFilters} />

      <StrategyTable
        strategies={strategies}
        onRefresh={fetchStrategies}
      />
    </div>
  );
}
```

#### 3.2 전략 상세/편집 페이지
**파일**: `quant-jump-stock-backoffice/src/app/admin/strategies/[id]/page.tsx`

```tsx
'use client';

import { useState, useEffect } from 'react';
import { StrategyBasicInfo } from '@/components/admin/StrategyBasicInfo';
import { StrategyConditionsEditor } from '@/components/admin/StrategyConditionsEditor';
import { BacktestRunner } from '@/components/admin/BacktestRunner';
import { BacktestResults } from '@/components/admin/BacktestResults';

export default function StrategyDetailPage({ params }: { params: { id: string } }) {
  const [strategy, setStrategy] = useState(null);

  useEffect(() => {
    fetchStrategy();
  }, [params.id]);

  const fetchStrategy = async () => {
    const response = await fetch(`/api/admin/strategies/${params.id}`);
    const data = await response.json();
    setStrategy(data);
  };

  if (!strategy) return <div>Loading...</div>;

  return (
    <div className="p-6 space-y-6">
      <StrategyBasicInfo strategy={strategy} onUpdate={fetchStrategy} />

      <StrategyConditionsEditor
        strategyId={params.id}
        conditions={strategy.conditions}
        onUpdate={fetchStrategy}
      />

      <BacktestRunner strategyId={params.id} />

      <BacktestResults strategyId={params.id} />
    </div>
  );
}
```

#### 3.3 매매 조건 에디터 컴포넌트
**파일**: `quant-jump-stock-backoffice/src/components/admin/StrategyConditionsEditor.tsx`

```tsx
'use client';

import { useState } from 'react';
import { Card, CardHeader, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ConditionRow } from './ConditionRow';

interface Condition {
  type: string;
  operator: string;
  value?: any;
  parameters?: Record<string, any>;
}

export function StrategyConditionsEditor({
  strategyId,
  conditions,
  onUpdate
}: {
  strategyId: string;
  conditions: any;
  onUpdate: () => void;
}) {
  const [buyConditions, setBuyConditions] = useState<Condition[]>(conditions?.buyConditions || []);
  const [sellConditions, setSellConditions] = useState<Condition[]>(conditions?.sellConditions || []);

  const addCondition = (type: 'buy' | 'sell') => {
    const newCondition: Condition = {
      type: 'SMA_CROSS',
      operator: 'CROSS_OVER',
      parameters: {}
    };

    if (type === 'buy') {
      setBuyConditions([...buyConditions, newCondition]);
    } else {
      setSellConditions([...sellConditions, newCondition]);
    }
  };

  const updateCondition = (type: 'buy' | 'sell', index: number, updated: Condition) => {
    if (type === 'buy') {
      const newConditions = [...buyConditions];
      newConditions[index] = updated;
      setBuyConditions(newConditions);
    } else {
      const newConditions = [...sellConditions];
      newConditions[index] = updated;
      setSellConditions(newConditions);
    }
  };

  const removeCondition = (type: 'buy' | 'sell', index: number) => {
    if (type === 'buy') {
      setBuyConditions(buyConditions.filter((_, i) => i !== index));
    } else {
      setSellConditions(sellConditions.filter((_, i) => i !== index));
    }
  };

  const saveConditions = async () => {
    const response = await fetch(`/api/admin/strategies/${strategyId}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        conditions: {
          buyConditions,
          sellConditions
        }
      })
    });

    if (response.ok) {
      alert('조건이 저장되었습니다');
      onUpdate();
    }
  };

  return (
    <Card>
      <CardHeader>
        <h2 className="text-xl font-bold">매매 조건 설정</h2>
      </CardHeader>
      <CardContent className="space-y-6">
        {/* 매수 조건 */}
        <div>
          <h3 className="text-lg font-semibold mb-3">매수 조건</h3>
          {buyConditions.map((condition, index) => (
            <ConditionRow
              key={index}
              condition={condition}
              onChange={(updated) => updateCondition('buy', index, updated)}
              onRemove={() => removeCondition('buy', index)}
            />
          ))}
          <Button
            variant="outline"
            onClick={() => addCondition('buy')}
            className="mt-2"
          >
            + 매수 조건 추가
          </Button>
        </div>

        {/* 매도 조건 */}
        <div>
          <h3 className="text-lg font-semibold mb-3">매도 조건</h3>
          {sellConditions.map((condition, index) => (
            <ConditionRow
              key={index}
              condition={condition}
              onChange={(updated) => updateCondition('sell', index, updated)}
              onRemove={() => removeCondition('sell', index)}
            />
          ))}
          <Button
            variant="outline"
            onClick={() => addCondition('sell')}
            className="mt-2"
          >
            + 매도 조건 추가
          </Button>
        </div>

        <Button onClick={saveConditions} className="w-full">
          조건 저장
        </Button>
      </CardContent>
    </Card>
  );
}
```

---

### Phase 4: Frontend 전략 마켓 UI (2-3일)

#### 4.1 전략 마켓 페이지
**파일**: `quant-jump-stock-frontend/src/app/strategies/page.tsx`

```tsx
'use client';

import { useState, useEffect } from 'react';
import { StrategyCard } from '@/components/strategies/StrategyCard';
import { StrategyCategories } from '@/components/strategies/StrategyCategories';

export default function StrategiesMarketPage() {
  const [strategies, setStrategies] = useState([]);
  const [category, setCategory] = useState('ALL');

  useEffect(() => {
    fetchStrategies();
  }, [category]);

  const fetchStrategies = async () => {
    const params = category !== 'ALL' ? `?category=${category}` : '';
    const response = await fetch(`/api/strategies${params}`);
    const data = await response.json();
    setStrategies(data);
  };

  return (
    <div className="container mx-auto px-4 py-8">
      <h1 className="text-3xl font-bold mb-8">검증된 전략 마켓</h1>

      <StrategyCategories
        selected={category}
        onChange={setCategory}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-8">
        {strategies.map(strategy => (
          <StrategyCard key={strategy.id} strategy={strategy} />
        ))}
      </div>
    </div>
  );
}
```

#### 4.2 전략 카드 컴포넌트
**파일**: `quant-jump-stock-frontend/src/components/strategies/StrategyCard.tsx`

```tsx
'use client';

import { Card, CardHeader, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useRouter } from 'next/navigation';

interface Strategy {
  id: number;
  name: string;
  description: string;
  category: string;
  isPremium: boolean;
  lastCagr: number;
  lastMdd: number;
  lastSharpeRatio: number;
  subscriberCount: number;
  averageRating: number;
}

export function StrategyCard({ strategy }: { strategy: Strategy }) {
  const router = useRouter();

  const subscribe = async () => {
    const response = await fetch(`/api/strategies/${strategy.id}/subscribe`, {
      method: 'POST'
    });

    if (response.ok) {
      alert('전략을 구독했습니다');
    }
  };

  return (
    <Card className="hover:shadow-lg transition-shadow">
      <CardHeader>
        <div className="flex justify-between items-start">
          <h3 className="text-lg font-bold">{strategy.name}</h3>
          {strategy.isPremium && (
            <Badge variant="secondary">프리미엄</Badge>
          )}
        </div>
        <p className="text-sm text-gray-500">{strategy.category}</p>
      </CardHeader>

      <CardContent>
        {/* 성과 지표 */}
        <div className="grid grid-cols-3 gap-4 mb-4">
          <div className="text-center">
            <p className="text-xs text-gray-500">CAGR</p>
            <p className="text-lg font-bold text-green-600">
              {strategy.lastCagr}%
            </p>
          </div>
          <div className="text-center">
            <p className="text-xs text-gray-500">MDD</p>
            <p className="text-lg font-bold text-red-600">
              {strategy.lastMdd}%
            </p>
          </div>
          <div className="text-center">
            <p className="text-xs text-gray-500">Sharpe</p>
            <p className="text-lg font-bold">
              {strategy.lastSharpeRatio}
            </p>
          </div>
        </div>

        {/* 설명 */}
        <p className="text-sm mb-4 line-clamp-2">{strategy.description}</p>

        {/* 구독 정보 */}
        <div className="flex justify-between items-center text-sm text-gray-500 mb-4">
          <span>👥 {strategy.subscriberCount}명 구독중</span>
          <span>⭐ {strategy.averageRating}</span>
        </div>

        {/* 액션 버튼 */}
        <div className="flex gap-2">
          <Button
            variant="outline"
            className="flex-1"
            onClick={() => router.push(`/strategies/${strategy.id}`)}
          >
            상세보기
          </Button>
          <Button
            className="flex-1"
            onClick={subscribe}
          >
            구독하기
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
```

---

## 🔄 Kafka 이벤트 플로우

```
1. Admin → Core API: POST /api/admin/backtest/strategies/{id}/run
2. Core API → Kafka: backtest-requests 토픽 발행
3. Data Engine: backtest-requests 토픽 구독 및 백테스트 실행
4. Data Engine → Kafka: backtest-results 토픽 발행
5. Core API: backtest-results 토픽 구독
6. Core API → PostgreSQL: 결과 저장 및 전략 업데이트
7. Admin/Frontend: GET /api/admin/backtest/strategies/{id}/results
```

---

## 📝 매매 기준 설정 - conditions JSONB 구조

```json
{
  "buyConditions": [
    {
      "type": "SMA_CROSS",
      "operator": "CROSS_OVER",
      "parameters": {
        "short_period": 20,
        "long_period": 50
      }
    },
    {
      "type": "RSI",
      "operator": "<",
      "value": 50,
      "parameters": {
        "period": 14
      }
    }
  ],
  "sellConditions": [
    {
      "type": "RSI",
      "operator": ">",
      "value": 70,
      "parameters": {
        "period": 14
      }
    }
  ],
  "filters": [
    {
      "type": "VOLUME",
      "operator": ">",
      "value": 1000000
    }
  ]
}
```

---

## 📅 구현 타임라인 (총 10-14일)

| Phase | 작업 내용 | 기간 | 담당 |
|-------|-----------|------|------|
| **Phase 1** | Backend API 구축 | 3-4일 | Backend |
| - | Strategy CRUD API | 1일 | |
| - | Backtest Admin API | 1일 | |
| - | Kafka 프로듀서/컨슈머 | 1-2일 | |
| **Phase 2** | Data Engine 백테스트 | 2-3일 | Data/AI |
| - | Kafka 컨슈머 구현 | 1일 | |
| - | 백테스트 엔진 구현 | 1-2일 | |
| **Phase 3** | Backoffice Admin UI | 3-4일 | Frontend |
| - | 전략 관리 페이지 | 1일 | |
| - | 매매 조건 에디터 | 1-2일 | |
| - | 백테스트 실행/결과 UI | 1일 | |
| **Phase 4** | Frontend 전략 마켓 | 2-3일 | Frontend |
| - | 전략 마켓 페이지 | 1일 | |
| - | 전략 상세 페이지 | 1일 | |
| - | 백테스트 결과 차트 | 1일 | |

**병렬 작업 가능**:
- Phase 1-2: Backend/Data Engine 동시 진행 (최대 4일)
- Phase 3-4: 어드민/프론트 동시 진행 (최대 4일)

---

## ✅ 체크리스트

### Backend
- [ ] DB Migration 생성 (V17__Add_Backtest_Columns)
- [ ] StrategyAdminController 구현
- [ ] BacktestAdminController 구현
- [ ] BacktestService 구현
- [ ] Kafka Producer/Consumer 구현
- [ ] DTO 정의
- [ ] Swagger 문서화

### Data Engine
- [ ] backtest_service.py 생성
- [ ] Kafka Consumer 구현
- [ ] 조건 평가 로직 구현
- [ ] 백테스트 실행 엔진
- [ ] 성과 지표 계산
- [ ] Kafka Producer (결과 발행)

### Backoffice
- [ ] 전략 목록 페이지
- [ ] 전략 상세 페이지
- [ ] StrategyConditionsEditor 컴포넌트
- [ ] ConditionRow 컴포넌트
- [ ] BacktestRunner 컴포넌트
- [ ] BacktestResults 컴포넌트

### Frontend
- [ ] 전략 마켓 페이지
- [ ] StrategyCard 컴포넌트
- [ ] 전략 상세 페이지
- [ ] 백테스트 결과 차트
- [ ] 구독 기능 구현

---

## 🚀 시작하기

### 1단계: DB Migration
```bash
cd quant-jump-stock-backend/quant-jump-stock-core/src/main/resources/db/migration
# V17__Add_Backtest_Columns_To_Strategies.sql 생성
```

### 2단계: Backend API 구현
```bash
cd quant-jump-stock-backend/quant-jump-stock-core
# 컨트롤러 및 서비스 구현
```

### 3단계: Data Engine 백테스트 구현
```bash
cd quant-jump-stock-data-engine
# backtest_service.py 구현
```

### 4단계: UI 구현
```bash
# Backoffice
cd quant-jump-stock-backoffice
# strategies 관리 페이지 구현

# Frontend
cd quant-jump-stock-frontend
# strategies 마켓 페이지 구현
```

---

## 📚 관련 문서

### 기술 문서
- [Kafka 아키텍처](./kafka-architecture-detailed.md) - Kafka 상세 설계
- [트레이딩 시스템 보완](./trading-system-enhancements.md) - 실전 트레이딩

### Jira 계획
- [Jira 티켓 계획](../jira-ticket-plan.md) - 전체 티켓 계획
- [Epic 1: 백테스트](../jira-epics/epic-1-backtest.md) - 백테스트 관련
- [Epic 2: 전략 관리](../jira-epics/epic-2-strategy.md) - 전략 관리
- [Epic 3: 마켓플레이스](../jira-epics/epic-3-marketplace.md) - 전략 마켓

### API 명세
- [Predictions API](../api/predictions.md) - 예측 API
- [Stocks API](../api/stocks.md) - 종목 API
- [Trading API](../api/trading.md) - 트레이딩 API

### 프로젝트 문서
- [ERD](../erd.md) - 데이터베이스 스키마
- [PRD v2.0](../prd-v2.md) - 제품 요구사항

### Backend 문서
- [데이터베이스 스키마](../../quant-jump-stock-backend/docs/database/SCHEMA.md)
- [시스템 아키텍처](../../quant-jump-stock-backend/docs/architecture/시스템_아키텍처.md)
