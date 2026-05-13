package com.quantjumpstock.core.infrastructure.security

import org.springframework.stereotype.Component

/**
 * AppSecret 복호화/암호화 fallback 정책 단일 소스.
 *
 * Phase 1A PRE 패널 리뷰 (refactoring-expert R1):
 *  - [com.quantjumpstock.core.adapter.output.external.KisApiAdapter] /
 *    [com.quantjumpstock.core.application.user.UserKisAccountService] /
 *    [com.quantjumpstock.core.adapter.output.external.KisTokenIssuer] 가 각각
 *    (legacy ECB, GCM v2) 두 의존성을 받아 동일한 fallback 분기 코드를 가졌음.
 *  - 하나의 컴포넌트로 정책을 통합 → 의존성 1개로 축소 + Task 8(V61 ECB drop)
 *    시점에 본 클래스만 수정하면 fallback 제거 완료.
 *
 * 마이그레이션 컴포넌트(AppSecretRowReencryptionService) 는 v1 복호화 → v2 암호화
 * 두 단계를 모두 직접 수행해야 하므로 본 추상화를 사용하지 않고 두 의존성을 직접 받음.
 */
@Component
class AppSecretCipher(
    private val legacy: EncryptionService,
    private val gcm: EncryptionServiceGcm,
) {
    /**
     * v2(GCM) 우선, v2 가 null/blank 이면 v1(ECB) legacy fallback.
     * Task 7 이후 신규 등록은 v1="" + v2=GCM 패턴이므로 v2 우선이 필수.
     * Task 8(V61 ECB drop) 후 본 메서드는 단순 `gcm.decrypt(v2!!)` 로 축소.
     */
    fun decrypt(v2Cipher: String?, v1Cipher: String): String =
        if (!v2Cipher.isNullOrBlank()) gcm.decrypt(v2Cipher) else legacy.decrypt(v1Cipher)

    /**
     * 신규 등록은 항상 v2(GCM) 로 암호화. v1 컬럼은 빈 문자열로 마킹.
     */
    fun encryptForStorage(plain: String): String = gcm.encrypt(plain)
}
