package com.quantjumpstock.core.application.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.quantjumpstock.core.domain.model.user.OAuthProvider
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserRole
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.config.OAuth2Config
import org.slf4j.LoggerFactory
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class OAuthService(
    private val oAuth2Config: OAuth2Config,
    private val userRepository: UserRepository,
    private val authService: AuthService
) {
    private val logger = LoggerFactory.getLogger(OAuthService::class.java)
    private val restTemplate = RestTemplate()

    /**
     * Google OAuth 인증 URL 생성
     */
    fun getGoogleAuthUrl(frontendRedirectUri: String?): String {
        val config = oAuth2Config.google
        return UriComponentsBuilder.fromHttpUrl(config.authUrl)
            .queryParam("client_id", config.clientId)
            .queryParam("redirect_uri", config.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("scope", config.scope.replace(",", " "))
            .queryParam("access_type", "offline")
            .queryParam("state", encodeState(frontendRedirectUri))
            .build()
            .toUriString()
    }

    /**
     * Naver OAuth 인증 URL 생성
     */
    fun getNaverAuthUrl(frontendRedirectUri: String?): String {
        val config = oAuth2Config.naver
        return UriComponentsBuilder.fromHttpUrl(config.authUrl)
            .queryParam("client_id", config.clientId)
            .queryParam("redirect_uri", config.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", encodeState(frontendRedirectUri))
            .build()
            .toUriString()
    }

    /**
     * Google OAuth 콜백 처리
     */
    fun handleGoogleCallback(code: String, state: String?): OAuthResult {
        try {
            // 1. Access Token 획득
            val tokenResponse = getGoogleAccessToken(code)

            // 2. 사용자 정보 조회
            val userInfo = getGoogleUserInfo(tokenResponse.accessToken)

            // 3. 사용자 생성 또는 조회
            val user = findOrCreateOAuthUser(
                provider = OAuthProvider.GOOGLE,
                providerId = userInfo.id,
                email = userInfo.email,
                name = userInfo.name,
                profileImageUrl = userInfo.picture
            )

            // 4. 세션 토큰 생성
            val token = authService.createSessionToken(user)

            // 5. 프론트엔드 리다이렉트 URL 생성
            val frontendUrl = decodeState(state) ?: oAuth2Config.frontendRedirectUrl

            return OAuthResult(
                success = true,
                token = token,
                redirectUrl = "$frontendUrl?token=$token"
            )
        } catch (e: Exception) {
            logger.error("Google OAuth 처리 실패", e)
            val frontendUrl = decodeState(state) ?: oAuth2Config.frontendRedirectUrl
            return OAuthResult(
                success = false,
                error = e.message ?: "Google 로그인 실패",
                redirectUrl = "$frontendUrl?error=${URLEncoder.encode(e.message ?: "login_failed", StandardCharsets.UTF_8)}"
            )
        }
    }

    /**
     * Naver OAuth 콜백 처리 (Server-side OAuth)
     */
    fun handleNaverCallback(code: String, state: String?): OAuthResult {
        try {
            // 1. Access Token 획득
            val tokenResponse = getNaverAccessToken(code, state)

            // 2. 사용자 정보 조회
            val userInfo = getNaverUserInfo(tokenResponse.accessToken)

            // 3. 사용자 생성 또는 조회
            val user = findOrCreateOAuthUser(
                provider = OAuthProvider.NAVER,
                providerId = userInfo.response.id,
                email = userInfo.response.email,
                name = userInfo.response.name ?: userInfo.response.nickname,
                profileImageUrl = userInfo.response.profileImage
            )

            // 4. 세션 토큰 생성
            val token = authService.createSessionToken(user)

            // 5. 프론트엔드 리다이렉트 URL 생성
            val frontendUrl = decodeState(state) ?: oAuth2Config.frontendRedirectUrl

            return OAuthResult(
                success = true,
                token = token,
                redirectUrl = "$frontendUrl?token=$token"
            )
        } catch (e: Exception) {
            logger.error("Naver OAuth 처리 실패", e)
            val frontendUrl = decodeState(state) ?: oAuth2Config.frontendRedirectUrl
            return OAuthResult(
                success = false,
                error = e.message ?: "네이버 로그인 실패",
                redirectUrl = "$frontendUrl?error=${URLEncoder.encode(e.message ?: "login_failed", StandardCharsets.UTF_8)}"
            )
        }
    }

    /**
     * Naver OAuth 코드 교환 (Client-side OAuth)
     * 프론트엔드에서 받은 인증 코드를 토큰으로 교환하고 사용자 정보를 반환
     */
    fun exchangeNaverCode(code: String): LoginResponse {
        try {
            // 1. Access Token 획득 (state 불필요, 프론트엔드에서 이미 검증)
            val tokenResponse = getNaverAccessToken(code, null)

            // 2. 사용자 정보 조회
            val userInfo = getNaverUserInfo(tokenResponse.accessToken)

            // 3. 사용자 생성 또는 조회
            val user = findOrCreateOAuthUser(
                provider = OAuthProvider.NAVER,
                providerId = userInfo.response.id,
                email = userInfo.response.email,
                name = userInfo.response.name ?: userInfo.response.nickname,
                profileImageUrl = userInfo.response.profileImage
            )

            // 4. 세션 토큰 생성
            val token = authService.createSessionToken(user)

            // 5. LoginResponse 반환
            return LoginResponse(
                success = true,
                token = token,
                user = UserInfo(
                    userId = user.userId,
                    name = user.name,
                    email = user.email,
                    role = user.role.name,
                    status = user.status.name
                )
            )
        } catch (e: Exception) {
            logger.error("Naver OAuth 코드 교환 실패", e)
            return LoginResponse(
                success = false,
                error = e.message ?: "네이버 로그인 실패"
            )
        }
    }

    private fun getGoogleAccessToken(code: String): OAuthTokenResponse {
        val config = oAuth2Config.google

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val body = LinkedMultiValueMap<String, String>().apply {
            add("code", code)
            add("client_id", config.clientId)
            add("client_secret", config.clientSecret)
            add("redirect_uri", config.redirectUri)
            add("grant_type", "authorization_code")
        }

        val response = restTemplate.exchange(
            config.tokenUrl,
            HttpMethod.POST,
            HttpEntity(body, headers),
            GoogleTokenResponse::class.java
        )

        return OAuthTokenResponse(
            accessToken = response.body?.accessToken ?: throw OAuthException("Failed to get access token")
        )
    }

    private fun getGoogleUserInfo(accessToken: String): GoogleUserInfo {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }

        val response = restTemplate.exchange(
            oAuth2Config.google.userInfoUrl,
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            GoogleUserInfo::class.java
        )

        return response.body ?: throw OAuthException("Failed to get user info")
    }

    private fun getNaverAccessToken(code: String, state: String?): OAuthTokenResponse {
        val config = oAuth2Config.naver

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", config.clientId)
            add("client_secret", config.clientSecret)
            add("code", code)
            if (!state.isNullOrBlank()) {
                add("state", state)
            }
        }

        val response = restTemplate.exchange(
            config.tokenUrl,
            HttpMethod.POST,
            HttpEntity(body, headers),
            NaverTokenResponse::class.java
        )

        return OAuthTokenResponse(
            accessToken = response.body?.accessToken ?: throw OAuthException("Failed to get access token")
        )
    }

    private fun getNaverUserInfo(accessToken: String): NaverUserInfoResponse {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
        }

        val response = restTemplate.exchange(
            oAuth2Config.naver.userInfoUrl,
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            NaverUserInfoResponse::class.java
        )

        return response.body ?: throw OAuthException("Failed to get user info")
    }

    private fun findOrCreateOAuthUser(
        provider: OAuthProvider,
        providerId: String,
        email: String?,
        name: String?,
        profileImageUrl: String?
    ): User {
        // 기존 OAuth 사용자 찾기
        val existingUser = userRepository.findByOAuthProviderAndProviderId(provider, providerId)
        if (existingUser != null) {
            logger.info("기존 OAuth 사용자 로그인: ${existingUser.userId}")
            return existingUser
        }

        // 이메일로 기존 사용자 찾기 (계정 연결)
        if (email != null) {
            val userByEmail = userRepository.findByEmail(email)
            if (userByEmail != null) {
                // 기존 계정에 OAuth 정보 연결
                val updatedUser = userByEmail.linkOAuth(
                    provider = provider,
                    providerId = providerId,
                    profileImage = profileImageUrl
                )
                logger.info("기존 계정에 OAuth 연결: ${userByEmail.userId}")
                return userRepository.save(updatedUser)
            }
        }

        // 새 사용자 생성
        val userId = generateUniqueUserId(provider, name)
        val newUser = User(
            userId = userId,
            email = email,
            name = name,
            passwordHash = null,  // OAuth 사용자는 비밀번호 없음
            oauthProvider = provider,
            oauthProviderId = providerId,
            profileImageUrl = profileImageUrl,
            status = UserStatus.ACTIVE,
            role = UserRole.USER
        )

        logger.info("새 OAuth 사용자 생성: $userId")
        return userRepository.save(newUser)
    }

    private fun generateUniqueUserId(provider: OAuthProvider, name: String?): String {
        val prefix = when (provider) {
            OAuthProvider.GOOGLE -> "g"
            OAuthProvider.NAVER -> "n"
        }
        val baseName = name?.replace(" ", "")?.take(10) ?: "user"
        val randomSuffix = UUID.randomUUID().toString().take(6)
        return "${prefix}_${baseName}_$randomSuffix"
    }

    private fun encodeState(frontendRedirectUri: String?): String {
        return frontendRedirectUri?.let {
            URLEncoder.encode(it, StandardCharsets.UTF_8)
        } ?: ""
    }

    private fun decodeState(state: String?): String? {
        return state?.takeIf { it.isNotBlank() }?.let {
            java.net.URLDecoder.decode(it, StandardCharsets.UTF_8)
        }
    }
}

// DTOs
data class OAuthResult(
    val success: Boolean,
    val token: String? = null,
    val error: String? = null,
    val redirectUrl: String
)

data class OAuthTokenResponse(
    val accessToken: String
)

data class GoogleTokenResponse(
    @JsonProperty("access_token") val accessToken: String?,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("expires_in") val expiresIn: Int?,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("scope") val scope: String?
)

data class GoogleUserInfo(
    val id: String,
    val email: String?,
    val name: String?,
    val picture: String?,
    @JsonProperty("verified_email") val verifiedEmail: Boolean?
)

// Naver OAuth DTOs
data class NaverTokenResponse(
    @JsonProperty("access_token") val accessToken: String?,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("token_type") val tokenType: String?,
    @JsonProperty("expires_in") val expiresIn: Int?
)

data class NaverUserInfoResponse(
    val resultcode: String,
    val message: String,
    val response: NaverUserInfo
)

data class NaverUserInfo(
    val id: String,
    val email: String?,
    val name: String?,
    val nickname: String?,
    @JsonProperty("profile_image") val profileImage: String?,
    val mobile: String?,
    @JsonProperty("mobile_e164") val mobileE164: String?
)

class OAuthException(message: String) : RuntimeException(message)
