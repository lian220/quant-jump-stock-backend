package com.quantjumpstock.core.infrastructure.security

import org.springframework.beans.factory.annotation.Value
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
 */
@Service
class EncryptionServiceGcm(
    @Value(
        // 개발용 default: 32 zero bytes의 Base64. 운영에서는 GCP Secret Manager가 override.
        // 절대 운영 값으로 사용 금지.
        "\${app.security.encryption-key-v2:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=}"
    )
    encryptionKeyBase64: String
) {
    companion object {
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_LENGTH_BYTES = 32
        private const val MIN_PAYLOAD_BYTES = IV_LENGTH_BYTES + TAG_LENGTH_BITS / 8
    }

    private val keyBytes: ByteArray = Base64.getDecoder().decode(encryptionKeyBase64).also {
        require(it.size == KEY_LENGTH_BYTES) {
            "encryption-key-v2 must decode to exactly $KEY_LENGTH_BYTES bytes (got ${it.size})"
        }
    }
    private val secureRandom = SecureRandom()

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
