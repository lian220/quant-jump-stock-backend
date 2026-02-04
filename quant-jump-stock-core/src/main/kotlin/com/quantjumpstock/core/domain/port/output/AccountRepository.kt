package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.trading.Account

/**
 * Account Repository Port - 도메인 포트
 */
interface AccountRepository {

    fun save(account: Account): Account

    fun findById(id: Long): Account?

    fun findByUserId(userId: Long): Account?

    fun deleteById(id: Long)

    fun existsByUserId(userId: Long): Boolean
}
