package com.quantjumpstock.core.infrastructure.migration

import com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountJpaRepository
import com.quantjumpstock.core.infrastructure.security.EncryptionService
import com.quantjumpstock.core.infrastructure.security.EncryptionServiceGcm
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 단일 row 단위 ECB → GCM 재암호화 트랜잭션 경계.
 *
 * [AppSecretReencryptionRunner] 가 row id 를 넘겨 호출.
 * `REQUIRES_NEW` 로 매 row 마다 독립 트랜잭션을 열어 다음을 보장:
 * - 한 row 의 복호화/재암호화 실패가 다른 row 의 커밋에 영향을 주지 않음 (fail-soft).
 * - Runner 의 self-invocation 으로 `@Transactional` 이 우회되던 버그 회피
 *   (별도 빈을 통한 호출이므로 Spring AOP 가 정상 적용됨).
 *
 * Idempotency: V2 가 이미 채워진 row 는 즉시 반환 (early-out).
 */
@Service
class AppSecretRowReencryptionService(
    private val repo: UserKisAccountJpaRepository,
    private val legacy: EncryptionService,
    private val gcm: EncryptionServiceGcm
) {

    /**
     * 단일 row 를 ECB → GCM 으로 재암호화하고 즉시 커밋.
     *
     * @param entityId 대상 [com.quantjumpstock.core.adapter.output.persistence.jpa.UserKisAccountEntity] 의 PK.
     * @throws IllegalStateException row 가 존재하지 않으면.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun reencryptOne(entityId: Long) {
        val entity = repo.findById(entityId).orElseThrow {
            IllegalStateException("UserKisAccountEntity not found: id=$entityId")
        }
        // idempotent: 이미 V2 가 있으면 변경 없이 반환
        if (entity.appSecretEncryptedV2 != null) return
        val plain = legacy.decrypt(entity.appSecretEncrypted)
        entity.appSecretEncryptedV2 = gcm.encrypt(plain)
        repo.save(entity)
    }
}
