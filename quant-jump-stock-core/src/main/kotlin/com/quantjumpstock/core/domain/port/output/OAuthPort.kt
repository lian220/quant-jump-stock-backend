package com.quantjumpstock.core.domain.port.output

/**
 * OAuth 인증 아웃바운드 포트
 * 소셜 로그인 제공자와의 토큰 교환 및 사용자 정보 조회를 추상화합니다.
 */
interface OAuthPort {

    data class OAuthUserInfo(
        val providerId: String,
        val email: String?,
        val name: String?,
        val profileImageUrl: String?,
        val phone: String?
    )

    /** Authorization Code → Access Token → 사용자 정보 조회 */
    fun exchangeCodeAndGetUserInfo(code: String, redirectUri: String): OAuthUserInfo
}
