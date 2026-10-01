package com.helioserver.campo

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Esta clase es el "corazón" de la ubicación en tu app.
 * Su trabajo es recibir datos de dos fuentes:
 * 1. La placa RTK (vía USB)
 * 2. El GPS del móvil (vía sistema Android)
 *
 * Y decidir cuál es la mejor posición para mostrar en el mapa.
 */
class LocationRepository {

    /**
     * Datos de una posición "unificada".
     * Incluye la ubicación, la descripción del fix (ej: "RTK Fijo") y si es de alta precisión.
     */
    data class UbicacionUnificada(
        val coordenadas: LatLng,
        val descripcionFix: String,
        val esAltaPrecision: Boolean,
        val precisionMetros: Double,
        val tiempo: Long = System.currentTimeMillis(),
        val usbConectado: Boolean = false,
        val ntripConectado: Boolean = false
    )

    // El "grifo" interno donde metemos los datos (MutableStateFlow)
    private val _posicionActual = MutableStateFlow<UbicacionUnificada?>(null)

    // El "chorro" público que el mapa y el resto de la app escuchan (StateFlow)
    val posicionActual: StateFlow<UbicacionUnificada?> = _posicionActual.asStateFlow()

    // Guardamos la última del sistema por si el RTK falla
    private var ultimaDelSistema: UbicacionUnificada? = null

    /**
     * Cuando la placa RTK nos da una posición, la mandamos aquí.
     * Al ser RTK, siempre tendrá prioridad sobre la del móvil.
     */
    fun actualizarDesdeRTK(lat: Double, lon: Double, descripcion: String, esRTK: Boolean, precision: Double, usb: Boolean, ntrip: Boolean) {
        val nueva = UbicacionUnificada(
            coordenadas = LatLng(lat, lon),
            descripcionFix = descripcion,
            esAltaPrecision = esRTK,
            precisionMetros = precision,
            usbConectado = usb,
            ntripConectado = ntrip
        )
        _posicionActual.value = nueva
    }

    /**
     * Cuando el GPS del móvil (Android) nos da una posición, la mandamos aquí.
     */
    fun actualizarDesdeSistema(lat: Double, lon: Double, precision: Double, usb: Boolean, ntrip: Boolean) {
        val nueva = UbicacionUnificada(
            coordenadas = LatLng(lat, lon),
            descripcionFix = "GPS Móvil",
            esAltaPrecision = false,
            precisionMetros = precision,
            usbConectado = usb,
            ntripConectado = ntrip
        )
        ultimaDelSistema = nueva

        // Solo actualizamos el flujo principal si no tenemos un dato RTK reciente
        val actual = _posicionActual.value
        val haceCuantoRTK = System.currentTimeMillis() - (actual?.tiempo ?: 0L)

        // HISTERESIS: Aumentamos el margen para abandonar el modo RTK.
        // Si teníamos RTK, esperamos hasta 30 segundos de silencio antes de saltar al GPS del móvil.
        val margenAbandono = if (actual?.esAltaPrecision == true) 30000 else 5000

        if (actual == null || haceCuantoRTK > margenAbandono) {
            _posicionActual.value = nueva
        }
    }
}
