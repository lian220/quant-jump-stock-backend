package com.quantjumpstock.core.application.admin

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

/**
 * 관리자용 회원 목록 응답
 */
data class AdminUserListResponse(
    @Schema(description = "회원 목록")
    val users: List<AdminUserSummary>,

    @Schema(description = "총 개수")
    val total: Long,

    @Schema(description = "현재 페이지")
    val page: Int,

    @Schema(description = "페이지 크기")
    val size: Int,

    @Schema(description = "총 페이지 수")
    val totalPages: Int,

    @Schema(description = "상태별 통계")
    val stats: AdminUserStats
)

/**
 * 관리자용 회원 요약
 */
data class AdminUserSummary(
    @Schema(description = "PK")
    val id: Long,

    @Schema(description = "사용자 ID")
    val userId: String,

    @Schema(description = "이름")
    val name: String?,

    @Schema(description = "이메일")
    val email: String?,

    @Schema(description = "전화번호")
    val phone: String?,

    @Schema(description = "상태")
    val status: String,

    @Schema(description = "역할")
    val role: String,

    @Schema(description = "OAuth 제공자")
    val oauthProvider: String?,

    @Schema(description = "투자 성향")
    val preferences: AdminUserPreferences?,

    @Schema(description = "가입일")
    val createdAt: LocalDateTime
)

/**
 * 관리자용 회원 투자 성향
 */
data class AdminUserPreferences(
    @Schema(description = "투자 카테고리")
    val investmentCategories: List<String>,

    @Schema(description = "관심 시장")
    val markets: List<String>,

    @Schema(description = "리스크 성향")
    val riskTolerance: String?
)

/**
 * 회원 상태별 통계
 */
data class AdminUserStats(
    @Schema(description = "전체 회원 수")
    val total: Long,

    @Schema(description = "활성 회원 수")
    val active: Long,

    @Schema(description = "비활성 회원 수")
    val inactive: Long,

    @Schema(description = "정지 회원 수")
    val suspended: Long
)
