package com.trmi.s3monitor.data.model

data class SensorData(
    val ts: Long = 0L,
    val temp: Float = 0f,
    val humi: Float = 0f,
    val co2: Int = 0,
    val pm1: Int = 0,
    val pm25: Int = 0,
    val pm10: Int = 0,
    val status: SensorStatus = SensorStatus()
)

data class SensorStatus(
    val temp: Boolean = false,
    val co2: Boolean = false,
    val pm: Boolean = false
)