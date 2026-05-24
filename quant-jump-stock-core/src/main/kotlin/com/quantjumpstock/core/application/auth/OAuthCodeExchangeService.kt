package com.quantjumpstock.core.application.auth

import com.fasterxml.jackson.annotation.JsonIgnore
import com.quantjumpstock.core.domain.model.user.OAuthProvider
import com.quantjumpstock.core.domain.port.output.OAuthPort
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserRole
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.port.output.TokenPort
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.UserTierRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * OAuth2 Authorization Code → JWT 교환 서비스
 * Frontend-first OAuth 흐름: FE에서 code를 받아 BE로 전달 → JWT 반환
 * 현재 지원: naver / 추후: google, kakao 등 확장 예정
 */
@Service
class OAuthCodeExchangeService(
    private val naverOAuthPort: OAuthPort,
    private val userRepository: UserRepository,
    private val userTierRepository: UserTierRepository,
    private val tokenPort: TokenPort,
    private val refreshTokenService: RefreshTokenService,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(OAuthCodeExchangeService::class.java)

    data class OAuthCodeRequest(
        val code: String,
        val redirectUri: String
    )

    /**
     * OAuth 교환 응답. refreshToken/userDbId 는 Controller 가 Set-Cookie 로 변환하기 위한 내부 전달용.
     */
    data class OAuthTokenResponse(
        val token: String,
        @field:JsonIgnore @get:JsonIgnore val refreshToken: String? = null,
        @field:JsonIgnore @get:JsonIgnore val userDbId: Long? = null,
    )

    /**
     * Authorization Code → JWT 교환
     * @param provider 소셜 로그인 제공자 (naver, google, kakao 등)
     * @param request code와 redirectUri
     * @return JWT 토큰
     */
    fun exchange(provider: String, request: OAuthCodeRequest): OAuthTokenResponse {
        val oauthProvider = when (provider.lowercase()) {
            "naver" -> OAuthProvider.NAVER
            "google" -> OAuthProvider.GOOGLE
            else -> throw IllegalArgumentException("지원하지 않는 OAuth 프로바이더: $provider")
        }

        val oauthUserInfo = when (oauthProvider) {
            OAuthProvider.NAVER -> naverOAuthPort.exchangeCodeAndGetUserInfo(request.code, request.redirectUri)
            OAuthProvider.GOOGLE -> throw IllegalArgumentException("Google OAuth는 아직 구현되지 않았습니다")
        }

        val user = transactionTemplate.execute {
            findOrCreateOAuthUser(
                provider = oauthProvider,
                providerId = oauthUserInfo.providerId,
                email = oauthUserInfo.email,
                name = oauthUserInfo.name,
                profileImageUrl = oauthUserInfo.profileImageUrl,
                phone = oauthUserInfo.phone
            )
        } ?: throw IllegalStateException("OAuth 사용자 생성/조회에 실패했습니다")

        val userDbId = user.id
            ?: throw IllegalStateException("OAuth 사용자에 id가 없습니다: userId=${user.userId}")
        val accessToken = tokenPort.generateAccessToken(user.userId, user.email, user.role.name, userDbId)
        val refreshToken = refreshTokenService.issue(user.userId, userDbId)
        return OAuthTokenResponse(token = accessToken, refreshToken = refreshToken, userDbId = userDbId)
    }

    /**
     * OAuth 사용자 조회 또는 신규 생성
     */
    fun findOrCreateOAuthUser(
        provider: OAuthProvider,
        providerId: String,
        email: String?,
        name: String?,
        profileImageUrl: String?,
        phone: String? = null
    ): User {
        // 1. provider + providerId로 기존 사용자 조회
        userRepository.findByOAuthProviderAndProviderId(provider, providerId)?.let { existingUser ->
            logger.info("기존 OAuth 사용자 로그인: userId=${existingUser.userId}")
            if (phone != null && existingUser.phone == null) {
                val updated = existingUser.copy(phone = phone)
                return userRepository.save(updated)
            }
            return existingUser
        }

        // 2. 이메일로 기존 계정 연결
        if (email != null) {
            userRepository.findByEmail(email)?.let { existingUser ->
                logger.warn("이메일 기반 계정 연결: provider=$provider, userId=${existingUser.userId}")
                var updatedUser = existingUser.linkOAuth(
                    provider = provider,
                    providerId = providerId,
                    profileImage = profileImageUrl
                )
                if (phone != null && updatedUser.phone == null) {
                    updatedUser = updatedUser.copy(phone = phone)
                }
                val saved = userRepository.save(updatedUser)
                logger.info("OAuth 계정 연결 완료: userId=${saved.userId}")
                return saved
            }
        }

        // 3. 신규 사용자 생성
        val userId = generateUniqueUserId(provider, name)
        val newUser = User(
            userId = userId,
            email = email,
            name = name,
            passwordHash = null,
            oauthProvider = provider,
            oauthProviderId = providerId,
            profileImageUrl = profileImageUrl,
            phone = phone,
            status = UserStatus.ACTIVE,
            role = UserRole.USER
        )

        return try {
            logger.info("새 OAuth 사용자 생성 시도: userId=$userId, provider=$provider")
            val saved = userRepository.save(newUser)
            logger.info("새 OAuth 사용자 생성 완료: userId=${saved.userId}, id=${saved.id}")

            try {
                userTierRepository.createFreeTierForUser(saved.userId)
            } catch (e: Exception) {
                logger.warn("사용자 티어 생성 실패: userId=${saved.userId}, error=${e.message}", e)
            }

            saved
        } catch (e: DataIntegrityViolationException) {
            logger.warn("OAuth 사용자 생성 중 충돌 발생, 재조회 시도: ${e.message}")
            userRepository.findByOAuthProviderAndProviderId(provider, providerId) ?: throw e
        } catch (e: Exception) {
            logger.error("OAuth 사용자 저장 실패: userId=$userId, error=${e.javaClass.simpleName}: ${e.message}", e)
            throw e
        }
    }

    private fun generateUniqueUserId(provider: OAuthProvider, name: String?): String {
        val prefix = when (provider) {
            OAuthProvider.GOOGLE -> "g"
            OAuthProvider.NAVER -> "n"
        }
        val baseName = name?.replace(" ", "")?.take(10) ?: "user"

        repeat(5) {
            val randomSuffix = UUID.randomUUID().toString().replace("-", "").take(8)
            val candidate = "${prefix}_${baseName}_$randomSuffix"
            if (!userRepository.existsByUserId(candidate)) {
                return candidate
            }
        }

        throw IllegalStateException("고유한 사용자 ID 생성에 실패했습니다: prefix=$prefix, baseName=$baseName")
    }

}
