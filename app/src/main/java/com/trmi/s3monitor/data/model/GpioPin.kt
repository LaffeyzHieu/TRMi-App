package com.trmi.s3monitor.data.model

/** 1 chân GPIO trên ESP: khớp JSON của GET /gpio và POST /gpio/set. */
data class GpioPin(
    val pin: Int,
    val state: Boolean
)
