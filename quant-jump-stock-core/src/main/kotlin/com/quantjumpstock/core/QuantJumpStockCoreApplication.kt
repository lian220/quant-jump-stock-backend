package com.quantjumpstock.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import java.util.TimeZone

@SpringBootApplication
@EnableAsync
@ConfigurationPropertiesScan("com.quantjumpstock.core.config")
class QuantJumpStockCoreApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    runApplication<QuantJumpStockCoreApplication>(*args)
}
