package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.AccountTypeEntityEnum
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.KisTokenJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.UserEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * KIS 토큰 DB 영속화 전담 빈.
 *
 * Phase 1A PRE 패널 + CodeRabbit 리뷰 #2 반영:
 *  - KIS HTTP 호출은 트랜잭션 밖, DB 영속화만 본 빈 (@Transactional) 에서 수행.
 *  - 별도 빈으로 분리해야 Spring AOP 프록시 적용.
 */
@Component
class KisTokenStore(
    private val tokenRepository: KisTokenJpaRepository,
) {
    /**
     * KIS OAuth 호출 결과를 DB 에 영속화. deactivate + save 가 한 트랜잭션.
     */
    @Transactional
    fun persist(
        user: UserEntity,
        accountType: AccountTypeEntityEnum,
        accessToken: String,
        expirationTime: LocalDateTime,
    ) {
        val userPk = user.id ?: throw IllegalStateException("User ID is null")
        tokenRepository.deactivateUserTokens(userPk, accountType, LocalDateTime.now())
        tokenRepository.save(
            KisTokenEntity(
                user = user,
                accountType = accountType,
                accessToken = accessToken,
                expirationTime = expirationTime,
            ),
        )
    }
}
