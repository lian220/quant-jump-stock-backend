package com.quantjumpstock.core.infrastructure.migration

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Phase 1A PRE-요구사항 P3 — ECB → GCM 재암호화 ApplicationRunner.
 *
 * 부팅 시 [UserKisAccountJpaRepository] 에서 `app_secret_encrypted_v2` 컬럼이 비어 있는
 * row를 조회 → 기존 ECB(legacy) 컬럼을 복호화 → GCM 으로 재암호화 → V2 컬럼에 저장.
 *
 * 특성:
 * - **Idempotent**: V2 가 이미 채워진 row 는 finder 단계에서 제외되므로 영향 없음.
 * - **Fail-soft**: 개별 row 의 복호화/재암호화 실패는 로그만 남기고 다음 row 진행 (서비스
 *   부팅 자체를 차단하지 않음). 운영 환경에서 단일 사용자 키 손상이 전체 서비스 다운으로
 *   확산되지 않도록 함.
 * - **Profile 제한 없음**: 모든 환경에서 1회 실행. V2 컬럼이 채워진 후에는 finder 가 빈
 *   리스트를 반환하므로 추가 비용은 단일 SELECT 쿼리.
 *
 * Task 7 (UserKisAccountService 의 V2 우선 read-path 전환) 과 Task 8 (V61 + 컬럼 drop)
 * 이후에는 본 Runner 자체를 제거.
 */
@Component
class AppSecretReencryptionRunner(
    private val repo: UserKisAccountJpaRepository,
    private val legacy: EncryptionService,
    private val gcm: EncryptionServiceGcm
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) = run()

    /**
     * 외부에서도 호출 가능한 수동 트리거 진입점 (테스트 / 운영자 트리거 용도).
     */
    @Transactional
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
            try {
                val plain = legacy.decrypt(entity.appSecretEncrypted)
                entity.appSecretEncryptedV2 = gcm.encrypt(plain)
                repo.save(entity)
                ok++
            } catch (ex: Exception) {
                log.error(
                    "재암호화 실패: id={} userId={} reason={}",
                    entity.id, entity.user.id, ex.message
                )
                fail++
            }
        }
        log.info("AppSecretReencryptionRunner: 완료 (성공={}, 실패={})", ok, fail)
    }
}
