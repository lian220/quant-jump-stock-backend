package com.quantjumpstock.core.domain.news.port.output

import com.quantjumpstock.core.domain.news.model.TagMapping

interface TagMappingRepository {
    fun findAll(): List<TagMapping>
}
