package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.Stock
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface StockRepository : MongoRepository<Stock, String> {
    fun findByTicker(ticker: String): Stock?
    fun findByIsActive(isActive: Boolean): List<Stock>
}
