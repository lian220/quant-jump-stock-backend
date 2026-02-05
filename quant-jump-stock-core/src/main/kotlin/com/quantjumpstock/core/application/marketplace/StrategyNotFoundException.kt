package com.quantjumpstock.core.application.marketplace

/**
 * 전략을 찾을 수 없을 때 발생하는 예외
 */
class StrategyNotFoundException(message: String) : RuntimeException(message)
