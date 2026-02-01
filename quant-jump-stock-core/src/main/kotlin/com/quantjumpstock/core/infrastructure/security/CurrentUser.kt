package com.quantjumpstock.core.infrastructure.security

import org.springframework.security.core.annotation.AuthenticationPrincipal

/**
 * 현재 로그인한 사용자 정보를 주입하는 어노테이션
 *
 * 사용 예시:
 * ```kotlin
 * fun getProfile(@CurrentUser user: UserPrincipal): ResponseEntity<Profile> {
 *     val userId = user.userId
 *     // ...
 * }
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@AuthenticationPrincipal
annotation class CurrentUser
