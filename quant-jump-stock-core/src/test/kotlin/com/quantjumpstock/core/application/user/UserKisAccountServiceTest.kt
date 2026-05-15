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

    // ─────────────────────────────────────────────────
    //  A+ 모델 (단일 활성 + 7일 휴지통 soft delete) 테스트
    // ─────────────────────────────────────────────────

    @Test
    fun `같은 accountType 으로 register 는 update — 휴지통 발생 안 함`() {
        val userId = createTestUser()
        val first = registerRequest("PSKxAAAAAAAA", "secret_first_pass", KisAccountType.MOCK)
        val second = registerRequest("PSKxBBBBBBBB", "secret_second_pass", KisAccountType.MOCK)

        tx.executeWithoutResult { service.registerOrUpdateKisAccount(userId, first) }
        tx.executeWithoutResult { service.registerOrUpdateKisAccount(userId, second) }
        em.clear()

        val active = kisRepo.findActiveByUserUserId(userId).orElseThrow()
        support.trackKisAccount(active.id!!)
        val trashedList = kisRepo.findTrashedByUserUserId(userId)

        assertThat(active.appKey).isEqualTo("PSKxBBBBBBBB")
        assertThat(active.deletedAt).isNull()
        assertThat(trashedList).describedAs("같은 모드 update 는 휴지통 발생 안 함").isEmpty()
    }

    @Test
    fun `다른 accountType 으로 register 는 기존을 휴지통으로 이동 + 신규 활성 INSERT`() {
        val userId = createTestUser()
        val mock = registerRequest("PSKxMOCKKEY1", "secret_for_mock", KisAccountType.MOCK)
        val real = registerRequest("PSKxREALKEY1", "secret_for_real", KisAccountType.REAL)

        tx.executeWithoutResult { service.registerOrUpdateKisAccount(userId, mock) }
        tx.executeWithoutResult { service.registerOrUpdateKisAccount(userId, real) }
        em.clear()

        val active = kisRepo.findActiveByUserUserId(userId).orElseThrow()
        val trashed = kisRepo.findTrashedByUserUserId(userId).firstOrNull()
            ?: error("휴지통 row 가 있어야 한다")
        support.trackKisAccount(active.id!!)
        support.trackKisAccount(trashed.id!!)

        assertThat(active.accountType).isEqualTo(com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType.REAL)
        assertThat(trashed.accountType).isEqualTo(com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType.MOCK)
        assertThat(trashed.deletedAt).isNotNull()
    }

    @Test
    fun `DELETE 의미의 softDelete — active 가 휴지통으로 이동`() {
        val userId = createTestUser()
        tx.executeWithoutResult {
            service.registerOrUpdateKisAccount(userId, registerRequest("PSKxAAAAAAAA", "secret_to_delete", KisAccountType.MOCK))
        }
        tx.executeWithoutResult { service.softDeleteActiveKisAccount(userId) }
        em.clear()

        assertThat(kisRepo.findActiveByUserUserId(userId)).isEmpty
        val trashed = kisRepo.findTrashedByUserUserId(userId).firstOrNull()
            ?: error("휴지통 row 가 있어야 한다")
        support.trackKisAccount(trashed.id!!)
        assertThat(trashed.deletedAt).isNotNull()
        assertThat(trashed.enabled).isFalse()
    }

    @Test
    fun `restore 후 휴지통이 활성으로 복구 — 기존 활성 없을 때`() {
        val userId = createTestUser()
        tx.executeWithoutResult {
            service.registerOrUpdateKisAccount(userId, registerRequest("PSKxAAAAAAAA", "secret_to_restore", KisAccountType.MOCK))
        }
        tx.executeWithoutResult { service.softDeleteActiveKisAccount(userId) }
        em.clear()

        val response = tx.execute { service.restoreFromTrash(userId) }!!
        em.clear()

        val active = kisRepo.findActiveByUserUserId(userId).orElseThrow()
        support.trackKisAccount(active.id!!)
        assertThat(active.deletedAt).isNull()
        assertThat(active.enabled).isTrue()
        assertThat(response.appKey).startsWith("PSKx")
            .describedAs("응답은 마스킹된 appKey 반환 (BE-1)")
    }

    @Test
    fun `restore 스왑 — 현재 활성이 있으면 휴지통으로 교체`() {
        val userId = createTestUser()
        tx.executeWithoutResult {
            service.registerOrUpdateKisAccount(userId, registerRequest("PSKxOLDOLDOLD", "secret_old_one", KisAccountType.MOCK))
        }
        tx.executeWithoutResult { service.softDeleteActiveKisAccount(userId) }
        tx.executeWithoutResult {
            service.registerOrUpdateKisAccount(userId, registerRequest("PSKxNEWNEWNEW", "secret_new_one", KisAccountType.MOCK))
        }
        tx.executeWithoutResult { service.restoreFromTrash(userId) }
        em.clear()

        val active = kisRepo.findActiveByUserUserId(userId).orElseThrow()
        val trashed = kisRepo.findTrashedByUserUserId(userId).firstOrNull()
            ?: error("스왑 후 휴지통 row 가 있어야 한다")
        support.trackKisAccount(active.id!!)
        support.trackKisAccount(trashed.id!!)

        assertThat(active.appKey).isEqualTo("PSKxOLDOLDOLD")
            .describedAs("이전 키가 복원되어 활성 row 가 된다")
        assertThat(trashed.appKey).isEqualTo("PSKxNEWNEWNEW")
            .describedAs("직전 활성 키가 휴지통으로 이동")
    }

    @Test
    fun `getKisAccount 응답은 마스킹된 appKey 와 계좌번호 반환 (BE-1)`() {
        val userId = createTestUser()
        tx.executeWithoutResult {
            service.registerOrUpdateKisAccount(userId, registerRequest("PSKxabcdefghIJKLMNOP", "secret_for_mask", KisAccountType.MOCK))
        }
        em.clear()
        kisRepo.findActiveByUserUserId(userId).ifPresent { support.trackKisAccount(it.id!!) }

        val response = service.getKisAccount(userId)

        assertThat(response.appKey).contains("****")
            .describedAs("appKey 는 마스킹 (앞4+뒤4 외 별표)")
        assertThat(response.accountNumber).contains("****")
            .describedAs("accountNumber 는 마스킹 (뒤4+상품코드 외 별표)")
        assertThat(response.accountNumber).endsWith("-01")
    }

    @Test
    fun `getTrashedKisAccount 는 휴지통 row 응답, 없으면 null`() {
        val userId = createTestUser()
        assertThat(service.getTrashedKisAccount(userId)).isNull()

        tx.executeWithoutResult {
            service.registerOrUpdateKisAccount(userId, registerRequest("PSKxAAAAAAAA", "secret_trash_test", KisAccountType.MOCK))
        }
        tx.executeWithoutResult { service.softDeleteActiveKisAccount(userId) }
        em.clear()
        kisRepo.findTrashedByUserUserId(userId).firstOrNull()?.let { support.trackKisAccount(it.id!!) }

        val trashedResp = service.getTrashedKisAccount(userId)
        assertThat(trashedResp).isNotNull
        assertThat(trashedResp!!.deletedAt).isNotNull()
    }

    @Test
    fun `hardDeleteExpired — 7일 경과 row 만 hard delete`() {
        val userId = createTestUser()
        tx.executeWithoutResult {
            service.registerOrUpdateKisAccount(userId, registerRequest("PSKxAAAAAAAA", "secret_expire_test", KisAccountType.MOCK))
        }
        tx.executeWithoutResult { service.softDeleteActiveKisAccount(userId) }
        em.clear()

        // 휴지통 row 의 deleted_at 을 8일 전으로 인위적으로 변경 (만료 시뮬레이션).
        val trashedId = kisRepo.findTrashedByUserUserId(userId).first().id!!
        tx.executeWithoutResult {
            em.createNativeQuery("UPDATE user_kis_accounts SET deleted_at = :ts WHERE id = :id")
                .setParameter("ts", java.time.LocalDateTime.now().minusDays(8))
                .setParameter("id", trashedId)
                .executeUpdate()
        }
        em.clear()

        val deleted = tx.execute { service.hardDeleteExpired() }!!
        assertThat(deleted).isEqualTo(1)
        assertThat(kisRepo.findById(trashedId)).isEmpty
    }

    private fun registerRequest(appKey: String, secret: String, type: KisAccountType): KisAccountRequest =
        KisAccountRequest(
            appKey = appKey,
            appSecret = secret,
            accountNumber = "12345678",
            accountProductCode = "01",
            accountType = type,
            enabled = true
        )

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
