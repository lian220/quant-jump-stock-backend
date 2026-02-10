package com.quantjumpstock.core.application.auth

import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserRole
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.port.output.UserRepository
import com.quantjumpstock.core.infrastructure.security.JwtService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

/**
 * 인증 서비스
 * 로그인/로그아웃 및 JWT 토큰 관리
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService
) {

    /**
     * 로그인 처리
     */
    fun login(request: LoginRequest): LoginResponse {
        val user = findUser(request.userId)
            ?: throw AuthException("사용자를 찾을 수 없습니다")

        if (user.passwordHash == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            throw AuthException("비밀번호가 일치하지 않습니다")
        }

        if (user.status != UserStatus.ACTIVE) {
            throw AuthException("계정이 비활성화 상태입니다")
        }

        val token = jwtService.generateToken(user.userId, user.email, user.role.name)

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
     * JWT 토큰 검증
     */
    fun validateToken(token: String): LoginResponse? {
        val claims = jwtService.validateToken(token) ?: return null

        val user = userRepository.findByUserId(claims.userId) ?: return null

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
     * 로그아웃 (JWT는 stateless - 클라이언트에서 토큰 삭제)
     */
    fun logout(token: String) {
        // JWT는 stateless이므로 서버 측에서 할 작업 없음
        // 필요시 블랙리스트 구현 가능
    }

    /**
     * 회원가입 처리
     */
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
