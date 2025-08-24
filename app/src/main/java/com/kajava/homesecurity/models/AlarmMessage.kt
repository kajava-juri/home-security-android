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

data class Device(
    @SerializedName("ID")
    val id: Int,
    @SerializedName("CreatedAt")
    val createdAt: String,
    @SerializedName("UpdatedAt")
    val updatedAt: String,
    @SerializedName("DeletedAt")
    val deletedAt: String?,
    val name: String,
    val description: String?,
    val location: String?
)

data class DevicesResponse(
    val data: List<Device>,
    val page: Int,
    val page_size: Int,
    val total_count: Int,
    val total_pages: Int
)

data class DeviceStatus(
    val isOnline: Boolean = false,
    val lastSeen: Long? = null,
    val alarmState: String? = null, // armed, disarmed, triggered
    val batteryLevel: Int? = null
)