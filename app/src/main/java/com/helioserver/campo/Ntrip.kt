package com.helioserver.campo

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Clase encargada de gestionar la conexión USB con la placa RTK (Ardusimple RTK2B / u-blox ZED-F9P)
 * y de diferenciar la calidad del posicionamiento (GPS simple vs corrección RTK) a partir de
 * las tramas NMEA (GGA) que envía la placa por el puerto serie USB.
 */
class Ntrip(
    private val context: Context,
    private val repository: LocationRepository? = null
) : SerialInputOutputManager.Listener {

    companion object {
        private const val TAG = "NtripManager"
        private const val ACTION_USB_PERMISSION = "com.helioserver.campo.USB_PERMISSION"
        private const val BAUD_RATE = 115200
        private const val TIMEOUT_DATOS_MS = 5000L

        // Calidad de fix reportada en el campo 6 de la trama $GxGGA (NMEA 0183)
        const val FIX_INVALIDO = 0
        const val FIX_GPS = 1
        const val FIX_DGPS = 2
        const val FIX_RTK_FIJO = 4      // RTK Fixed: precisión centimétrica
        const val FIX_RTK_FLOTANTE = 5  // RTK Float: precisión decimétrica
    }

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private var usbSerialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var driverPendiente: UsbSerialDriver? = null
    private val nmeaBuffer = StringBuilder()
    private var receptorRegistrado = false
    private val estaReconectando = AtomicBoolean(false)

    // Cliente NTRIP: recibe correcciones RTCM del caster y las reenvía a la placa por USB
    private val ntripActivo = AtomicBoolean(false)
    @Volatile private var ntripSocket: Socket? = null
    @Volatile private var ultimaTramaGGA: String? = null
    private var ntripThread: Thread? = null
    @Volatile private var enviandoGga = false
    private var ggaThread: Thread? = null
    @Volatile private var ntripConectado: Boolean = false

    // Último punto válido recibido de la placa y su calidad de fix
    @Volatile private var ultimaLat: Double? = null
    @Volatile private var ultimaLon: Double? = null
    @Volatile private var ultimoFix: Int = FIX_INVALIDO
    @Volatile private var ultimaLecturaMs: Long = 0L

    // Desviación estándar horizontal real reportada por la placa en la trama $GxGST (metros)
    @Volatile private var ultimaPrecisionGst: Double? = null
    @Volatile private var ultimaLecturaGstMs: Long = 0L

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION != intent.action) return
            synchronized(this) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                val concedido = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                val driver = driverPendiente
                if (concedido && device != null && driver != null) {
                    Log.d(TAG, "Permiso USB concedido para ${device.deviceName}")
                    abrirPuerto(driver)
                } else {
                    Log.w(TAG, "Permiso USB denegado para la placa RTK")
                }
            }
        }
    }

    /** Indica si en este momento se dispone de una posición RTK válida y reciente. */
    private val rtkDisponible: Boolean
        get() = ultimaLat != null && ultimaLon != null &&
                (System.currentTimeMillis() - ultimaLecturaMs) < TIMEOUT_DATOS_MS

    /** Calidad del último fix recibido (ver constantes FIX_*), que permite diferenciar RTK de un GPS simple. */
    fun calidadFix(): Int = if (rtkDisponible) ultimoFix else FIX_INVALIDO

    /** Descripción legible de la calidad del fix actual, útil para mostrar en la UI. */
    fun descripcionFix(): String = when (calidadFix()) {
        FIX_RTK_FIJO -> "RTK Fijo (cm)"
        FIX_RTK_FLOTANTE -> "RTK Flotante (dm)"
        FIX_DGPS -> "DGPS"
        FIX_GPS -> "GPS simple"
        else -> "Sin datos RTK"
    }

    /**
     * Intenta obtener la posición actual desde el receptor RTK.
     * Si no hay datos reales de la placa, utiliza la localización del sistema como fallback.
     */
    fun obtenerPosicion(fallbackLocation: Location?): LatLng {
        val lat = ultimaLat
        val lon = ultimaLon
        if (rtkDisponible && lat != null && lon != null) {
            Log.d(TAG, "Posición desde placa RTK (${descripcionFix()}): $lat, $lon")
            return LatLng(lat, lon)
        }

        // Fallback: Si no hay hardware RTK o datos válidos, usamos el GPS del móvil
        return if (fallbackLocation != null) {
            Log.d(TAG, "RTK no disponible. Usando fallback del sistema.")
            LatLng(fallbackLocation.latitude, fallbackLocation.longitude)
        } else {
            Log.e(TAG, "Error crítico: No hay ninguna fuente de posición disponible.")
            LatLng(36.7345, -4.3555) // Ubicación por defecto (Málaga)
        }
    }

    /**
     * Inicia la comunicación con la placa Ardusimple: busca el dispositivo USB serie conectado,
     * solicita permiso si hace falta y abre el puerto para empezar a leer tramas NMEA.
     */
    fun conectar() {
        if (usbSerialPort != null || estaReconectando.get()) {
            Log.d(TAG, "Conexión ya activa o en proceso de reconexión.")
            return
        }
        Log.d(TAG, "Iniciando servicios de alta precisión (RTK/NTRIP)...")
        registrarReceiverSiHaceFalta()

        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull()
        if (driver == null) {
            Log.w(TAG, "No se ha encontrado ninguna placa RTK conectada por USB.")
            return
        }
        driverPendiente = driver

        val device = driver.device
        if (usbManager.hasPermission(device)) {
            abrirPuerto(driver)
        } else {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(
                context, 0, Intent(ACTION_USB_PERMISSION), flags
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registrarReceiverSiHaceFalta() {
        if (receptorRegistrado) return
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        receptorRegistrado = true
    }

    private fun abrirPuerto(driver: UsbSerialDriver) {
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            Log.e(TAG, "No se pudo abrir la conexión con la placa RTK (¿falta permiso?).")
            return
        }
        try {
            val port = driver.ports[0]
            port.open(connection)
            port.setParameters(
                BAUD_RATE, UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE
            )
            try {
                port.dtr = true
                port.rts = true
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo establecer DTR/RTS", e)
            }
            usbSerialPort = port
            ioManager = SerialInputOutputManager(port, this).also { it.start() }
            Log.d(TAG, "Puerto serie RTK abierto correctamente a $BAUD_RATE baudios.")
            
            // Intentamos activar la trama NMEA GST para obtener la precisión real en metros.
            // Añadimos un pequeño retardo para asegurar que la placa está lista para recibir comandos.
            Handler(Looper.getMainLooper()).postDelayed({
                activarMensajeGst(port)
            }, 500)
            
            iniciarClienteNtrip()
        } catch (e: IOException) {
            Log.e(TAG, "Error abriendo el puerto serie de la placa RTK", e)
        }
    }

    /**
     * Envía un comando binario UBX a la placa para activar el mensaje NMEA GST por el puerto USB.
     * El comando es: UBX-CFG-MSG (0x06 0x01) para el mensaje 0xF0 0x07 (GST).
     * El checksum (Fletcher-8) se calcula dinámicamente sobre Class+ID+Length+Payload para
     * evitar errores manuales que hagan que el receptor descarte el comando silenciosamente.
     */
    private fun activarMensajeGst(port: UsbSerialPort) {
        try {
            val claseId = byteArrayOf(0x06, 0x01) // CFG-MSG
            // Payload: [Class del msg a activar (0xF0 = NMEA estándar), ID del msg (0x07 = GST), Rate (1)]
            val payload = byteArrayOf(0xF0.toByte(), 0x07, 0x01)
            val longitud = byteArrayOf(payload.size.toByte(), 0x00)

            var ckA = 0
            var ckB = 0
            for (b in claseId + longitud + payload) {
                ckA = (ckA + (b.toInt() and 0xFF)) and 0xFF
                ckB = (ckB + ckA) and 0xFF
            }

            val comandoGst = byteArrayOf(0xB5.toByte(), 0x62.toByte()) + claseId + longitud + payload +
                byteArrayOf(ckA.toByte(), ckB.toByte())

            port.write(comandoGst, 1000)
            Log.d(TAG, "Comando UBX enviado para activar trama GST (precisión real).")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo enviar el comando de activación GST", e)
        }
    }

    override fun onNewData(data: ByteArray) {
        synchronized(nmeaBuffer) {
            nmeaBuffer.append(String(data, Charsets.US_ASCII))
            while (true) {
                // Protección contra desbordamiento/corrupción
                if (nmeaBuffer.length > 4096) {
                    Log.w(TAG, "Buffer NMEA saturado, limpiando datos corruptos.")
                    nmeaBuffer.clear()
                    break
                }
                val idx = nmeaBuffer.indexOf("\n")
                if (idx < 0) break
                val linea = nmeaBuffer.substring(0, idx).trim()
                nmeaBuffer.delete(0, idx + 1)
                if (linea.isNotEmpty()) procesarLineaNmea(linea)
            }
            // Evita que el buffer crezca sin control si nunca llega un salto de línea
            if (nmeaBuffer.length > 4096) nmeaBuffer.clear()
        }
    }

    override fun onRunError(e: Exception) {
        val msg = e.message ?: ""
        if (msg.contains("get_status") || msg.contains("ENODEV") || msg.contains("Connection closed")) {
            Log.w(TAG, "Pérdida de conexión con el puerto serie RTK: $msg")
        } else {
            Log.e(TAG, "Error de lectura del puerto serie RTK", e)
        }

        // Si el error es una IOException y no estamos ya intentando reconectar, disparamos el proceso
        if (e is IOException && estaReconectando.compareAndSet(false, true)) {
            Log.d(TAG, "Iniciando reconexión automática tras error en el puerto serie...")
            
            // Ejecutamos detener en el hilo actual (background) y programamos conectar en el main
            detener()
            
            Handler(Looper.getMainLooper()).postDelayed({
                estaReconectando.set(false)
                conectar()
            }, 3000)
        }
    }

    /** Distribuye cada línea NMEA recibida al parser correspondiente según su tipo de trama. */
    private fun procesarLineaNmea(trama: String) {
        if (!trama.startsWith("$")) return
        when {
            trama.contains("GGA") -> procesarTramaGga(trama)
            trama.contains("GST") -> procesarTramaGst(trama)
        }
    }

    /**
     * Parsea una trama NMEA $GxGGA para extraer latitud, longitud y calidad de fix, que es lo
     * que permite diferenciar una posición corregida por RTK (fijo/flotante) de un GPS normal.
     */
    private fun procesarTramaGga(trama: String) {
        if (!trama.contains("GGA")) return
        try {
            val cuerpo = trama.substringBefore("*")
            val campos = cuerpo.split(",")
            if (campos.size < 7) return

            val latCruda = campos[2]
            val latHem = campos[3]
            val lonCruda = campos[4]
            val lonHem = campos[5]
            val calidad = campos[6].toIntOrNull() ?: FIX_INVALIDO
            val altitud = campos[9].toDoubleOrNull() ?: 0.0

            if (latCruda.isEmpty() || lonCruda.isEmpty() || calidad == FIX_INVALIDO) {
                ultimoFix = FIX_INVALIDO
                return
            }

            // Precisión horizontal: preferimos la desviación estándar real (stdLat/stdLon en
            // metros) reportada por el receptor en la trama $GxGST, si ha llegado recientemente.
            // El HDOP no es una medida de precisión válida en RTK (depende de la geometría de
            // satélites, no del estado de la corrección), así que solo se usa como último recurso
            // con valores fijos típicos por tipo de fix.
            val precisionGst = ultimaPrecisionGst
            val precisionEstimada = if (precisionGst != null &&
                (System.currentTimeMillis() - ultimaLecturaGstMs) < TIMEOUT_DATOS_MS
            ) {
                precisionGst
            } else {
                when (calidad) {
                    FIX_RTK_FIJO -> 0.02
                    FIX_RTK_FLOTANTE -> 0.25
                    FIX_DGPS -> 1.0
                    FIX_GPS -> 2.5
                    else -> 5.0
                }
            }

            ultimaLat = nmeaACoordenada(latCruda, latHem)
            ultimaLon = nmeaACoordenada(lonCruda, lonHem)
            ultimoFix = calidad
            ultimaLecturaMs = System.currentTimeMillis()
            ultimaTramaGGA = trama // se reenvía al caster NTRIP (necesaria en redes VRS como ERGNSS)

            // Notificamos al repositorio central si está configurado
            repository?.actualizarDesdeRTK(
                lat = ultimaLat!!,
                lon = ultimaLon!!,
                descripcion = descripcionFix(),
                esRTK = calidad == FIX_RTK_FIJO || calidad == FIX_RTK_FLOTANTE,
                precision = precisionEstimada,
                usb = usbSerialPort != null,
                ntrip = ntripConectado
            )

            Log.d(TAG, "GGA recibido: lat=$ultimaLat lon=$ultimaLon alt=${altitud}m prec=${String.format("%.3f", precisionEstimada)}m calidad=$calidad (${descripcionFix()})")
        } catch (e: Exception) {
            Log.w(TAG, "Trama NMEA no válida: $trama", e)
        }
    }

    /** Convierte el formato NMEA ddmm.mmmm / dddmm.mmmm a grados decimales. */
    private fun nmeaACoordenada(valor: String, hemisferio: String): Double {
        val punto = valor.indexOf('.')
        val digitosGrados = if (punto > 2) punto - 2 else 2
        val grados = valor.substring(0, digitosGrados).toDouble()
        val minutos = valor.substring(digitosGrados).toDouble()
        var decimal = grados + minutos / 60.0
        if (hemisferio == "S" || hemisferio == "W") decimal = -decimal
        return decimal
    }

    /**
     * Parsea una trama NMEA $GxGST (estadísticas de error del receptor) para obtener la
     * desviación estándar horizontal real (stdLat/stdLon, en metros). Es una medida de precisión
     * mucho más fiable que aproximar a partir del HDOP, ya que la calcula el propio receptor a
     * partir de su solución de posicionamiento (incluye el estado real de la corrección RTK).
     * Formato: $GxGST,hhmmss.ss,rms,stdMajor,stdMinor,orient,stdLat,stdLon,stdAlt*hh
     */
    private fun procesarTramaGst(trama: String) {
        try {
            val cuerpo = trama.substringBefore("*")
            val campos = cuerpo.split(",")
            if (campos.size < 8) return
            val stdLat = campos[6].toDoubleOrNull()
            val stdLon = campos[7].toDoubleOrNull()
            if (stdLat == null || stdLon == null) return

            // Usamos el mayor de los dos ejes como precisión horizontal conservadora
            ultimaPrecisionGst = maxOf(stdLat, stdLon)
            ultimaLecturaGstMs = System.currentTimeMillis()
        } catch (e: Exception) {
            Log.w(TAG, "Trama GST no válida: $trama", e)
        }
    }

    /**
     * Arranca el cliente NTRIP: se conecta al caster (IGN ERGNSS u otro configurado en
     * local.properties), envía la posición GGA y reenvía las correcciones RTCM recibidas
     * a la placa Ardusimple a través del puerto USB para lograr el fijo RTK.
     */
    private fun iniciarClienteNtrip() {
        if (ntripActivo.get()) return
        val host = BuildConfig.NTRIP_HOST
        val mountpoint = BuildConfig.NTRIP_MOUNTPOINT
        if (host.isBlank() || mountpoint.isBlank()) {
            Log.w(TAG, "Cliente NTRIP no configurado (host/mountpoint vacíos en local.properties).")
            return
        }
        ntripActivo.set(true)
        ntripThread = Thread({ bucleClienteNtrip(host, BuildConfig.NTRIP_PORT, mountpoint) }, "NtripClientThread")
            .also { it.isDaemon = true; it.start() }
    }

    private fun bucleClienteNtrip(host: String, port: Int, mountpoint: String) {
        // Identidad del hilo que ejecuta esta conexión: permite detectar si, por una
        // reconexión concurrente, este hilo ha quedado obsoleto (superado por otro más
        // reciente) y así evitar que pise el estado compartido (ntripSocket, ntripConectado,
        // ggaThread) que ya pertenece a la nueva conexión activa.
        val hiloActual = Thread.currentThread()
        fun esHiloVigente() = ntripThread === hiloActual

        while (ntripActivo.get() && esHiloVigente()) {
            var socket: Socket? = null
            try {
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), 10000)
                socket.soTimeout = 15000

                if (!ntripActivo.get() || !esHiloVigente()) {
                    socket.close()
                    return
                }
                ntripSocket = socket
                ntripConectado = true

                val credenciales = Base64.encodeToString(
                    "${BuildConfig.NTRIP_USER}:${BuildConfig.NTRIP_PASSWORD}".toByteArray(Charsets.US_ASCII),
                    Base64.NO_WRAP
                )
                val peticion = buildString {
                    append("GET /$mountpoint HTTP/1.1\r\n")
                    append("Host: $host\r\n")
                    append("Ntrip-Version: Ntrip/2.0\r\n")
                    append("User-Agent: NTRIP CampoRTK/1.0\r\n")
                    append("Authorization: Basic $credenciales\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                val salida = socket.getOutputStream()
                salida.write(peticion.toByteArray(Charsets.US_ASCII))
                salida.flush()

                if (!ntripActivo.get() || !esHiloVigente()) return // Verificar antes de seguir

                val entrada = socket.getInputStream()
                val cabecera = leerCabeceraHttp(entrada)
                Log.d(TAG, "Respuesta caster NTRIP ($mountpoint): $cabecera")
                if (!cabecera.contains("200")) {
                    Log.e(TAG, "El caster NTRIP rechazó la conexión: $cabecera")
                    socket.close()
                    dormirReintento()
                    continue
                }

                Log.d(TAG, "Conectado al caster NTRIP $host:$port/$mountpoint. Recibiendo correcciones RTCM...")
                iniciarEnvioPeriodicoGga(socket)

                val buffer = ByteArray(1024)
                while (ntripActivo.get() && esHiloVigente()) {
                    val port = usbSerialPort
                    if (port == null || !port.isOpen) {
                        Log.w(TAG, "USB no disponible, deteniendo cliente NTRIP por seguridad.")
                        break
                    }

                    val leidos = entrada.read(buffer)
                    if (leidos < 0) break
                    try {
                        port.write(buffer.copyOf(leidos), 2000)
                    } catch (e: IOException) {
                        Log.w(TAG, "Error escribiendo en USB, deteniendo NTRIP.", e)
                        break
                    }
                }
            } catch (e: Exception) {
                if (esHiloVigente()) ntripConectado = false
                if (ntripActivo.get()) Log.w(TAG, "Conexión NTRIP interrumpida, se reintentará", e)
            } finally {
                // Solo el hilo vigente puede tocar el estado compartido: si este hilo ya fue
                // reemplazado por una reconexión más reciente, limitarse a cerrar su propio socket.
                if (esHiloVigente()) {
                    ntripConectado = false
                    detenerEnvioPeriodicoGga()
                    ntripSocket = null
                }
                try {
                    socket?.close()
                } catch (e: IOException) {
                    // Ignorado: el socket ya podía estar cerrado
                }
            }
            if (ntripActivo.get() && esHiloVigente()) dormirReintento()
        }
    }

    private fun dormirReintento() {
        try {
            Thread.sleep(10000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** Lee la cabecera de la respuesta HTTP/NTRIP hasta la línea en blanco (\r\n\r\n). */
    private fun leerCabeceraHttp(input: InputStream): String {
        val sb = StringBuilder()
        val ultimos = intArrayOf(-1, -1, -1, -1)
        while (sb.length < 8192) {
            val b = input.read()
            if (b < 0) break
            sb.append(b.toChar())
            ultimos[0] = ultimos[1]; ultimos[1] = ultimos[2]; ultimos[2] = ultimos[3]; ultimos[3] = b
            if (ultimos[0] == '\r'.code && ultimos[1] == '\n'.code &&
                ultimos[2] == '\r'.code && ultimos[3] == '\n'.code
            ) break
        }
        return sb.toString().trim()
    }

    /** Envía periódicamente (cada 10s) la última trama GGA recibida, requisito de redes VRS como ERGNSS. */
    private fun iniciarEnvioPeriodicoGga(socket: Socket) {
        enviandoGga = true
        ggaThread = Thread({
            while (enviandoGga && ntripActivo.get()) {
                try {
                    val gga = ultimaTramaGGA
                    if (gga != null) {
                        val salida = socket.getOutputStream()
                        salida.write("$gga\r\n".toByteArray(Charsets.US_ASCII))
                        salida.flush()
                    }
                } catch (e: IOException) {
                    break
                }
                try {
                    Thread.sleep(10000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }, "NtripGgaThread").also { it.isDaemon = true; it.start() }
    }

    private fun detenerEnvioPeriodicoGga() {
        enviandoGga = false
        val hiloGga = ggaThread
        ggaThread = null
        hiloGga?.interrupt()
        if (hiloGga != null && hiloGga !== Thread.currentThread()) {
            try {
                hiloGga.join(2000)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Finaliza las conexiones y libera recursos (puerto serie, cliente NTRIP y receptor de permisos USB).
     */
    fun detener() {
        Log.d(TAG, "Liberando hardware y conexiones NTRIP.")
        try {
            ntripActivo.set(false)
            detenerEnvioPeriodicoGga()
            try {
                ntripSocket?.close()
            } catch (e: IOException) {
                // Ignorado
            }
            ntripThread?.interrupt()
            
            // No bloqueamos excesivamente el hilo principal si se llama desde él
            val maxWait = if (Looper.myLooper() == Looper.getMainLooper()) 200L else 3000L
            try {
                ntripThread?.join(maxWait)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            ntripThread = null

            try {
                ioManager?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error deteniendo el IoManager", e)
            }
            ioManager = null
            
            try {
                usbSerialPort?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error cerrando el puerto serie RTK", e)
            }
            usbSerialPort = null
        } finally {
            if (receptorRegistrado) {
                try {
                    context.unregisterReceiver(usbReceiver)
                } catch (e: Exception) {
                    // Ya desregistrado o error silencioso
                }
                receptorRegistrado = false
            }
        }
    }
}
