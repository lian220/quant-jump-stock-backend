package com.quantjumpstock.core.infrastructure.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Spring Security UserDetails 구현
 * 인증된 사용자 정보를 담는 클래스
 */
data class UserPrincipal(
    val id: Long,
    val userId: String,
    val email: String?,
    private val password: String,
    val roles: Set<String> = setOf("USER"),
    val enabled: Boolean = true
) : UserDetails {

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return roles.map { SimpleGrantedAuthority("ROLE_$it") }
    }

    override fun getPassword(): String = password

    override fun getUsername(): String = userId

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = enabled

    companion object {
        /**
         * UserEntity로부터 UserPrincipal 생성
         */
        fun from(user: com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity): UserPrincipal {
            return UserPrincipal(
                id = user.id!!,
                userId = user.userId,
                email = user.email,
                password = user.passwordHash ?: "",
                roles = setOf(user.role.name),
                enabled = user.status == com.quantjumpstock.core.adapter.output.persistence.jpa.UserStatus.ACTIVE
            )
        }
    }
}
