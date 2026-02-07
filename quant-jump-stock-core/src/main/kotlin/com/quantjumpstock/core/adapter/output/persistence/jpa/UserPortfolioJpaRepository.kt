package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserPortfolioJpaRepository : JpaRepository<UserPortfolioEntity, Long> {

    fun findByUserId(userId: Long): List<UserPortfolioEntity>

    fun findByUserIdAndStrategyId(userId: Long, strategyId: Long): UserPortfolioEntity?
}
