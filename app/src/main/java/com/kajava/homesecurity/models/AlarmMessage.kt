package com.kajava.homesecurity.models

import com.google.gson.annotations.SerializedName

data class AlarmMessage(
    @SerializedName("triggered_by")
    val triggeredBy: String? = null,
    val timestamp: Long = 0,
    val state: String? = null,
    val message: String? = null
)

data class WebSocketMessage(
    val action: String,
    val topics: List<String>? = null
)

data class CommandResponse(
    val status: String,
    val message: String,
    val command: String,
    val timestamp: Long
)