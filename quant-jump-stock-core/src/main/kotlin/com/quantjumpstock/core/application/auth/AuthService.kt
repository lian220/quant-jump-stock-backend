package com.quantjumpstock.core.application.auth

import com.fasterxml.jackson.annotation.JsonIgnore
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserRole
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.port.output.TokenPort
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.domain.port.output.UserTierRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 인증 서비스
 * 로그인/로그아웃 및 JWT 토큰 관리
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenPort: TokenPort,
    private val userTierRepository: UserTierRepository,
    private val refreshTokenService: RefreshTokenService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 로그인 처리
     */
    fun login(request: LoginRequest): LoginResponse {
        val user = findUser(request.userId)
            ?: throw AuthException("사용자를 찾을 수 없습니다")

        if (user.passwordHash == null) {
            throw AuthException("소셜 로그인으로 가입된 계정입니다. OAuth 로그인을 이용해주세요.")
        }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw AuthException("비밀번호가 일치하지 않습니다")
        }

        if (user.status != UserStatus.ACTIVE) {
            throw AuthException("계정이 비활성화 상태입니다")
        }

        val userDbId = user.id ?: throw IllegalStateException("저장된 사용자에 id가 없습니다: userId=${user.userId}")
        val accessToken = tokenPort.generateAccessToken(user.userId, user.email, user.role.name, userDbId)
        val refreshToken = refreshTokenService.issue(user.userId, userDbId)

        return LoginResponse(
            success = true,
            token = accessToken,
            user = UserInfo(
                userId = user.userId,
                name = user.name,
                email = user.email,
                phone = user.phone,
                role = user.role.name,
                status = user.status.name
            ),
            refreshToken = refreshToken,
            userDbId = userDbId,
        )
    }

    /**
     * JWT 토큰 검증
     */
    fun validateToken(token: String): LoginResponse? {
        val claims = tokenPort.validateAccessToken(token) ?: return null

        val user = userRepository.findByUserId(claims.userId) ?: return null

        return LoginResponse(
            success = true,
            token = token,
            user = UserInfo(
                userId = user.userId,
                name = user.name,
                email = user.email,
                phone = user.phone,
                role = user.role.name,
                status = user.status.name
            )
        )
    }

    /**
     * Bearer 토큰에서 사용자 PK(Long)를 추출
     */
    fun resolveUserPk(authorization: String): Long? {
        if (!authorization.startsWith("Bearer ")) return null
        val token = authorization.removePrefix("Bearer ")
        val claims = tokenPort.validateAccessToken(token) ?: return null
        return claims.dbId
    }

    /**
     * Bearer 토큰에서 사용자 PK(Long)와 로그인 ID(String)를 한 번에 추출
     */
    fun resolveUser(authorization: String): ResolvedUser? {
        if (!authorization.startsWith("Bearer ")) return null
        val token = authorization.removePrefix("Bearer ")
        val claims = tokenPort.validateAccessToken(token) ?: return null
        val dbId = claims.dbId ?: return null
        return ResolvedUser(userDbId = dbId, userId = claims.userId)
    }

    /**
     * 로그아웃 — Bearer access token 으로 사용자를 식별해 모든 refresh token 을 revoke.
     * Phase 1A 보안 PRE Task 12: RFC 9700 최소 구현.
     *
     * Phase 1A P0-fix C3: access token 이 만료되어도 사용자 의지로 세션을 정리해야 하므로
     * tokenPort.extractSubjectIgnoreExpiry 로 sub/dbId 만 추출 (서명은 검증). 만료 진입 직후
     * logout 시 revoke=0 으로 끝나던 보안 누락 보완.
     *
     * @return revoke 된 refresh token 수 (감지 가능한 사용자 정보 없으면 0)
     */
    fun logout(authorization: String?): Int {
        val token = authorization?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
            ?: return 0
        val subject = tokenPort.extractSubjectIgnoreExpiry(token) ?: return 0
        val userDbId = subject.dbId ?: return 0
        return refreshTokenService.revokeAll(userDbId)
    }

    /**
     * 회원가입 처리
     * User 저장과 무료 티어 생성을 하나의 트랜잭션으로 처리.
     * Tier 생성 실패 시 User 저장도 롤백됨.
     */
    @Transactional
    fun signup(request: SignupRequest): SignupResponse {
        if (userRepository.existsByUserId(request.userId)) {
            return SignupResponse(
                success = false,
                message = "이미 사용 중인 아이디입니다"
            )
        }

        if (userRepository.existsByEmail(request.email)) {
            return SignupResponse(
                success = false,
                message = "이미 사용 중인 이메일입니다"
            )
        }

        if (request.password.length < 6) {
            return SignupResponse(
                success = false,
                message = "비밀번호는 6자 이상이어야 합니다"
            )
        }

        if (request.phone != null && (request.phone.length > 20 || !request.phone.matches(Regex("^[0-9\\-+() ]+$")))) {
            return SignupResponse(
                success = false,
                message = "올바른 전화번호 형식이 아닙니다"
            )
        }

        val user = User(
            userId = request.userId,
            email = request.email,
            name = request.name,
            passwordHash = passwordEncoder.encode(request.password),
            phone = request.phone,
            status = UserStatus.ACTIVE,
            role = UserRole.USER
        )

        val savedUser = userRepository.save(user)

        // 무료 티어 자동 생성 (실패 시 트랜잭션 전체 롤백)
        userTierRepository.createFreeTierForUser(savedUser.userId)

        // 회원가입 성공 시 JWT 발급 (자동 로그인)
        val savedUserId = savedUser.id
            ?: throw IllegalStateException("저장된 사용자에 id가 없습니다: userId=${savedUser.userId}")
        val accessToken = tokenPort.generateAccessToken(savedUser.userId, savedUser.email, savedUser.role.name, savedUserId)
        val refreshToken = refreshTokenService.issue(savedUser.userId, savedUserId)

        return SignupResponse(
            success = true,
            message = "회원가입이 완료되었습니다",
            token = accessToken,
            user = UserInfo(
                userId = savedUser.userId,
                name = savedUser.name,
                email = savedUser.email,
                phone = savedUser.phone,
                role = savedUser.role.name,
                status = savedUser.status.name
            ),
            refreshToken = refreshToken,
            userDbId = savedUserId,
        )
    }

    private fun findUser(userIdOrEmail: String): User? {
        val byUserId = userRepository.findByUserId(userIdOrEmail)
        if (byUserId != null) {
            return byUserId
        }
        return userRepository.findByEmail(userIdOrEmail)
    }
}

/**
 * 로그인 요청
 */
data class LoginRequest(
    val userId: String,
    val password: String
)

/**
 * 로그인 응답.
 *
 * refreshToken / userDbId 는 Controller 가 Set-Cookie 헤더로 변환하기 위한 내부 전달용이며
 * JSON 응답에서는 제외된다 (@JsonIgnore).
 */
data class LoginResponse(
    val success: Boolean,
    val token: String? = null,
    val user: UserInfo? = null,
    val error: String? = null,
    @field:JsonIgnore @get:JsonIgnore val refreshToken: String? = null,
    @field:JsonIgnore @get:JsonIgnore val userDbId: Long? = null,
)

/**
 * 사용자 정보
 */
data class UserInfo(
    val userId: String,
    val name: String?,
    val email: String?,
    val phone: String? = null,
    val role: String,
    val status: String
)

/**
 * 회원가입 요청
 */
data class SignupRequest(
    val userId: String,
    val email: String,
    val password: String,
    val name: String? = null,
    val phone: String? = null
)

/**
 * 회원가입 응답. refreshToken/userDbId 는 LoginResponse 와 동일 사유로 JSON 제외.
 */
data class SignupResponse(
    val success: Boolean,
    val message: String? = null,
    val token: String? = null,
    val user: UserInfo? = null,
    val error: String? = null,
    @field:JsonIgnore @get:JsonIgnore val refreshToken: String? = null,
    @field:JsonIgnore @get:JsonIgnore val userDbId: Long? = null,
)

/**
 * 인증 예외
 */
/**
 * 인증된 사용자 정보 (DB PK + 로그인 ID)
 */
data class ResolvedUser(
    val userDbId: Long,
    val userId: String
)

/**
 * 인증 예외
 */
class AuthException(message: String) : RuntimeException(message)
