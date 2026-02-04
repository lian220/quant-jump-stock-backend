package com.quantjumpstock.core.application.auth

import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserRole
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.port.output.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 인증 서비스
 * 로그인/로그아웃 및 토큰 관리
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    // 간단한 토큰 저장소 (프로덕션에서는 Redis 사용 권장)
    private val tokenStore = ConcurrentHashMap<String, TokenInfo>()

    /**
     * 로그인 처리
     */
    fun login(request: LoginRequest): LoginResponse {
        // 1. 사용자 조회 (userId 또는 email로)
        val user = findUser(request.userId)
            ?: throw AuthException("사용자를 찾을 수 없습니다")

        // 2. 비밀번호 확인
        if (user.passwordHash == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw AuthException("비밀번호가 일치하지 않습니다")
        }

        // 3. 상태 확인
        if (user.status != UserStatus.ACTIVE) {
            throw AuthException("계정이 비활성화 상태입니다")
        }

        // 4. 토큰 생성
        val token = generateToken()
        val tokenInfo = TokenInfo(
            userId = user.userId,
            role = user.role,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusHours(24)
        )
        tokenStore[token] = tokenInfo

        // 5. 응답 반환
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
    }

    /**
     * 토큰 검증
     */
    fun validateToken(token: String): LoginResponse? {
        val tokenInfo = tokenStore[token] ?: return null

        // 만료 확인
        if (tokenInfo.expiresAt.isBefore(LocalDateTime.now())) {
            tokenStore.remove(token)
            return null
        }

        // 사용자 조회
        val user = userRepository.findByUserId(tokenInfo.userId) ?: return null

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
    }

    /**
     * 로그아웃
     */
    fun logout(token: String) {
        tokenStore.remove(token)
    }

    /**
     * 회원가입 처리
     */
    fun signup(request: SignupRequest): SignupResponse {
        // 1. 아이디 중복 확인
        if (userRepository.existsByUserId(request.userId)) {
            return SignupResponse(
                success = false,
                message = "이미 사용 중인 아이디입니다"
            )
        }

        // 2. 이메일 중복 확인
        if (userRepository.existsByEmail(request.email)) {
            return SignupResponse(
                success = false,
                message = "이미 사용 중인 이메일입니다"
            )
        }

        // 3. 비밀번호 유효성 검사
        if (request.password.length < 6) {
            return SignupResponse(
                success = false,
                message = "비밀번호는 6자 이상이어야 합니다"
            )
        }

        // 4. 사용자 생성
        val user = User(
            userId = request.userId,
            email = request.email,
            name = request.name,
            passwordHash = passwordEncoder.encode(request.password),
            status = UserStatus.ACTIVE,
            role = UserRole.USER
        )

        userRepository.save(user)

        return SignupResponse(
            success = true,
            message = "회원가입이 완료되었습니다"
        )
    }

    /**
     * OAuth 로그인을 위한 세션 토큰 생성
     */
    fun createSessionToken(user: User): String {
        val token = generateToken()
        val tokenInfo = TokenInfo(
            userId = user.userId,
            role = user.role,
            createdAt = LocalDateTime.now(),
            expiresAt = LocalDateTime.now().plusHours(24)
        )
        tokenStore[token] = tokenInfo
        return token
    }

    /**
     * 사용자 조회 (userId 또는 email)
     */
    private fun findUser(userIdOrEmail: String): User? {
        // userId로 먼저 조회
        val byUserId = userRepository.findByUserId(userIdOrEmail)
        if (byUserId != null) {
            return byUserId
        }

        // email로 조회
        return userRepository.findByEmail(userIdOrEmail)
    }

    /**
     * 토큰 생성
     */
    private fun generateToken(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }
}

/**
 * 토큰 정보
 */
data class TokenInfo(
    val userId: String,
    val role: UserRole,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime
)

/**
 * 로그인 요청
 */
data class LoginRequest(
    val userId: String,  // userId 또는 email
    val password: String
)

/**
 * 로그인 응답
 */
data class LoginResponse(
    val success: Boolean,
    val token: String? = null,
    val user: UserInfo? = null,
    val error: String? = null
)

/**
 * 사용자 정보
 */
data class UserInfo(
    val userId: String,
    val name: String?,
    val email: String?,
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
    val name: String? = null
)

/**
 * 회원가입 응답
 */
data class SignupResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

/**
 * 인증 예외
 */
class AuthException(message: String) : RuntimeException(message)
