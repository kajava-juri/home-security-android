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
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.kajava.homesecurity.adapters.DeviceAdapter
import com.kajava.homesecurity.api.ApiConfig
import com.kajava.homesecurity.api.ApiService
import com.kajava.homesecurity.databinding.ActivityDeviceListBinding
import com.kajava.homesecurity.models.Device
import com.kajava.homesecurity.models.DeviceStatus
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DeviceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceListBinding
    private lateinit var deviceAdapter: DeviceAdapter
    private var mqttService: MqttService? = null
    private var isServiceBound = false
    private var apiService: ApiService? = null

    companion object {
        private const val TAG = "DeviceListActivity"
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

        try {
            binding = ActivityDeviceListBinding.inflate(layoutInflater)
            setContentView(binding.root)

            Log.d(TAG, "DeviceListActivity created successfully")

            setupApi()
            setupRecyclerView()
            setupUI()
            checkPermissionsAndStart()
            loadDevices()

        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
            Toast.makeText(this, "Error starting app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.device_list_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                loadDevices()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload settings when returning from settings screen
        if (isServiceBound) {
            // Update MQTT service with new settings if changed
            val settingsManager = com.kajava.homesecurity.settings.SettingsManager(this)
            val newSettings = settingsManager.loadSettings()
            mqttService?.updateSettings(newSettings)
        }
    }

    private fun setupApi() {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl(ApiConfig.API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit.create(ApiService::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up API: ${e.message}", e)
            apiService = null
        }

    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter { device ->
            // Handle device click - navigate to device detail
            val intent = Intent(this, DeviceDetailActivity::class.java).apply {
                putExtra("device_id", device.id)
                putExtra("device_name", device.name)
                putExtra("device_location", device.location)
            }
            startActivity(intent)
        }

        binding.recyclerViewDevices.apply {
            layoutManager = LinearLayoutManager(this@DeviceListActivity)
            adapter = deviceAdapter
        }
    }

    private fun setupUI() {
        binding.apply {
            // Set up toolbar
            setSupportActionBar(toolbar)
            supportActionBar?.title = "Home Security Devices"

            // Swipe refresh functionality
            swipeRefresh.setOnRefreshListener {
                loadDevices()
            }

            // Floating action button for manual refresh
            fabRefresh.setOnClickListener {
                loadDevices()
            }

            // MQTT connection toggle
            btnToggleMqtt.setOnClickListener {
                if (isServiceBound && mqttService?.isConnected() == true) {
                    stopMqttService()
                } else {
                    startMqttService()
                }
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
    }

    private fun stopMqttService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }

        val intent = Intent(this, MqttService::class.java)
        stopService(intent)
        updateConnectionStatus(false)
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        binding.apply {
            if (isConnected) {
                tvConnectionStatus.text = "MQTT: Connected"
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this@DeviceListActivity, android.R.color.holo_green_dark))
                btnToggleMqtt.text = "Disconnect"
                btnToggleMqtt.setBackgroundColor(ContextCompat.getColor(this@DeviceListActivity, android.R.color.holo_red_light))
            } else {
                tvConnectionStatus.text = "MQTT: Disconnected"
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this@DeviceListActivity, android.R.color.holo_red_dark))
                btnToggleMqtt.text = "Connect"
                btnToggleMqtt.setBackgroundColor(ContextCompat.getColor(this@DeviceListActivity, android.R.color.holo_green_light))
            }
        }
    }

    private fun loadDevices() {
        binding.swipeRefresh.isRefreshing = true

        lifecycleScope.launch {
            try {
                val response = apiService!!.getDevices()

                if (response.isSuccessful) {
                    val devicesResponse = response.body()
                    devicesResponse?.let {
                        Log.d(TAG, "Loaded ${it.data.size} devices")

                        // Create device status map (placeholder data for now)
                        val deviceStatuses = it.data.associate { device ->
                            device.id to DeviceStatus(
                                isOnline = (0..1).random() == 1, // Random for demo
                                lastSeen = System.currentTimeMillis() - (0..3600000).random(), // Random last seen
                                alarmState = listOf("armed", "disarmed", "triggered", null).random()
                            )
                        }

                        deviceAdapter.updateDevices(it.data, deviceStatuses)

                        // Update UI
                        binding.tvDeviceCount.text = "${it.data.size} devices found"
                    }
                } else {
                    Log.e(TAG, "API call failed: ${response.code()} - ${response.message()}")
                    Toast.makeText(this@DeviceListActivity, "Failed to load devices: ${response.message()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading devices: ${e.message}")
                Toast.makeText(this@DeviceListActivity, "Error loading devices: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (isServiceBound) {
                unbindService(serviceConnection)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
    }
}