package com.quantjumpstock.core.infrastructure.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256/GCM 암호화 서비스 (V2).
 *
 * 출력 포맷: Base64(IV(12바이트) || ciphertext(GCM 태그 16바이트 포함))
 * 키: Base64로 인코딩된 32바이트(256비트) CSPRNG 값.
 *
 * Phase 1A PRE 검토 반영:
 *  - zero-key (32바이트 0x00) fallback 이 prod 프로파일로 흘러들어가지 않도록 init 가드.
 *  - prod 프로파일: zero-key 감지 시 IllegalStateException 으로 부팅 실패.
 *  - 그 외 프로파일(local/test/dev): warn 로그만 남기고 진행.
 */
@Service
class EncryptionServiceGcm(
    @Value(
        // 개발용 default: 32 zero bytes의 Base64. 운영에서는 GCP Secret Manager가 override.
        // 절대 운영 값으로 사용 금지 — init 가드가 prod 프로파일에서 부팅을 차단한다.
        "\${app.security.encryption-key-v2:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=}"
    )
    encryptionKeyBase64: String,
    private val environment: Environment
) {
    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_LENGTH_BYTES = 32
        private const val MIN_PAYLOAD_BYTES = IV_LENGTH_BYTES + TAG_LENGTH_BITS / 8
        private val PROD_PROFILES = setOf("prod", "production")
    }

    private val logger = LoggerFactory.getLogger(javaClass)

    private val keyBytes: ByteArray = Base64.getDecoder().decode(encryptionKeyBase64).also {
        require(it.size == KEY_LENGTH_BYTES) {
            "encryption-key-v2 must decode to exactly $KEY_LENGTH_BYTES bytes (got ${it.size})"
        }
        guardWeakKey(it)
    }
    private val secureRandom = SecureRandom()

    private fun guardWeakKey(bytes: ByteArray) {
        val isZeroKey = bytes.all { it == 0.toByte() }
        if (!isZeroKey) return

        val activeProfiles = environment.activeProfiles.toSet()
        if (activeProfiles.any { it in PROD_PROFILES }) {
            throw IllegalStateException(
                "encryption-key-v2 is the zero-bytes development fallback in a production profile " +
                    "(active=$activeProfiles). Set APP_ENCRYPTION_KEY_V2 via GCP Secret Manager."
            )
        }
        logger.warn(
            "encryption-key-v2 is the zero-bytes development fallback (active profiles=$activeProfiles). " +
                "Production must override APP_ENCRYPTION_KEY_V2 via GCP Secret Manager."
        )
    }

    fun encrypt(plainText: String): String {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val keySpec = SecretKeySpec(keyBytes, ALGORITHM)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size).also {
            iv.copyInto(it, 0)
            ciphertext.copyInto(it, iv.size)
        }
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encrypted: String): String {
        val combined = Base64.getDecoder().decode(encrypted)
        require(combined.size >= MIN_PAYLOAD_BYTES) {
            "encrypted payload too short: expected at least $MIN_PAYLOAD_BYTES bytes, got ${combined.size}"
        }
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val keySpec = SecretKeySpec(keyBytes, ALGORITHM)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
