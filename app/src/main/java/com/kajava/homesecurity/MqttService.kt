package com.kajava.homesecurity

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.kajava.homesecurity.models.AlarmMessage
import com.kajava.homesecurity.models.CommandResponse
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.KeyManagerFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLSocketFactory
import com.kajava.homesecurity.settings.SettingsManager
import com.kajava.homesecurity.settings.AppSettings

class MqttService : Service() {

    companion object {
        private const val TAG = "MqttService"
        private const val BROKER_URL = "ssl://192.168.1.223:8883" // Replace with your broker URL
        private const val CLIENT_ID = "android_home_security"
        private const val TOPIC_PATTERN = "sensor_hub/+/alarm/+"  // Subscribe to all alarm topics
        private const val COMMAND_RESPONSE_TOPIC = "sensor_hub/+/cmd/response"
        private const val QOS = 1
        private const val RECONNECT_DELAY_SECONDS = 5L
    }

    private val binder = LocalBinder()
    private var mqttClient: MqttClient? = null
    private lateinit var notificationHelper: NotificationHelper
    private val gson = Gson()
    private var reconnectExecutor: ScheduledExecutorService? = null
    private var isServiceRunning = false

    // Connection status callback
    var onConnectionStatusChanged: ((Boolean) -> Unit)? = null
    var onCommandResponse: ((CommandResponse) -> Unit)? = null

    private lateinit var settingsManager: SettingsManager
    private var currentSettings = AppSettings()

    inner class LocalBinder : Binder() {
        fun getService(): MqttService = this@MqttService
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        settingsManager = SettingsManager(this)
        currentSettings = settingsManager.loadSettings()
        Log.d(TAG, "MQTT Service created with settings: ${currentSettings.mqttBrokerHost}:${currentSettings.mqttBrokerPort}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MQTT Service started")

        if (!isServiceRunning) {
            isServiceRunning = true
            startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notificationHelper.createServiceNotification())
            connectMqtt()
        }

        return START_STICKY // Restart service if killed
    }

    // Add method to update settings
    fun updateSettings(newSettings: AppSettings) {
        if (currentSettings != newSettings) {
            Log.d(TAG, "Settings updated, reconnecting...")
            currentSettings = newSettings

            // Reconnect with new settings
            if (isServiceRunning) {
                disconnectMqtt()
                connectMqtt()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        disconnectMqtt()
        reconnectExecutor?.shutdown()
        Log.d(TAG, "MQTT Service destroyed")
    }

    private fun connectMqtt() {
        try {
            // Reload settings in case they changed
            currentSettings = settingsManager.loadSettings()

            val brokerUrl = buildBrokerUrl()
            Log.d(TAG, "Connecting to MQTT broker: $brokerUrl")

            val persistence = MemoryPersistence()
            mqttClient = MqttClient(brokerUrl, currentSettings.mqttClientId, persistence)

            val connOpts = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = currentSettings.connectionTimeout
                keepAliveInterval = currentSettings.keepAliveInterval
                isAutomaticReconnect = false

                // Set up SSL/TLS if enabled
                if (currentSettings.useSsl) {
                    socketFactory = createSSLSocketFactory()
                }

                // Set authentication if provided
                if (currentSettings.mqttUsername.isNotEmpty()) {
                    userName = currentSettings.mqttUsername
                    password = currentSettings.mqttPassword.toCharArray()
                }
            }

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    Log.d(TAG, "MQTT connection lost: ${cause?.message}")
                    notificationHelper.updateServiceNotification(false)
                    onConnectionStatusChanged?.invoke(false)

                    if (isServiceRunning) {
                        scheduleReconnect()
                    }
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    Log.d(TAG, "Message arrived on topic: $topic")
                    Log.d(TAG, "Message content: ${message?.toString()}")

                    topic?.let { t ->
                        message?.let { msg ->
                            when {
                                t == COMMAND_RESPONSE_TOPIC -> {
                                    handleCommandResponse(msg.toString())
                                }
                                t.matches("sensor_hub/.*/alarm/.*".toRegex()) -> {
                                    handleAlarmMessage(t, msg.toString())
                                }
                                else -> {
                                    Log.d(TAG, "Unhandled topic: $t")
                                }
                            }
                        }
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Not needed for subscriber
                }
            })

            mqttClient?.connect(connOpts)

            Log.d(TAG, "Connected to MQTT broker")
            notificationHelper.updateServiceNotification(true)
            onConnectionStatusChanged?.invoke(true)

            // Subscribe to topics
            mqttClient?.subscribe(TOPIC_PATTERN, QOS)
            mqttClient?.subscribe(COMMAND_RESPONSE_TOPIC, QOS)
            Log.d(TAG, "Subscribed to topics")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to MQTT broker: ${e.message}")
            scheduleReconnect()
        }
    }

    private fun buildBrokerUrl(): String {
        val protocol = if (currentSettings.useSsl) "ssl" else "tcp"
        return "$protocol://${currentSettings.mqttBrokerHost}:${currentSettings.mqttBrokerPort}"
    }

    fun getCurrentSettings(): AppSettings {
        return currentSettings
    }

    private fun handleCommandResponse(messageContent: String) {
        try {
            Log.d(TAG, "Handling command response: $messageContent")
            val commandResponse = gson.fromJson(messageContent, CommandResponse::class.java)

            // Log the response
            Log.d(TAG, "Command response: ${commandResponse.command} - ${commandResponse.status}: ${commandResponse.message}")

            // Notify callback if set
            onCommandResponse?.invoke(commandResponse)

            // Show a toast notification for command responses (optional)
            // You can customize this behavior based on your needs

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing command response: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createSSLSocketFactory(): SSLSocketFactory {
        return try {
            // Load CA bundle
            val caInput = assets.open("bundle.pem")
            val cf = CertificateFactory.getInstance("X.509")
            val caCertificates = cf.generateCertificates(caInput)
            caInput.close()

            Log.d(TAG, "Loaded ${caCertificates.size} CA certificates from bundle.pem")

            // Create trust store with CA certificates
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStore.load(null, null)
            caCertificates.forEachIndexed { index, cert ->
                trustStore.setCertificateEntry("ca$index", cert)
            }

            // Create TrustManager
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(trustStore)

            // Load client certificate
            val clientCertInput = assets.open("client.crt")
            val clientCert = cf.generateCertificate(clientCertInput)
            clientCertInput.close()

            // Load client private key
            val clientKeyInput = assets.open("client.key")
            val clientKeyBytes = clientKeyInput.readBytes()
            clientKeyInput.close()

            // Parse the private key (assuming PKCS#8 format)
            val clientPrivateKey = parsePrivateKey(clientKeyBytes)

            // Create client keystore
            val clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            clientKeyStore.load(null, null)
            clientKeyStore.setKeyEntry("client", clientPrivateKey, "".toCharArray(), arrayOf(clientCert))

            // Create KeyManager for client authentication
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(clientKeyStore, "".toCharArray())

            // Create SSL context with both trust and key managers
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(kmf.keyManagers, tmf.trustManagers, null)

            Log.d(TAG, "SSL context created successfully with client authentication")
            sslContext.socketFactory

        } catch (e: Exception) {
            Log.e(TAG, "Error creating SSL socket factory: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    private fun parsePrivateKey(keyBytes: ByteArray): java.security.PrivateKey {
        try {
            val keyString = String(keyBytes)

            // Remove PEM headers and whitespace
            val privateKeyPEM = keyString
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("\\s".toRegex(), "")

            // Decode base64
            val decoded = android.util.Base64.decode(privateKeyPEM, android.util.Base64.DEFAULT)

            // Try PKCS#8 format first
            return try {
                val keySpec = java.security.spec.PKCS8EncodedKeySpec(decoded)
                val keyFactory = java.security.KeyFactory.getInstance("RSA")
                keyFactory.generatePrivate(keySpec)
            } catch (e: Exception) {
                Log.d(TAG, "PKCS#8 failed, trying PKCS#1 format")
                // If PKCS#8 fails, try converting from PKCS#1 to PKCS#8
                parsePKCS1PrivateKey(decoded)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing private key: ${e.message}")
            throw e
        }
    }

    private fun parsePKCS1PrivateKey(pkcs1Bytes: ByteArray): java.security.PrivateKey {
        // This is a simplified PKCS#1 to PKCS#8 converter
        // For production, you might want to use a proper ASN.1 library

        // PKCS#8 wrapper for RSA private key
        val pkcs8Header = byteArrayOf(
            0x30, 0x82.toByte(), 0x00, 0x00, // SEQUENCE
            0x02, 0x01, 0x00, // INTEGER version
            0x30, 0x0d, // SEQUENCE algorithmIdentifier
            0x06, 0x09, 0x2a.toByte(), 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01, // OID rsaEncryption
            0x05, 0x00, // NULL parameters
            0x04, 0x82.toByte(), 0x00, 0x00 // OCTET STRING (will be updated with length)
        )

        // Calculate total length
        val totalLength = pkcs8Header.size + pkcs1Bytes.size
        val pkcs8Bytes = ByteArray(totalLength)

        // Copy header
        System.arraycopy(pkcs8Header, 0, pkcs8Bytes, 0, pkcs8Header.size)

        // Update lengths in header
        val contentLength = pkcs1Bytes.size + 22
        pkcs8Bytes[2] = ((contentLength shr 8) and 0xFF).toByte()
        pkcs8Bytes[3] = (contentLength and 0xFF).toByte()
        pkcs8Bytes[pkcs8Header.size - 2] = ((pkcs1Bytes.size shr 8) and 0xFF).toByte()
        pkcs8Bytes[pkcs8Header.size - 1] = (pkcs1Bytes.size and 0xFF).toByte()

        // Copy PKCS#1 data
        System.arraycopy(pkcs1Bytes, 0, pkcs8Bytes, pkcs8Header.size, pkcs1Bytes.size)

        // Create private key
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(pkcs8Bytes)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec)
    }

    private fun handleAlarmMessage(topic: String, messageContent: String) {
        try {
            // Parse topic: sensor_hub/<device>/alarm/<action>
            val topicParts = topic.split("/")
            if (topicParts.size >= 4) {
                val device = topicParts[1]       // pico_w_1
                val sensorType = topicParts[2]   // alarm
                val action = topicParts[3]       // triggered, armed, disarmed

                Log.d(TAG, "Parsed topic - Device: $device, Type: $sensorType, Action: $action")

                // Try to parse the JSON message
                val alarmMessage = try {
                    val parsedMessage = gson.fromJson(messageContent, AlarmMessage::class.java)
                    // Set the state from the topic action and keep triggeredBy from JSON
                    parsedMessage.copy(state = action)
                } catch (e: Exception) {
                    Log.w(TAG, "JSON parsing failed: ${e.message}, creating simple alarm message")
                    // If JSON parsing fails, create a simple alarm message
                    AlarmMessage(
                        timestamp = System.currentTimeMillis(),
                        state = action,
                        message = messageContent
                    )
                }

                Log.d(TAG, "Created alarm message: state=${alarmMessage.state}, triggeredBy=${alarmMessage.triggeredBy}")

                // Show notification for alarm events
                if (sensorType == "alarm") {
                    Log.d(TAG, "Showing notification for alarm event: $action")
                    notificationHelper.showAlarmNotification(alarmMessage)
                } else {
                    Log.w(TAG, "Not showing notification - sensorType is $sensorType, not 'alarm'")
                }
            } else {
                Log.w(TAG, "Topic has insufficient parts: ${topicParts.size}, expected at least 4. Topic: $topic")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling alarm message: ${e.message}")
            e.printStackTrace()
            // Show a generic notification if parsing fails
            val genericAlarm = AlarmMessage(
                message = messageContent,
                timestamp = System.currentTimeMillis()
            )
            Log.d(TAG, "Showing generic notification due to error")
            notificationHelper.showAlarmNotification(genericAlarm)
        }
    }

    private fun disconnectMqtt() {
        try {
            mqttClient?.disconnect()
            mqttClient?.close()
            mqttClient = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting MQTT: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        if (!isServiceRunning) return

        if (reconnectExecutor?.isShutdown != false) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor()
        }

        reconnectExecutor?.schedule({
            if (isServiceRunning) {
                Log.d(TAG, "Attempting to reconnect to MQTT...")
                disconnectMqtt()
                connectMqtt()
            }
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS)
    }

    fun isConnected(): Boolean {
        return mqttClient?.isConnected == true
    }

    fun publishMessage(topic: String, message: String) {
        try {
            if (isConnected()) {
                val mqttMessage = MqttMessage(message.toByteArray())
                mqttMessage.qos = QOS
                mqttClient?.publish(topic, mqttMessage)
                Log.d(TAG, "Published message to topic: $topic")
            } else {
                Log.w(TAG, "Cannot publish message - not connected")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing message: ${e.message}")
        }
    }
}
