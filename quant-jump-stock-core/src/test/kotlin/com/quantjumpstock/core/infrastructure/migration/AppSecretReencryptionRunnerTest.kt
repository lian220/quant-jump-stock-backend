package com.quantjumpstock.core.infrastructure.migration

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Phase 1A PRE-요구사항 P3 단계 6 — ECB → GCM 재암호화 ApplicationRunner 통합 테스트.
 *
 * @Transactional 으로 각 테스트의 변경분이 롤백된다 (Runner 내부에서 별도 트랜잭션을
 * 시작하지 않으므로 Spring Test 의 기본 롤백이 적용됨).
 */
@SpringBootTest(properties = ["spring.flyway.enabled=true"])
@Transactional
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

    @Test
    fun `ECB로 암호화된 row가 GCM으로 재암호화되어 v2에 저장된다`() {
        val user = createTestUser()
        val plain = "MY_KIS_APP_SECRET_FOR_TEST_${System.nanoTime()}"
        val ecbCipher = legacy.encrypt(plain)

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

        runner.run()

        val updated = kisRepo.findById(saved.id!!).orElseThrow()
        assertThat(updated.appSecretEncryptedV2).isNotNull()
        assertThat(gcm.decrypt(updated.appSecretEncryptedV2!!)).isEqualTo(plain)
        // 기존 ECB 컬럼은 보존되어야 함 (V62 drop 전까지 회귀 보호)
        assertThat(updated.appSecretEncrypted).isEqualTo(ecbCipher)
    }

    @Test
    fun `이미 v2가 채워진 row는 건드리지 않는다 (idempotent)`() {
        val user = createTestUser()
        val plain = "PRESERVED_SECRET_${System.nanoTime()}"
        val v2 = gcm.encrypt(plain)

        val saved = kisRepo.save(
            UserKisAccountEntity(
                user = user,
                appKey = "PSKxTEST",
                appSecretEncrypted = legacy.encrypt("OLD_SECRET"),
                appSecretEncryptedV2 = v2,
                accountNumber = "12345678",
                accountProductCode = "01",
                accountType = KisAccountType.MOCK,
                enabled = true
            )
        )

        runner.run()

        val updated = kisRepo.findById(saved.id!!).orElseThrow()
        assertThat(updated.appSecretEncryptedV2).isEqualTo(v2)
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
