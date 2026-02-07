package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.portfolio.UserPortfolio

interface UserPortfolioRepository {

    fun save(portfolio: UserPortfolio): UserPortfolio

    fun findById(id: Long): UserPortfolio?

    fun findByUserId(userId: Long): List<UserPortfolio>

    fun findByUserIdAndStrategyId(userId: Long, strategyId: Long): UserPortfolio?

    fun deleteById(id: Long)

    fun existsById(id: Long): Boolean
}
