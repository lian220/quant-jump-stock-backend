package com.quantjumpstock.core.adapter.input.rest.stock

import com.quantjumpstock.core.domain.model.stock.PriceHistoryPeriod
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/**
 * `@RequestParam period: PriceHistoryPeriod` 쿼리 바인딩 컨버터.
 *
 * Spring 의 기본 enum 변환은 enum 이름(ONE_MONTH 등)으로만 매칭하므로
 * 토큰("1m"/"3m"/"6m"/"1y")을 받으려면 별도 컨버터가 필요하다.
 * (`@JsonCreator` 는 요청 body 역직렬화에만 적용되고 쿼리 파라미터에는 적용되지 않음)
 * 잘못된 토큰은 `PriceHistoryPeriod.from` 이 IllegalArgumentException 을 던져 400 으로 매핑된다.
 */
@Component
class PriceHistoryPeriodConverter : Converter<String, PriceHistoryPeriod> {
    override fun convert(source: String): PriceHistoryPeriod = PriceHistoryPeriod.from(source)
}
