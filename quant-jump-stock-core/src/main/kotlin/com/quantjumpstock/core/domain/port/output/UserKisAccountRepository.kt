package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.user.KisAccountType
import com.quantjumpstock.core.domain.model.user.UserKisAccount

/**
 * UserKisAccount Repository Port
 * 도메인 레이어에서 정의하는 출력 포트
 */
interface UserKisAccountRepository {
    fun save(kisAccount: UserKisAccount): UserKisAccount
    fun findById(id: Long): UserKisAccount?
    fun findByUserId(userId: Long): UserKisAccount?
    fun findByUserUserId(userId: String): UserKisAccount?
    fun findActiveByUserUserId(userId: String): UserKisAccount?
    fun findByUserIdAndAccountType(userId: Long, accountType: KisAccountType): UserKisAccount?
    fun deleteById(id: Long)
    fun existsByUserId(userId: Long): Boolean
}
