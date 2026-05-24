package com.quantjumpstock.core.adapter.output.persistence.jpa

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenEntity, UUID> {

    @Modifying
    @Query(
        """
        UPDATE RefreshTokenEntity rt
        SET rt.isRevoked = true, rt.revokedAt = :now
        WHERE rt.userDbId = :userDbId AND rt.isRevoked = false
        """
    )
    fun revokeAllByUser(@Param("userDbId") userDbId: Long, @Param("now") now: Instant): Int

    @Modifying
    @Query(
        """
        UPDATE RefreshTokenEntity rt
        SET rt.isRevoked = true, rt.revokedAt = :now
        WHERE rt.jti = :jti AND rt.isRevoked = false
        """
    )
    fun revokeByJti(@Param("jti") jti: UUID, @Param("now") now: Instant): Int
}
