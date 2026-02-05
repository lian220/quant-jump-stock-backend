package com.quantjumpstock.core.domain.ml.port.output

/**
 * ML Package Storage Port (Output Port)
 *
 * ML 패키지 스토리지 관련 추상 인터페이스.
 * Application Service에서 이 Port를 통해 외부 스토리지에 접근.
 */
interface MlPackageStoragePort {

    /**
     * ML 패키지 업로드
     *
     * @param scriptName 업로드할 스크립트 이름
     * @return 업로드 결과
     */
    fun uploadPackage(scriptName: String): UploadResult

    /**
     * 패키지 상태 조회
     *
     * @return 패키지 상태 정보
     */
    fun getPackageStatus(): Map<String, Any>

    /**
     * 스토리지 사용 가능 여부
     */
    fun isAvailable(): Boolean

    /**
     * 업로드 결과 데이터 클래스
     */
    data class UploadResult(
        val success: Boolean,
        val message: String,
        val gcsUri: String? = null,
        val version: Int? = null
    )
}
