package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserPreferencesJpaRepository : JpaRepository<UserPreferencesEntity, Long> {

    fun findByUserId(userId: Long): Optional<UserPreferencesEntity>

    fun findByUserIdIn(userIds: List<Long>): List<UserPreferencesEntity>
}
