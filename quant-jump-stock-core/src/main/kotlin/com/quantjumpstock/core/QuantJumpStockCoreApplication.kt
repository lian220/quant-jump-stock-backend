package com.quantjumpstock.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@EnableAsync
class QuantJumpStockCoreApplication

fun main(args: Array<String>) {
    runApplication<QuantJumpStockCoreApplication>(*args)
}
