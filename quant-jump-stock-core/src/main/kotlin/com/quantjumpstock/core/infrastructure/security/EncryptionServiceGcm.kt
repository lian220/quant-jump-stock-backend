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
    @Value("\${app.security.encryption-key-v2:}")
    encryptionKeyBase64: String
) {
    private val keyBytes: ByteArray = Base64.getDecoder().decode(encryptionKeyBase64).also {
        require(it.size == 32) { "encryption-key-v2 must decode to exactly 32 bytes (got ${it.size})" }
    }
    private val algorithm = "AES"
    private val transformation = "AES/GCM/NoPadding"
    private val ivLengthBytes = 12
    private val tagLengthBits = 128
    private val secureRandom = SecureRandom()

    fun encrypt(plainText: String): String {
        val iv = ByteArray(ivLengthBytes).also { secureRandom.nextBytes(it) }
        val keySpec = SecretKeySpec(keyBytes, algorithm)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(tagLengthBits, iv))
        val ciphertext = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size).also {
            iv.copyInto(it, 0)
            ciphertext.copyInto(it, iv.size)
        }
        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encrypted: String): String {
        val combined = Base64.getDecoder().decode(encrypted)
        require(combined.size > ivLengthBytes) { "encrypted payload too short" }
        val iv = combined.copyOfRange(0, ivLengthBytes)
        val ciphertext = combined.copyOfRange(ivLengthBytes, combined.size)
        val keySpec = SecretKeySpec(keyBytes, algorithm)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(tagLengthBits, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }
}
