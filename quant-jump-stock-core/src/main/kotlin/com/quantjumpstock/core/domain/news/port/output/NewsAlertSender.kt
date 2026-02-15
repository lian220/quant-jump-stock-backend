package com.quantjumpstock.core.domain.news.port.output

interface NewsAlertSender {
    fun sendAlert(message: String)
}
