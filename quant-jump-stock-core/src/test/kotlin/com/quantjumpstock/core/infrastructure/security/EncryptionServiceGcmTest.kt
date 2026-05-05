package com.quantjumpstock.core.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EncryptionServiceGcmTest {

    // 테스트 전용 고정 키 (Base64(0..31) — 정확히 32바이트로 디코딩되어 키 길이 require를 통과)
    private val v2Key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
    private val service = EncryptionServiceGcm(v2Key)

    @Test
    fun `암호화-복호화 라운드트립이 동작한다`() {
        val plain = "MY_KIS_APP_SECRET_VALUE"
        val encrypted = service.encrypt(plain)
        assertThat(service.decrypt(encrypted)).isEqualTo(plain)
    }

    @Test
    fun `같은 평문이라도 매번 다른 암호문이 만들어진다 (IV 무작위)`() {
        val plain = "MY_KIS_APP_SECRET_VALUE"
        val c1 = service.encrypt(plain)
        val c2 = service.encrypt(plain)
        assertThat(c1).isNotEqualTo(c2)
        assertThat(service.decrypt(c1)).isEqualTo(plain)
        assertThat(service.decrypt(c2)).isEqualTo(plain)
    }

    @Test
    fun `tampered ciphertext는 복호화 실패한다 (GCM 태그 검증)`() {
        val plain = "MY_KIS_APP_SECRET_VALUE"
        val encrypted = service.encrypt(plain)
        val tampered = encrypted.dropLast(4) + "AAAA"
        assertThatThrownBy { service.decrypt(tampered) }
            .isInstanceOfAny(javax.crypto.AEADBadTagException::class.java, java.lang.IllegalArgumentException::class.java)
    }
}
