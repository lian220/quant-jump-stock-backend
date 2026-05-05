package com.quantjumpstock.core.infrastructure.security

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.experimental.xor

class EncryptionServiceGcmTest {

    // TEST ONLY — sequential bytes 0x00..0x1F (32 bytes), 절대 운영에 사용 금지
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
        val decoded = Base64.getDecoder().decode(encrypted)
        decoded[decoded.lastIndex] = decoded[decoded.lastIndex].xor(0xFF.toByte())
        val tampered = Base64.getEncoder().encodeToString(decoded)
        assertThatThrownBy { service.decrypt(tampered) }
            .isInstanceOfAny(javax.crypto.AEADBadTagException::class.java, java.lang.IllegalArgumentException::class.java)
    }

    @Test
    fun `너무 짧은 ciphertext는 IllegalArgumentException`() {
        val tooShort = Base64.getEncoder().encodeToString(ByteArray(20)) // 28 미만
        assertThatThrownBy { service.decrypt(tooShort) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("expected at least")
    }
}
