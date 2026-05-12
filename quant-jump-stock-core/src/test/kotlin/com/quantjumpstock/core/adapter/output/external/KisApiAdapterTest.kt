package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserStatus
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserRole
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Optional

/**
 * Phase 1A PRE Task 10 — KisApiAdapter 의 두 가지 결함 검증.
 *
 * 1. `restClientCache` 단일키(userId) 결함:
 *    같은 userId 가 계정 타입을 MOCK→REAL 로 바꾸면 캐시된 RestClient 의 baseURL 이
 *    이전 환경(MOCK)으로 고정되어 신규 거래가 잘못된 엔드포인트로 향한다.
 *    → 캐시 키를 (userId, accountType) 복합키로 분리해야 한다.
 *
 * 2. AppSecret 복호화가 항상 v1(ECB) 컬럼만 사용하는 결함 (Task 7 도입 후 회귀):
 *    Task 7 이후 신규 등록은 `appSecretEncrypted = ""` + `appSecretEncryptedV2 = GCM(...)` 로 저장됨.
 *    어댑터가 v1 만 보면 빈 시크릿으로 KIS 인증이 실패한다.
 *    → v2 우선, v2 가 null/공백이면 v1 legacy fallback 으로 복호화해야 한다.
 *
 * Step 1 시점: `internal fun getOrCreateRestClient(userId, accountType)` / `internal fun decryptAppSecret(entity)`
 * 가 노출되어 있지 않으므로 컴파일 실패 (intentional, TDD).
 */
class KisApiAdapterTest {

    private val userKisRepo: UserKisAccountJpaRepository = mockk()
    private val legacy: EncryptionService = mockk()
    private val gcm: EncryptionServiceGcm = mockk()
    private val tokenRepo: KisTokenJpaRepository = mockk(relaxed = true)
    private val tokenIssuer: KisTokenIssuer = mockk(relaxed = true)

    private val adapter = KisApiAdapter(userKisRepo, legacy, gcm, tokenRepo, tokenIssuer)

    @Test
    fun `getOrCreateRestClient 는 (userId, accountType) 복합키로 캐시한다`() {
        val userId = "trader-${System.nanoTime()}"

        val mockClient = adapter.getOrCreateRestClient(userId, KisAccountType.MOCK)
        val realClient = adapter.getOrCreateRestClient(userId, KisAccountType.REAL)
        val mockClientAgain = adapter.getOrCreateRestClient(userId, KisAccountType.MOCK)

        assertThat(mockClient)
            .describedAs("같은 userId 라도 accountType 이 다르면 다른 RestClient 인스턴스를 사용해야 한다 (baseURL 분리)")
            .isNotSameAs(realClient)
        assertThat(mockClient)
            .describedAs("동일한 (userId, accountType) 은 동일 인스턴스 재사용")
            .isSameAs(mockClientAgain)
    }

    @Test
    fun `decryptAppSecret 은 v2 가 있으면 GCM 으로 복호화한다`() {
        val entity = userKisEntity(v1Cipher = "ECB_V1", v2Cipher = "GCM_V2")
        every { gcm.decrypt("GCM_V2") } returns "PLAIN_FROM_GCM"

        val result = adapter.decryptAppSecret(entity)

        assertThat(result).isEqualTo("PLAIN_FROM_GCM")
        verify(exactly = 0) { legacy.decrypt(any()) }
    }

    @Test
    fun `decryptAppSecret 은 v2 가 null 이면 legacy ECB 로 fallback 한다`() {
        val entity = userKisEntity(v1Cipher = "ECB_V1", v2Cipher = null)
        every { legacy.decrypt("ECB_V1") } returns "PLAIN_FROM_LEGACY"

        val result = adapter.decryptAppSecret(entity)

        assertThat(result).isEqualTo("PLAIN_FROM_LEGACY")
        verify(exactly = 0) { gcm.decrypt(any()) }
    }

    @Test
    fun `decryptAppSecret 은 v2 가 빈 문자열이어도 legacy 로 fallback 한다`() {
        val entity = userKisEntity(v1Cipher = "ECB_V1", v2Cipher = "")
        every { legacy.decrypt("ECB_V1") } returns "PLAIN_FROM_LEGACY"

        val result = adapter.decryptAppSecret(entity)

        assertThat(result).isEqualTo("PLAIN_FROM_LEGACY")
        verify(exactly = 0) { gcm.decrypt(any()) }
    }

    private fun userKisEntity(
        v1Cipher: String,
        v2Cipher: String?,
        accountType: KisAccountType = KisAccountType.MOCK
    ): UserKisAccountEntity {
        val user = UserEntity(
            id = 1L,
            userId = "trader-${System.nanoTime()}",
            name = "테스트",
            email = "t@example.com",
            passwordHash = "hash",
            role = UserRole.USER,
            status = UserStatus.ACTIVE
        )
        return UserKisAccountEntity(
            id = 1L,
            user = user,
            appKey = "PSKxAPPKEY",
            appSecretEncrypted = v1Cipher,
            appSecretEncryptedV2 = v2Cipher,
            accountNumber = "12345678",
            accountProductCode = "01",
            accountType = accountType,
            enabled = true
        )
    }
}
