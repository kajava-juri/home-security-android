package com.kajava.homesecurity.settings

import android.content.Context
import android.content.SharedPreferences

data class AppSettings(
    val mqttBrokerHost: String = "192.168.1.100",
    val mqttBrokerPort: Int = 8883,
    val mqttClientId: String = "android_home_security",
    val mqttUsername: String = "",
    val mqttPassword: String = "",
    val useSsl: Boolean = true,
    val connectionTimeout: Int = 10,
    val keepAliveInterval: Int = 20
)

class SettingsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "home_security_settings"
        private const val KEY_MQTT_HOST = "mqtt_host"
        private const val KEY_MQTT_PORT = "mqtt_port"
        private const val KEY_MQTT_CLIENT_ID = "mqtt_client_id"
        private const val KEY_MQTT_USERNAME = "mqtt_username"
        private const val KEY_MQTT_PASSWORD = "mqtt_password"
        private const val KEY_USE_SSL = "use_ssl"
        private const val KEY_CONNECTION_TIMEOUT = "connection_timeout"
        private const val KEY_KEEP_ALIVE_INTERVAL = "keep_alive_interval"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSettings(settings: AppSettings) {
        sharedPreferences.edit().apply {
            putString(KEY_MQTT_HOST, settings.mqttBrokerHost)
            putInt(KEY_MQTT_PORT, settings.mqttBrokerPort)
            putString(KEY_MQTT_CLIENT_ID, settings.mqttClientId)
            putString(KEY_MQTT_USERNAME, settings.mqttUsername)
            putString(KEY_MQTT_PASSWORD, settings.mqttPassword)
            putBoolean(KEY_USE_SSL, settings.useSsl)
            putInt(KEY_CONNECTION_TIMEOUT, settings.connectionTimeout)
            putInt(KEY_KEEP_ALIVE_INTERVAL, settings.keepAliveInterval)
            apply()
        }
    }

    fun loadSettings(): AppSettings {
        return AppSettings(
            mqttBrokerHost = sharedPreferences.getString(KEY_MQTT_HOST, "192.168.1.100") ?: "192.168.1.100",
            mqttBrokerPort = sharedPreferences.getInt(KEY_MQTT_PORT, 8883),
            mqttClientId = sharedPreferences.getString(KEY_MQTT_CLIENT_ID, "android_home_security") ?: "android_home_security",
            mqttUsername = sharedPreferences.getString(KEY_MQTT_USERNAME, "") ?: "",
            mqttPassword = sharedPreferences.getString(KEY_MQTT_PASSWORD, "") ?: "",
            useSsl = sharedPreferences.getBoolean(KEY_USE_SSL, true),
            connectionTimeout = sharedPreferences.getInt(KEY_CONNECTION_TIMEOUT, 10),
            keepAliveInterval = sharedPreferences.getInt(KEY_KEEP_ALIVE_INTERVAL, 20)
        )
    }

    fun resetToDefaults() {
        saveSettings(AppSettings())
    }
}