package com.helioserver.campo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
//
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class MiServicioMqtt : Service() {


    private var serverUri = ""
    private val clientId = "android-service"

    private lateinit var mqttClient: MqttAndroidClient

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Iniciar como Foreground Service inmediatamente
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            val initialNotification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ligadejabegas)
                .setContentTitle("Servicio MQTT")
                .setContentText("Conectando...")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(notificationId, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
            } else {
                startForeground(notificationId, initialNotification)
            }
        }

        serverUri = BuildConfig.MQTT_HOST
        var serverUser = BuildConfig.MQTT_USER
        var serverPass = BuildConfig.MQTT_PASSWORD

        Log.d("ServicioMQTT", "Intentando conectar a: $serverUri con usuario $serverUser $serverPass")
        mqttClient = MqttAndroidClient(applicationContext, serverUri, clientId)
        val mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions.isAutomaticReconnect = true
        mqttConnectOptions.userName = BuildConfig.MQTT_USER
        mqttConnectOptions.password = BuildConfig.MQTT_PASSWORD.toCharArray()
        
        // Soporte para SSL/TLS (Puerto 8883)
        if (serverUri.startsWith("ssl://")) {
            try {
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2")
                sslContext.init(null, null, null)
                mqttConnectOptions.socketFactory = sslContext.socketFactory
                // Desactivar verificación de hostname si el reset persiste (opcional)
                // mqttConnectOptions.isHttpsHostnameVerificationEnabled = false
            } catch (e: Exception) {
                Log.e("ServicioMQTT", "Error al configurar SSL", e)
            }
        }
        
        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                if (reconnect) {
                    // Reconexión exitosa
                    Log.d("ServicioMQTT","Reconectado con éxito")
                    subscribir()
                } else {
                    Log.d("ServicioMQTT","Conectado con éxito")
                    // Conexión inicial exitosa
                }
            }

            override fun connectionLost(cause: Throwable?) {
                Log.d("ServicioMQTT","Conexión perdida")
                // Conexión perdida, puedes intentar reconectar aquí
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                // Mensaje MQTT recibido, puedes procesarlo aquí
                Log.d("ServicioMQTT","Mensaje llegó")
                //val payload = message?.payload?.toString(Charsets.UTF_8)
                // Hacer algo con el mensaje recibido
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Mensaje entregado correctamente al servidor MQTT
            }
        })

        mqttClient.connect(mqttConnectOptions, null, object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.d("ServicioMQTT","Conexión ok")
                subscribir()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                Log.d("ServicioMQTT","Conexión failure")
                // Error en la conexión, puedes intentar reconectar aquí
            }
        })

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }


    val channelId = "mi_canal_id"
    val notificationId = 1
    lateinit var notification: Notification
    lateinit var channel: NotificationChannel
    lateinit var contexto : Context
    lateinit var pendingIntent: PendingIntent
    override fun onCreate()
    {
        val channelName = "Mi Canal de Notificaciones"
        val channelDescription = "Descripción del canal de notificaciones"
        contexto=this
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            channel.description = channelDescription
        }

        val activityIntent = Intent(applicationContext, MapsActivity::class.java)
        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        Log.d("ServicioMQTT","OnCreate completado")

    }

    // Dos topics, la misma función
    private val subscriptionTopic1 = "android/regatas"
    private val subscriptionTopic2 = "art/android/boyas"

    // Define the listener outside the function
    private val messageListener = IMqttMessageListener { topic, message ->
        val payload = message?.payload?.toString(Charsets.UTF_8)
        Log.d("ServicioMQTT", "Mensaje llegó (topic: $topic): $payload")

        // Handle message based on the topic:
        if (topic == subscriptionTopic1 || topic == subscriptionTopic2) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                val notificationTitle = "Mensaje de Ipe"
                val notificationText = message?.payload?.toString(Charsets.UTF_8)

                val notificationBuilder = NotificationCompat.Builder(contexto, channelId)
                    .setSmallIcon(R.drawable.ligadejabegas)
                    .setContentTitle(notificationTitle)
                    .setContentText(notificationText)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)


                notification = notificationBuilder.build()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
                } else {
                    startForeground(notificationId, notification)
                }
            }
        } else if (topic == "otro") {
            // Process messages from topic2 (e.g., perform a different action)
            // ... (your logic for handling messages from topic2)
        } else {
            // Handle unexpected topics (optional)
            Log.w("ServicioMQTT", "Received message from unknown topic: $topic")
        }
    }


    private fun subscribir() {
        val qos = 0 // Adjust QoS (Quality of Service) if needed

        // Subscribe to both topics using the defined listener
        mqttClient.subscribe(subscriptionTopic1, qos, messageListener)
        mqttClient.subscribe(subscriptionTopic2, qos, messageListener) // Subscribe to topic2 using same listener
    }


}