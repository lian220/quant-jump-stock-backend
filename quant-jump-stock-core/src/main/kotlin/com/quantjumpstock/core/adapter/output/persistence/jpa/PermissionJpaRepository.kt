package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface PermissionJpaRepository : JpaRepository<PermissionEntity, Long> {

    fun findByCode(code: String): Optional<PermissionEntity>

    fun findByCategory(category: String): List<PermissionEntity>

    fun existsByCode(code: String): Boolean

    fun findByCodeIn(codes: List<String>): List<PermissionEntity>

    @Query("""
        SELECT DISTINCT p FROM PermissionEntity p
        JOIN p.rolePermissions rp
        JOIN rp.role r
        JOIN r.userRoles ur
        WHERE ur.user.id = :userId
    """)
    fun findByUserId(userId: Long): List<PermissionEntity>

    @Query("""
        SELECT DISTINCT p.code FROM PermissionEntity p
        JOIN p.rolePermissions rp
        JOIN rp.role r
        JOIN r.userRoles ur
        WHERE ur.user.id = :userId
    """)
    fun findPermissionCodesByUserId(userId: Long): List<String>

    @Query("""
        SELECT DISTINCT p FROM PermissionEntity p
        JOIN p.rolePermissions rp
        WHERE rp.role.id = :roleId
    """)
    fun findByRoleId(roleId: Long): List<PermissionEntity>
}
