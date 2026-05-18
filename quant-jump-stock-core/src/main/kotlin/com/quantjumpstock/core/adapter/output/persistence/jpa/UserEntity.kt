package com.quantjumpstock.core.adapter.output.persistence.jpa

import jakarta.persistence.*
import java.time.LocalDateTime
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp

@Entity
@Table(name = "users")
class UserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", unique = true, nullable = false, length = 50)
    val userId: String,

    @Column(length = 100)
    val name: String? = null,

    @Column(unique = true, length = 100)
    val email: String? = null,

    @Column(name = "password_hash", length = 255)
    val passwordHash: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", length = 20)
    val oauthProvider: OAuthProvider? = null,

    @Column(name = "oauth_provider_id", length = 255)
    val oauthProviderId: String? = null,

    @Column(name = "profile_image_url", length = 500)
    val profileImageUrl: String? = null,

    @Column(name = "phone", length = 20)
    val phone: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val status: UserStatus = UserStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val role: UserRole = UserRole.USER,

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var tradingConfig: TradingConfigEntity? = null,

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var accountBalance: AccountBalanceEntity? = null,

    // UserKisAccount 는 A+ 모델 (단일 활성 + 7일 휴지통) 도입으로 사용자당 N row 보유 가능.
    // 활성/휴지통 row 조회는 UserKisAccountRepository 의 명시적 메서드를 통해서만 한다.
    // 따라서 UserEntity 의 역방향 필드 제거 (사용처 없었음).

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val trades: MutableList<TradeEntity> = mutableListOf(),

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)

enum class UserStatus {
    ACTIVE, INACTIVE, SUSPENDED
}

enum class UserRole {
    ADMIN, USER, MODERATOR
}

enum class OAuthProvider {
    GOOGLE, NAVER
}
