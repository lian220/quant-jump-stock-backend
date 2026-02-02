package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface RoleJpaRepository : JpaRepository<RoleEntity, Long> {

    fun findByName(name: String): Optional<RoleEntity>

    fun findByIsSystemTrue(): List<RoleEntity>

    fun existsByName(name: String): Boolean

    @Query("""
        SELECT r FROM RoleEntity r
        LEFT JOIN FETCH r.rolePermissions rp
        LEFT JOIN FETCH rp.permission
        WHERE r.name = :name
    """)
    fun findByNameWithPermissions(name: String): Optional<RoleEntity>

    @Query("""
        SELECT r FROM RoleEntity r
        LEFT JOIN FETCH r.rolePermissions rp
        LEFT JOIN FETCH rp.permission
    """)
    fun findAllWithPermissions(): List<RoleEntity>

    @Query("""
        SELECT DISTINCT r FROM RoleEntity r
        JOIN r.userRoles ur
        WHERE ur.user.id = :userId
    """)
    fun findByUserId(userId: Long): List<RoleEntity>
}
