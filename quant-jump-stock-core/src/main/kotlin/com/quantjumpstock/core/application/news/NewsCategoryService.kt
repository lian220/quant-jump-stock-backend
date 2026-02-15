package com.quantjumpstock.core.application.news

import com.quantjumpstock.core.adapter.output.persistence.jpa.NewsCategoryEntity
import com.quantjumpstock.core.adapter.output.persistence.jpa.NewsCategoryJpaRepository
import com.quantjumpstock.core.adapter.output.persistence.jpa.NewsSourceTagMappingJpaRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NewsCategoryService(
    private val categoryRepository: NewsCategoryJpaRepository,
    private val tagMappingRepository: NewsSourceTagMappingJpaRepository
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    // 캐시: 카테고리 가중치 (name → weight)
    @Volatile
    private var categoryWeightCache: Map<String, Double> = emptyMap()

    // 캐시: 소스 태그 → 카테고리명 매핑 (source:tag → categoryName)
    @Volatile
    private var tagMappingCache: Map<String, String> = emptyMap()

    @PostConstruct
    fun init() {
        refreshCache()
    }

    fun refreshCache() {
        val categories = categoryRepository.findByIsActiveTrue()
        categoryWeightCache = categories.associate { it.name to it.weight }

        val mappings = tagMappingRepository.findAll()
        tagMappingCache = mappings.associate {
            "${it.source}:${it.sourceTag}" to it.category.name
        }
        logger.info("뉴스 카테고리 캐시 갱신: ${categories.size}개 카테고리, ${mappings.size}개 태그 매핑")
    }

    /** NewsScorer에서 사용: 카테고리명 → 가중치 */
    fun getCategoryWeight(categoryName: String): Double {
        return categoryWeightCache[categoryName] ?: 0.10
    }

    /** 전체 카테고리 가중치 맵 (NewsScorer 주입용) */
    fun getAllCategoryWeights(): Map<String, Double> = categoryWeightCache

    /** 소스의 raw 태그를 정규화 카테고리명으로 변환 */
    fun resolveCategory(source: String, sourceTag: String): String? {
        return tagMappingCache["$source:$sourceTag"]
    }

    /** raw 태그 목록을 정규화 카테고리명 목록으로 변환 */
    fun resolveTags(source: String, rawTags: List<String>): List<String> {
        return rawTags.map { tag ->
            tagMappingCache["$source:$tag"] ?: tag
        }.distinct()
    }

    /** 그룹별 카테고리 목록 (API 응답용) */
    fun getCategoriesGrouped(): List<CategoryGroupDto> {
        val categories = categoryRepository.findByIsActiveTrueOrderBySortOrder()
        return categories
            .groupBy { it.categoryGroup }
            .map { (group, items) ->
                CategoryGroupDto(
                    group = group,
                    groupLabel = GROUP_LABELS[group] ?: group,
                    categories = items.map { it.toDto() }
                )
            }
            .sortedBy { GROUP_ORDER[it.group] ?: 99 }
    }

    /** 전체 카테고리 flat 목록 */
    fun getAllCategories(): List<CategoryDto> {
        return categoryRepository.findByIsActiveTrueOrderBySortOrder().map { it.toDto() }
    }

    companion object {
        private val GROUP_LABELS = mapOf(
            "MARKET" to "시장",
            "COMPANY" to "기업",
            "ASSET" to "자산",
            "MACRO" to "매크로",
            "INFO" to "정보"
        )
        private val GROUP_ORDER = mapOf(
            "MARKET" to 1,
            "COMPANY" to 2,
            "MACRO" to 3,
            "ASSET" to 4,
            "INFO" to 5
        )
    }
}

private fun NewsCategoryEntity.toDto() = CategoryDto(
    id = id!!,
    name = name,
    nameEn = nameEn,
    group = categoryGroup,
    description = description,
    icon = icon,
    weight = weight
)
