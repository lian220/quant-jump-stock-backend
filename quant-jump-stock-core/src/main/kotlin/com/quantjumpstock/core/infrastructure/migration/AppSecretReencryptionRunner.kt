package com.quantjumpstock.core.infrastructure.migration

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Phase 1A PRE-요구사항 P3 — ECB → GCM 재암호화 ApplicationRunner.
 *
 * 부팅 시 [UserKisAccountJpaRepository] 에서 `app_secret_encrypted_v2` 컬럼이 비어 있는
 * row 를 조회 → 각 row 를 [AppSecretRowReencryptionService.reencryptOne] 에 위임.
 *
 * 트랜잭션 경계는 [AppSecretRowReencryptionService] 에서 `REQUIRES_NEW` 로 row 단위 분리.
 * - Runner 자체에는 `@Transactional` 을 두지 않음 (self-invocation bypass 회피 + 명시적 위임).
 * - 단일 row 실패가 다른 row 의 커밋에 영향을 주지 않음 (진정한 fail-soft).
 *
 * 특성:
 * - **Idempotent**: V2 가 이미 채워진 row 는 finder 단계에서 제외됨. 추가 안전장치로
 *   Service 레벨에서도 V2 존재 시 early-out.
 * - **Fail-soft**: 개별 row 의 복호화/재암호화 실패는 로그만 남기고 다음 row 진행
 *   (서비스 부팅 자체를 차단하지 않음).
 * - **Profile 제한 없음**: 모든 환경에서 1회 실행. V2 컬럼이 채워진 후에는 finder 가 빈
 *   리스트를 반환하므로 추가 비용은 단일 SELECT 쿼리.
 *
 * Task 7 (UserKisAccountService 의 V2 우선 read-path 전환) 과 Task 8 (V61 + 컬럼 drop)
 * 이후에는 본 Runner / Service 자체를 제거.
 */
@Component
class AppSecretReencryptionRunner(
    private val repo: UserKisAccountJpaRepository,
    private val rowService: AppSecretRowReencryptionService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) = run()

    /**
     * 외부에서도 호출 가능한 수동 트리거 진입점 (테스트 / 운영자 트리거 용도).
     */
    fun run() {
        val targets = repo.findAllByAppSecretEncryptedV2IsNull()
        if (targets.isEmpty()) {
            log.info("AppSecretReencryptionRunner: 재암호화 대상 0건. 종료.")
            return
        }
        log.info("AppSecretReencryptionRunner: {}건 재암호화 시작", targets.size)

        var ok = 0
        var fail = 0
        for (entity in targets) {
            val id = entity.id ?: continue
            try {
                rowService.reencryptOne(id)
                ok++
            } catch (ex: Exception) {
                // entity.user.id 등 lazy 연관 접근은 세션 경계 밖에서 LazyInitializationException
                // 위험이 있으므로 PK(Long) 만 로깅.
                log.error("재암호화 실패: id={} reason={}", id, ex.message)
                fail++
            }
        }
        log.info("AppSecretReencryptionRunner: 완료 (성공={}, 실패={})", ok, fail)
    }
}
