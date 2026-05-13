package com.quantjumpstock.core.application.user

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
import com.quantjumpstock.core.testsupport.KisAccountTestSupport
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * Phase 1A PRE-요구사항 Task 7 — UserKisAccountService 의 GCM(v2) 컬럼 전환 검증.
 *
 * Step 1 시점에서는 신규 등록이 여전히 v1(ECB) 컬럼에 저장되므로 본 테스트는 실패해야 한다.
 * Step 3 (Service + 도메인 + 어댑터 매핑 수정) 이후 통과한다.
 *
 * AppSecretReencryptionRunnerTest 와 동일하게 클래스 레벨 @Transactional 을 사용하지 않고
 * TransactionTemplate 으로 fixture 를 commit + AfterEach 에서 명시적 cleanup 한다.
 */
@SpringBootTest(properties = ["spring.flyway.enabled=true"])
class UserKisAccountServiceTest {

    @Autowired
    lateinit var service: UserKisAccountService

    @Autowired
    lateinit var userRepo: UserJpaRepository

    @Autowired
    lateinit var kisRepo: UserKisAccountJpaRepository

    @Autowired
    lateinit var gcm: EncryptionServiceGcm

    @Autowired
    lateinit var txManager: PlatformTransactionManager

    @PersistenceContext
    lateinit var em: EntityManager

    private val tx by lazy { TransactionTemplate(txManager) }
    private val support by lazy { KisAccountTestSupport(userRepo, kisRepo, txManager) }

    @AfterEach
    fun cleanup() = support.cleanup()

    @Test
    fun `registerOrUpdate 후 v2 컬럼이 채워지고 v1은 빈 문자열이다`() {
        val userId = createTestUser()
        val plainSecret = "MY_NEW_SECRET_${System.nanoTime()}"
        val request = KisAccountRequest(
            appKey = "PSKxTEST",
            appSecret = plainSecret,
            accountNumber = "12345678",
            accountProductCode = "01",
            accountType = KisAccountType.MOCK,
            enabled = true
        )

        tx.executeWithoutResult {
            service.registerOrUpdateKisAccount(userId, request)
        }

        em.clear()

        val entity = kisRepo.findByUserUserId(userId).orElseThrow()
        support.trackKisAccount(entity.id!!)

        assertThat(entity.appSecretEncryptedV2)
            .describedAs("신규 등록은 v2(GCM) 컬럼에 저장되어야 한다")
            .isNotNull()
        assertThat(gcm.decrypt(entity.appSecretEncryptedV2!!))
            .isEqualTo(plainSecret)
        assertThat(entity.appSecretEncrypted)
            .describedAs("v1(ECB) 컬럼은 신규 등록 시 빈 문자열로 마킹된다 (Task 8/9 에서 drop)")
            .isEmpty()
    }

    @Test
    fun `getDecryptedAppSecret 는 v2 에서 복호화한다`() {
        val userId = createTestUser()
        val plain = "PLAIN_SECRET_${System.nanoTime()}"
        tx.executeWithoutResult {
            service.registerOrUpdateKisAccount(
                userId,
                KisAccountRequest(
                    appKey = "PSKxTEST",
                    appSecret = plain,
                    accountNumber = "12345678",
                    accountProductCode = "01",
                    accountType = KisAccountType.MOCK,
                    enabled = true
                )
            )
        }

        em.clear()

        // cleanup 용 entity id 수집
        kisRepo.findByUserUserId(userId).ifPresent { support.trackKisAccount(it.id!!) }

        val decrypted = service.getDecryptedAppSecret(userId)
        assertThat(decrypted).isEqualTo(plain)
    }

    private fun createTestUser(): String =
        support.createUser(prefix = "kis_svc_test", transactional = true).userId
}
