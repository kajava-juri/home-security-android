package com.kajava.homesecurity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kajava.homesecurity.databinding.ActivityDeviceDetailBinding
import com.google.gson.Gson

class DeviceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceDetailBinding
    private var mqttService: MqttService? = null
    private var isServiceBound = false
    private val gson = Gson()

    // Device info
    private var deviceId: Int = -1
    private var deviceName: String = ""
    private var deviceLocation: String = ""

    companion object {
        private const val TAG = "DeviceDetailActivity"
        private const val COMMAND_SOURCE = "android_app"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MqttService.LocalBinder
            mqttService = binder.getService()
            isServiceBound = true

            // Update connection status
            updateConnectionStatus(mqttService?.isConnected() == true)

            // Listen for connection status changes
            mqttService?.onConnectionStatusChanged = { isConnected ->
                runOnUiThread {
                    updateConnectionStatus(isConnected)
                }
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mqttService = null
            isServiceBound = false
            updateConnectionStatus(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get device info from intent
        deviceId = intent.getIntExtra("device_id", -1)
        deviceName = intent.getStringExtra("device_name") ?: "Unknown Device"
        deviceLocation = intent.getStringExtra("device_location") ?: "Unknown Location"

        setupUI()
        bindMqttService()
    }

    private fun setupUI() {
        binding.apply {
            // Set device info
            tvDeviceName.text = deviceName
            tvDeviceLocation.text = deviceLocation

            // Set up toolbar/action bar
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                title = deviceName
                setDisplayHomeAsUpEnabled(true)
            }

            // Command buttons
            btnArmAlarm.setOnClickListener {
                sendCommand("arm")
            }

            btnDisarmAlarm.setOnClickListener {
                sendCommand("disarm")
            }

            btnResetAlarm.setOnClickListener {
                sendCommand("reset")
            }

            btnGetStatus.setOnClickListener {
                sendCommand("status")
            }

            // Test notification button
            btnTestNotification.setOnClickListener {
                testNotification()
            }
        }
    }

    private fun bindMqttService() {
        val intent = Intent(this, MqttService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun sendCommand(command: String) {
        if (!isServiceBound || mqttService?.isConnected() != true) {
            Toast.makeText(this, "Not connected to MQTT broker", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Create command message
            val commandMessage = mapOf(
                "command" to command,
                "source" to COMMAND_SOURCE
            )

            val jsonMessage = gson.toJson(commandMessage)

            // Build topic for specific device: sensor_hub/{device_name}/cmd
            val commandTopic = "sensor_hub/$deviceName/cmd"

            // Publish command
            mqttService?.publishMessage(commandTopic, jsonMessage)

            // Show feedback to user
            val commandName = command.replaceFirstChar { it.uppercase() }
            Toast.makeText(this, "$commandName command sent to $deviceName", Toast.LENGTH_SHORT).show()

            // Update last command info
            binding.tvLastCommand.text = "Last command: $commandName (${getCurrentTime()})"

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send $command command: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun testNotification() {
        val notificationHelper = NotificationHelper(this)
        val testAlarm = com.kajava.homesecurity.models.AlarmMessage(
            triggeredBy = deviceName,
            timestamp = System.currentTimeMillis(),
            state = "triggered"
        )
        notificationHelper.showAlarmNotification(testAlarm)
        Toast.makeText(this, "Test notification sent for $deviceName", Toast.LENGTH_SHORT).show()
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        binding.apply {
            if (isConnected) {
                tvConnectionStatus.text = "MQTT: Connected"
                tvConnectionStatus.setTextColor(
                    ContextCompat.getColor(this@DeviceDetailActivity, android.R.color.holo_green_dark)
                )

                // Enable command buttons when connected
                btnArmAlarm.isEnabled = true
                btnDisarmAlarm.isEnabled = true
                btnResetAlarm.isEnabled = true
                btnGetStatus.isEnabled = true
            } else {
                tvConnectionStatus.text = "MQTT: Disconnected"
                tvConnectionStatus.setTextColor(
                    ContextCompat.getColor(this@DeviceDetailActivity, android.R.color.holo_red_dark)
                )

                // Disable command buttons when disconnected
                btnArmAlarm.isEnabled = false
                btnDisarmAlarm.isEnabled = false
                btnResetAlarm.isEnabled = false
                btnGetStatus.isEnabled = false
            }
        }
    }

    private fun getCurrentTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
}