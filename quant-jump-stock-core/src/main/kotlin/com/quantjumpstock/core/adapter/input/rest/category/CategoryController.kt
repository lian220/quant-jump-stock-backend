package com.quantjumpstock.core.adapter.input.rest.category

import com.quantjumpstock.core.application.category.CategoriesResponse
import com.quantjumpstock.core.application.category.CategoryDto
import com.quantjumpstock.core.application.category.CategoryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * 전략 카테고리 Controller
 * 카테고리 조회 API
 */
@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Category", description = "전략 카테고리 API")
@CrossOrigin(origins = ["http://localhost:3000", "http://localhost:4000"], allowCredentials = "true")
class CategoryController(
    private val categoryService: CategoryService
) {

    /**
     * 전체 카테고리 목록 조회
     * GET /api/v1/categories
     */
    @GetMapping
    @Operation(
        summary = "카테고리 목록 조회",
        description = "활성화된 모든 전략 카테고리를 조회합니다. 정렬순서대로 반환됩니다."
    )
    fun getAllCategories(): ResponseEntity<CategoriesResponse> {
        val response = categoryService.getAllCategories()
        return ResponseEntity.ok(response)
    }

    /**
     * 코드로 카테고리 조회
     * GET /api/v1/categories/code/{code}
     */
    @GetMapping("/code/{code}")
    @Operation(
        summary = "코드로 카테고리 조회",
        description = "카테고리 코드로 특정 카테고리를 조회합니다."
    )
    fun getCategoryByCode(@PathVariable code: String): ResponseEntity<CategoryDto> {
        val category = categoryService.getCategoryByCode(code)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(category)
    }

    /**
     * ID로 카테고리 조회
     * GET /api/v1/categories/{id}
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "ID로 카테고리 조회",
        description = "카테고리 ID로 특정 카테고리를 조회합니다."
    )
    fun getCategoryById(@PathVariable id: Long): ResponseEntity<CategoryDto> {
        val category = categoryService.getCategoryById(id)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(category)
    }
}
