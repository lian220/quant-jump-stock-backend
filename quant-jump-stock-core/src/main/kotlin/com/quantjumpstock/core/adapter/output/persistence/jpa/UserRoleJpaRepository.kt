package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRoleJpaRepository : JpaRepository<UserRoleEntity, Long> {

    fun findByUserId(userId: Long): List<UserRoleEntity>

    fun findByRoleId(roleId: Long): List<UserRoleEntity>

    fun findByUserIdAndRoleId(userId: Long, roleId: Long): Optional<UserRoleEntity>

    fun existsByUserIdAndRoleId(userId: Long, roleId: Long): Boolean

    @Query("""
        SELECT ur FROM UserRoleEntity ur
        JOIN FETCH ur.role r
        WHERE ur.user.id = :userId
    """)
    fun findByUserIdWithRole(userId: Long): List<UserRoleEntity>

    @Query("""
        SELECT ur FROM UserRoleEntity ur
        JOIN FETCH ur.role r
        LEFT JOIN FETCH r.rolePermissions rp
        LEFT JOIN FETCH rp.permission
        WHERE ur.user.id = :userId
    """)
    fun findByUserIdWithRoleAndPermissions(userId: Long): List<UserRoleEntity>

    @Modifying
    @Query("DELETE FROM UserRoleEntity ur WHERE ur.user.id = :userId")
    fun deleteByUserId(userId: Long): Int

    @Modifying
    @Query("DELETE FROM UserRoleEntity ur WHERE ur.user.id = :userId AND ur.role.id = :roleId")
    fun deleteByUserIdAndRoleId(userId: Long, roleId: Long): Int

    @Query("SELECT COUNT(ur) FROM UserRoleEntity ur WHERE ur.role.id = :roleId")
    fun countByRoleId(roleId: Long): Long
}
