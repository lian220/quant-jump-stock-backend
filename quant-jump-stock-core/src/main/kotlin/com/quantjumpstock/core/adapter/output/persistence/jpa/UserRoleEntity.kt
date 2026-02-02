package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp

@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_roles",
            columnNames = ["user_id", "role_id"]
        )
    ]
)
data class UserRoleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: UserEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    val role: RoleEntity,

    @Column(name = "assigned_at")
    val assignedAt: LocalDateTime = LocalDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    val assignedBy: UserEntity? = null,

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
