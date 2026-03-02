package com.quantjumpstock.core.adapter.output.external

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import com.quantjumpstock.core.domain.port.output.OAuthPort
import java.time.Duration

/**
 * 네이버 OAuth2 API 클라이언트 (Output Adapter)
 * Authorization Code → Access Token → User Info 교환
 */
@Component
class NaverOAuthClient(
    @Value("\${naver.oauth.client-id:not-configured}")
    private val clientId: String,
    @Value("\${naver.oauth.client-secret:not-configured}")
    private val clientSecret: String
) : OAuthPort {
    private val logger = LoggerFactory.getLogger(NaverOAuthClient::class.java)

    data class NaverUserInfo(
        val id: String,
        val email: String?,
        val name: String?,
        val profileImage: String?,
        val mobile: String?
    )

    private val restClient: RestClient = RestClient.builder()
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(10))
            setReadTimeout(Duration.ofSeconds(30))
        })
        .build()

    override fun exchangeCodeAndGetUserInfo(code: String, redirectUri: String): OAuthPort.OAuthUserInfo {
        val accessToken = exchangeCodeForToken(code, redirectUri)
        val userInfo = getUserInfo(accessToken)
        return OAuthPort.OAuthUserInfo(
            providerId = userInfo.id,
            email = userInfo.email,
            name = userInfo.name,
            profileImageUrl = userInfo.profileImage,
            phone = userInfo.mobile
        )
    }

    /**
     * Authorization Code → Access Token 교환
     */
    private fun exchangeCodeForToken(code: String, redirectUri: String): String {
        val params = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            add("client_secret", clientSecret)
            add("code", code)
            add("redirect_uri", redirectUri)
        }

        return try {
            @Suppress("UNCHECKED_CAST")
            val response = restClient.post()
                .uri("https://nid.naver.com/oauth2.0/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(params)
                .retrieve()
                .body(Map::class.java) as Map<String, Any>?
                ?: throw IllegalStateException("네이버 토큰 API 응답이 null입니다")

            val error = response["error"] as? String
            if (error != null) {
                val errorDesc = response["error_description"] as? String ?: ""
                throw IllegalStateException("네이버 토큰 교환 실패: $error - $errorDesc")
            }

            response["access_token"] as? String
                ?: throw IllegalStateException("네이버 토큰 응답에 access_token이 없습니다")
        } catch (e: RestClientResponseException) {
            logger.error("네이버 토큰 API 호출 실패: status=${e.statusCode}")
            throw IllegalStateException("네이버 토큰 교환 중 오류 발생: ${e.message}", e)
        }
    }

    /**
     * Access Token → 사용자 정보 조회
     */
    private fun getUserInfo(accessToken: String): NaverUserInfo {
        return try {
            @Suppress("UNCHECKED_CAST")
            val response = restClient.get()
                .uri("https://openapi.naver.com/v1/nid/me")
                .header("Authorization", "Bearer $accessToken")
                .retrieve()
                .body(Map::class.java) as Map<String, Any>?
                ?: throw IllegalStateException("네이버 사용자 정보 API 응답이 null입니다")

            val resultCode = response["resultcode"] as? String
            if (resultCode != "00") {
                val message = response["message"] as? String ?: "unknown error"
                throw IllegalStateException("네이버 사용자 정보 조회 실패: $message")
            }

            @Suppress("UNCHECKED_CAST")
            val userInfo = response["response"] as? Map<String, Any>
                ?: throw IllegalStateException("네이버 사용자 정보 response 필드가 없습니다")

            NaverUserInfo(
                id = userInfo["id"] as? String
                    ?: throw IllegalStateException("네이버 사용자 ID가 없습니다"),
                email = userInfo["email"] as? String,
                name = userInfo["name"] as? String ?: userInfo["nickname"] as? String,
                profileImage = userInfo["profile_image"] as? String,
                mobile = userInfo["mobile"] as? String
            )
        } catch (e: RestClientResponseException) {
            logger.error("네이버 사용자 정보 API 호출 실패: status=${e.statusCode}")
            throw IllegalStateException("네이버 사용자 정보 조회 중 오류 발생: ${e.message}", e)
        }
    }
}
