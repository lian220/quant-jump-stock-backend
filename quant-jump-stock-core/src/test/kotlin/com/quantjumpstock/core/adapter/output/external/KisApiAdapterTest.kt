package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountTypeEntityEnum
import com.quantjumpstock.core.adapter.output.persistence.jpa.BrokerEntityEnum
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserBrokerAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserBrokerAccountJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserRole
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserStatus
import com.quantjumpstock.core.infrastructure.security.AppSecretCipher
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * KisApiAdapter 핵심 동작 검증.
 *
 * Phase 1D (2026-05-19): user_kis_accounts → user_broker_accounts 마이그 반영.
 *  - 계좌 lookup 은 UserBrokerAccountJpaRepository.findFirstActiveKisByUserLoginId 로 변경.
 *  - AppSecret 복호화는 GCM 단일 컬럼 (v1 ECB fallback 인자는 빈 문자열).
 *  - RestClient 캐시 키는 여전히 (userId, accountType) 복합키.
 */
class KisApiAdapterTest {

    private val userBrokerRepo: UserBrokerAccountJpaRepository = mockk()
    private val cipher: AppSecretCipher = mockk()
    private val tokenRepo: KisTokenJpaRepository = mockk(relaxed = true)
    private val tokenIssuer: KisTokenIssuer = mockk(relaxed = true)

    private val adapter = KisApiAdapter(userBrokerRepo, cipher, tokenRepo, tokenIssuer)

    @Test
    fun `getOrCreateRestClient 는 (userId, accountType) 복합키로 캐시한다`() {
        val userId = "trader-${System.nanoTime()}"

        val mockClient = adapter.getOrCreateRestClient(userId, AccountTypeEntityEnum.MOCK)
        val realClient = adapter.getOrCreateRestClient(userId, AccountTypeEntityEnum.REAL)
        val mockClientAgain = adapter.getOrCreateRestClient(userId, AccountTypeEntityEnum.MOCK)

        assertThat(mockClient)
            .describedAs("같은 userId 라도 accountType 이 다르면 다른 RestClient (baseURL 분리)")
            .isNotSameAs(realClient)
        assertThat(mockClient)
            .describedAs("동일한 (userId, accountType) 은 동일 인스턴스 재사용")
            .isSameAs(mockClientAgain)
    }

    @Test
    fun `decryptAppSecret 은 entity 의 GCM 컬럼을 cipher 에 위임 (v1 은 빈 문자열)`() {
        val entity = userBrokerEntity(gcmCipher = "GCM_V2_CIPHER")
        every { cipher.decrypt("GCM_V2_CIPHER", "") } returns "PLAIN_FROM_GCM"

        val result = adapter.decryptAppSecret(entity)

        assertThat(result).isEqualTo("PLAIN_FROM_GCM")
        verify(exactly = 1) { cipher.decrypt("GCM_V2_CIPHER", "") }
    }

    private fun userBrokerEntity(
        gcmCipher: String,
        accountType: AccountTypeEntityEnum = AccountTypeEntityEnum.MOCK,
    ): UserBrokerAccountEntity {
        val user = UserEntity(
            id = 1L,
            userId = "trader-${System.nanoTime()}",
            name = "테스트",
            email = "t@example.com",
            passwordHash = "hash",
            role = UserRole.USER,
            status = UserStatus.ACTIVE,
        )
        return UserBrokerAccountEntity(
            id = 1L,
            user = user,
            broker = BrokerEntityEnum.KIS,
            accountType = accountType,
            accountNumber = "12345678",
            accountProductCode = "01",
            appKey = "PSKxAPPKEY",
            appSecretEncrypted = gcmCipher,
            enabled = true,
        )
    }
}
