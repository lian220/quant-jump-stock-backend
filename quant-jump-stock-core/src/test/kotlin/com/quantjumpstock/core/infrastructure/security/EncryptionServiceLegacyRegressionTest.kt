package com.quantjumpstock.core.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Phase 1A PRE — 회귀 보호 테스트.
 *
 * 기존 ECB 모드 EncryptionService의 동작을 잠궈서, GCM 전환 작업 동안에도 V1 데이터의
 * 복호화가 그대로 가능함을 보장. Task 7 후 [UserKisAccountService]가 V2(GCM) 컬럼을
 * 사용하기 시작해도, 이 테스트는 EncryptionService(legacy) 자체가 유지되는 한 통과해야 함.
 *
 * Task 8(V61, 구 컬럼 drop) 시점에 EncryptionService와 함께 본 테스트 삭제.
 */
class EncryptionServiceLegacyRegressionTest {

    companion object {
        // TEST_ONLY: 운영 키와 무관한 32+ 바이트 더미. 실제 키 형태와 닮지 않게 의도적으로 길게.
        private const val TEST_ONLY_LEGACY_KEY =
            "TEST_ONLY_DO_NOT_USE_IN_PRODUCTION_LEGACY_ECB_REGRESSION_FIXTURE"
    }

    private val service = EncryptionService(TEST_ONLY_LEGACY_KEY)

    @Test
    fun `ECB 모드에서 같은 평문은 같은 암호문을 만든다 (현재 동작 보존)`() {
        val cipher1 = service.encrypt("KIS_APP_SECRET_TEST_VALUE_12345")
        val cipher2 = service.encrypt("KIS_APP_SECRET_TEST_VALUE_12345")
        assertThat(cipher1).isEqualTo(cipher2)
    }

    @Test
    fun `암호화-복호화 라운드트립`() {
        val plain = "KIS_APP_SECRET_TEST_VALUE_12345"
        val encrypted = service.encrypt(plain)
        assertThat(service.decrypt(encrypted)).isEqualTo(plain)
    }
}
