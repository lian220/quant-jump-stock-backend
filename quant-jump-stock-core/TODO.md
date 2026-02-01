# TODO List

## Vertex AI CustomJob 파라미터 기능 추가

### 📋 작업 내용
Vertex AI CustomJob 실행 시 동적 파라미터를 전달할 수 있는 기능 추가

### 🎯 목표
- API 요청으로 모델 학습 파라미터를 동적으로 전달
- 환경 변수 외에 Command Line Arguments 지원

### 🔧 구현 방법

#### 1. API 인터페이스 수정
```kotlin
// VertexAIApi.kt
@PostMapping("/predict")
@Operation(summary = "Vertex AI 예측 수동 실행", description = "...")
@VertexAIJobResponses
fun runPrediction(
    @RequestBody(required = false)
    @Parameter(description = "학습 파라미터")
    params: VertexAIJobParams?
): ResponseEntity<Map<String, Any>>
```

#### 2. 파라미터 DTO 생성
```kotlin
data class VertexAIJobParams(
    val modelType: String? = null,        // 예: "lstm", "transformer"
    val epochs: Int? = null,              // 예: 10, 50, 100
    val learningRate: Double? = null,     // 예: 0.001, 0.01
    val batchSize: Int? = null,           // 예: 16, 32, 64
    val customArgs: Map<String, String>? = null  // 추가 커스텀 파라미터
)
```

#### 3. VertexAIService 수정
```kotlin
// VertexAIService.kt - buildCustomJob() 메서드
private fun buildCustomJob(
    packageUri: String,
    envVars: Map<String, String>,
    args: List<String> = emptyList()  // ✨ 추가
): CustomJob {
    val pythonPackageSpec = PythonPackageSpec.newBuilder()
        .setExecutorImageUri(containerUri)
        .addPackageUris(packageUri)
        .setPythonModule("aiplatform_custom_trainer_script.task")
        .addAllEnv(envVarList)
        .addAllArgs(args)  // ✨ Command line arguments 추가
        .build()
    // ...
}
```

#### 4. 파라미터 변환 로직
```kotlin
private fun convertParamsToArgs(params: VertexAIJobParams?): List<String> {
    if (params == null) return emptyList()

    val args = mutableListOf<String>()

    params.modelType?.let {
        args.add("--model-type")
        args.add(it)
    }
    params.epochs?.let {
        args.add("--epochs")
        args.add(it.toString())
    }
    params.learningRate?.let {
        args.add("--learning-rate")
        args.add(it.toString())
    }
    params.batchSize?.let {
        args.add("--batch-size")
        args.add(it.toString())
    }
    params.customArgs?.forEach { (key, value) ->
        args.add("--$key")
        args.add(value)
    }

    return args
}
```

### 📚 참고 문서
- [Google Cloud PythonPackageSpec.Builder API](https://cloud.google.com/java/docs/reference/google-cloud-aiplatform/3.52.0/com.google.cloud.aiplatform.v1.PythonPackageSpec.Builder)
- [Configure container settings for custom training](https://cloud.google.com/vertex-ai/docs/training/configure-container-settings)

### ⚠️ 주의사항
- Python 학습 스크립트(`predict_optimized.py`)에서 `argparse`로 파라미터를 받을 수 있도록 수정 필요
- 최대 100,000자 제한 (모든 arguments 합계)
- 파라미터 검증 로직 추가 필요

### ✅ 체크리스트
- [ ] `VertexAIJobParams` DTO 생성
- [ ] `VertexAIApi` 인터페이스 수정
- [ ] `VertexAIService.buildCustomJob()` 수정
- [ ] `convertParamsToArgs()` 변환 로직 구현
- [ ] Python 스크립트 argparse 추가
- [ ] API 문서 업데이트
- [ ] 테스트 코드 작성
- [ ] 통합 테스트

---

## Stock 데이터 PostgreSQL 마이그레이션 후속 작업

### 📋 작업 내용
stocks 컬렉션을 MongoDB에서 PostgreSQL로 마이그레이션 완료. 비즈니스 로직 적용 필요.

### ✅ 완료된 작업
- [x] PostgreSQL `stocks` 테이블 생성 (V6 마이그레이션)
- [x] `StockEntity.kt` JPA Entity 생성
- [x] `StockJpaRepository.kt` Repository 생성
- [x] 데이터 마이그레이션 (35개 stocks)

### 🎯 다음 단계

#### 1. 비즈니스 로직 적용
**Option A: Adapter 패턴 (권장)**
- `StockPersistenceAdapter` 생성
- Dual-write 지원 (RDB Primary, MongoDB Secondary)
- `db.read-source`, `db.dual-write` 설정 기반 동작

**Option B: Service Layer 직접 사용**
- `StockService` 생성
- `StockJpaRepository` 직접 사용

#### 2. MongoDB 단계적 제거
- **Phase 1**: Dual-write 모드 (현재)
  - `db.dual-write: true`
  - RDB Primary, MongoDB Secondary
- **Phase 2**: RDB Only 모드
  - `db.dual-write: false`
  - MongoDB 쓰기 중단
- **Phase 3**: MongoDB 데이터 삭제
  - 검증 후 `stocks` 컬렉션 삭제

#### 3. 참조 관계 추가 (선택)
```sql
ALTER TABLE trades
ADD CONSTRAINT fk_trades_stock
    FOREIGN KEY (ticker)
    REFERENCES stocks(ticker)
    ON DELETE RESTRICT;
```

### 📚 참고 문서
- `claudedocs/Stock_마이그레이션_MongoDB_to_PostgreSQL.md`

### ⚠️ 주의사항
- MongoDB `stocks` 컬렉션은 현재 유지 (dual-write 대비)
- RDB 읽기 우선: `db.read-source: rdb`
- 초기 데이터는 `V7__Insert_Initial_Stocks_Data.sql`로 관리

### ✅ 체크리스트
- [ ] `StockPersistenceAdapter` 또는 `StockService` 구현
- [ ] 기존 Stock 사용 지점 RDB로 전환
- [ ] Dual-write 모드 테스트
- [ ] 성능 모니터링 (RDB vs MongoDB)
- [ ] 충분한 검증 후 MongoDB 제거
