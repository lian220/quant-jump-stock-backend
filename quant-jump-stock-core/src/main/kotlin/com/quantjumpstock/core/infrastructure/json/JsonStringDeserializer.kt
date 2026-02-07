package com.quantjumpstock.core.infrastructure.json

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode

/**
 * JSON 문자열과 JSON 객체 모두를 String으로 역직렬화하는 커스텀 디시리얼라이저.
 *
 * - String 입력: "{"key":"value"}" → 그대로 반환
 * - JSON 객체 입력: {"key":"value"} → toString()으로 변환
 */
class JsonStringDeserializer : JsonDeserializer<String>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String {
        val node = p.readValueAsTree<JsonNode>()
        return if (node.isTextual) node.asText() else node.toString()
    }
}
