package com.quantjumpstock.core.adapter.output.external

import com.quantjumpstock.core.adapter.output.persistence.jpa.KisAccountType
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
 *  - 기존 [KisTokenIssuer.issueNewToken] 은 `@Transactional` 안에서 KIS OAuth HTTP 호출
 *    (최대 30s) + DB 쓰기를 함께 수행 → slow/failed 네트워크 호출이 DB 트랜잭션 자원을
 *    장기 hold (Hikari pool 압박).
 *  - 본 빈으로 DB 쓰기만 분리 → KisTokenIssuer 는 KIS HTTP 호출만 *트랜잭션 밖* 수행
 *    하고, 결과를 본 빈의 `persist` 에 넘김. DB 트랜잭션은 deactivate + save 두 query
 *    만큼만 유지.
 *  - 별도 빈으로 분리해야 Spring AOP 프록시가 적용되어 `@Transactional` 이 정상 동작
 *    (self-invocation 회피).
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
        accountType: KisAccountType,
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
