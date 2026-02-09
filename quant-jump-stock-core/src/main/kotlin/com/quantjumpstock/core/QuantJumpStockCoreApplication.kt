package com.quantjumpstock.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication class QuantJumpStockCoreApplication

fun main(args: Array<String>) {
    runApplication<QuantJumpStockCoreApplication>(*args)
}
