package com.trmi.s3monitor.data.model

data class Threshold(
    val temp: Float = 35f,
    val humi_low: Float = 35f,
    val co2: Int = 1000,
    val pm1: Float = 100f,
    val pm25: Float = 35f,
    val pm10: Float = 50f
)