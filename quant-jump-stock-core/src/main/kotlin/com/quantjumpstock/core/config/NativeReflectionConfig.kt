package com.quantjumpstock.core.config

import com.quantjumpstock.core.application.news.NotificationListResponse
import com.quantjumpstock.core.application.news.NotificationResponse
import com.quantjumpstock.core.application.news.SimpleResponse
import com.quantjumpstock.core.application.news.SubscriptionListResponse
import com.quantjumpstock.core.application.news.SubscriptionResponse
import com.quantjumpstock.core.application.portfolio.DefaultStockItem
import com.quantjumpstock.core.application.portfolio.DefaultStockListResponse
import com.quantjumpstock.core.application.portfolio.DefaultStockResponse
import com.quantjumpstock.core.application.portfolio.PortfolioResponse
import com.quantjumpstock.core.application.portfolio.PortfolioStockListResponse
import com.quantjumpstock.core.application.portfolio.PortfolioStockResponse
import com.quantjumpstock.core.application.portfolio.UserPortfolioResponse
import com.quantjumpstock.core.application.subscription.AlertUpdateResponse
import com.quantjumpstock.core.application.subscription.MySubscriptionsResponse
import com.quantjumpstock.core.application.subscription.SubscribeResponse
import com.quantjumpstock.core.application.subscription.SubscriptionSummary
import com.quantjumpstock.core.application.subscription.UnsubscribeResponse
import com.quantjumpstock.core.application.tier.TierConfigurationDto
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

/**
 * GraalVM Native Image에서 Jackson이 DTO를 직렬화할 수 있도록
 * 리플렉션 힌트를 등록합니다.
 *
 * ResponseEntity<Any>를 반환하는 컨트롤러의 DTO는
 * Spring AOT가 타입을 추론할 수 없어 수동 등록이 필요합니다.
 */
@Configuration
@RegisterReflectionForBinding(
    // 전략 구독 DTO
    MySubscriptionsResponse::class,
    SubscriptionSummary::class,
    SubscribeResponse::class,
    UnsubscribeResponse::class,
    AlertUpdateResponse::class,
    // 포트폴리오 DTO
    UserPortfolioResponse::class,
    PortfolioStockResponse::class,
    PortfolioStockListResponse::class,
    DefaultStockListResponse::class,
    DefaultStockResponse::class,
    DefaultStockItem::class,
    PortfolioResponse::class,
    // 뉴스 구독 DTO
    SubscriptionResponse::class,
    SubscriptionListResponse::class,
    NotificationResponse::class,
    NotificationListResponse::class,
    SimpleResponse::class,
    // 티어 설정 DTO
    TierConfigurationDto::class,
)
class NativeReflectionConfig
