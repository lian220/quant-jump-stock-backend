package com.quantjumpstock.core.adapter.input.rest.stock

import com.quantjumpstock.core.domain.model.stock.PriceHistoryPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PriceHistoryPeriodConverterTest {
    private val converter = PriceHistoryPeriodConverter()

    @Test
    fun `토큰을 enum 으로 변환`() {
        assertEquals(PriceHistoryPeriod.ONE_MONTH, converter.convert("1m"))
        assertEquals(PriceHistoryPeriod.SIX_MONTHS, converter.convert("6m"))
        assertEquals(PriceHistoryPeriod.ONE_YEAR, converter.convert("1y"))
    }

    @Test
    fun `잘못된 토큰은 IllegalArgumentException (400 으로 매핑)`() {
        assertThrows(IllegalArgumentException::class.java) { converter.convert("2w") }
    }
}
