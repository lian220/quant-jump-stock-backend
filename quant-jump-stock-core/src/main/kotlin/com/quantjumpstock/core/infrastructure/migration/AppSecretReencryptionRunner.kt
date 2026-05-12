package com.quantjumpstock.core.infrastructure.migration

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/**
 * Phase 1A PRE-요구사항 P3 — ECB → GCM 재암호화 ApplicationRunner.
 *
 * 부팅 시 [UserKisAccountJpaRepository.findIdsForReencryption] 으로 ID 만 조회 → 각 ID 를
 * [AppSecretRowReencryptionService.reencryptOne] 에 위임 (REQUIRES_NEW 트랜잭션).
 *
 * 트랜잭션 경계는 [AppSecretRowReencryptionService] 에서 row 단위로 분리.
 * - Runner 자체에는 `@Transactional` 을 두지 않음 (self-invocation bypass 회피 + 명시적 위임).
 * - 단일 row 실패가 다른 row 의 커밋에 영향을 주지 않음 (진정한 fail-soft).
 *
 * 특성:
 * - **Idempotent**: V2 가 이미 채워진 row 는 finder 단계에서 제외됨. 추가 안전장치로
 *   Service 레벨에서도 V2 존재 시 early-out.
 * - **Fail-soft**: 개별 row 의 복호화/재암호화 실패는 로그만 남기고 다음 row 진행.
 * - **메모리 친화적**: ID-only projection 으로 entity 전체를 메모리에 올리지 않음.
 *   (이전: `findAllBy...IsNull()` → user LAZY 연관까지 로드돼 OOM 위험)
 * - **Profile 제한 없음**: 모든 환경에서 1회 실행. V2 가 채워진 후에는 finder 가 빈
 *   리스트를 반환하므로 추가 비용은 단일 SELECT 쿼리.
 *
 * 한계 (현 시점):
 * - 다중 인스턴스가 동시에 부팅되면 동일 ID 를 두 인스턴스가 처리할 수 있음 → 중복
 *   암호화 (기능적 정합성은 유지, GCM 의 IV 무작위성으로 결과는 다른 cipher 지만 평문 동일).
 *   향후 Phase 1B 에서 Cloud Scheduler 단일 트리거로 분리 예정.
 *
 * Task 7 (UserKisAccountService 의 V2 우선 read-path 전환) 과 Task 8 (V61 + 컬럼 drop)
 * 이후에는 본 Runner / Service 자체를 제거.
 */
@Component
class AppSecretReencryptionRunner(
    private val repo: UserKisAccountJpaRepository,
    private val rowService: AppSecretRowReencryptionService,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments?) = executeReencryption()

    /**
     * 외부 트리거(테스트/운영자) 에서 직접 호출 가능한 진입점.
     *
     * 이름 변경 사유 (패널 리뷰): 기존 `run()` 은 (1) ApplicationRunner.run(args) 와
     * 시그니처만 다른 동명 메서드라 자기참조 착시를 유발했고, (2) Kotlin 표준 라이브러리의
     * scope function `run` 과 의미 충돌이 있어 의도적으로 `executeReencryption` 으로 명시.
     */
    fun executeReencryption() {
        val targetIds = repo.findIdsForReencryption()
        if (targetIds.isEmpty()) {
            log.info("AppSecretReencryptionRunner: 재암호화 대상 0건. 종료.")
            return
        }
        log.info("AppSecretReencryptionRunner: {}건 재암호화 시작", targetIds.size)

        var ok = 0
        var fail = 0
        for (id in targetIds) {
            try {
                rowService.reencryptOne(id)
                ok++
            } catch (ex: Exception) {
                log.error("재암호화 실패: id={} reason={}", id, ex.message)
                fail++
            }
        }
        log.info("AppSecretReencryptionRunner: 완료 (성공={}, 실패={})", ok, fail)
    }
}
