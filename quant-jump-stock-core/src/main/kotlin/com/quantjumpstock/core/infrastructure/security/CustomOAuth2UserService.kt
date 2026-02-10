package com.quantjumpstock.core.infrastructure.security

import com.quantjumpstock.core.domain.model.user.OAuthProvider
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserRole
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.port.output.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CustomOAuth2UserService(
    private val userRepository: UserRepository
) : DefaultOAuth2UserService() {

    private val logger = LoggerFactory.getLogger(CustomOAuth2UserService::class.java)

    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        val oauth2User = super.loadUser(userRequest)
        val registrationId = userRequest.clientRegistration.registrationId

        val provider = when (registrationId) {
            "google" -> OAuthProvider.GOOGLE
            "naver" -> OAuthProvider.NAVER
            else -> throw IllegalArgumentException("지원하지 않는 OAuth 프로바이더: $registrationId")
        }

        val userInfo = extractUserInfo(provider, oauth2User)

        val user = findOrCreateOAuthUser(
            provider = provider,
            providerId = userInfo.providerId,
            email = userInfo.email,
            name = userInfo.name,
            profileImageUrl = userInfo.profileImageUrl
        )

        val attributes = HashMap(oauth2User.attributes).apply {
            put("internal_user_id", user.userId)
            put("internal_user_db_id", user.id)
            put("internal_user_role", user.role.name)
            put("internal_user_email", user.email ?: "")
        }

        val nameAttributeKey = userRequest.clientRegistration
            .providerDetails.userInfoEndpoint.userNameAttributeName

        return DefaultOAuth2User(
            oauth2User.authorities,
            attributes,
            nameAttributeKey
        )
    }

    private fun extractUserInfo(provider: OAuthProvider, oauth2User: OAuth2User): OAuthUserInfo {
        return when (provider) {
            OAuthProvider.GOOGLE -> OAuthUserInfo(
                providerId = oauth2User.getAttribute<String>("sub")
                    ?: oauth2User.getAttribute("id")
                    ?: throw IllegalStateException("Google OAuth2 사용자의 ID를 찾을 수 없습니다"),
                email = oauth2User.getAttribute("email"),
                name = oauth2User.getAttribute("name"),
                profileImageUrl = oauth2User.getAttribute("picture")
            )
            OAuthProvider.NAVER -> {
                @Suppress("UNCHECKED_CAST")
                val response = oauth2User.getAttribute<Map<String, Any>>("response")
                    ?: throw IllegalStateException("네이버 사용자 정보의 response 필드가 없습니다")
                OAuthUserInfo(
                    providerId = response["id"] as? String
                        ?: throw IllegalStateException("네이버 OAuth2 사용자의 ID를 찾을 수 없습니다"),
                    email = response["email"] as? String,
                    name = response["name"] as? String ?: response["nickname"] as? String,
                    profileImageUrl = response["profile_image"] as? String
                )
            }
        }
    }

    @Transactional
    fun findOrCreateOAuthUser(
        provider: OAuthProvider,
        providerId: String,
        email: String?,
        name: String?,
        profileImageUrl: String?
    ): User {
        userRepository.findByOAuthProviderAndProviderId(provider, providerId)?.let {
            logger.info("기존 OAuth 사용자 로그인: userId=${it.userId}, id=${it.id}")
            return it
        }

        if (email != null) {
            userRepository.findByEmail(email)?.let { existingUser ->
                logger.warn("이메일 기반 계정 연결 (이메일 소유권 미검증): provider=$provider, email=$email, userId=${existingUser.userId}")
                val updatedUser = existingUser.linkOAuth(
                    provider = provider,
                    providerId = providerId,
                    profileImage = profileImageUrl
                )
                val saved = userRepository.save(updatedUser)
                logger.info("OAuth 계정 연결 완료: userId=${saved.userId}, id=${saved.id}")
                return saved
            }
        }

        val userId = generateUniqueUserId(provider, name)
        val newUser = User(
            userId = userId,
            email = email,
            name = name,
            passwordHash = null,
            oauthProvider = provider,
            oauthProviderId = providerId,
            profileImageUrl = profileImageUrl,
            status = UserStatus.ACTIVE,
            role = UserRole.USER
        )

        return try {
            logger.info("새 OAuth 사용자 생성 시도: userId=$userId, provider=$provider, providerId=$providerId, email=$email")
            val saved = userRepository.save(newUser)
            logger.info("새 OAuth 사용자 생성 완료: userId=${saved.userId}, id=${saved.id}")
            saved
        } catch (e: DataIntegrityViolationException) {
            logger.warn("OAuth 사용자 생성 중 충돌 발생, 재조회 시도: ${e.message}")
            userRepository.findByOAuthProviderAndProviderId(provider, providerId)
                ?: throw e
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

private data class OAuthUserInfo(
    val providerId: String,
    val email: String?,
    val name: String?,
    val profileImageUrl: String?
)
