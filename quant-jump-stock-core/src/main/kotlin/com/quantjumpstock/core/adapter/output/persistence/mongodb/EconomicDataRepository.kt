package com.quantjumpstock.core.adapter.output.persistence.mongodb

import com.quantjumpstock.core.domain.EconomicData
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository

@Repository
interface EconomicDataRepository : MongoRepository<EconomicData, String> {
    fun findFirstByOrderByDateDesc(): EconomicData?
}
