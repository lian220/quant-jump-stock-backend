package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.user.OAuthProvider
import com.quantjumpstock.core.domain.model.user.User
import com.quantjumpstock.core.domain.model.user.UserStatus

/**
 * User Repository Port - 도메인 포트
 */
interface UserRepository {

    fun save(user: User): User

    fun findById(id: Long): User?

    fun findByUserId(userId: String): User?

    fun findByEmail(email: String): User?

    fun existsById(id: Long): Boolean

    fun existsByUserId(userId: String): Boolean

    fun existsByEmail(email: String): Boolean

    fun deleteById(id: Long)

    /**
     * OAuth 제공자와 제공자 ID로 사용자 조회
     */
    fun findByOAuthProviderAndProviderId(provider: OAuthProvider, providerId: String): User?

    /**
     * 관리자용 페이징 조회 (검색어/상태 필터 포함)
     * @return Pair(users, totalElements)
     */
    fun findAllPaged(page: Int, size: Int, search: String?, status: UserStatus?): Pair<List<User>, Long>

    fun countByStatus(status: UserStatus): Long

    fun count(): Long

    /**
     * 어드민용: DB PK 목록으로 일괄 조회
     */
    fun findAllByDbIds(ids: List<Long>): List<User>
}
