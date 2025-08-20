package com.kajava.homesecurity

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.kajava.homesecurity.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mqttService: MqttService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MqttService.LocalBinder
            mqttService = binder.getService()
            isServiceBound = true

            // Update UI based on connection status
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

    // Permission launcher for notifications
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startMqttService()
        } else {
            Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissionsAndStart()
    }

    private fun setupUI() {
        binding.apply {
            btnStartService.setOnClickListener {
                startMqttService()
            }

            btnStopService.setOnClickListener {
                stopMqttService()
            }

            btnTestNotification.setOnClickListener {
                testNotification()
            }
        }
    }

    private fun checkPermissionsAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startMqttService()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startMqttService()
        }
    }

    private fun startMqttService() {
        val intent = Intent(this, MqttService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        updateUIButtons(true)
        binding.tvStatus.text = getString(R.string.connection_details_starting)
    }

    private fun stopMqttService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }

        val intent = Intent(this, MqttService::class.java)
        stopService(intent)

        updateUIButtons(false)
        updateConnectionStatus(false)
    }

    private fun testNotification() {
        val notificationHelper = NotificationHelper(this)
        val testAlarm = com.kajava.homesecurity.models.AlarmMessage(
            triggeredBy = "Test Sensor",
            timestamp = System.currentTimeMillis()
        )
        notificationHelper.showAlarmNotification(testAlarm)
        Toast.makeText(this, getString(R.string.test_notification_sent), Toast.LENGTH_SHORT).show()
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        binding.apply {
            if (isConnected) {
                tvStatus.text = getString(R.string.connection_status_connected)
                tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                tvConnectionDetails.text = getString(R.string.connection_details_connected)
            } else {
                tvStatus.text = getString(R.string.connection_status_disconnected)
                tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                tvConnectionDetails.text = getString(R.string.connection_details_disconnected)
            }
        }
    }

    private fun updateUIButtons(serviceRunning: Boolean) {
        binding.apply {
            btnStartService.isEnabled = !serviceRunning
            btnStopService.isEnabled = serviceRunning
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
}