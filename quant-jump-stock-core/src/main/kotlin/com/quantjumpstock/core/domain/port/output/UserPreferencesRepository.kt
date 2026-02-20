package com.quantjumpstock.core.domain.port.output

import com.quantjumpstock.core.domain.model.user.UserPreferences

/**
 * UserPreferences Repository Port - 도메인 포트
 */
interface UserPreferencesRepository {

    fun save(preferences: UserPreferences): UserPreferences

    fun findByUserId(userId: Long): UserPreferences?

    fun findByUserIdIn(userIds: List<Long>): List<UserPreferences>
}
