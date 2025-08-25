package com.kajava.homesecurity

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kajava.homesecurity.databinding.ActivitySettingsBinding
import com.kajava.homesecurity.settings.AppSettings
import com.kajava.homesecurity.settings.SettingsManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var settingsManager: SettingsManager
    private var currentSettings = AppSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settingsManager = SettingsManager(this)

        setupToolbar()
        loadCurrentSettings()
        setupInputListeners()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "MQTT Settings"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun loadCurrentSettings() {
        currentSettings = settingsManager.loadSettings()

        binding.apply {
            etMqttHost.setText(currentSettings.mqttBrokerHost)
            etMqttPort.setText(currentSettings.mqttBrokerPort.toString())
            etMqttClientId.setText(currentSettings.mqttClientId)
            etMqttUsername.setText(currentSettings.mqttUsername)
            etMqttPassword.setText(currentSettings.mqttPassword)
            switchUseSsl.isChecked = currentSettings.useSsl
            etConnectionTimeout.setText(currentSettings.connectionTimeout.toString())
            etKeepAliveInterval.setText(currentSettings.keepAliveInterval.toString())

            // Update connection string preview
            updateConnectionPreview()
        }
    }

    private fun setupInputListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateConnectionPreview()
            }
        }

        binding.apply {
            etMqttHost.addTextChangedListener(textWatcher)
            etMqttPort.addTextChangedListener(textWatcher)
            switchUseSsl.setOnCheckedChangeListener { _, _ -> updateConnectionPreview() }
        }
    }

    private fun setupButtons() {
        binding.apply {
            btnSave.setOnClickListener {
                if (validateAndSaveSettings()) {
                    Toast.makeText(this@SettingsActivity, "Settings saved successfully", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            btnTestConnection.setOnClickListener {
                testConnection()
            }

            btnResetDefaults.setOnClickListener {
                resetToDefaults()
            }

            btnCancel.setOnClickListener {
                finish()
            }
        }
    }

    private fun validateAndSaveSettings(): Boolean {
        try {
            val host = binding.etMqttHost.text.toString().trim()
            val port = binding.etMqttPort.text.toString().toIntOrNull() ?: 8883
            val clientId = binding.etMqttClientId.text.toString().trim()
            val username = binding.etMqttUsername.text.toString().trim()
            val password = binding.etMqttPassword.text.toString()
            val useSsl = binding.switchUseSsl.isChecked
            val timeout = binding.etConnectionTimeout.text.toString().toIntOrNull() ?: 10
            val keepAlive = binding.etKeepAliveInterval.text.toString().toIntOrNull() ?: 20

            // Validation
            if (host.isEmpty()) {
                binding.etMqttHost.error = "Host cannot be empty"
                return false
            }

            if (port < 1 || port > 65535) {
                binding.etMqttPort.error = "Port must be between 1 and 65535"
                return false
            }

            if (clientId.isEmpty()) {
                binding.etMqttClientId.error = "Client ID cannot be empty"
                return false
            }

            if (timeout < 5 || timeout > 60) {
                binding.etConnectionTimeout.error = "Timeout must be between 5 and 60 seconds"
                return false
            }

            if (keepAlive < 10 || keepAlive > 300) {
                binding.etKeepAliveInterval.error = "Keep alive must be between 10 and 300 seconds"
                return false
            }

            // Save settings
            val newSettings = AppSettings(
                mqttBrokerHost = host,
                mqttBrokerPort = port,
                mqttClientId = clientId,
                mqttUsername = username,
                mqttPassword = password,
                useSsl = useSsl,
                connectionTimeout = timeout,
                keepAliveInterval = keepAlive
            )

            settingsManager.saveSettings(newSettings)
            return true

        } catch (e: Exception) {
            Toast.makeText(this, "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }

    private fun updateConnectionPreview() {
        val host = binding.etMqttHost.text.toString().trim()
        val port = binding.etMqttPort.text.toString()
        val useSsl = binding.switchUseSsl.isChecked

        val protocol = if (useSsl) "ssl" else "tcp"
        val connectionString = "$protocol://$host:$port"

        binding.tvConnectionPreview.text = "Connection: $connectionString"
    }

    private fun testConnection() {
        Toast.makeText(this, "Connection test would be implemented here", Toast.LENGTH_SHORT).show()
        // TODO: Implement actual connection test
        // This could create a temporary MQTT client and try to connect
    }

    private fun resetToDefaults() {
        settingsManager.resetToDefaults()
        loadCurrentSettings()
        Toast.makeText(this, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}