package com.quantjumpstock.core.infrastructure.migration

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 1A PRE-요구사항 P3 단계 6 — ECB → GCM 재암호화 ApplicationRunner 통합 테스트.
 *
 * 본 테스트는 [AppSecretRowReencryptionService] 가 `REQUIRES_NEW` 로 별도 트랜잭션을
 * 커밋하는 동작을 검증해야 하므로 클래스 레벨 `@Transactional` 을 사용하지 않는다.
 * (외부 트랜잭션이 active 인 상태에서는 `REQUIRES_NEW` 내부 트랜잭션이 외부 트랜잭션의
 *  미커밋 INSERT 를 볼 수 없어 row 조회가 실패한다.)
 *
 * 대신 [TransactionTemplate] 으로 fixture 를 commit 하고, [AfterEach] 에서 생성한
 * row 들을 명시적으로 cleanup 한다.
 */
@SpringBootTest(properties = ["spring.flyway.enabled=true"])
class AppSecretReencryptionRunnerTest {

    @Autowired
    lateinit var userRepo: UserJpaRepository

    @Autowired
    lateinit var kisRepo: UserKisAccountJpaRepository

    @Autowired
    lateinit var legacy: EncryptionService

    @Autowired
    lateinit var gcm: EncryptionServiceGcm

    @Autowired
    lateinit var runner: AppSecretReencryptionRunner

    @Autowired
    lateinit var txManager: PlatformTransactionManager

    @PersistenceContext
    lateinit var em: EntityManager

    private val createdKisIds = mutableListOf<Long>()
    private val createdUserIds = mutableListOf<Long>()

    private val tx by lazy { TransactionTemplate(txManager) }

    @AfterEach
    fun cleanup() {
        // 명시적 cleanup: 본 테스트가 생성한 row 만 삭제 (다른 테스트의 fixture 는 보존)
        tx.executeWithoutResult {
            createdKisIds.forEach { kisRepo.deleteById(it) }
            createdUserIds.forEach { userRepo.deleteById(it) }
        }
        createdKisIds.clear()
        createdUserIds.clear()
    }

    @Test
    fun `ECB로 암호화된 row가 GCM으로 재암호화되어 v2에 저장된다`() {
        val plain = "MY_KIS_APP_SECRET_FOR_TEST_${System.nanoTime()}"
        val ecbCipher = legacy.encrypt(plain)

        val kisIdRef = AtomicReference<Long>()
        tx.executeWithoutResult {
            val user = createTestUser()
            createdUserIds += user.id!!
            val saved = kisRepo.save(
                UserKisAccountEntity(
                    user = user,
                    appKey = "PSKxTEST",
                    appSecretEncrypted = ecbCipher,
                    appSecretEncryptedV2 = null,
                    accountNumber = "12345678",
                    accountProductCode = "01",
                    accountType = KisAccountType.MOCK,
                    enabled = true
                )
            )
            kisIdRef.set(saved.id!!)
            createdKisIds += saved.id!!
        }

        runner.executeReencryption()

        // 1차 캐시 클리어 → 실제 DB 값 검증 (in-memory dirty entity 회피)
        em.clear()

        val updated = tx.execute {
            kisRepo.findById(kisIdRef.get()).orElseThrow()
        }!!
        assertThat(updated.appSecretEncryptedV2).isNotNull()
        assertThat(gcm.decrypt(updated.appSecretEncryptedV2!!)).isEqualTo(plain)
        // 기존 ECB 컬럼은 보존되어야 함 (V62 drop 전까지 회귀 보호)
        assertThat(updated.appSecretEncrypted).isEqualTo(ecbCipher)
    }

    @Test
    fun `이미 v2가 채워진 row는 건드리지 않는다 (idempotent)`() {
        val plain = "PRESERVED_SECRET_${System.nanoTime()}"
        val v2 = gcm.encrypt(plain)
        val oldEcb = legacy.encrypt("OLD_SECRET_${System.nanoTime()}")

        val kisIdRef = AtomicReference<Long>()
        tx.executeWithoutResult {
            val user = createTestUser()
            createdUserIds += user.id!!
            val saved = kisRepo.save(
                UserKisAccountEntity(
                    user = user,
                    appKey = "PSKxTEST",
                    appSecretEncrypted = oldEcb,
                    appSecretEncryptedV2 = v2,
                    accountNumber = "12345678",
                    accountProductCode = "01",
                    accountType = KisAccountType.MOCK,
                    enabled = true
                )
            )
            kisIdRef.set(saved.id!!)
            createdKisIds += saved.id!!
        }

        runner.executeReencryption()

        em.clear()

        val updated = tx.execute {
            kisRepo.findById(kisIdRef.get()).orElseThrow()
        }!!
        assertThat(updated.appSecretEncryptedV2).isEqualTo(v2)
        assertThat(updated.appSecretEncrypted).isEqualTo(oldEcb)
    }

    private fun createTestUser(): UserEntity {
        val unique = UUID.randomUUID().toString().take(8)
        return userRepo.save(
            UserEntity(
                userId = "kis_reenc_$unique",
                name = "재암호화 테스트 사용자",
                email = "kis_reenc_${unique}@example.com",
                passwordHash = "hashed_password"
            )
        )
    }
}
