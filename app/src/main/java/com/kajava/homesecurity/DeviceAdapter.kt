package com.kajava.homesecurity.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.kajava.homesecurity.R
import com.kajava.homesecurity.databinding.ItemDeviceBinding
import com.kajava.homesecurity.models.Device
import com.kajava.homesecurity.models.DeviceStatus
import java.text.SimpleDateFormat
import java.util.*

class DeviceAdapter(
    private val onDeviceClick: (Device) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    private var devices = listOf<Device>()
    private var deviceStatuses = mapOf<Int, DeviceStatus>()

    fun updateDevices(newDevices: List<Device>, newStatuses: Map<Int, DeviceStatus>) {
        devices = newDevices
        deviceStatuses = newStatuses
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    override fun getItemCount(): Int = devices.size

    inner class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(device: Device) {
            val status = deviceStatuses[device.id]

            binding.apply {
                // Device info
                tvDeviceName.text = device.name
                tvDeviceLocation.text = device.location ?: "Unknown location"
                tvDeviceDescription.text = device.description?.takeIf { it.isNotEmpty() }
                    ?: "No description"

                // Online status
                val isOnline = status?.isOnline ?: false
                tvOnlineStatus.text = if (isOnline) "Online" else "Offline"
                tvOnlineStatus.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        if (isOnline) android.R.color.holo_green_dark
                        else android.R.color.holo_red_dark
                    )
                )

                // Last seen
                status?.lastSeen?.let { lastSeen ->
                    val timeAgo = getTimeAgo(lastSeen)
                    tvLastSeen.text = "Last seen: $timeAgo"
                } ?: run {
                    tvLastSeen.text = "Last seen: Unknown"
                }

                // Alarm state
                val alarmState = status?.alarmState
                when (alarmState) {
                    "armed" -> {
                        tvAlarmState.text = "🛡️ Armed"
                        tvAlarmState.setTextColor(
                            ContextCompat.getColor(itemView.context, android.R.color.holo_blue_dark)
                        )
                    }
                    "disarmed" -> {
                        tvAlarmState.text = "✅ Disarmed"
                        tvAlarmState.setTextColor(
                            ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
                        )
                    }
                    "triggered" -> {
                        tvAlarmState.text = "🚨 Triggered"
                        tvAlarmState.setTextColor(
                            ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark)
                        )
                    }
                    else -> {
                        tvAlarmState.text = "❓ Unknown"
                        tvAlarmState.setTextColor(
                            ContextCompat.getColor(itemView.context, android.R.color.darker_gray)
                        )
                    }
                }


                // Click handler
                root.setOnClickListener {
                    onDeviceClick(device)
                }

                // Add visual feedback for clickable items
                root.setBackgroundResource(android.R.drawable.list_selector_background)
            }
        }

        private fun getTimeAgo(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60 * 1000 -> "Just now"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
                else -> {
                    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                    sdf.format(Date(timestamp))
                }
            }
        }
    }
}