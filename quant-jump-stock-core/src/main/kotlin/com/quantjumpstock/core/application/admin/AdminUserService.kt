package com.quantjumpstock.core.application.admin

import com.quantjumpstock.core.domain.model.user.UserPreferences
import com.quantjumpstock.core.domain.model.user.UserStatus
import com.quantjumpstock.core.domain.port.output.UserPreferencesRepository
import com.quantjumpstock.core.domain.port.output.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 관리자용 회원 관리 서비스
 */
@Service
@Transactional(readOnly = true)
class AdminUserService(
    private val userRepository: UserRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun getAllUsers(
        page: Int,
        size: Int,
        search: String?,
        status: String?
    ): AdminUserListResponse {
        logger.info("관리자 회원 목록 조회: page=$page, size=$size, search=$search, status=$status")

        val userStatus = status?.let {
            try { UserStatus.valueOf(it) } catch (_: IllegalArgumentException) { null }
        }

        val (users, totalElements) = userRepository.findAllPaged(page, size, search, userStatus)

        val userIds = users.mapNotNull { it.id }
        val preferencesMap = if (userIds.isNotEmpty()) {
            userPreferencesRepository.findByUserIdIn(userIds).associateBy { it.userId }
        } else {
            emptyMap()
        }

        val userSummaries = users.map { user ->
            AdminUserSummary(
                id = user.id!!,
                userId = user.userId,
                name = user.name,
                email = user.email,
                phone = user.phone,
                status = user.status.name,
                role = user.role.name,
                oauthProvider = user.oauthProvider?.name,
                preferences = preferencesMap[user.id]?.let { toAdminPreferences(it) },
                createdAt = user.createdAt
            )
        }

        val totalPages = if (size > 0) ((totalElements + size - 1) / size).toInt() else 0

        val stats = AdminUserStats(
            total = userRepository.count(),
            active = userRepository.countByStatus(UserStatus.ACTIVE),
            inactive = userRepository.countByStatus(UserStatus.INACTIVE),
            suspended = userRepository.countByStatus(UserStatus.SUSPENDED)
        )

        return AdminUserListResponse(
            users = userSummaries,
            total = totalElements,
            page = page,
            size = size,
            totalPages = totalPages,
            stats = stats
        )
    }

    private fun toAdminPreferences(prefs: UserPreferences): AdminUserPreferences {
        return AdminUserPreferences(
            investmentCategories = prefs.investmentCategories,
            markets = prefs.markets,
            riskTolerance = prefs.riskTolerance
        )
    }
}
