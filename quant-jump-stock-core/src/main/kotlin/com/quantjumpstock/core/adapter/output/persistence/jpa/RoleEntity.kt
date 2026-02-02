package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp

@Entity
@Table(name = "roles")
data class RoleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false, length = 50)
    val name: String,

    @Column(length = 255)
    val description: String? = null,

    @Column(name = "is_system")
    val isSystem: Boolean = false,

    @OneToMany(mappedBy = "role", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val rolePermissions: MutableList<RolePermissionEntity> = mutableListOf(),

    @OneToMany(mappedBy = "role", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val userRoles: MutableList<UserRoleEntity> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        const val SUPER_ADMIN = "SUPER_ADMIN"
        const val ADMIN = "ADMIN"
        const val MODERATOR = "MODERATOR"
        const val USER = "USER"
    }
}
