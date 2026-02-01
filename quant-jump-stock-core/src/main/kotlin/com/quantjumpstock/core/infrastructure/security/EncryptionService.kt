package com.quantjumpstock.core.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256 암호화/복호화 서비스
 * 민감한 정보(KIS API Secret 등)를 암호화하여 DB에 저장합니다.
 */
@Service
class EncryptionService(
    @Value("\${app.security.encryption-key:CHANGE_THIS_32_CHAR_SECRET_KEY}")
    private val encryptionKey: String
) {

    private val algorithm = "AES"
    private val transformation = "AES/ECB/PKCS5Padding"

    init {
        require(encryptionKey.length >= 32) {
            "Encryption key must be at least 32 characters. " +
            "Please set 'app.security.encryption-key' in application.yml or environment variable."
        }
    }

    /**
     * 문자열 암호화
     * @param plainText 평문
     * @return Base64 인코딩된 암호문
     */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(transformation)
        val keySpec = SecretKeySpec(encryptionKey.substring(0, 32).toByteArray(), algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)

        val encryptedBytes = cipher.doFinal(plainText.toByteArray())
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    /**
     * 문자열 복호화
     * @param encryptedText Base64 인코딩된 암호문
     * @return 평문
     */
    fun decrypt(encryptedText: String): String {
        val cipher = Cipher.getInstance(transformation)
        val keySpec = SecretKeySpec(encryptionKey.substring(0, 32).toByteArray(), algorithm)
        cipher.init(Cipher.DECRYPT_MODE, keySpec)

        val decryptedBytes = cipher.doFinal(Base64.getDecoder().decode(encryptedText))
        return String(decryptedBytes)
    }
}
