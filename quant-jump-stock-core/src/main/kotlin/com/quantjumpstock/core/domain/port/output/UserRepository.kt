package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.user.User

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
}
