package com.quantjumpstock.core.domain

import java.time.LocalDateTime
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field

@Document(collection = "users")
data class User(
        @Id val id: String? = null,
        @Field("user_id") val userId: String,
        val email: String? = null,
        @Field("display_name") val displayName: String? = null,
        val preferences: UserPreferences? = UserPreferences(),
        val stocks: List<UserStockEmbedded> = emptyList(),
        @Field("account_balance") val accountBalance: AccountBalance? = null,
        @Field("trading_config") val tradingConfig: TradingConfig? = TradingConfig(),
        @Field("created_at") val createdAt: LocalDateTime = LocalDateTime.now(),
        @Field("updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)

data class UserPreferences(
        val defaultCurrency: String = "USD",
        val notificationEnabled: Boolean = true
)

data class UserStockEmbedded(
        val ticker: String,
        @Field("use_leverage") val useLeverage: Boolean = false,
        val notes: String? = null,
        val tags: List<String> = emptyList(),
        @Field("is_active") val isActive: Boolean = true,
        @Field("added_at") val addedAt: LocalDateTime = LocalDateTime.now()
)

data class AccountBalance(
        @Field("available_usd") val availableUsd: Double,
        @Field("total_valuation_usd") val totalValuationUsd: Double,
        @Field("total_assets_usd") val totalAssetsUsd: Double,
        @Field("total_cost_usd") val totalCostUsd: Double,
        @Field("total_value_usd") val totalValueUsd: Double,
        @Field("total_profit_usd") val totalProfitUsd: Double,
        @Field("total_profit_percent") val totalProfitPercent: Double,
        @Field("total_deposit_usd") val totalDepositUsd: Double,
        @Field("previous_total_deposit_usd") val previousTotalDepositUsd: Double = 0.0,
        @Field("total_return_percent") val totalReturnPercent: Double = 0.0,
        @Field("realized_return_percent") val realizedReturnPercent: Double = 0.0,
        @Field("holdings_count") val holdingsCount: Int,
        @Field("exchange_rate") val exchangeRate: Double,
        val currency: String = "USD",
        @Field("last_updated") val lastUpdated: LocalDateTime = LocalDateTime.now()
)

data class TradingConfig(
        val enabled: Boolean = false,
        @Field("auto_trading_enabled") val autoTradingEnabled: Boolean = false,
        @Field("min_composite_score") val minCompositeScore: Double = 2.0,
        @Field("max_stocks_to_buy") val maxStocksToBuy: Int = 5,
        @Field("max_amount_per_stock") val maxAmountPerStock: Double = 10000.0,
        @Field("stop_loss_percent") val stopLossPercent: Double = -7.0,
        @Field("take_profit_percent") val takeProfitPercent: Double = 5.0,
        @Field("use_sentiment") val useSentiment: Boolean = true,
        @Field("min_sentiment_score") val minSentimentScore: Double = 0.15,
        @Field("order_type") val orderType: String = "00",
        @Field("allow_buy_existing_stocks") val allowBuyExistingStocks: Boolean = true,
        @Field("updated_at") val updatedAt: LocalDateTime = LocalDateTime.now()
)
