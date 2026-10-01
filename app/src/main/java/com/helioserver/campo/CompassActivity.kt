package com.helioserver.campo

//import androidx.appcompat.app.AppCompatActivity

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface.OnShowListener
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.text.TextPaint
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import java.lang.Integer.max
import java.lang.Integer.min
import java.lang.String.format
import java.util.Locale
import kotlin.math.floor
import kotlin.math.round


class CompassActivity : AppCompatActivity() {


    private lateinit var sensorManager: SensorManager
    private lateinit var rumboG_T: TextView
    private lateinit var rumboM_T: TextView
    private lateinit var valorBrujulaT: TextView
    private lateinit var valorBrujulaIT: TextView
    private lateinit  var valores : TextView
    private lateinit  var ayuda : TextView

    var rumbo21=2.0
    var rumbo43=2.0
    var imagen=true
    var magneticas=true

    lateinit var fondo:Lienzo
    lateinit var fondo2:Lienzo2

    lateinit var texto21M : String
    lateinit var texto21G : String
    lateinit var texto43M : String
    lateinit var texto43G : String
    var dec=4.5 //declinacion magnetica


    var buffer10 : DoubleArray = DoubleArray(10)
    var buffer30 : DoubleArray = DoubleArray(30)
    var clicks : DoubleArray = DoubleArray(30)
    var ultima=0.0

    var suavizada=0.0
    var superSuavizada=0.0

    var width=0
    var height=0

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun mostrarAyuda(context: Context, textoAyuda: CharSequence) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Ayuda")
        builder.setMessage(textoAyuda)
        builder.setPositiveButton("Entendido", null)

        val dialog = builder.create()
        dialog.setOnShowListener(OnShowListener {
            val btnPositive: Button = dialog.getButton(Dialog.BUTTON_POSITIVE)
            btnPositive.textSize = 26f
        })

        dialog.show()
    }
    var textoAyuda=     "IMPORTANTE: No necesitas mirar la pantalla para tomar medidas de rumbo: Pon el móvil en posición horizontal, a la altura de los ojos, y alinea " +
            "un lateral del móvil con el objetivo. \n\n Toca la pantalla para capturar una medida. Repítelo varias veces hasta conseguir tres o cuatro mediciones con un mismo valor"+

    "\n\nEn Málaga, para obtener el rumbo magnético debes sumar 4.5º al calculado con GPS (rumbo geográfico).\n"+
            "\nPor otra parte, un error de 1º en el rumbo de las dos líneas de boya supone, para el campo de regatas de la ART, un error"+
            "   total de 5 m, por lo que debe intentar que las medidas de rumbo de cada línea tenga menos de 1º de error"


    fun sensor(intent: Intent, sensorManager: SensorManager)
    {
        //com.helioserver.campo.sensorManager = getSystemService(ComponentActivity.SENSOR_SERVICE) as SensorManager

        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { accelerometer ->
            sensorManager.registerListener(mSensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)?.also { magneticField ->
            sensorManager.registerListener(mSensorEventListener, magneticField, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI)
        }
        //val intent = Intent(this, BrujulaActivity::class.java)



    }


    var cnt10=0
    fun suaviza(medida: Double) :Double{
        buffer10[cnt10]=medida
        cnt10=(cnt10+1)%10
        var suma=0.0
        for(i in 0..9)
            suma +=buffer10[i]
        return round(suma)/10
    }
    var cnt30=0
    fun suaviza30(medida: Double) :Double{
        buffer30[cnt30]=medida
        cnt30=(cnt30+1)%30
        var suma=0.0
        for(i in 0..29)
            suma +=buffer30[i]
        return round(suma)/30
    }

    fun updateOrientationAngles() {
        // 1
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        // 2
        val orientation = SensorManager.getOrientation(rotationMatrix, orientationAngles)
        // 3
        val decM = if(magneticas)0.0 else -dec;

        val dato= Math.toDegrees(orientation[0].toDouble()) + decM
        val medida = (dato+ 360.0) % 360.0
        // 4

        suavizada=suaviza(medida)
        superSuavizada=suaviza30(medida)
        ultima = round(medida * 10) / 10

        //var  s = "%05.1f".format(suavizada)+"º"
       // valorBrujulaT.setText(s.replace(',','.'))


        fondo.invalidate()
    }

    private val mSensorEventListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                //if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                // Do work
            } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
            updateOrientationAngles()
        }

        override fun onAccuracyChanged(sensor: Sensor, i: Int) {}
    }


    var escalaV=1f
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val displayMetrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = display
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            width = bounds.width()
            height = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            width = displayMetrics.widthPixels
            height = displayMetrics.heightPixels
        }

        escalaV = height/2088f
        setContentView(R.layout.activity_compass)
        this.supportActionBar?.hide()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)


        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rumbo21=intent.getDoubleExtra("rumbo21",1.0)
        rumbo43=intent.getDoubleExtra("rumbo43",1.0)


        var opos21=(rumbo21+540.0)%360
        var opos43=(rumbo43+540.0)%360

        texto21G=rumbo21.toString() + "º (" +"%05.1f".format(opos21)+"º)"
        texto43G=rumbo43.toString() + "º (" +"%05.1f".format(opos43)+"º)"

        /*
        rumboG_T=findViewById(R.id.rumboG)
        rumboG_T.setText(
            "Rumbo GPS boyas 2 y 1: " + rumbo21.toString() + "º (" +"%05.1f".format(opos21)+
                    "º)\n" +
                    "Rumbo GPS boyas B y A: " + rumbo43.toString() + "º (" +"%05.1f".format(opos43)+
                    "º)\n"
        )
        */

        opos21=(rumbo21+dec+540)%360
        opos43=(rumbo43+dec+540)%360
        texto21M=(dec+rumbo21).toString() + "º (" +"%05.1f".format(opos21)+"º)"
        texto43M=(dec+rumbo43).toString() + "º (" +"%05.1f".format(opos43)+"º)"
        /*
        rumboM_T=findViewById(R.id.rumboM)
        rumboM_T.setText("Rumbo magnético, boyas 1 y 2: "+(rumbo21+4.5).toString()+"º ("+"%05.1f".format(opos21)+"º)\n"+
                "Rumbo magnético, boyas A y B: "+(rumbo43+4.5).toString()+"º ("+"%05.1f".format(opos43)+"º)")
        */
        //valorBrujulaT=findViewById(R.id.valorBrujula)
        //valores=findViewById(R.id.valores)
        sensor(intent, sensorManager)
        mostrarAyuda(this,textoAyuda)
        //valores.movementMethod = ScrollingMovementMethod()


        fondo = Lienzo(this)
        fondo2 = Lienzo2(this)
        val layout1 :ConstraintLayout=findViewById(R.id.layout1)
        layout1.addView(fondo)
        layout1.addView(fondo2)



        // Get radio group selected item using on checked change listener
        val radio_group : RadioGroup
        radio_group=findViewById<RadioGroup>(R.id.grupo)
        radio_group.setOnCheckedChangeListener(
            RadioGroup.OnCheckedChangeListener { group, checkedId ->
                val radio: RadioButton = findViewById(checkedId)
                magneticas= radio == findViewById(R.id.radioButtonM)
                fondo2.invalidate()
            })

    }

    var cntClicks=0
    var idxClick=0
    fun guardaclicks(medida: Double){
        var n = clicks.size
        cntClicks=cntClicks+1
        clicks[idxClick]=medida
        idxClick=(idxClick+1)%n
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        val x = event.x
        val y = event.y
        if(imagen){
            imagen=false
        }
        fondo2.invalidate()
        if(tiempo()){
            var opos=(ultima+540.0)%360
            guardaclicks(ultima)


            /*
            var linea= "%05.1f".format(ultima) +"º ("+"%05.1f".format(opos)+"º)"
            var t=valores.text.toString()
            if(t.equals(""))
                valores.setText("Clicks:\n")
            valores.setText(valores.text.toString() + linea+"\n")
            */
        }
        return false
    }





    /**
     * Para evitar que dos toques muy seguidos llenen el buffer
     */
    var tiempo = System.nanoTime()
    private fun tiempo(): Boolean {
        var oldtiempo= tiempo
        var newtiempo=System.nanoTime()
        if(newtiempo-oldtiempo>500 * 1_000_000)
        {
            tiempo=newtiempo
            return true
        }
        return false
    }

        inner class Lienzo2(context: Context) : View(context) {
            val rojo: Paint = Paint()
            val verde: Paint = Paint()
            val negro: Paint = Paint()
            val negro2: Paint = Paint()
            val negro4: Paint = Paint()
            var YD=0f
            var YI=0f
            var ancho=0f
            var margen=10f
            var alto=0f
            //var escalaV=1f
            var textPaint = TextPaint()
            init{
                //escalaV=height/2088f
                //negro.color = Color.BLACK
                negro.strokeWidth = 2f
                //negro2.color = Color.BLACK
                negro2.strokeWidth = 4f
                //negro4.color = Color.BLACK
                negro4.strokeWidth = 8f
                rojo.strokeWidth = 3f
                verde.strokeWidth = 3f
                negro.textSize=40f*escalaV
                rojo.textSize=40f*escalaV
                verde.textSize=40f*escalaV
                textPaint.textSize=40f*escalaV
                textPaint.typeface = Typeface.MONOSPACE


                var configuration=this.resources.configuration
                val currentNightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                when (currentNightMode) {
                    Configuration.UI_MODE_NIGHT_NO -> {
                        rojo.color = Color.rgb(150,0,0)
                        verde.color = Color.rgb(0,150,0)

                        negro.color = Color.BLACK
                        negro2.color = Color.BLACK
                        negro4.color = Color.BLACK
                        textPaint.color=Color.BLACK
                    } // Night mode is not active, we're using the light theme.
                    Configuration.UI_MODE_NIGHT_YES -> {
                        rojo.color = Color.rgb(255,0,0)
                        verde.color = Color.rgb(0,255,0)

                        negro.color = Color.WHITE
                        negro2.color = Color.WHITE
                        negro4.color = Color.WHITE
                        textPaint.color=Color.WHITE
                    } // Night mode is active, we're using dark theme.
                }
            }
            fun dibujaBase(canvas:Canvas)
            {

                var startX = margen
                var stopX = ancho-margen
                var m=ancho/2

                canvas.drawLine(startX, YD, stopX, YD, negro4) //Horizontales, brujula superior
                canvas.drawLine(startX, YI, stopX, YI, negro4)
                canvas.drawLine(startX, YD-0.06f*alto, stopX, YD-0.06f*alto, negro4)
                canvas.drawLine(startX, YI+0.06f*alto, stopX, YI+0.06f*alto, negro4)

                canvas.drawLine(m, YD, m, YD+25, negro4)
                canvas.drawLine(m, YI, m, YI-25, negro4)
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                var x= m-negro.measureText("Rumbos según GPS")/2
                var h = canvas.height

                var yesquema=h*0.08f
                if(isLandscape)yesquema=0.1f
                canvas.drawText("Rumbos según GPS",x,yesquema,negro)

                var texto21= if(magneticas)texto21M else texto21G
                var texto43= if(magneticas)texto43M else texto43G
                canvas.drawText("salida",m-verde.measureText("salida")/2 ,h*0.32f,verde)
                canvas.drawText(texto21,m-verde.measureText(texto21)/2 ,h*0.30f,verde)
                canvas.drawText("ciaboga",m-rojo.measureText("ciaboga")/2 ,h*0.14f,rojo)
                canvas.drawText(texto43,m-rojo.measureText(texto43)/2 ,h*0.12f,rojo)

                canvas.drawLine(m,h*0.18f , m, h*0.28f, negro)
                canvas.drawLine(m*0.80f,h*0.14f , m*0.80f, h*0.28f, negro)
                canvas.drawLine(m*1.20f,h*0.14f , m*1.20f, h*0.28f, negro)
                canvas.drawLine(m*0.60f,h*0.11f , m*0.60f, h*0.32f, verde)
                canvas.drawLine(m*1.40f,h*0.11f , m*1.40f, h*0.32f, rojo)

                if(imagen) {
                    var rect2 = Rect(0, 5 * alto.toInt() / 7, ancho.toInt() - 1, alto.toInt() - 1)
                    val assets = getAssets()
                    // Abrir el archivo de imagen
                    val inputStream = assets.open("compass.png")
                    // Crear una imagen a partir de la entrada
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    var rect1 = Rect(0, 0, bmp.width.toInt() - 1, bmp.height.toInt() - 1)
                    if(isLandscape)
                    {
                        rect2 = Rect(ancho.toInt()/3, 5 * alto.toInt() / 7, 2*ancho.toInt()/3, alto.toInt() - 1)
                    }
                    canvas.drawBitmap(bmp, rect1, rect2, null)
                }
            }

            fun pintaclicks(canvas:Canvas)
            {
                var texto: String="00: 000.0º (000.0º)"
                var x=textPaint.measureText(texto)/2f
                if(cntClicks==0)return
                canvas.drawText("Clicks:",ancho/2f-x,
                    YI+0.095f*alto,textPaint)

                var max=15
                var numClicksVistos=min(max,cntClicks)
                for(i in 0..numClicksVistos-1)
                {
                    var indice=i + max(0,cntClicks-max)
                    var v=clicks[indice%clicks.size]
                    var tmp1=format("%05.1f",v)
                    var tmp2=format("%05.1f",(v+180f)%360)
                    texto=format("%02d",indice+1)+": "+tmp1+"º ("+tmp2+"º)"
                    canvas.drawText(texto,ancho/2f-x,YI+0.1f*alto+(1+i)*textPaint.textSize,textPaint)
                }
            }

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                alto=canvas.height.toFloat()
                YD = alto*0.4f
                YI = alto*0.6f
                ancho=canvas.width.toFloat()
                dibujaBase(canvas)
                pintaclicks(canvas)

            }
        }

        inner class Lienzo(context: Context) : View(context) {
        val rojo: Paint = Paint()
        val verde: Paint = Paint()
        val negro: Paint = Paint()
        val negro2: Paint = Paint()
        val negro4: Paint = Paint()
        var textPaint = TextPaint()


        var YD=0f
        var YI=0f
        var ancho=0f
        var margen=10f
        var alto=0f
        //var escalaV=1f

            init{
                //escalaV=height/2088f

                //negro.color = Color.BLACK
           negro.strokeWidth = 2f
           //negro2.color = Color.BLACK
           negro2.strokeWidth = 4f
           negro4.color = Color.BLACK
           negro4.strokeWidth = 8f
           rojo.color = Color.rgb(150,0,0)
           rojo.strokeWidth = 5f
           verde.color = Color.rgb(0,150,0)
           verde.strokeWidth = 5f
           negro.textSize=50f*escalaV
           negro2.textSize=100f*escalaV
           rojo.textSize=30f*escalaV
           verde.textSize=30f*escalaV
           textPaint.typeface = Typeface.MONOSPACE


                var configuration=this.resources.configuration
           val currentNightMode = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
           when (currentNightMode) {
               Configuration.UI_MODE_NIGHT_NO -> {
                   negro.color = Color.BLACK
                   negro2.color = Color.BLACK
                   negro4.color = Color.BLACK
                   textPaint.color=Color.BLACK
               } // Night mode is not active, we're using the light theme.
               Configuration.UI_MODE_NIGHT_YES -> {
                   negro.color = Color.WHITE
                   negro2.color = Color.WHITE
                   negro4.color = Color.WHITE
                   textPaint.color=Color.WHITE
               } // Night mode is active, we're using dark theme.
           }

       }

        fun rumbo(grados: String): String
        {
            if(grados=="000")return "N"
            if(grados=="180")return "S"
            if(grados=="090")return "E"
            if(grados=="270")return "W"
            if(grados=="045")return "NE"
            if(grados=="135")return "SE"
            if(grados=="225")return "SW"
            if(grados=="315")return "NW"
            return grados
        }
        fun dibujaDinamico(canvas: Canvas){
            var local:Locale= Locale.getDefault()
            var brujula=(superSuavizada).toFloat()
            var decm=if(magneticas) dec else 0.0
            var raya12=rumbo21+decm
            var rayaAB=rumbo43+decm
            var r12_360=(floor(raya12).toInt()+360)%360
            var rAB_360=(floor(rayaAB).toInt()+360)%360
            // Valores de la brujula

            textPaint.textSize=70f*escalaV
            var datoTexto=format(local,"%05.1f",(suavizada+180)%360).replace(',','.')+"º"
            var x=textPaint.measureText(datoTexto).toFloat()/2
            canvas.drawText(datoTexto,ancho/2f-x,YI-negro2.textSize,textPaint)


            textPaint.textSize=170f*escalaV
            datoTexto=format(local,"%05.1f",suavizada).replace(',','.')+"º"
            x=textPaint.measureText(datoTexto).toFloat()/2
            canvas.drawText(datoTexto,ancho/2f-x,YD+0.08f*alto,textPaint)


            //Bandas
            for (i in -360..540)
            {
                // Cuando i == brujula, xd = ancho/2
                // xd = ancho/2 +(i-brujula*escala)


                var i360=(i+360)%360
                var i180=(i+540)%360
                var textD=format("%03d",(i+360)%360)
                var textI=format("%03d",(i+540)%360)
                var xd=i*30f+brujula-100f
                //val escalai=25f
                val escala=25f
                xd = ancho/2 +(i-brujula)*escala
                var xi=i*30f-brujula-100f
                xi = ancho/2 +(brujula-i)*escala
                if(i%5==0) {
                    textD=rumbo(textD)
                    textI=rumbo(textI)
                    var fs=negro.textSize
                    negro.textSize=fs+(3-textD.length)*fs/5
                    var ts=negro.measureText(textD)/2 // Medio texto
                    canvas.drawText(textD, xd-ts, YD - 55f*escalaV, negro)
                    ts=negro.measureText(textI)/2
                    canvas.drawText(textI, xi-ts, YI + 100f*escalaV, negro)
                    negro.textSize=fs
                    canvas.drawLine(xd, YD, xd, YD - 50, negro2)
                    canvas.drawLine(xi, YI, xi, YI + 50, negro2)
                }
                if(i360==r12_360||i180==r12_360)
                {
                    var decimales=(raya12-floor(raya12)).toFloat()
                    var xr=ancho/2 +(i-brujula)*escala +decimales*escala
                    canvas.drawLine(xr,YD, xr, YD + 60, rojo)
                    xr=ancho/2 +(brujula- i)*escala - decimales*escala
                    canvas.drawLine(xr,YI, xr, YI - 60, rojo)
                }
                if(i360==rAB_360||i180==rAB_360)
                {
                    var decimales=(rayaAB-floor(rayaAB)).toFloat()
                    var xr=ancho/2 +(i-brujula)*escala  + decimales*escala
                    canvas.drawLine(xr,YD, xr, YD + 60, verde)
                    xr=ancho/2 +(brujula- i)*escala - decimales*escala
                    canvas.drawLine(xr,YI, xr, YI - 60, verde)
                }


                canvas.drawLine(xd, YD, xd, YD - 20, negro)
                canvas.drawLine(xi, YI, xi, YI + 20, negro)

            }

        }


        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            ancho=canvas.width.toFloat()
            alto=canvas.height.toFloat()
            YD = alto*0.4f
            YI = alto*0.6f
            //escalaV=alto/2088f //respecto a pixel3

            //dibujaBase(canvas)
            dibujaDinamico(canvas)
        }
    }



}

