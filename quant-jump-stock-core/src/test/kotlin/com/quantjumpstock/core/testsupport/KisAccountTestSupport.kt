package com.quantjumpstock.core.testsupport

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * KIS 계좌 통합 테스트(@SpringBootTest) 공용 픽스처.
 *
 * Phase 1A PRE 패널 리뷰 (refactoring-expert R5):
 *  - UserKisAccountServiceTest / AppSecretReencryptionRunnerTest 가 동일한
 *    `createTestUser` + `createdKisIds`/`createdUserIds` cleanup 패턴을 중복 보유.
 *  - 본 클래스로 통합해 테스트 클래스 boilerplate 제거 + 향후 동일 패턴 테스트가
 *    추가될 때 즉시 재사용 가능.
 *
 * 사용 예:
 * ```kotlin
 * private val support by lazy { KisAccountTestSupport(userRepo, kisRepo, txManager) }
 *
 * @AfterEach fun cleanup() = support.cleanup()
 *
 * @Test fun something() {
 *     val user = support.createUser("kis_svc")
 *     // ... user 사용 ...
 *     support.trackKisAccount(savedKisId)
 *     // cleanup() 가 KIS row → User row 순으로 자동 삭제
 * }
 * ```
 */
class KisAccountTestSupport(
    private val userRepo: UserJpaRepository,
    private val kisRepo: UserKisAccountJpaRepository,
    txManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(txManager)
    private val createdKisIds = mutableListOf<Long>()
    private val createdUserIds = mutableListOf<Long>()

    /**
     * 테스트용 [UserEntity] 생성 + commit. 자동으로 cleanup 추적 목록에 등록된다.
     *
     * @param prefix `userId` / `email` 접두사 (테스트 의도 식별용, 예: "kis_svc", "kis_reenc")
     * @param transactional `true` 면 [TransactionTemplate] 으로 commit. 기본 `false` (일반 save).
     *                       AppSecretReencryptionRunnerTest 처럼 `@Transactional` 클래스 외부에서
     *                       commit 보장이 필요하면 `true`.
     */
    fun createUser(prefix: String, transactional: Boolean = false): UserEntity {
        val unique = UUID.randomUUID().toString().take(8)
        val entity = UserEntity(
            userId = "${prefix}_$unique",
            name = "$prefix 테스트 사용자",
            email = "${prefix}_${unique}@example.com",
            passwordHash = "hashed_password",
        )
        val saved = if (transactional) {
            tx.execute { userRepo.save(entity) }!!
        } else {
            userRepo.save(entity)
        }
        createdUserIds += saved.id ?: error("UserEntity id is null after save")
        return saved
    }

    /** 테스트가 직접 저장한 KIS 계정의 PK 를 cleanup 추적 목록에 등록한다. */
    fun trackKisAccount(id: Long) {
        createdKisIds += id
    }

    /**
     * `@AfterEach` 에서 호출. KIS row → User row 순으로 삭제 (FK 제약 때문에).
     */
    fun cleanup() {
        tx.executeWithoutResult {
            createdKisIds.forEach { kisRepo.deleteById(it) }
            createdUserIds.forEach { userRepo.deleteById(it) }
        }
        createdKisIds.clear()
        createdUserIds.clear()
    }
}
