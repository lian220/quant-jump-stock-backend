package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RolePermissionJpaRepository : JpaRepository<RolePermissionEntity, Long> {

    fun findByRoleId(roleId: Long): List<RolePermissionEntity>

    fun findByPermissionId(permissionId: Long): List<RolePermissionEntity>

    fun existsByRoleIdAndPermissionId(roleId: Long, permissionId: Long): Boolean

    @Modifying
    @Query("DELETE FROM RolePermissionEntity rp WHERE rp.role.id = :roleId")
    fun deleteByRoleId(roleId: Long): Int

    @Modifying
    @Query("DELETE FROM RolePermissionEntity rp WHERE rp.role.id = :roleId AND rp.permission.id = :permissionId")
    fun deleteByRoleIdAndPermissionId(roleId: Long, permissionId: Long): Int
}
