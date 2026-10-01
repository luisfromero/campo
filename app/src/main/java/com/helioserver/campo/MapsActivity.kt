package com.helioserver.campo

//import android.R

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActionBar
import android.app.AlertDialog
import android.app.Dialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.usb.UsbManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CombinedVibration
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.InfoWindowAdapter
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.SphericalUtil
import com.helioserver.campo.databinding.ActivityMapsBinding
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Integer.max
import java.lang.Math.round
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.core.content.edit

const val DISTANCIA = 0
const val BOYA_SE = 1 //Salida estribor calle 5
const val BOYA_SB = 2 //Salida babor calle 1
const val BOYA_CE = 3 //Ciaboga estribor calle 5
const val BOYA_CB = 4 //Ciaboga babor call1 1
const val ANCLA_SE = 5 //Ancla de la ciaboga estribor
const val ANCLA_SB = 6 //Ancla de la ciaboga babor
const val ANCLA_CE = 7 //Ancla de la ciaboga estribor
const val ANCLA_CB = 8 //Ancla de la ciaboga babor

const val anchoAncla = 30.0


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {


    //*******************************************************************************************
    //                         Las variables                                                    *
    //*******************************************************************************************

    private var noGirarPantallaActivado: Boolean = false
    private val PREF_NO_GIRAR_PANTALLA = "pref_no_girar_pantalla"



    private var campoRTK = false
    private lateinit var ntripManager: Ntrip
    private val locationRepository = LocationRepository()

    private lateinit var map: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var lastLocation: Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    private lateinit var textViewSE: TextView
    private lateinit var textViewSB: TextView
    private lateinit var textViewCE: TextView
    private lateinit var textViewCB: TextView
    private lateinit var textViewD: TextView
    private lateinit var textViewP: TextView
    private lateinit var infor: TextView

    private lateinit var menuBackup: MenuItem
    private lateinit var menuRestore: MenuItem
    private lateinit var menuIntercambia1: MenuItem
    private lateinit var menuIntercambia2: MenuItem
    private lateinit var menuIntercambia3: MenuItem
    private lateinit var menuBrujula: MenuItem
    private lateinit var menuMQTT: MenuItem
    private var menuNoGirarItem: MenuItem? = null


    private var menuMQTTchecked=false;
    private lateinit var botonSE: Button
    private lateinit var botonSB: Button
    private lateinit var botonCE: Button
    private lateinit var botonCB: Button
    private lateinit var botonCentrar: Button
    private lateinit var botonValidar: Button
    private lateinit var btnBuscarAnclas: ImageButton

    lateinit var seekBarDistancia : SeekBar

    private lateinit var checkboxLockDistance: CheckBox
    private lateinit var checkboxLock1: CheckBox
    private lateinit var checkboxLock2: CheckBox
    private lateinit var checkboxLockA: CheckBox
    private lateinit var checkboxLockB: CheckBox

    var gps_enabled = false

    var distancia =350.0
    var sesgo = 0.0
    var precision=0.0

    /** distancia del movil al marcador 1*/ 
    var distanciaSE=0.0
    var distanciaSB=0.0
    var distanciaCE=0.0
    var distanciaCB=0.0

    var distanciaAnclaSE=0.0
    var distanciaAnclaSB=0.0
    var distanciaAnclaCE=0.0
    var distanciaAnclaCB=0.0


    var rumboBarco=0.0

    var rumboSE=0.0
    var rumboSB=0.0
    var rumboCE=0.0
    var rumboCB=0.0

    var rumboAnclaSE=0.0
    var rumboAnclaSB=0.0
    var rumboAnclaCE=0.0
    var rumboAnclaCB=0.0
    
    
    var rumbo_SE_SB=0.0
    var rumbo_SB_SE=0.0
    var rumbo_SB_CB=0.0
    var rumbo_SE_CE=0.0
    var rumbo_CB_CE=0.0
    var distanciaCalleBabor=0.0
    var distanciaCalleEstribor=0.0

    var setSE=false
    var setSB=false
    var setCE=false
    var setCB=false

    var primeraAnimacion=false

    var canal=false

    lateinit var BoyaSE : Location
    lateinit var BoyaSB : Location
    lateinit var BoyaCE : Location
    lateinit var BoyaCB : Location

    lateinit var LLBoyaSE : LatLng
    lateinit var LLBoyaSB : LatLng
    lateinit var LLBoyaCE : LatLng
    lateinit var LLBoyaCB : LatLng

    lateinit var LLAnclaBoyaSE : LatLng
    lateinit var LLAnclaBoyaSB : LatLng
    lateinit var LLAnclaBoyaCE : LatLng
    lateinit var LLAnclaBoyaCB : LatLng

    lateinit var LLValidaCE: LatLng
    lateinit var LLValidaCB: LatLng


    lateinit var anclaBoyaSE : Location
    lateinit var anclaBoyaSB : Location
    lateinit var anclaBoyaCE : Location
    lateinit var anclaBoyaCB : Location


    private var buscarAnclas=false
    
    /**  Posición ideal de A si se han fijado 1 y 2    */lateinit var LLidealCE : LatLng
    /**  Posición ideal de A si se han fijado 1 y 2    */lateinit var LLidealCB : LatLng


    /**  Posición ideal de A si se han fijado 1,2 y B */ lateinit var LLcorrectaCE : LatLng
    /**  Posición ideal de B si se han fijado 1,2 y A */ lateinit var LLcorrectaCB : LatLng

    lateinit var markerSE : Marker
    lateinit var markerSB : Marker
    lateinit var markerCE : Marker
    lateinit var markerCB : Marker

    lateinit var markerCE_ideal : Marker //Ideal sesgo 0
    lateinit var markerCB_ideal : Marker //Ideal sesgo 0
    lateinit var markerCBvalida : Marker //Ideal B para A
    lateinit var markerCEvalida : Marker //Ideal A para B

    lateinit var markerAnclaSE : Marker
    lateinit var markerAnclaSB : Marker
    lateinit var markerAnclaCE : Marker
    lateinit var markerAnclaCB : Marker
    private var markerCruzRTK: Marker? = null

    val circuloAD = cuadrado(20f , Color.RED)
    val circuloBD = cuadrado(20f , Color.GREEN)
    val circuloBA = circulo(20f , Color.rgb(160,220,160))
    val circuloAB = circulo(20f , Color.rgb(255,130,0))


    val malaga = LatLng(36.7345, -4.3555)
    var marcadores=false
    var boyas=false
    var sinboyas=true  //¿Redundante?

    var viewLatLng: LatLng=malaga
    var exactitud=20.0 // Ajuste al valor verdadero (accuracy)

    var lockDistance=false
    var lockSE=false
    var lockSB=false
    var lockCE=false
    var lockCB=false

    var horiAcc =10.0
    var longitudCampo=0.0
    var sesgoCampo=0.0
    private var gpsactivo=false

    var recording=false
    var boyaActiva = 1

    var listaPosiciones = ArrayList<LatLng>()
    var listaPrecisiones = ArrayList<Double>()
    var precisionVista = 10000.0
    var mejorPrecision = 10000.0
    var mejorLatitud = 10000.0
    var mejorLongitud = 10000.0


    var destruido=false
    var mqttClient: MqttAndroidClient? =null

    /**
     * Actualiza la ubicación de una boya.
     * @param boya
     * @param latlng
     */
    fun actualizarBoyaConLatLng(boya:Int, ll: LatLng?)
    {
        var latlng: LatLng
        if(ll==null)
            latlng=LatLng(lastLocation.latitude ,lastLocation.longitude)
        else
            latlng=ll

        if(boya==BOYA_SE){
            if (!::BoyaSE.isInitialized) BoyaSE = Location("manual")
            BoyaSE.latitude= latlng.latitude
            BoyaSE.longitude= latlng.longitude
            LLBoyaSE = latlng
        }
        if(boya==BOYA_SB){
            if (!::BoyaSB.isInitialized) BoyaSB = Location("manual")
            BoyaSB.latitude= latlng.latitude
            BoyaSB.longitude= latlng.longitude
            LLBoyaSB = latlng
        }
        if(boya==BOYA_CE) {
            if (!::BoyaCE.isInitialized) BoyaCE = Location("manual")
            BoyaCE.latitude = latlng.latitude
            BoyaCE.longitude = latlng.longitude
            LLBoyaCE = latlng
        }
        if(boya==BOYA_CB) {
            if (!::BoyaCB.isInitialized) BoyaCB = Location("manual")
            BoyaCB.latitude = latlng.latitude
            BoyaCB.longitude = latlng.longitude
            LLBoyaCB = latlng
        }
        if(boya==ANCLA_SE ) {
            if (!::anclaBoyaSE.isInitialized) anclaBoyaSE = Location("manual")
            anclaBoyaSE.latitude = latlng.latitude
            anclaBoyaSE.longitude = latlng.longitude
        }
        if(boya==ANCLA_SB) {
            if (!::anclaBoyaSB.isInitialized) anclaBoyaSB = Location("manual")
            anclaBoyaSB.latitude = latlng.latitude
            anclaBoyaSB.longitude = latlng.longitude
        }
        if(boya==ANCLA_CE ) {
            if (!::anclaBoyaCE.isInitialized) anclaBoyaCE = Location("manual")
            anclaBoyaCE.latitude = latlng.latitude
            anclaBoyaCE.longitude = latlng.longitude
        }
        if(boya==ANCLA_CB) {
            if (!::anclaBoyaCB.isInitialized) anclaBoyaCB = Location("manual")
            anclaBoyaCB.latitude = latlng.latitude
            anclaBoyaCB.longitude = latlng.longitude
        }
        
    }

    //*******************************************************************************************
    //                         Servicio                                                     *
    //*******************************************************************************************

/*
- Se llama desde onCreate
- Está en un archivo kt adjunto
- Se registra en el manifiesto
 */
    fun servicio()
    {
        val serviceIntent = Intent(this, MiServicioMqtt::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

    }



    //*******************************************************************************************
    //                         Mis funciones                                                    *
    //*******************************************************************************************


    fun actualizarIconoAncla() {
        if (buscarAnclas) {
            btnBuscarAnclas.setColorFilter(Color.YELLOW)
        } else {
            btnBuscarAnclas.setColorFilter(Color.WHITE)
        }
    }

    fun Double.bonito(): Double {
        if (this.isNaN() || this.isInfinite()) return 0.0
        return (this * 10.0).roundToInt() / 10.0
    }

    fun ayuda(){
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://regatas.jabegas.com/app"))
        startActivity(browserIntent)
    }

    //Convierte un rumbo en grados para centrarlo en cero
    fun rango(angulo: Double): Double {
        return (angulo+540)%360-180
    }

    fun getTextoDistanciaRumbo(distancia: Double, rumbo: Double): String {
        val rumboT = if (rumbo > 0) "+${rumbo.toInt()}º" else "${rumbo.toInt()}º"
        return "${distancia}m, $rumboT"
    }


    fun reiniciar(inicial: Boolean=false)
    {
        recording=false
        inicial()
        setSE=inicial
        setSB=inicial
        setCE=inicial
        setCB=inicial
        lockDistance=false
        lockSE=false
        lockSB=false
        lockCE=false
        lockCB=false
        distancia=350.0
        sesgo=0.0
        actualizarBoyaConLatLng(BOYA_SE,null)
        actualizarBoyaConLatLng(BOYA_SB, SphericalUtil.computeOffset(LLBoyaSE,155.0,90.0))
        actualizarBoyaConLatLng(BOYA_CE, SphericalUtil.computeOffset(LLBoyaSE,distancia,180.0))
        actualizarBoyaConLatLng(BOYA_CB, SphericalUtil.computeOffset(LLBoyaSB,distancia,180.0))

        boyas2marcadores()
        visibilidadMarcadores()

        textViewSE.setText("")
        textViewSB.setText("")
        textViewCE.setText("")
        textViewCB.setText("")
        textViewP.setText("")
        infor.setText("")

        seekBarDistancia.progress=350
        seekBarDistancia.isEnabled=true

        habilitaBotones()
        seekBarDistancia.setEnabled(true);
        botonSE.setText(getString(R.string.boton1))
        botonSB.setText(getString(R.string.boton2))
        botonCE.setText(getString(R.string.boton3))
        botonCB.setText(getString(R.string.boton4))
        menuBackup.setEnabled(true)
        menuIntercambia1.setEnabled(true)
        menuIntercambia2.setEnabled(true)
        menuIntercambia3.setEnabled(true)
        menuBrujula.setEnabled(true)
        botonValidar.setVisibility(View.INVISIBLE)
        menuMQTTchecked=false;


    }

    /**
     * Establece la posición de los marcadores a partir de las localizaciones del GPS (variables Boya)
     */
    fun boyas2marcadores()
    {
        if(boyas&&marcadores)
        {
            markerSE.position=LatLng(BoyaSE.latitude,BoyaSE.longitude)
            markerSB.position=LatLng(BoyaSB.latitude,BoyaSB.longitude)
            markerCE.position=LatLng(BoyaCE.latitude,BoyaCE.longitude)
            markerCB.position=LatLng(BoyaCB.latitude,BoyaCB.longitude)
        }

    }



    /**
     * Función que se llama al marcar alguna boya
     */
    fun marcandoBoya(num: Int, lugar : Location =lastLocation, remota: Boolean=false) {
        sinboyas=false

        var currentLatLng = LatLng(lugar.latitude, lugar.longitude)
        if(num==1){setSE=true;BoyaSE=lugar; markerSE.position=currentLatLng;markerSE.setVisible(true);LLBoyaSE=LatLng(BoyaSE.latitude,BoyaSE.longitude)}
        if(num==2){setSB=true;BoyaSB=lugar; markerSB.position=currentLatLng;markerSB.setVisible(true);LLBoyaSB=LatLng(BoyaSB.latitude,BoyaSB.longitude)}
        if(num==3){setCE=true;BoyaCE=lugar; markerCE.position=currentLatLng;markerCE.setVisible(true);LLBoyaCE=LatLng(BoyaCE.latitude,BoyaCE.longitude)}
        if(num==4){setCB=true;BoyaCB=lugar; markerCB.position=currentLatLng;markerCB.setVisible(true);LLBoyaCB=LatLng(BoyaCB.latitude,BoyaCB.longitude)}
        if(remota)centrar()
    }
    /**
     * Función que se llama al marcar alguna boya
     */
    fun marcandoBoya(num: Int, lugar : LatLng, remota: Boolean=false) {
        sinboyas=false

        var currentLatLng=lugar
        if(num==BOYA_SE){setSE=true;BoyaSE=lugar.toLocation(); markerSE.position=lugar;markerSE.setVisible(true);LLBoyaSE=lugar}
        if(num==BOYA_SB){setSB=true;BoyaSB=lugar.toLocation(); markerSB.position=lugar;markerSB.setVisible(true);LLBoyaSB=lugar}
        if(num==BOYA_CE){setCE=true;BoyaCE=lugar.toLocation(); markerCE.position=lugar;markerCE.setVisible(true);LLBoyaCE=lugar}
        if(num==BOYA_CB){setCB=true;BoyaCB=lugar.toLocation(); markerCB.position=lugar;markerCB.setVisible(true);LLBoyaCB=lugar}
        if(remota)centrar()
    }



    var ZOOM_DEFAULT=19
    fun verValidar(): Boolean
    {
        botonValidar.setVisibility(View.VISIBLE);
        return true;
    }


    fun validar()
    {
        //orden dextrógiro Cuidado, ahora coordenada3 es boya4 set4 boyaB
        var coordenadas1=BoyaSE.longitude.toString()+","+BoyaSE.latitude.toString()
        var coordenadas2=BoyaSB.longitude.toString()+","+BoyaSB.latitude.toString()
        var coordenadas4=BoyaCE.longitude.toString()+","+BoyaCE.latitude.toString()
        var coordenadas3=BoyaCB.longitude.toString()+","+BoyaCB.latitude.toString()
        botonValidar.setVisibility(View.INVISIBLE);
        if(!setSB)return
        if(!setCE &&!setCB){
            coordenadas3=LLidealCB.longitude.toString()+","+LLidealCB.latitude.toString()
            coordenadas4=LLidealCE.longitude.toString()+","+LLidealCE.latitude.toString()
        }
        if(setCE &&!setCB){
            coordenadas3=LLidealCB.longitude.toString()+","+LLidealCB.latitude.toString()
        }
        if(!setCE &&setCB){
            coordenadas4=LLidealCE.longitude.toString()+","+LLidealCE.latitude.toString()
        }
        var coordenadas=coordenadas1+","+coordenadas2+","+coordenadas3+","+coordenadas4
        val url = "https://regatas.jabegas.com/php/validar?coordenadas="+coordenadas
        val queue = Volley.newRequestQueue(this)
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            { response ->
                // Procesar la respuesta exitosa
                val data = response.toString()
                Log.d("TAG", "Respuesta: $data")
                var toast: Toast
                if(data.contains("SUCCESS"))
                    toast=Toast.makeText(this,"Éxito. Base de datos actualizada",Toast.LENGTH_LONG)
                else
                    toast=Toast.makeText(this,"Error de validación\n¿No es hoy un día de regatas?",Toast.LENGTH_LONG)
                toast.show()

            },
            { error ->
                // Manejar el error
                Log.e("TAG", "Error: $error")
            }
        )
        queue.add(stringRequest)

        /*
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()
        CoroutineScope(IO).launch {
            val response = client.newCall(request).execute()

            // Procesar la respuesta
            if (response.isSuccessful) {
                val body = response.body!!.string(UTF_8)
                println("Respuesta: $body")
            } else {
                println("Error: ${response.code}")
            }
        }
        */

    }

    fun centrar()
    {

        // Lista de LatLons de las boyas válidas
        val validBuoysLatLons = mutableListOf<LatLng>()
        var averageLatLng: LatLng = malaga
        // Agregar LatLons de las boyas válidas a la lista
        if (setSE) validBuoysLatLons.add(LatLng(BoyaSE.latitude, BoyaSE.longitude))
        if (setSB) validBuoysLatLons.add(LatLng(BoyaSB.latitude, BoyaSB.longitude))
        if (setCE) validBuoysLatLons.add(LatLng(BoyaCE.latitude, BoyaCE.longitude))
        if (setCB) validBuoysLatLons.add(LatLng(BoyaCB.latitude, BoyaCB.longitude))

        // Determinar el nivel de zoom
        var zoomLevel: Int=ZOOM_DEFAULT
        if (validBuoysLatLons.isEmpty()) validBuoysLatLons.add(malaga)


        val validPointsCount = validBuoysLatLons.size
        var sumLatitude = 0.0
        var sumLongitude = 0.0

        for (location in validBuoysLatLons) {
            sumLatitude += location.latitude
            sumLongitude += location.longitude
        }

        val averageLatitude = sumLatitude / validPointsCount
        val averageLongitude = sumLongitude / validPointsCount
        averageLatLng = LatLng(averageLatitude, averageLongitude)
        if(validPointsCount>1)
        {
            val builder = LatLngBounds.Builder()
            for (location in validBuoysLatLons) {
                builder.include(location)
            }
            val bounds = builder.build()
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds,100)) //100 pixeles y caben todos en pantalla bien

        }
        else
        {
        // Crear el CameraUpdate
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(validBuoysLatLons[0], zoomLevel.toFloat())
            map.moveCamera(cameraUpdate)
        }



    }

    /**
     * Función que se llama al marcar alguna boya
     */
    fun bloquea( num: Int) {
        if (recording) return
        //Log.d("Calculadora", "Bloqueando "+num.toString())
        var des=true
        if(num==0){
            lockDistance=!lockDistance;des=!lockDistance
            seekBarDistancia.post {
                seekBarDistancia.isEnabled=!lockDistance
                seekBarDistancia.visibility = if (lockDistance) View.GONE else View.VISIBLE
            }
            checkboxLockDistance.isChecked = lockDistance

            }
        if(num==1){
            lockSE=!lockSE;des=!lockSE
            checkboxLock1.isChecked = lockSE
            habilitaBoton(1,lockSE)
        }
        if(num==2){
            lockSB=!lockSB;des=!lockSB
            checkboxLock2.isChecked = lockSB
            habilitaBoton(2,lockSB)}
        if(num==3){
            lockCE=!lockCE;des=!lockCE
            checkboxLockA.isChecked = lockCE
            habilitaBoton(3,lockCE)}
        if(num==4){
            lockCB=!lockCB;des=!lockCB
            checkboxLockB.isChecked = lockCB
            habilitaBoton(4,lockCB)}

        //Realment es necesario?
        //visibilidadMarcadores()
        //calculadora()
        //Toast.makeText(this,"Boya "+num.toString()+" "+(if (des) "des" else  "")+"bloqueada", Toast.LENGTH_SHORT).show()
    }

    fun visibilidadMarcadores()
    {
        Log.d("Calculadora", "Antes de visibilidadMarcadores")
        if(!marcadores)return
        val salidaCompleta = setSE && setSB
        markerCE_ideal.setVisible(salidaCompleta)
        markerCB_ideal.setVisible(salidaCompleta)
        markerCEvalida.setVisible(salidaCompleta && setCB)
        markerCBvalida.setVisible(salidaCompleta && setCE)
        markerSE.setVisible(setSE)
        markerSB.setVisible(setSB)
        markerCE.setVisible(setCE)
        markerCB.setVisible(setCB)

        val mostrarAnclas = buscarAnclas && salidaCompleta
        markerAnclaSE.setVisible(mostrarAnclas)
        markerAnclaSB.setVisible(mostrarAnclas)
        markerAnclaCE.setVisible(mostrarAnclas)
        markerAnclaCB.setVisible(mostrarAnclas)

        Log.d("Calculadora", "Después de visibilidadMarcadores")
    }

    fun hintMarcadores()
    {
        if(!marcadores)return
        markerSE.snippet = "meta, poniente\nN "+markerSE.position.latitude.toString()+"\nW "+(-markerSE.position.longitude).toString()
        markerSB.snippet = "meta, levante\nN "+markerSB.position.latitude.toString()+"\nW "+(-markerSB.position.longitude).toString()
        markerCE.snippet = "ciaboga, poniente\nN "+markerCE.position.latitude.toString()+"\nW "+(-markerCE.position.longitude).toString()
        markerCB.snippet = "ciaboga, levante\nN "+markerCB.position.latitude.toString()+"\nW "+(-markerCB.position.longitude).toString()
        markerCE_ideal.snippet = "ideal ciaboga, poniente\nN "+markerCE_ideal.position.latitude.toString()+"\nW "+(-markerCE_ideal.position.longitude).toString()
        markerCB_ideal.snippet = "ideal ciaboga, levante\nN "+markerCB_ideal.position.latitude.toString()+"\nW "+(-markerCB_ideal.position.longitude).toString()
        markerCEvalida.snippet = "correcta ciaboga, poniente\nN "+markerCEvalida.position.latitude.toString()+"\nW "+(-markerCEvalida.position.longitude).toString()
        markerCBvalida.snippet = "correcta ciaboga, levante\nN "+markerCBvalida.position.latitude.toString()+"\nW "+(-markerCBvalida.position.longitude).toString()
    }



    /**
     * Descarta datos imprecisos.
     */
    fun descartar() : Boolean{
        if(listaPrecisiones.isEmpty()) return false
        if(precision<8)return false
        if(precision>2*exactitud)return true
        return false
    }


    fun exactitud() :Double
    {
        var exactitud=0.0
        val n=listaPrecisiones.size
        if(n<2)return precision
        for(i in 0..n -1) {
            exactitud+=listaPrecisiones[i]
        }
        return ((exactitud*10)/n).toInt()/10.0
    }

    fun promediando() :LatLng {
        mejorPrecision= if(precision < mejorPrecision) precision else mejorPrecision
        precisionVista=mejorPrecision
        //El primer dato nunca se descarta
        if(descartar())return LatLng(mejorLatitud,mejorLongitud)

        listaPosiciones.add(currentLatLng)
        listaPrecisiones.add(precision)
        exactitud=exactitud()
        // Ahora calculo
        val n=listaPosiciones.size
        if(n==1){

            mejorLatitud=currentLatLng.latitude
            mejorLongitud=currentLatLng.longitude
            return currentLatLng
        }


        val pesos = DoubleArray(n)
        var sumapesos=0.0
        var desvLatitudes=0.0
        var desvLongitudes=0.0
        mejorLatitud=0.0
        mejorLongitud=0.0
        //Factor de ponderación
        for(i in 0 until n)
        {
            val prec = if (listaPrecisiones[i] < 0.1) 0.1 else listaPrecisiones[i]
            pesos[i]=1.0/prec
            sumapesos+=pesos[i]
        }
        
        if (sumapesos == 0.0) return currentLatLng

        //Medias ponderadas
        for(i in 0 until n)
        {
            pesos[i]=pesos[i]/sumapesos
            mejorLatitud+=listaPosiciones[i].latitude*pesos[i]
            mejorLongitud+=listaPosiciones[i].longitude*pesos[i]
        }
        for(i in 0..n -1) {
            desvLatitudes+= (listaPosiciones[i].latitude - mejorLatitud).pow(2)
            desvLongitudes+= (listaPosiciones[i].longitude - mejorLongitud).pow(2)
        }
        desvLatitudes=11132 *  sqrt(desvLatitudes/(n-1)) / sqrt(n.toDouble()) // 10000 para pasarlo a metros aprox
        desvLongitudes=10600 * sqrt(desvLongitudes/(n-1))  / sqrt(n.toDouble()) // Segunda division para convertir sigma en error
        var desvMedia=(desvLatitudes+desvLongitudes)/2
        desvMedia=(desvMedia*3000).toInt()/10.0 //No se porque tengo que multiplicar por 300 para que salga algo razonable
        if(n>1){
            mejorPrecision=desvMedia
            precisionVista=mejorPrecision
        }


        return LatLng(mejorLatitud,mejorLongitud)
    }

    /**
     * Durante el promediado, actualiza las boyas
     *
     */
    fun preventana()
    {

        if(recording){
            //precisionVista=precision
            viewLatLng=promediando()
            marcandoBoya(boyaActiva,viewLatLng)
            boyas2marcadores()

        }
        else{

            precisionVista=precision
            viewLatLng=currentLatLng
        }

    }

    /**
     * Actualiza la ventana de datos
     */
    fun ventana()
    {

        val lat=String.format("N%9.6f",currentLatLng.latitude)
        val lon=String.format("W%09.6f",-currentLatLng.longitude)

        if(recording){
            val latB=String.format("N%9.6f",viewLatLng.latitude)
            val lonB=String.format("W%09.6f",-viewLatLng.longitude)
            textViewP.setText("Precisión: "+precisionVista.toString()+
                    "\n Exactitud: "+exactitud.toString()+
                    "\nmedia  "+latB+"\nactual "+lat+"\nmedia  "+lonB+"\nactual "+lon+"\n"+
                    listaPosiciones.size.toString()+ " muestras")
            textViewP.setTextColor(if(precisionVista<4) Color.parseColor("#ff008800") else Color.parseColor("#ffff0000") )
        }
        else{
            var prefijo=getString(R.string.precisi_n_gps)+precision.toString()
            if(campoRTK)
                prefijo=getString(R.string.precisi_n_rtk)+precision.toString()
            textViewP.setText(prefijo+
                    "\nlatitud  "+lat+"\nlongitud "+lon)
            textViewP.setTextColor(if(precision<7) Color.parseColor("#ff008800") else Color.parseColor("#ffff0000") )

        }
    }

    fun LatLng.toLocation(): Location {
        return Location("LatLng").apply {
            latitude = this@toLocation.latitude
            longitude = this@toLocation.longitude
        }
    }

    fun Location.toLatLng(): LatLng = LatLng(this.latitude, this.longitude)

    private fun crearIconoCruzBlanca(): Bitmap {
        val size = 60
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        // Línea horizontal
        canvas.drawLine(10f, size / 2f, size - 10f, size / 2f, paint)
        // Línea vertical
        canvas.drawLine(size / 2f, 10f, size / 2f, size - 10f, paint)
        return bitmap
    }


    fun calculadora(causa:String="Por fijar")
    {

        Log.d("Calculadora","Iniciando cálculos")
        var barcoSinRumbo=false
        if(!marcadores || !boyas)return
        
        // Evitar cálculos si la ubicación no es válida
        if (lastLocation.latitude.isNaN() || lastLocation.longitude.isNaN()) return

        rumboBarco=lastLocation.bearing.toDouble();
        if (rumboBarco.isNaN()) {
            rumboBarco = 0.0
            barcoSinRumbo = true
        }

        boyas2marcadores()

        horiAcc = lastLocation.getAccuracy().toDouble()
        if(!recording)habilitaBotones()

        val results = FloatArray(1)

        // Si estamos en modo RTK, la variable 'precision' ya ha sido actualizada
        // desde el repositorio con el valor de la placa externa.
        // Solo sobreescribimos con horiAcc si NO estamos en RTK.
        if (!campoRTK) {
            precision=horiAcc.bonito()
        }

        preventana()
        ventana()

        val salidaCompleta=setSE&&setSB
        var sinLineaCiaboga=!setCE&&!setCB

        if(salidaCompleta)
        {
            var angle= if (BoyaSE.distanceTo(BoyaSB)==0.0f) 0.0 else BoyaSE.bearingTo(BoyaSB)+90.0
            rumbo_SB_SE=(BoyaSB.bearingTo(BoyaSE)*10.0).toInt()/10.0
            if(rumbo_SB_SE<0)rumbo_SB_SE+=360
            LLidealCE=SphericalUtil.computeOffset(LatLng(BoyaSE.latitude,BoyaSE.longitude),distancia,angle)
            LLidealCB=SphericalUtil.computeOffset(LatLng(BoyaSB.latitude,BoyaSB.longitude),distancia,angle)
            //markerCE_ideal.position=LatLng(idealCE.latitude, idealCE.longitude)
            //markerCB_ideal.position=LatLng(idealCB.latitude, idealCB.longitude)
            rumbo_SE_SB=angle-90.0
            rumbo_SE_CE=angle
            rumbo_SB_CB=angle
            // No se han fijado ,pero las pongo en ideales
            if(sinLineaCiaboga)
            {
                actualizarBoyaConLatLng(BOYA_CE,LLidealCE)
                actualizarBoyaConLatLng(BOYA_CB,LLidealCB)
            }



        }

        //Tenemos la salida completa y la ciaboga de estribor
        if(salidaCompleta&&setCE)
        {
            rumbo_SE_CE = this@MapsActivity.BoyaSE.bearingTo(BoyaCE).toDouble()
            distanciaCalleEstribor=BoyaSE.distanceTo(BoyaCE).toDouble()
            distanciaCalleEstribor= SphericalUtil.computeDistanceBetween(LLBoyaSE,LLBoyaCE)
            LLcorrectaCB=SphericalUtil.computeOffset(LatLng(BoyaSB.latitude,BoyaSB.longitude),distanciaCalleEstribor.toDouble() ,rumbo_SE_CE.toDouble())
            //markerCBvalida.position=LatLng(LLcorrectaCB.latitude, LLcorrectaCB.longitude)

            // No se han fijado ,pero las pongo en ideales
            if(!setCB) {
                //BoyaB.latitude = markerBvalida.position.latitude
                //BoyaB.longitude = markerBvalida.position.longitude
                //LLBoyaB=location2latlng(BoyaB)
                actualizarBoyaConLatLng(BOYA_CB,LLcorrectaCB)
                rumbo_SB_CB=rumbo_SE_CE
                rumbo_CB_CE =rumbo_SB_SE
            }
            setSesgo()
        }

        //Tenemos la salida completa y la ciaboga de babor
        if(salidaCompleta&&setCB)
        {
            rumbo_SB_CB = this@MapsActivity.BoyaSB.bearingTo(BoyaCB).toDouble()
            distanciaCalleBabor=BoyaSB.distanceTo(BoyaCB).toDouble()
            distanciaCalleBabor= SphericalUtil.computeDistanceBetween(LLBoyaSB,LLBoyaCB)
            LLcorrectaCE=SphericalUtil.computeOffset(LatLng(BoyaSE.latitude,BoyaSE.longitude),distanciaCalleBabor.toDouble(),rumbo_SB_CB.toDouble())
            //markerCEvalida.position=LatLng(LLcorrectaCE.latitude, LLcorrectaCE.longitude)

            if(!setCE)
            {
                //BoyaA.latitude=markerAvalida.position.latitude
                //BoyaA.longitude=markerAvalida.position.longitude
                //LLBoyaA=location2latlng(BoyaA)
                actualizarBoyaConLatLng(BOYA_CE,LLcorrectaCE)

            }
            else{
                rumbo_CB_CE=(BoyaCB.bearingTo(BoyaCE)*10.0).toInt()/10.0
                if(rumbo_CB_CE<0)rumbo_CB_CE+=360

            }

            setSesgo()
        }



        //Calculamos los rumbos a las 4 boyas

        if(::BoyaSE.isInitialized){
            //Calculamos la posición del ancla de salida de estribor
            LLBoyaSE= BoyaSE.toLatLng()
            //android.location.Location.distanceBetween(BoyaSE.latitude,BoyaSE.longitude,lastLocation.latitude,lastLocation.longitude,results)
            distanciaSE= SphericalUtil.computeDistanceBetween(LLBoyaSE,lastLocation.toLatLng())
            distanciaSE= distanciaSE.bonito()

            //val rumboA1 = lastLocation.bearingTo(BoyaSE) // Rumbo desde la ubicación actual a Boya1
            //val rumboRelativo1 = rango(rumboA1 - rumboBarco) // Ángulo relativo al rumbo del barco

            rumboSE =rango(lastLocation.bearingTo(BoyaSE) - rumboBarco)
            textViewSE.text = getTextoDistanciaRumbo(distanciaSE, rumboSE)

            if(buscarAnclas){
                actualizarBoyaConLatLng(ANCLA_SE,SphericalUtil.computeOffset(LLBoyaSE,-anchoAncla,rumbo_SE_SB))
                rumboAnclaSE =rango(lastLocation.bearingTo(anclaBoyaSE) - rumboBarco)
                distanciaAnclaSE= SphericalUtil.computeDistanceBetween(anclaBoyaSE.toLatLng(),lastLocation.toLatLng())
                distanciaAnclaSE= distanciaAnclaSE.bonito()
                textViewSE.text = getTextoDistanciaRumbo(distanciaAnclaSE, rumboAnclaSE)
            }
        }

        if(::BoyaSB.isInitialized){
            //Calculamos la posición del ancla de salida de estribor
            LLBoyaSB= BoyaSB.toLatLng()
            //android.location.Location.distanceBetween(BoyaSB.latitude,BoyaSB.longitude,lastLocation.latitude,lastLocation.longitude,results)
            distanciaSB= SphericalUtil.computeDistanceBetween(LLBoyaSB,lastLocation.toLatLng())
            distanciaSB= distanciaSB.bonito()

            //val rumboA2 = lastLocation.bearingTo(BoyaSB) // Rumbo desde la ubicación actual a Boya2
            //val rumboRelativo2 = rango(rumboA2 - rumboBarco) // Ángulo relativo al rumbo del barco
            
            rumboSB =rango(lastLocation.bearingTo(BoyaSB) - rumboBarco)
            textViewSB.text = getTextoDistanciaRumbo(distanciaSB, rumboSB)

            if(buscarAnclas){
                actualizarBoyaConLatLng(ANCLA_SB,SphericalUtil.computeOffset(LLBoyaSB,anchoAncla,rumbo_SE_SB))
                rumboAnclaSB =rango(lastLocation.bearingTo(anclaBoyaSB) - rumboBarco)
                distanciaAnclaSB= SphericalUtil.computeDistanceBetween(anclaBoyaSB.toLatLng(),lastLocation.toLatLng())
                distanciaAnclaSB= distanciaAnclaSB.bonito()
                textViewSB.text = getTextoDistanciaRumbo(distanciaAnclaSB, rumboAnclaSB)
            }
        }

        if(::BoyaCE.isInitialized){
            //Calculamos la posición del ancla de ciaboga de estribor
            LLBoyaCE= BoyaCE.toLatLng()
            distanciaCE= SphericalUtil.computeDistanceBetween(LLBoyaCE,lastLocation.toLatLng())
            distanciaCE= distanciaCE.bonito()

            rumboCE =rango(lastLocation.bearingTo(BoyaCE) - rumboBarco)
            textViewCE.text = getTextoDistanciaRumbo(distanciaCE, rumboCE)

            if(buscarAnclas){
                actualizarBoyaConLatLng(ANCLA_CE,SphericalUtil.computeOffset(LLBoyaCE,-anchoAncla,rumbo_SE_SB))
                rumboAnclaCE =rango(lastLocation.bearingTo(anclaBoyaCE) - rumboBarco)
                distanciaAnclaCE= SphericalUtil.computeDistanceBetween(anclaBoyaCE.toLatLng(),lastLocation.toLatLng())
                distanciaAnclaCE= distanciaAnclaCE.bonito()
                textViewCE.text = getTextoDistanciaRumbo(distanciaAnclaCE, rumboAnclaCE)
            }

        }
        
        if(::BoyaCB.isInitialized){
            //Calculamos la posición del ancla de ciaboga de babor
            LLBoyaCB= BoyaCB.toLatLng()
            distanciaCB= SphericalUtil.computeDistanceBetween(LLBoyaCB,lastLocation.toLatLng())
            distanciaCB= distanciaCB.bonito()
            
            rumboCB =rango(lastLocation.bearingTo(BoyaCB) - rumboBarco)
            textViewCB.text = getTextoDistanciaRumbo(distanciaCB, rumboCB)

            if(buscarAnclas){
                actualizarBoyaConLatLng(ANCLA_CB,SphericalUtil.computeOffset(LLBoyaCB,anchoAncla,rumbo_SE_SB))
                rumboAnclaCB =rango(lastLocation.bearingTo(anclaBoyaCB) - rumboBarco)
                distanciaAnclaCB= SphericalUtil.computeDistanceBetween(anclaBoyaCB.toLatLng(),lastLocation.toLatLng())
                distanciaAnclaCB= distanciaAnclaCB.bonito()
                textViewCB.text = getTextoDistanciaRumbo(distanciaAnclaCB, rumboAnclaCB)
            }

        }


        //Ammpliamos marcadores con nuevos valores calculados
        if (marcadores) {
            if (::LLidealCE.isInitialized) markerCE_ideal.position = LLidealCE
            if (::LLidealCB.isInitialized) markerCB_ideal.position = LLidealCB
            if(::LLcorrectaCE.isInitialized)markerCEvalida.position=LLcorrectaCE
            if(::LLcorrectaCB.isInitialized)markerCBvalida.position=LLcorrectaCB

            if (::anclaBoyaSE.isInitialized) markerAnclaSE.position = anclaBoyaSE.toLatLng()
            if (::anclaBoyaSB.isInitialized) markerAnclaSB.position = anclaBoyaSB.toLatLng()
            if (::anclaBoyaCE.isInitialized) markerAnclaCE.position = anclaBoyaCE.toLatLng()
            if (::anclaBoyaCB.isInitialized) markerAnclaCB.position = anclaBoyaCB.toLatLng()
        }

        visibilidadMarcadores()
        hintMarcadores()
        Log.d("Calculadora","finalizando cálculos "+causa)

    }

    private fun location2latlng(lastLocation: Location): LatLng {
        return LatLng(lastLocation.latitude,lastLocation.longitude)
    }

    fun borrar(boya: Int): Boolean
    {
        if(boya==1&&!setSE)return false
        if(boya==2&&!setSB)return false
        if(boya==3&&!setCE)return false
        if(boya==4&&!setCB)return false
        if(recording&&boyaActiva==boya)
            Recording(boya) //Detiene la grabación
        if(boya==1)setSE=false
        if(boya==2)setSB=false
        if(boya==3)setCE=false
        if(boya==4)setCB=false
        sinboyas=!setSE&&!setSB&&!setCE&&!setCB
        visibilidadMarcadores()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val v= getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
            v.vibrate(CombinedVibration.createParallel(VibrationEffect.createOneShot(100,VibrationEffect.DEFAULT_AMPLITUDE)))
        } else {
            TODO("VERSION.SDK_INT < S")
        }

        return true
    }

    /**
     * Calcula el sesgo del campo
     */
    fun setSesgo()
    {
        var sesgo12A=0.0
        if(setSE&&setSB&&setCE){
            infor.visibility=View.VISIBLE
            sesgo12A=rango(rumbo_SE_SB+90-rumbo_SE_CE)
            if(!setCB)
            {
                longitudCampo=(distanciaCalleEstribor*10).toInt()/10.0
                sesgoCampo=(sesgo12A*10).toInt()/10.0
                infor.setText(longitudCampo.toString()+"m\n"+sesgoCampo.toString()+"º")
            }
        }
        var sesgo12B=0.0
        if(setSE&&setSB&&setCB){
            infor.visibility=View.VISIBLE
            infor.setText("")
            sesgo12B=rango(rumbo_SE_SB+90-rumbo_SB_CB)
            if(!setCE)
            {
                longitudCampo=(distanciaCalleBabor*10).toInt()/10.0
                sesgoCampo=(sesgo12B*10).toInt()/10.0
                infor.setText(buildString {
                    append(longitudCampo.toString())
                    append("m\n")
                    append(sesgoCampo.toString())
                    append("º")
                })

            }
            else{
                longitudCampo=((distanciaCalleBabor+distanciaCalleEstribor)/0.2).toInt()/10.0
                sesgoCampo=((sesgo12A+sesgo12B)/0.2).toInt()/10.0
                infor.setText(buildString {
                    append(longitudCampo.toString())
                    append("m\n(")
                    append(round(distanciaCalleEstribor).toString())
                    append("-")
                    append(round(distanciaCalleBabor).toString())
                    append(")\n")
                    append(sesgoCampo.toString())
                    append("º")
                })
            }
        }
    }

     fun habilitaBoton(i :Int, status: Boolean){
         if(i==BOYA_SE || i==BOYA_SB) {
             val hiddenType = if (lockSE && lockSB) View.GONE else View.INVISIBLE
             botonSE.post {
                 botonSE.visibility = if (lockSE) hiddenType else View.VISIBLE
                 botonSE.isEnabled = !lockSE
             }
             botonSB.post {
                 botonSB.visibility = if (lockSB) hiddenType else View.VISIBLE
                 botonSB.isEnabled = !lockSB
             }
         }

         if(i==BOYA_CE || i==BOYA_CB) {
             val hiddenType = if (lockCE && lockCB) View.GONE else View.INVISIBLE
             botonCE.post {
                 val visible = !lockCE
                 botonCE.visibility = if (visible) View.VISIBLE else hiddenType
                 botonCE.isEnabled = visible
             }
             botonCB.post {
                 val visible = !lockCB
                 botonCB.visibility = if (visible) View.VISIBLE else hiddenType
                 botonCB.isEnabled = visible
             }
         }
    }


    private fun leerBoyaRTK(i: Int) {
        val currentLL = ntripManager.obtenerPosicion(if (::lastLocation.isInitialized) lastLocation else null)
        marcandoBoya(i, currentLL)
        calculadora("RTK Measure")
        Toast.makeText(
            this,
            "Medida fijada para boya $i (${ntripManager.descripcionFix()})",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun Recording(i : Int)
    {
        if (campoRTK) {
            leerBoyaRTK(i)
            return
        }
        boyaActiva = i
        recording = !recording
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if(recording){
            actualizaFrecuenciaGPS(1000)
            listaPrecisiones.clear()
            listaPosiciones.clear()
            botonSE.setEnabled(if(i!=1)false else true)
            botonSB.setEnabled(if(i!=2)false else true)
            botonCE.setEnabled(if(i!=3)false else true)
            botonCB.setEnabled(if(i!=4)false else true)
            
            checkboxLock1.isEnabled = false
            checkboxLock2.isEnabled = false
            checkboxLockA.isEnabled = false
            checkboxLockB.isEnabled = false
            checkboxLockDistance.isEnabled = false
        }
        else
        {
            actualizaFrecuenciaGPS(5000)
            checkboxLock1.isEnabled = true
            checkboxLock2.isEnabled = true
            checkboxLockA.isEnabled = true
            checkboxLockB.isEnabled = true
            checkboxLockDistance.isEnabled = true
            habilitaBotones()
        }
        if (boyaActiva == 1) botonSE.setText(if(recording) getString(R.string.grabando) else getString(R.string.boton1))
        if (boyaActiva == 2) botonSB.setText(if(recording) getString(R.string.grabando) else getString(R.string.boton2))
        if (boyaActiva == 3) botonCE.setText(if(recording) getString(R.string.grabando) else getString(R.string.boton3))
        if (boyaActiva == 4) botonCB.setText(if(recording) getString(R.string.grabando) else getString(R.string.boton4))

        menuBackup.setEnabled(!recording)
        menuRestore.setEnabled(!recording)
        menuIntercambia1.setEnabled(!recording)
        menuIntercambia2.setEnabled(!recording)
        menuIntercambia3.setEnabled(!recording)
        menuBrujula.setEnabled(!recording)
        preventana()
        ventana()
        calculadora()
        visibilidadMarcadores()
    }

    /**
     * Bloquea un boton
     */


    /**
     * Copia datos al portapapeles
     */
    fun informar(){
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val txto=informe()
        val clip: ClipData = ClipData.newPlainText("texto",txto )
        clipboard.setPrimaryClip(clip)
        if (txto != null) {
            showHelloWorldDialog(this,txto)
        }
    }

    fun showHelloWorldDialog(context: Context, informe: CharSequence) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(getString(R.string.informe_copiado_al_portapapeles))
        builder.setMessage(informe)
        builder.setPositiveButton("OK", null)
        val dialog = builder.create()
        dialog.setOnShowListener(DialogInterface.OnShowListener {
            val btnPositive: Button = dialog.getButton(Dialog.BUTTON_POSITIVE)
            btnPositive.textSize = 26f
        })

        dialog.show()
    }


    private fun informe(): CharSequence? {
        var fijada : String
        fijada = if(setSE==true)"" else "\nBoya no fijada por el usuario"
        var x= "Salida calle 5 (poniente):"+fijada+
        "\n\tLatitud:  "+String.format("%08.5f",BoyaSE.latitude)+
        "\n\tLongitud: "+String.format("%08.5f",BoyaSE.longitude)+
        "\n\n"
        fijada = if(setSB==true)"" else "\nBoya no fijada por el usuario"
        x=x+
        "Salida calle 1 (levante):"+fijada+
        "\n\tLatitud:  "+String.format("%08.5f",BoyaSB.latitude)+
        "\n\tLongitud: "+String.format("%08.5f",BoyaSB.longitude)+
        "\n\n"

        var t_rumbo1=if (setSE&&setSB) "Rumbo de línea de salida (levante a poniente): "+rumbo_SB_SE.toString()+"\n\n" else ""
        x = x+t_rumbo1

        fijada = if(setCE==true)"" else "\nBoya no fijada por el usuario"
        x=x+
        "Ciaboga de la calle 5, poniente:"+fijada+
        "\n\tLatitud:  "+String.format("%08.5f",BoyaCE.latitude)+
        "\n\tLongitud: "+String.format("%08.5f",BoyaCE.longitude)+
        "\n\n"
        fijada = if(setCB==true)"" else "\nBoya no fijada por el usuario"
        x=x+
        "Ciaboga de la calle 1, levante:"+fijada+
        "\n\tLatitud:  "+String.format("%08.5f",BoyaCB.latitude)+
        "\n\tLongitud: "+String.format("%08.5f",BoyaCB.longitude)+
        "\n\n"
        var t_rumbo3=if (setCE&&setCB) "Rumbo de línea de ciaboga (levante a poniente): "+rumbo_CB_CE.toString()+"\n\n" else ""
        x = x+t_rumbo3
        x=x+
        "\n"+
        "Longitud del campo (Calle 1): "+String.format("%.1fm",distanciaCalleBabor)+"\n"+
        "\n"+
        "Longitud del campo (Calle 5): "+String.format("%.1fm",distanciaCalleEstribor)+"\n"+
        "\n"+
        "Longitud media del campo: " +String.format("%.1fm",longitudCampo)+"\n"+
        "\n" +
        "Sesgo del campo: " +String.format("%.1fº",sesgoCampo)+"\n"
        return x
    }


    /**
     * Intercambia boyas 1 y 2 (cambia el sentido de la regata)
     */
    fun intercambia(mode:Int = 1)
    {
        var boya: Location
        if(mode==1){
            boya= BoyaSB
            BoyaSB = BoyaSE
            BoyaSE= boya

            var llboya=LLBoyaSB
            LLBoyaSB=LLBoyaSE
            LLBoyaSE=llboya
        }
        if(mode==2){
            boya= BoyaCB
            BoyaCB = BoyaCE
            BoyaCE= boya

            var llboya=LLBoyaCB
            LLBoyaCB=LLBoyaCE
            LLBoyaCE=llboya
        }
        if(mode==3){
            boya= BoyaCB
            BoyaCB = BoyaSB
            BoyaSB= boya

            boya= BoyaCE
            BoyaCE = BoyaSE
            BoyaSE= boya

            var llboya=LLBoyaCB
            LLBoyaCB=LLBoyaSB
            LLBoyaSB=llboya

            llboya=LLBoyaSE
            LLBoyaSE=LLBoyaCE
            LLBoyaCE=llboya
        }
        calculadora("Por intercambio")
    }

    /**
     * Habilita o inhabilita botones según variables lock
     */
    fun habilitaBotones()
    {
        //val minp=1000000
        seekBarDistancia.isEnabled=!lockDistance
        seekBarDistancia.visibility = if (lockDistance) View.GONE else View.VISIBLE
        checkboxLockDistance.isChecked = lockDistance
        checkboxLock1.isChecked = lockSE
        checkboxLock2.isChecked = lockSB
        checkboxLockA.isChecked = lockCE
        checkboxLockB.isChecked = lockCB

        val hiddenType12 = if (lockSE && lockSB) View.GONE else View.INVISIBLE
        val hiddenTypeAB = if (lockCE && lockCB) View.GONE else View.INVISIBLE

        botonSE.visibility = if (lockSE) hiddenType12 else View.VISIBLE
        botonSE.setEnabled(!lockSE)

        botonSB.visibility = if (lockSB) hiddenType12 else View.VISIBLE
        botonSB.setEnabled(!lockSB)

        val visibleA = !lockCE
        botonCE.visibility = if (visibleA) View.VISIBLE else hiddenTypeAB
        botonCE.setEnabled(visibleA)

        val visibleB = !lockCB
        botonCB.visibility = if (visibleB) View.VISIBLE else hiddenTypeAB
        botonCB.setEnabled(visibleB)

    }


    fun circulo(radio : Float, color : Int): Bitmap? {
         val bitmap= Bitmap.createBitmap(radio.toInt()*2, radio.toInt()*2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.WHITE
        canvas.drawCircle(radio.toFloat(), radio.toFloat(), radio.toFloat(), paint)
        val radio2=radio-4
        paint.color = color
        canvas.drawCircle(radio.toFloat(), radio.toFloat(), radio2.toFloat(), paint)
        return bitmap
    }

    fun cuadrado(radio : Float, color : Int): Bitmap? {
        val bitmap= Bitmap.createBitmap(radio.toInt()*2, radio.toInt()*2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.WHITE
        canvas.drawRect(0f,0f,2*radio-1f ,2*radio-1f ,paint)
        //val radio2=radio-4
        paint.color = color
        canvas.drawRect(2f,2f,2*radio-3f ,2*radio-3f ,paint)
        return bitmap
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    //*******************************************************************************************
    //                         Funciones de android o google                                    *
    //*******************************************************************************************

    fun findviews()
    {
        seekBarDistancia = findViewById(R.id.seekBar)
        seekBarDistancia.max=400
        //seekBar.min=200


        textViewSE = findViewById(R.id.textOverlay1)
        textViewSB = findViewById(R.id.textOverlay2)
        textViewCE = findViewById(R.id.textOverlay3)
        textViewCB = findViewById(R.id.textOverlay4)
        textViewD = findViewById(R.id.editTextText)
        textViewP = findViewById(R.id.precision)

        checkboxLockDistance = findViewById(R.id.checkboxLockDistance)
        checkboxLockDistance.isChecked = lockDistance
        checkboxLockDistance.setOnClickListener { bloquea(0) }
        checkboxLock1 = findViewById(R.id.checkboxLock1)
        checkboxLock1.isChecked = lockSE
        checkboxLock1.setOnClickListener { bloquea(1) }
        checkboxLock2 = findViewById(R.id.checkboxLock2)
        checkboxLock2.isChecked = lockSB
        checkboxLock2.setOnClickListener { bloquea(2) }
        checkboxLockA = findViewById(R.id.checkboxLock3)
        checkboxLockA.isChecked = lockCE
        checkboxLockA.setOnClickListener { bloquea(3) }
        checkboxLockB = findViewById(R.id.checkboxLock4)
        checkboxLockB.isChecked = lockCB
        checkboxLockB.setOnClickListener { bloquea(4) }

        btnBuscarAnclas = findViewById(R.id.btnBuscarAnclas)
        btnBuscarAnclas.setOnClickListener {
            buscarAnclas = !buscarAnclas
            actualizarIconoAncla()
            calculadora("Por conmutar búsqueda de anclas")
        }
        actualizarIconoAncla()

        infor=findViewById(R.id.sesgo)
        botonSE=findViewById(R.id.button1)
        botonSB=findViewById(R.id.button2)
        botonCE=findViewById(R.id.button3)
        botonCB =findViewById(R.id.button4)
        botonCentrar=findViewById(R.id.centrar)
        botonValidar=findViewById(R.id.validar)
        seekBarDistancia.setOnSeekBarChangeListener( object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // here, you react to the value being set in seekBar
                var x=progress/5
                x=max(1,x)
                seekBar.progress=x*5
                distancia = seekBar.progress*1.0;
                textViewD.setText("Longitud deseada: "+distancia.toString())
                calculadora("Por cambio de distancia")
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                // you can probably leave this empty
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                // you can probably leave this empty
            }
        })

        botonSE.setOnClickListener(View.OnClickListener { view ->
            Recording(1)})
        botonSB.setOnClickListener(View.OnClickListener { view ->
            Recording(2)})
        botonCE.setOnClickListener(View.OnClickListener { view ->
            Recording(3)})
        botonCB.setOnClickListener(View.OnClickListener { view ->
            Recording(4)})

        botonSE.setOnLongClickListener(View.OnLongClickListener { view ->
            borrar(1)})
        botonSB.setOnLongClickListener(View.OnLongClickListener { view ->
            borrar(2)})
        botonCE.setOnLongClickListener(View.OnLongClickListener { view ->
            borrar(3)})
        botonCB.setOnLongClickListener(View.OnLongClickListener { view ->
            borrar(4)})

        botonCentrar.setOnClickListener(View.OnClickListener { view ->
            centrar()})
        botonCentrar.setOnLongClickListener(View.OnLongClickListener { view ->
            verValidar()})
        botonValidar.setOnClickListener(View.OnClickListener { view ->
            validar()})

        // textViewD.setOnClickListener(View.OnClickListener { view ->bloquea(0)})
        // textView1.setOnClickListener(View.OnClickListener { view ->bloquea(1)})
        // textView2.setOnClickListener(View.OnClickListener { view ->bloquea(2)})
        // textViewA.setOnClickListener(View.OnClickListener { view ->bloquea(3)})
        // textViewB.setOnClickListener(View.OnClickListener { view ->bloquea(4)})
    }

    var mMenu: Menu? =null;

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        mMenu=menu;

        return true
    }

    fun menuViews(menu:Menu)
    {
        menuBackup = menu.findItem(R.id.menu_backup)
        menuRestore = menu.findItem(R.id.menu_restore)
        menuIntercambia1 = menu.findItem(R.id.menu_itemInvertir1)
        menuIntercambia2 = menu.findItem(R.id.menu_itemInvertir2)
        menuIntercambia3 = menu.findItem(R.id.menu_itemInvertir3)
        menuBrujula = menu.findItem(R.id.menu_brujula)
        menuMQTT=menu.findItem(R.id.menu_mqtt)
        menuMQTT.setChecked(menuMQTTchecked)
        // Guardar referencia al item de menú para actualizar su estado
        menuNoGirarItem = menu.findItem(R.id.menu_no_girar_pantalla)

        // Establecer el estado inicial del checkbox en el menú
        menuNoGirarItem?.isChecked = noGirarPantallaActivado


    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_main, menu)
        mMenu=menu;
        menuViews(menu);
        return true
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_itemInformar -> {
                // Acción para el menú_item1
                informar()
                true
            }
            R.id.menu_itemInvertir1 -> {
                // Acción para el menú_item1
                intercambia(1)
                true
            }
            R.id.menu_itemInvertir2 -> {
                // Acción para el menú_item1
                intercambia(2)
                true
            }
            R.id.menu_itemInvertir3 -> {
                // Acción para el menú_item1
                intercambia(3)
                true
            }
            R.id.menu_item0 -> {
                // Acción para el menú_item1
                reiniciar()
                true
            }
            R.id.menu_item1 -> {
                // Acción para el menú_item1
                ayuda()
                true
            }
            R.id.menu_brujula -> {
                // Acción para el menú_item2
                // start your activity by passing the intent

                val intent = Intent(this, CompassActivity::class.java)

                intent.putExtra("rumbo21", rumbo_SB_SE)
                intent.putExtra("rumbo43", rumbo_CB_CE)
                startActivity(intent)
                true
            }
            R.id.menu_mqtt-> {
                // Acción para el menú_item2
                //disconnect()
                menuMQTT.isChecked=!menuMQTT.isChecked
                menuMQTTchecked=!menuMQTT.isChecked
                //if(menuMQTT.isChecked)enviaPrueba()
                true
            }
            R.id.menu_item2 -> {
                // Acción para el menú_item2
                //disconnect()
                finish()
                true
            }
            R.id.menu_backup -> {
                // Acción para el menú_item2
                backup()
                true
            }
            R.id.menu_restore -> {
                // Acción para el menú_item2
                restaurar()
                true
            }

            R.id.menu_no_girar_pantalla -> {
                // Cambiar el estado
                noGirarPantallaActivado = !noGirarPantallaActivado
                // Actualizar el estado visual del checkbox en el menú
                item.isChecked = noGirarPantallaActivado
                // Guardar el nuevo estado
                sharedPref.edit { putBoolean(PREF_NO_GIRAR_PANTALLA, noGirarPantallaActivado) }
                // Aplicar la restricción de orientación
                aplicarRestriccionDeOrientacion()
                true
            }
            // Puedes agregar más casos para otros elementos del menú



            else -> super.onOptionsItemSelected(item)
        }
    }

//*************************Guardar y restablecer estado ********************************************
//Tras cierre
private fun necesitaRestaurar() {
    destruido=sharedPref.getBoolean("destruido",false)
}
    private fun restaurar() {
        BoyaSE.latitude=sharedPref.getFloat("boya1lat",malaga.latitude.toFloat()).toDouble()
        BoyaSE.longitude=sharedPref.getFloat("boya1lon",malaga.longitude.toFloat()).toDouble()
        BoyaSB.latitude=sharedPref.getFloat("boya2lat",malaga.latitude.toFloat()).toDouble()
        BoyaSB.longitude=sharedPref.getFloat("boya2lon",malaga.longitude.toFloat()).toDouble()
        BoyaCE.latitude=sharedPref.getFloat("boya3lat",malaga.latitude.toFloat()).toDouble()
        BoyaCE.longitude=sharedPref.getFloat("boya3lon",malaga.longitude.toFloat()).toDouble()
        BoyaCB.latitude=sharedPref.getFloat("boya4lat",malaga.latitude.toFloat()).toDouble()
        BoyaCB.longitude=sharedPref.getFloat("boya4lon",malaga.longitude.toFloat()).toDouble()

        LLBoyaSE=BoyaSE.toLatLng()
        LLBoyaSB=BoyaSB.toLatLng()
        LLBoyaCE=BoyaCE.toLatLng()
        LLBoyaCB=BoyaCB.toLatLng()


        setSE=sharedPref.getBoolean("set1",false)
        setSB=sharedPref.getBoolean("set2",false)
        setCE=sharedPref.getBoolean("set3",false)
        setCB=sharedPref.getBoolean("set4",false)
        lockDistance=sharedPref.getBoolean("lockDistance",false)
        lockSE=sharedPref.getBoolean("lock1",false)
        lockSB=sharedPref.getBoolean("lock2",false)
        lockCE=sharedPref.getBoolean("lock3",false)
        lockCB=sharedPref.getBoolean("lock4",false)

    //menuMQTT.setChecked(sharedPref.getBoolean("remotas",false))


    val visibleValidar=sharedPref.getBoolean("validar",false)
    botonValidar.setVisibility(if(visibleValidar)View.VISIBLE else View.INVISIBLE)
    distancia=sharedPref.getFloat("distancia",350f).toDouble()
    sesgo=sharedPref.getFloat("sesgo",0f).toDouble()
    seekBarDistancia.progress=distancia.toInt()
    sinboyas=!setSE&&!setSB&&!setCE&&!setCB
    visibilidadMarcadores()
    hintMarcadores()
    calculadora("Por restauración")
    }

    private fun backup() {

        with (sharedPref.edit()) {
            if (::BoyaSE.isInitialized) {
                putFloat("boya1lat", BoyaSE.latitude.toFloat())
                putFloat("boya1lon", BoyaSE.longitude.toFloat())
            }
            if (::BoyaSB.isInitialized) {
                putFloat("boya2lat", BoyaSB.latitude.toFloat())
                putFloat("boya2lon", BoyaSB.longitude.toFloat())
            }
            if (::BoyaCE.isInitialized) {
                putFloat("boya3lat", BoyaCE.latitude.toFloat())
                putFloat("boya3lon", BoyaCE.longitude.toFloat())
            }
            if (::BoyaCB.isInitialized) {
                putFloat("boya4lat", BoyaCB.latitude.toFloat())
                putFloat("boya4lon", BoyaCB.longitude.toFloat())
            }
            putFloat("distancia",distancia.toFloat())
            putFloat("sesgo",sesgo.toFloat())
            putBoolean("set1",setSE)
            putBoolean("set2",setSB)
            putBoolean("set3",setCE)
            putBoolean("set4",setCB)
            putBoolean("lockDistance",lockDistance)
            putBoolean("lock1",lockSE)
            putBoolean("lock2",lockSB)
            putBoolean("lock3",lockCE)
            putBoolean("lock4",lockCB)
            putBoolean("remotas",menuMQTT.isChecked())
            putBoolean("validar",botonValidar.visibility==View.VISIBLE);
            putBoolean("destruido",destruido)
            apply()
        }
    }

//Tras pause

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if(!boyas)return //Alguna vez falló sin esto
        val posiciones = DoubleArray(10)
        posiciones[0]=BoyaSE.latitude
        posiciones[1]=BoyaSE.longitude
        posiciones[2]=BoyaSB.latitude
        posiciones[3]=BoyaSB.longitude
        posiciones[4]=BoyaCE.latitude
        posiciones[5]=BoyaCE.longitude
        posiciones[6]=BoyaCB.latitude
        posiciones[7]=BoyaCB.longitude
        posiciones[8]=distancia
        posiciones[9]=sesgo
        val setted=BooleanArray(11)
        setted[0]=setSE
        setted[1]=setSB
        setted[2]=setCE
        setted[3]=setCB
        setted[4]=lockSE
        setted[5]=lockSB
        setted[6]=lockCE
        setted[7]=lockCB
        setted[8]=botonValidar.visibility==View.VISIBLE;
        setted[9]=menuMQTT.isChecked();
        setted[10]=lockDistance
        markerSE.setVisible(setSE)
        markerSB.setVisible(setSB)
        markerCE.setVisible(setCE)
        markerCB.setVisible(setCB)
        outState.putDoubleArray("Posiciones", posiciones)
        outState.putBooleanArray("Fijados", setted)
        //calculadora()
    }

    private fun recuperaDestruido(savedInstanceState: Bundle)
    {
        val posiciones = savedInstanceState.getDoubleArray("Posiciones")
        val setted = savedInstanceState.getBooleanArray("Fijados")
        setSE=setted!![0]
        setSB=setted[1]
        setCE=setted[2]
        setCB=setted[3]
        lockSE=setted[4]
        lockSB=setted[5]
        lockCE=setted[6]
        lockCB=setted[7]
        botonValidar.setVisibility(if(setted[8]) View.VISIBLE else View.INVISIBLE)
        menuMQTTchecked=setted[9];
        lockDistance=setted[10]
        //habilitaBotones()
        BoyaSE=Location("provider")
        BoyaSE.latitude= posiciones!![0]
        BoyaSE.longitude= posiciones!![1]
        LLBoyaSE= LatLng(posiciones!![0],posiciones!![1])
        BoyaSB=Location("provider")
        BoyaSB.latitude= posiciones!![2]
        BoyaSB.longitude= posiciones!![3]
        LLBoyaSB= LatLng(posiciones!![2],posiciones!![3])
        BoyaCE=Location("provider")
        BoyaCE.latitude= posiciones!![4]
        BoyaCE.longitude= posiciones!![5]
        LLBoyaCE= LatLng(posiciones!![4],posiciones!![5])
        BoyaCB=Location("provider")
        BoyaCB.latitude= posiciones!![6]
        BoyaCB.longitude= posiciones!![7]
        LLBoyaCB= LatLng(posiciones!![6],posiciones!![7])
        distancia= posiciones!![8]

        sesgo= posiciones!![9]
        boyas= true
        sinboyas=!setSE&&!setSB&&!setCE&&!setCB
        visibilidadMarcadores()
        hintMarcadores()

    }


    //*******************************************************************************************
    //                         Otras                                                            *
    //*******************************************************************************************

    /**
     * Adquiere o comprueba permiso de ubicacion precisa
     */
    fun permisos()
    {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            gps_enabled=false

            val locationPermissionRequest = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                when {
                    permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                        gps_enabled=true
                        enmarchagps()
                        habilitaGPSenmapa()  //Dos intentos. onGPSReady y onMapReady

                        // Precise location access granted.
                    }
                    permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                        gps_enabled=true
                        enmarchagps()
                        habilitaGPSenmapa()  //Dos intentos. onGPSReady y onMapReady
                        // Only approximate location access granted.
                    } else -> {
                    // No location access granted.
                }
                }
            }

            locationPermissionRequest.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION))

        }
        else gps_enabled=true


    }

    /**
     * Lo necesita la notificacion
     * que a su vez, lo necesita el mqtt como servicio
     */
    private fun createNotificationChannel(channelId: String, channelName: String): String{
        val chan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(
                channelId,
                channelName, NotificationManager.IMPORTANCE_NONE
            )
        } else {
            canal=false
            return ""
        }
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }



    fun icono()
    {

        val actionBar = getSupportActionBar()
        if (actionBar != null) {
            actionBar.setDisplayOptions(
                actionBar.getDisplayOptions()
                        or androidx.appcompat.app.ActionBar.DISPLAY_SHOW_CUSTOM

            )
            val imageView = ImageView(this)

            val assets = getAssets()

            // Abrir el archivo de imagen
            val inputStream = assets.open("lj.png")

            // Crear una imagen a partir de la entrada
            val bitmap = BitmapFactory.decodeStream(inputStream)


            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        //imageView.setImageResource(R.mipmap.ic_launcher_foreground)
        imageView.setImageBitmap(bitmap)
            val layoutParams = ActionBar.LayoutParams(
            ActionBar.LayoutParams.WRAP_CONTENT,
            ActionBar.LayoutParams.WRAP_CONTENT, Gravity.END
                    or Gravity.BOTTOM
        )
        layoutParams.rightMargin = 0
        imageView.layoutParams = layoutParams
        actionBar!!.customView = imageView
            actionBar.title =""
        }

    }

    // --- LÓGICA DE ORIENTACIÓN ---
    @SuppressLint("SourceLockedOrientationActivity")
    private fun aplicarRestriccionDeOrientacion() {
        if (noGirarPantallaActivado) {
            // Bloquear a la orientación actual
            val currentOrientation = resources.configuration.orientation
            if (currentOrientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            // O una opción más simple si quieres bloquear a retrato siempre:
            // requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // O bloquear a la orientación "natural" del dispositivo:
            // requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
            //    (esto bloquea a la actual y no permite el giro por sensor)
        } else {
            // Permitir que el sensor controle la orientación
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }



    private lateinit var sharedPref: SharedPreferences
    fun preferenciasDeInicio()
    {
        sharedPref = getPreferences(Context.MODE_PRIVATE) // O un nombre específico si prefieres

        // Cargar el estado guardado para "No girar pantalla"
        noGirarPantallaActivado = sharedPref.getBoolean(PREF_NO_GIRAR_PANTALLA, false)
        aplicarRestriccionDeOrientacion() // Aplicar al inicio
    }

   // @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAGVIDA,"Estoy en onCreate")
       preferenciasDeInicio()
       ntripManager = Ntrip(this, locationRepository)

       binding = ActivityMapsBinding.inflate(layoutInflater)
       setContentView(binding.root)
       
       if (campoRTK) {
           binding.editTextText2.text = "campo RTK"
       }

       val toolbar = findViewById<Toolbar>(R.id.toolbar)
       setSupportActionBar(toolbar)

       val rootView = findViewById<View>(R.id.constraintLayout)
       ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.constraintLayout)) { view, insets ->
           val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
           view.setPadding(0, 90, 0, systemBars.bottom-40)
           insets
       }
       ViewCompat.requestApplyInsets(rootView)
       WindowCompat.setDecorFitsSystemWindows(window, true)

       necesitaRestaurar()
       servicio()
       val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
       connectivityManager.registerNetworkCallback(NetworkRequest.Builder().build(), object : ConnectivityManager.NetworkCallback() {
           override fun onAvailable(network: Network) {
               conectar()
           }
       })

        icono()
        permisos()
        inicial()

       canal=createNotificationChannel("canalNotis", "My Background Service")!=""
       if(canal)notis = NotificationCompat.Builder(this,"canalNotis")
           .setSmallIcon(R.drawable.ligadejabegas)
           .setContentTitle("Campo de Regats")
           .setContentText("Canal MQTT habilitado")
           .setPriority(NotificationCompat.PRIORITY_DEFAULT).build()


        if(mqttClient==null)mqtt(this)
        conectar()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)



       locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).apply {
           // Intervalo mínimo entre actualizaciones. Puede ser más corto que 'intervalMillis'
           // si otras apps están solicitando ubicaciones a un ritmo más rápido.
           setMinUpdateIntervalMillis(2000L) // Ejemplo: mínimo cada 2 segundos

           // Desplazamiento mínimo en metros antes de que se active una actualización de ubicación.
           // setMinUpdateDistanceMeters(10f) // Ejemplo: mínimo 10 metros de desplazamiento

           // Si quieres permitir que se agrupen varias actualizaciones para ahorrar batería
           // setWaitForAccurateLocation(false) // Por defecto es true para HIGH_ACCURACY

           // Granularidad de la solicitud de ubicación (afecta a los permisos necesarios)
           // setGranularity(Granularity.GRANULARITY_FINE) // Ya implícito con PRIORITY_HIGH_ACCURACY

           // Puedes añadir más configuraciones aquí según tus necesidades:
           // .setMaxUpdateDelayMillis(maxUpdateDelayMillis: Long)
           // .setMaxUpdates(numUpdates: Int)
           // .setDurationMillis(durationMillis: Long)
           // .setWaitForAccurateLocation(waitForAccurateLocation: Boolean)
           // etc.
       }.build()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Observar el flujo de ubicación unificada (RTK + Sistema)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                locationRepository.posicionActual.collect { datos ->
                    datos?.let {
                        val lat = it.coordenadas.latitude
                        val lon = it.coordenadas.longitude
                        
                        // Actualizamos las variables que usa el resto de la app con toda la precisión disponible
                        currentLatLng = it.coordenadas
                        
                        // Actualizamos el modo RTK basado en la precisión real
                        campoRTK = it.esAltaPrecision
                        precision = it.precisionMetros

                        // Sincronizamos lastLocation para que los cálculos de distancias en 'calculadora'
                        // usen la posición unificada (RTK si está disponible) y no la del GPS del móvil.
                        // Mantener el rumbo (bearing) si ya lo teníamos de una actualización previa del sistema.
                        if (!::lastLocation.isInitialized) {
                            lastLocation = Location("Unified")
                        }
                        lastLocation.latitude = lat
                        lastLocation.longitude = lon
                        lastLocation.accuracy = precision.toFloat()
                        lastLocation.time = it.tiempo
                        
                        // Información de depuración visual en editTextText2
                        val statusUSB = if (it.usbConectado) "USB ok" else "USB fail"
                        val statusIGN = if (it.ntripConectado) "IGN ok" else "IGN wait"
                        
                        val debugInfo = if (campoRTK) {
                            "RTK: ${it.descripcionFix} | Prec: ${String.format("%.2f", precision)}m | $statusUSB | $statusIGN"
                        } else {
                            "GPS Móvil | Prec: ${String.format("%.1f", precision)}m | $statusUSB | $statusIGN"
                        }
                        binding.editTextText2.text = debugInfo

                        // Actualizar la cruz blanca RTK
                        if (it.esAltaPrecision) {
                            if (markerCruzRTK == null) {
                                markerCruzRTK = map?.addMarker(MarkerOptions()
                                    .position(it.coordenadas)
                                    .title("Posición RTK")
                                    .anchor(0.5f, 0.5f)
                                    .icon(BitmapDescriptorFactory.fromBitmap(crearIconoCruzBlanca())))
                            } else {
                                markerCruzRTK?.position = it.coordenadas
                                markerCruzRTK?.isVisible = true
                            }
                        } else {
                            markerCruzRTK?.isVisible = false
                        }

                        // Actualizamos la UI
                        calculadora("Origen: ${it.descripcionFix}")
                        
                        if (sinboyas || !primeraAnimacion) {
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(it.coordenadas, 20f))
                            primeraAnimacion = true
                        }

                        if (it.esAltaPrecision || lastLocation.accuracy < 100) {
                            creaBoyas(lastLocation, sinboyas)
                            if (destruido) restaurar()
                            destruido = false
                        }
                    }
                }
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                val location = locationResult.lastLocation ?: return
                lastLocation = location
                
                // Avisamos al repositorio de la nueva posición del sistema
                locationRepository.actualizarDesdeSistema(
                    location.latitude, 
                    location.longitude, 
                    location.accuracy.toDouble(),
                    false, // USB no detectado por GPS móvil
                    false  // NTRIP no detectado por GPS móvil
                )
            }
        }

       findviews()
       mMenu?.let { menuViews(it) };
       //Se ha destruido
        if (savedInstanceState != null) {
            recuperaDestruido(savedInstanceState)
        }


       val mensajeToast = getString(R.string.recordatorio)
       Toast.makeText(applicationContext, mensajeToast, Toast.LENGTH_LONG).show()
       //connect(this)

   }

    lateinit var notis : Notification
    var mapReady=false
    var currentLatLng :LatLng = LatLng(malaga.latitude,malaga.longitude)

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        setUpMap()
        mapReady=true
        habilitaGPSenmapa()  //Dos intentos. onGPSReady y onMapReady
        //return
        map.setOnCameraMoveListener {
            if(!gps_enabled) {
                val Location = map.getCameraPosition()
                lastLocation=Location("manual")
                lastLocation.latitude=Location.target.latitude
                lastLocation.longitude=Location.target.longitude
            }

        }
    }


    /**
     * Se usa para poner inicialmente las boyas cuando hay señal de GPS
     */
    fun creaBoyas(loc: Location, force: Boolean =false)
    {
        if(!boyas ||force ) {
            if (!setSE) {BoyaSE = Location(loc) ;LLBoyaSE=LatLng(BoyaSE.latitude,BoyaSE.longitude)}   //Clona
            if (!setSB) {BoyaSB = Location(loc) ;LLBoyaSB=LatLng(BoyaSB.latitude,BoyaSB.longitude)}
            if (!setCE) {
                BoyaCE = Location(loc);
                anclaBoyaCE= Location(BoyaCE)
                LLBoyaCE=LatLng(BoyaCE.latitude,BoyaCE.longitude)
            }
            if (!setCB) {
                BoyaCB = Location(loc);
                anclaBoyaCB= Location(BoyaCB)
                LLBoyaCB=LatLng(BoyaCB.latitude,BoyaCB.longitude)}
        }
        boyas=true
    }

    /**
     * Mientra no hay GPS, sitúa las boyas en casa
     */
    fun inicial()
    {
        if(!this::lastLocation.isInitialized) {
            lastLocation = Location("manual")
            lastLocation.latitude = malaga.latitude
            lastLocation.longitude = malaga.longitude
        }
        
        if (!::BoyaSE.isInitialized) BoyaSE = Location(lastLocation)
        if (!::BoyaSB.isInitialized) BoyaSB = Location(lastLocation)
        if (!::BoyaCE.isInitialized) BoyaCE = Location(lastLocation)
        if (!::BoyaCB.isInitialized) BoyaCB = Location(lastLocation)
        
        if (!::LLBoyaSE.isInitialized) LLBoyaSE = LatLng(BoyaSE.latitude, BoyaSE.longitude)
        if (!::LLBoyaSB.isInitialized) LLBoyaSB = LatLng(BoyaSB.latitude, BoyaSB.longitude)
        if (!::LLBoyaCE.isInitialized) LLBoyaCE = LatLng(BoyaCE.latitude, BoyaCE.longitude)
        if (!::LLBoyaCB.isInitialized) LLBoyaCB = LatLng(BoyaCB.latitude, BoyaCB.longitude)
        
        if (!::anclaBoyaSE.isInitialized) anclaBoyaSE = Location(BoyaSE)
        if (!::anclaBoyaSB.isInitialized) anclaBoyaSB = Location(BoyaSB)
        if (!::anclaBoyaCE.isInitialized) anclaBoyaCE = Location(BoyaCE)
        if (!::anclaBoyaCB.isInitialized) anclaBoyaCB = Location(BoyaCB)

    }

    /**
     * Pone en marcha lastLocation.addOnSuccessListener
     */
    fun enmarchagps()
    {
        if(gpsactivo)return
        if (gps_enabled) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                gpsactivo=false
                return
            }
            fusedLocationClient.lastLocation.addOnSuccessListener(this) { location ->
                if (location != null) {
                    lastLocation = location
                    if(mapReady) {
                        map.isMyLocationEnabled =true
                    }
                    startLocationUpdates()
                    if (lastLocation.accuracy < 100)
                        creaBoyas(lastLocation, sinboyas)

                }

            }
            gpsactivo=true
        }

    }


    fun habilitaGPSenmapa()
    {
        if(mapReady&&gpsactivo){
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return
            }
            map.isMyLocationEnabled = true

        }
    }


    private fun setUpMap() {
        markerSE= map.addMarker(MarkerOptions().position(malaga).title("Boya SE").visible(false  ).snippet("Meta (poniente)"))!!
        markerSB= map.addMarker(MarkerOptions().position(malaga).title("Boya SB").visible(false  ).snippet("Meta (levante)"))!!
        markerCE= map.addMarker(MarkerOptions().position(malaga).title("Boya CE").visible(false  ).snippet("Ciaboga (poniente)").rotation(180f))!!
        markerCB= map.addMarker(MarkerOptions().position(malaga).title("Boya CB").visible(false  ).snippet("Ciaboga (levante)").rotation(180f))!!
        markerCE_ideal= map.addMarker(MarkerOptions().position(malaga).title("Boya CE ideal").visible(false  ).snippet("Boya CE ideal").alpha(0.4f).anchor(0.5f, 0.5f))!!
        markerCB_ideal= map.addMarker(MarkerOptions().position(malaga).title("Boya CB ideal").visible(false  ).snippet("Boya CB ideal").alpha(0.4f).anchor(0.5f, 0.5f))!!
        markerCEvalida= map.addMarker(MarkerOptions().position(malaga).title("Boya CE correcta").visible(false  ).snippet("Boya CE correcta").alpha(0.4f).anchor(0.5f, 0.5f))!!
        markerCBvalida= map.addMarker(MarkerOptions().position(malaga).title("Boya CB correcta").visible(false  ).snippet("Boya CB correcta").alpha(0.4f).anchor(0.5f, 0.5f))!!

        markerSE.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        markerSB.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        markerCE.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        markerCB.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        markerCE_ideal.setIcon(circuloAD?.let { BitmapDescriptorFactory.fromBitmap(it) }) //)
        markerCB_ideal.setIcon(circuloBD?.let { BitmapDescriptorFactory.fromBitmap(it) })
        markerCEvalida.setIcon(circuloAB?.let { BitmapDescriptorFactory.fromBitmap(it) })
        markerCBvalida.setIcon(circuloBA?.let { BitmapDescriptorFactory.fromBitmap(it) })

        val anchorIcon = bitmapDescriptorFromVector(this, R.drawable.ic_anchor)
        markerAnclaSE = map.addMarker(MarkerOptions().position(malaga).title("Ancla SE").visible(false).icon(anchorIcon).anchor(0.5f, 0.5f))!!
        markerAnclaSB = map.addMarker(MarkerOptions().position(malaga).title("Ancla SB").visible(false).icon(anchorIcon).anchor(0.5f, 0.5f))!!
        markerAnclaCE = map.addMarker(MarkerOptions().position(malaga).title("Ancla CE").visible(false).icon(anchorIcon).anchor(0.5f, 0.5f))!!
        markerAnclaCB = map.addMarker(MarkerOptions().position(malaga).title("Ancla CB").visible(false).icon(anchorIcon).anchor(0.5f, 0.5f))!!

        marcadores = true

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(malaga, 19f))
        enmarchagps()
        creaBoyas(lastLocation, true) //Aqui debería ser malaga
        //currentLatLng = LatLng(lastLocation.latitude, lastLocation.longitude)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 20f))

        map.mapType = GoogleMap.MAP_TYPE_SATELLITE
        val options = GoogleMapOptions()
        options.mapType(GoogleMap.MAP_TYPE_SATELLITE)
            .compassEnabled(true)


        map.setInfoWindowAdapter(object : InfoWindowAdapter {
            override fun getInfoWindow(arg0: Marker): View? {
                return null
            }
            override fun getInfoContents(marker: Marker): View? {
                var mContext= applicationContext
                val info = LinearLayout(mContext)
                info.orientation = LinearLayout.VERTICAL
                val title = TextView(mContext)
                title.setTextColor(Color.BLACK)
                title.gravity = Gravity.CENTER
                title.setTypeface(null, Typeface.BOLD)
                title.text = marker.title
                val snippet = TextView(mContext)
                snippet.setTextColor(Color.GRAY)
                snippet.text = marker.snippet
                info.addView(title)
                info.addView(snippet)
                return info
            }


        })
        mapReady=true

    }




    override fun onPause() {
        super.onPause()
        Log.d(TAGVIDA,"Estoy en onPause")
        // Siempre intentamos detener el servicio USB al pausar para liberar recursos
        ntripManager.detener()

        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAGVIDA,"Estoy en onResume")
        // Siempre intentamos conectar al USB al volver; si no hay placa, simplemente no hará nada
        ntripManager.conectar()

        // if (gpsactivo)
        startLocationUpdates()
        conectar()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Si el Intent es porque se ha conectado un USB mientras la app estaba abierta
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == intent.action) {
            Log.d("USB_DETECTOR", "USB enchufado dinámicamente")
            ntripManager.conectar()
        }
    }

    //@SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if(!gps_enabled)return;
        //if((set1||set2||set3||set4))return
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest,
            locationCallback,
            Looper.getMainLooper())
    }


    fun actualizaFrecuenciaGPS(nuevosMilisegundos: Long) {
        if (!::fusedLocationClient.isInitialized || !::locationCallback.isInitialized) {
            Log.e("GPS_Update", "FusedLocationProviderClient o LocationCallback no inicializados.")
            return
        }

        Log.d("GPS_Update", "Intentando actualizar frecuencia GPS a: $nuevosMilisegundos ms")

        // 1. Detener las actualizaciones de ubicación actuales
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("GPS_Update", "Actualizaciones de ubicación detenidas.")
        } catch (e: Exception) {
            // Aunque removeLocationUpdates no suele lanzar SecurityException,
            // es bueno ser cauteloso o loguear otros posibles errores.
            Log.e("GPS_Update", "Error al detener actualizaciones de ubicación: ${e.message}")
        }

        // 2. Crear una NUEVA instancia de LocationRequest con los nuevos parámetros
        //    y guardarla para referencia si es necesario.
        var currentLocationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, nuevosMilisegundos).apply {
            // Puedes añadir otras configuraciones que quieras mantener consistentes aquí,
            // por ejemplo, el intervalo mínimo.
            // Si el intervalo mínimo debe ser una fracción del intervalo principal:
            val minIntervalo = if (nuevosMilisegundos > 1000L) nuevosMilisegundos / 2 else nuevosMilisegundos
            setMinUpdateIntervalMillis(minIntervalo)
            // setWaitForAccurateLocation(false) // si es necesario
            // setMinUpdateDistanceMeters(10f) // si es necesario
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                currentLocationRequest, // Usa la nueva instancia
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("GPS_Update", "Actualizaciones de ubicación reiniciadas con nueva frecuencia.")
        } catch (e: SecurityException) {
            // Esta excepción es importante de manejar si, por alguna razón,
            // los permisos se revocan entre la comprobación y la llamada.
            Log.e("GPS_Update", "SecurityException al reiniciar actualizaciones de ubicación: ${e.message}")
            // Considera solicitar permisos de nuevo o informar al usuario.
        } catch (e: Exception) {
            Log.e("GPS_Update", "Error al reiniciar actualizaciones de ubicación: ${e.message}")
        }
    }


    override fun onDestroy() {
        disconnect()
        destruido=true
        backup()
        
        // Detener el servicio MQTT para evitar DeadObjectException al intentar callbacks
        try {
            val serviceIntent = Intent(this, MiServicioMqtt::class.java)
            stopService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAGVIDA, "Error al detener el servicio MQTT", e)
        }

        Log.d(TAGVIDA,"Estoy en onDestroy")
        super.onDestroy()
    }

    //********************************************************************************
    //                              MQTT
    //********************************************************************************


    // https://www.emqx.com/en/blog/android-connects-mqtt-using-kotlin

    companion object {  //Creo que es equivalente a static
        const val TAGMQTT = "Regatas_MqttClient"
        const val TAGVIDA = "Regatas_Ciclo"
    }

    fun unsubscribe(topic: String) {
        try {
            mqttClient?.unsubscribe(topic, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAGMQTT, "Unsubscribed to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAGMQTT, "Failed to unsubscribe $topic")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect(null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAGMQTT, "Disconnected")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAGMQTT, "Failed to disconnect")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun subscribe(topic: String, qos: Int = 0) {
        try {
            mqttClient?.subscribe(topic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAGMQTT, "Subscribed to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAGMQTT, "Failed to subscribe $topic")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    fun publish(topic: String, msg: String, qos: Int = 0, retained: Boolean = false) {
        try {
            val message = MqttMessage()
            message.payload = msg.toByteArray()
            message.qos = qos
            message.isRetained = retained
            mqttClient?.publish(topic, message, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAGMQTT, "$msg published to $topic")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.d(TAGMQTT, "Failed to publish $msg to $topic")
                }
            })
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    val mqttOptions = MqttConnectOptions()

    class MyMqttCallback(private val activity: MapsActivity, private val context: Context) : MqttCallback {

        fun nombreBoya(i:Int):String{
            if(i==BOYA_SE)
                return "Salida calle 5"
            if(i==BOYA_SB)
                return "Salida calle 1"
            if(i==BOYA_CE)
                return "Ciaboga calle 5"
            if(i==BOYA_CB)
                return "Ciaboga calle 1"
            return "Boya ?"


        }


        fun parseJson(jsonA: JSONArray, context: Context, activity : MapsActivity){

                    for( i in 0 until jsonA.length()) {
                        var json: JSONObject = jsonA.getJSONObject(i)
                        val boya = json.getInt("boya")
                        val coordenadas = json.getJSONObject("coordenadas")
                        val latitud = coordenadas.getDouble("lat")
                        val longitud = coordenadas.getDouble("lon")
                        //val promedio = if (!coordenadas.isNull("promedio")) coordenadas.getBoolean("tiempoPromedio") else 0;
                        val texto =
                            "Boya " + nombreBoya(boya) + " actualizada:\n(N" + latitud.toString() + ", W" + (-longitud).toString()
                        if(boya==1&&activity.lockSE)continue;
                        if(boya==2&&activity.lockSB)continue;
                        if(boya==3&&activity.lockCE)continue;
                        if(boya==4&&activity.lockCB)continue;
                        activity.runOnUiThread(Runnable {
                            var x: LatLng=LatLng(latitud,longitud)
                            activity.marcandoBoya(boya,x)
                            activity.calculadora("Remota")
                            Toast.makeText(context, texto, Toast.LENGTH_LONG).show()
                        })
                    }

        }



        override fun messageArrived(topic: String?, message: MqttMessage?) {
            Log.d(TAGMQTT, "Receive message: ${message.toString()} from topic: $topic")
            // {boya:1,coordenadas:{lat:36.717583,lon:-4.36260}}
            // topic propuesta android/regatas
            val json = JSONArray(message.toString())
            if(activity::menuMQTT.isInitialized && activity.menuMQTT.isChecked)
                parseJson(json,context,activity)
        }

        override fun connectionLost(cause: Throwable?) {
            Log.d(TAGMQTT, "Connection lost ${cause.toString()}")
            //activity.disconnect()
            //connect(applicationContext)
            //connect()

        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {

        }

    }

    fun mqtt(context:Context)    {

        serverURI= BuildConfig.MQTT_HOST
        mqttClient= MqttAndroidClient(context, serverURI, MqttAsyncClient.generateClientId() ).apply{
            //setForegroundService(notis)
        }
        return

        //**** Borrar cuando vaya bien
        mqttClient?.setCallback(object : MqttCallback {
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d(TAGMQTT, "Receive message: ${message.toString()} from topic: $topic")
                // {boya:1,coordenadas:{lat:36.717583,lon:-4.36260}}
                // topic propuesta android/regata/campo
                val json = JSONObject(message.toString())
                val boya = json.getInt("boya")
                val coordenadas=json.getJSONObject("coordenadas")
                val latitud=coordenadas.getDouble("lat")
                val longitud=coordenadas.getDouble("lon")
                if(true)
                    this@MapsActivity.runOnUiThread(Runnable {
                        lastLocation.latitude=latitud
                        lastLocation.longitude=longitud
                        marcandoBoya(boya, LatLng(lastLocation.latitude, lastLocation.longitude))

                        Toast.makeText(
                            getApplicationContext(),
                            "Boya "+boya.toString()+" actualizada:\n(N"+latitud.toString()+", W"+(-longitud).toString(),
                            Toast.LENGTH_LONG
                        ).show()
                    })
                //Toast.makeText(this@MapsActivity,,Toast.LENGTH_LONG).show()
            }

            override fun connectionLost(cause: Throwable?) {
                Log.d(TAGMQTT, "Connection lost ${cause.toString()}")
                //connect(applicationContext)
                //connect()

            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {

            }
        })

    }

    var serverURI = ""

    val MQTT_TOPIC="android/regatas"
    val MQTT_TOPIC2="art/android/boyas"

    val conexionCB: IMqttActionListener = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            Log.d(TAGMQTT, "Connection success")
            subscribe(MQTT_TOPIC2, 0)
        }
        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.d(TAGMQTT, "Connection failure")
            if (mqttClient!!.isConnected) subscribe(MQTT_TOPIC2, 0)
        }
        }

    fun conectar() {
        try {
            if(true) {
                Log.d(TAGMQTT,"Iniciando intento de conexión")
                if(mqttClient==null)return
                Log.d(TAGMQTT,"Hay ya un objeto")
                if(mqttClient!!.isConnected)  {
                    Log.d(TAGMQTT,"...y está conectado")
                    return
                }
                Log.d(TAGMQTT,"...y no está conectado")
                Log.d(TAGMQTT,"Intentando conectar a: $serverURI")
                mqttClient?.setCallback(MyMqttCallback(this,applicationContext))
                mqttOptions.userName=BuildConfig.MQTT_USER
                mqttOptions.password=BuildConfig.MQTT_PASSWORD.toCharArray()
                mqttOptions.isAutomaticReconnect=true

                // Soporte para SSL/TLS (Puerto 8883)
                if (serverURI.startsWith("ssl://")) {
                    try {
                        val sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2")
                        sslContext.init(null, null, null)
                        mqttOptions.socketFactory = sslContext.socketFactory
                        // mqttOptions.isHttpsHostnameVerificationEnabled = false
                    } catch (e: Exception) {
                        Log.e(TAGMQTT, "Error al configurar SSL", e)
                    }
                }

                var token = mqttClient?.connect(mqttOptions)
                token?.actionCallback=conexionCB
                }
            else
                mqttClient?.connect(mqttOptions, null, conexionCB)
        } catch (e: MqttException) {
            e.printStackTrace()
        }

    }

    fun enviaPrueba()
    {
        val body="[{\n" +
                "\t\"boya\": 1,\n" +
                "\t\"coordenadas\": {\n" +
                "\t\t\"lat\": 36.7174,\n" +
                "\t\t\"lon\": -4.3627\n" +
                "\t}\n" +
                "},{\n" +
                "\t\"boya\": 2,\n" +
                "\t\"coordenadas\": {\n" +
                "\t\t\"lat\": 36.7174,\n" +
                "\t\t\"lon\": -4.3607\n" +
                "\t}\n" +
                "},\n" +
                "{\n" +
                "\t\"boya\": 3,\n" +
                "\t\"coordenadas\": {\n" +
                "\t\t\"lat\": 36.7144,\n" +
                "\t\t\"lon\": -4.3627\n" +
                "\t}\n" +
                "},{\n" +
                "\t\"boya\": 4,\n" +
                "\t\"coordenadas\": {\n" +
                "\t\t\"lat\": 36.7144,\n" +
                "\t\t\"lon\": -4.3607\n" +
                "\t}\n" +
                "}]\n"
    publish(MQTT_TOPIC,body)
    }


}

