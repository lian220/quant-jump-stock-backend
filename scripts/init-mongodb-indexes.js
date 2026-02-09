// MongoDB 인덱스 생성 스크립트
// 실행 방법: mongosh stock_trading < init-mongodb-indexes.js

print("==========================================");
print("MongoDB 인덱스 생성 시작");
print("==========================================\n");

// Database 연결
db = db.getSiblingDB('stock_trading');

// 1. stock_analysis_results 컬렉션 인덱스
print("1. stock_analysis_results 컬렉션 인덱스 생성...");
try {
    // 기존 인덱스 확인
    const existingIndexes = db.stock_analysis_results.getIndexes();
    const hasDateTickerIndex = existingIndexes.some(idx =>
        idx.name === 'idx_date_ticker' ||
        (idx.key && idx.key.date === 1 && idx.key.ticker === 1)
    );

    if (hasDateTickerIndex) {
        print("   ✅ idx_date_ticker 인덱스가 이미 존재합니다.");
    } else {
        db.stock_analysis_results.createIndex(
            { date: 1, ticker: 1 },
            {
                unique: true,
                name: "idx_date_ticker",
                background: true  // 백그라운드 생성으로 성능 영향 최소화
            }
        );
        print("   ✅ idx_date_ticker 인덱스 생성 완료");
    }

    // ticker 단일 인덱스 (조회 최적화)
    const hasTickerIndex = existingIndexes.some(idx =>
        idx.name === 'idx_ticker' ||
        (idx.key && idx.key.ticker === 1 && Object.keys(idx.key).length === 1)
    );

    if (!hasTickerIndex) {
        db.stock_analysis_results.createIndex(
            { ticker: 1 },
            { name: "idx_ticker", background: true }
        );
        print("   ✅ idx_ticker 인덱스 생성 완료");
    } else {
        print("   ✅ idx_ticker 인덱스가 이미 존재합니다.");
    }

    // date 단일 인덱스 (시계열 조회 최적화)
    const hasDateIndex = existingIndexes.some(idx =>
        idx.name === 'idx_date' ||
        (idx.key && idx.key.date === 1 && Object.keys(idx.key).length === 1)
    );

    if (!hasDateIndex) {
        db.stock_analysis_results.createIndex(
            { date: -1 },
            { name: "idx_date", background: true }
        );
        print("   ✅ idx_date 인덱스 생성 완료 (내림차순)");
    } else {
        print("   ✅ idx_date 인덱스가 이미 존재합니다.");
    }
} catch (e) {
    print("   ❌ stock_analysis_results 인덱스 생성 실패:", e.message);
}

print("");

// 2. stock_predictions 컬렉션 인덱스
print("2. stock_predictions 컬렉션 인덱스 생성...");
try {
    const existingIndexes = db.stock_predictions.getIndexes();
    const hasDateTickerIndex = existingIndexes.some(idx =>
        idx.name === 'idx_date_ticker' ||
        (idx.key && idx.key.date === 1 && idx.key.ticker === 1)
    );

    if (hasDateTickerIndex) {
        print("   ✅ idx_date_ticker 인덱스가 이미 존재합니다.");
    } else {
        db.stock_predictions.createIndex(
            { date: 1, ticker: 1 },
            {
                unique: true,
                name: "idx_date_ticker",
                background: true
            }
        );
        print("   ✅ idx_date_ticker 인덱스 생성 완료");
    }

    // ticker 단일 인덱스
    const hasTickerIndex = existingIndexes.some(idx =>
        idx.name === 'idx_ticker' ||
        (idx.key && idx.key.ticker === 1 && Object.keys(idx.key).length === 1)
    );

    if (!hasTickerIndex) {
        db.stock_predictions.createIndex(
            { ticker: 1 },
            { name: "idx_ticker", background: true }
        );
        print("   ✅ idx_ticker 인덱스 생성 완료");
    } else {
        print("   ✅ idx_ticker 인덱스가 이미 존재합니다.");
    }

    // date 단일 인덱스
    const hasDateIndex = existingIndexes.some(idx =>
        idx.name === 'idx_date' ||
        (idx.key && idx.key.date === 1 && Object.keys(idx.key).length === 1)
    );

    if (!hasDateIndex) {
        db.stock_predictions.createIndex(
            { date: -1 },
            { name: "idx_date", background: true }
        );
        print("   ✅ idx_date 인덱스 생성 완료 (내림차순)");
    } else {
        print("   ✅ idx_date 인덱스가 이미 존재합니다.");
    }
} catch (e) {
    print("   ❌ stock_predictions 인덱스 생성 실패:", e.message);
}

print("\n==========================================");
print("인덱스 생성 완료!");
print("==========================================\n");

// 생성된 인덱스 확인
print("생성된 인덱스 목록:");
print("\n[stock_analysis_results]");
db.stock_analysis_results.getIndexes().forEach(function(idx) {
    print("  - " + idx.name + ":", JSON.stringify(idx.key), idx.unique ? "(UNIQUE)" : "");
});

print("\n[stock_predictions]");
db.stock_predictions.getIndexes().forEach(function(idx) {
    print("  - " + idx.name + ":", JSON.stringify(idx.key), idx.unique ? "(UNIQUE)" : "");
});

print("\n✅ 모든 작업 완료!");
