package com.quantjumpstock.core.adapter.output.persistence.jpa

import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountBalanceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.Optional

@Repository
interface AccountBalanceJpaRepository : JpaRepository<AccountBalanceEntity, Long> {

    @Query("SELECT ab FROM AccountBalanceEntity ab WHERE ab.user.id = :userId")
    fun findByUserId(userId: Long): Optional<AccountBalanceEntity>

    @Query("SELECT ab FROM AccountBalanceEntity ab WHERE ab.user.id = :userId")
    fun findByUserIdNullable(userId: Long): AccountBalanceEntity?

    @Query("SELECT ab FROM AccountBalanceEntity ab JOIN FETCH ab.user WHERE ab.user.userId = :userId")
    fun findByUserUserId(userId: String): Optional<AccountBalanceEntity>

    @Query("SELECT ab.cash - ab.lockedCash FROM AccountBalanceEntity ab WHERE ab.user.id = :userId")
    fun getAvailableCash(userId: Long): BigDecimal?

    @Modifying
    @Query("""
        UPDATE AccountBalanceEntity ab
        SET ab.cash = ab.cash + :amount, ab.updatedAt = CURRENT_TIMESTAMP
        WHERE ab.user.id = :userId
    """)
    fun addCash(userId: Long, amount: BigDecimal): Int

    @Modifying
    @Query("""
        UPDATE AccountBalanceEntity ab
        SET ab.lockedCash = ab.lockedCash + :amount, ab.updatedAt = CURRENT_TIMESTAMP
        WHERE ab.user.id = :userId AND ab.cash - ab.lockedCash >= :amount
    """)
    fun lockCash(userId: Long, amount: BigDecimal): Int

    @Modifying
    @Query("""
        UPDATE AccountBalanceEntity ab
        SET ab.lockedCash = ab.lockedCash - :amount, ab.updatedAt = CURRENT_TIMESTAMP
        WHERE ab.user.id = :userId AND ab.lockedCash >= :amount
    """)
    fun unlockCash(userId: Long, amount: BigDecimal): Int

    @Query("SELECT SUM(ab.cash) FROM AccountBalanceEntity ab")
    fun getTotalCashInSystem(): BigDecimal?

    @Query("SELECT CASE WHEN COUNT(ab) > 0 THEN true ELSE false END FROM AccountBalanceEntity ab WHERE ab.user.id = :userId")
    fun existsByUserId(userId: Long): Boolean
}
