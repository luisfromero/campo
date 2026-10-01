# ⛵ Campo de Regatas ART (Alta Precisión / RTK)

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![GNSS RTK](https://img.shields.io/badge/GNSS-u--blox%20ZED--F9P%20%7C%20RTK-orange.svg)](https://www.ardusimple.com/)
[![NTRIP](https://img.shields.io/badge/NTRIP-2.0%20(IGN%20ERGNSS)-lightgrey.svg)](https://www.ign.es/)
[![MQTT](https://img.shields.io/badge/Protocol-MQTT%20%7C%20SSL%20%7C%20AES-brightgreen.svg)](https://mqtt.org/)

**Campo de Regatas ART** es una aplicación móvil profesional para Android diseñada específicamente para asistir a jueces, organizadores y tripulaciones de embarcaciones de balizamiento en el **trazado, alineación y medición de precisión de campos de regata rectangulares de 350 metros** (utilizados en regatas de barcas de jábega y remo tradicional/olímpico).

Combina navegación GPS/RTK de precisión centimétrica, brújula de marcación háptica, sincronización de boyas remotas por MQTT y mapas satelitales interactivos.

---

## 🚀 Características Principales

### 🎯 1. Posicionamiento GNSS RTK Centimétrico
* **Soporte de Hardware RTK USB**: Conexión plug-and-play mediante USB OTG con placas GNSS de alta precisión (como **Ardusimple RTK2B** basada en el chip **u-blox ZED-F9P**).
* **Cliente NTRIP v2.0 Integrado**: Conexión directa a redes de corrección diferencial (como el **IGN ERGNSS / SPTR**) para lograr posicionamiento **RTK Fijo (centimétrico)** o **RTK Flotante (decimétrico)**.
* **Procesamiento NMEA y UBX**: Parsea tramas NMEA (`$GxGGA`, `$GxGST`) en tiempo real para extraer la desviación estándar horizontal real en metros, superando las estimaciones aproximadas basadas en HDOP.

### 📐 2. Geometría y Cálculo del Campo de Regata
* **Cálculo de Boyas Ideales**: A partir de dos boyas base fijadas en la línea de salida/meta (350 metros), calcula matemáticamente la ubicación teórica perfecta para las boyas de la línea de ciaboga.
* **Guiado en Tiempo Real**: Muestra el rumbo e indicación de desvío relativo (a estribor/babor) y distancia restante hasta alcanzar la posición exacta de cada baliza desde la embarcación de balizamiento.
* **Análisis de Sesgo y Calles**: Verifica la distancia media del campo, el paralelismo de la línea de ciaboga respecto a la de salida y la longitud de cada calle individual (calle 1 a calle 5).
* **Herramientas de Ajuste**: Permite intercambiar boyas invertidas, bloquear posiciones para evitar sobrescrituras accidentales y guardar/restaurar el estado del campo.

### 📡 3. Sincronización IoT de Boyas Remotas (MQTT)
* **Red de Balizamiento Colaborativa**: Conexión a servidor MQTT bajo TLS/SSL (puerto 8883) que permite compartir la posición exacta de las boyas en tiempo real entre múltiples barcos de apoyo, jueces o balizadores.
* **Cifrado AES-256**: Comunicaciones seguras para la transmisión de coordenadas y estado de balizas.

### 🧭 4. Módulo de Brújula de Marcación
* **Medición Ciega y Háptica**: Diseñada para tomar rumbos desde embarcaciones en movimiento sin necesidad de mirar la pantalla. Permite apuntar con el borde del teléfono hacia la baliza y pulsar en cualquier punto de la pantalla para registrar el rumbo.
* **Visualización de Recíprocos**: Calcula automáticamente rumbos inversos e integra la orientación de los sensores del móvil con las coordenadas GPS/RTK.

### 🗺️ 5. Visualización e Informes
* **Mapa Satelital Interactivo**: Integración con Google Maps para mostrar la ubicación de la embarcación, el punto de máxima precisión RTK (cruz blanca), balizas reales, balizas ideales y anclas.
* **Informes Rápidos**: Generación de resúmenes de geometría de campo listos para copiar al portapapeles y enviar vía WhatsApp u otras aplicaciones.

---

## 🛠️ Arquitectura y Tecnologías

| Componente | Tecnología / Librería |
| :--- | :--- |
| **Lenguaje** | [Kotlin](https://kotlinlang.org/) (JVM 21 / Java 21) |
| **UI Framework** | Android Views (ViewBinding) + [Jetpack Compose](https://developer.android.com/jetpack/compose) |
| **Comunicación Serie USB** | [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) (115200 baudios) |
| **Cliente NTRIP** | Implementación propia Socket HTTP/1.1 NTRIP v2.0 (reenvío RTCM por puerto serie) |
| **Protocolo MQTT** | [Paho MQTT Android Client](https://github.com/hannesa2/paho.mqtt.android) con soporte SSL/TLS y AES-256 |
| **Ubicación y Mapas** | Google Maps SDK, Play Services Location, Google Maps Utils |

---

## 🔌 Configuración del Hardware GNSS RTK

1. Conecta la placa **Ardusimple RTK2B** (u-blox ZED-F9P) al dispositivo Android mediante un adaptador **USB OTG**.
2. Al abrir la aplicación o conectar el USB, la app solicitará permiso para acceder al puerto serie.
3. La aplicación abre el puerto a `115200 baudios` y envía comandos binarios **UBX-CFG-MSG** para activar las tramas NMEA necesarias (`$GxGST` para precisión real).
4. El cliente NTRIP integrado se conectará automáticamente al Caster configurado para empezar a recibir correcciones RTCM y enviarlas a la placa por USB.

---

## ⚙️ Configuración del Proyecto y Secretos

Para compilar el proyecto localmente, las credenciales y claves sensibles deben almacenarse en el archivo `local.properties` (que está excluido de Git en el `.gitignore`):

```properties
# Archivo: local.properties

# Ubicación del SDK de Android
sdk.dir=C\:\\Users\\...\\AppData\\Local\\Android\\Sdk

# Credenciales del Caster NTRIP (ej. IGN ERGNSS)
ntrip.host=192.148.213.42
ntrip.port=2102
ntrip.mountpoint=MALA3M
ntrip.user=tu_usuario
ntrip.password=tu_contraseña

# Clave de API de Google Maps
MAPS_API_KEY=AIzaSy...

# Contraseñas del almacén de claves (Keystore)
KEYSTORE_STORE_PASSWORD=tu_contraseña_store
KEYSTORE_KEY_PASSWORD=tu_contraseña_key
```

---

## 📲 Instalación y Compilación

1. Clona el repositorio:
   ```bash
   git clone https://github.com/tu-usuario/campo-de-regatas-art.git
   ```
2. Abre el proyecto en **Android Studio** (Jellyfish / Koala o superior).
3. Asegúrate de configurar el archivo `local.properties` como se describe arriba.
4. Compila y ejecuta en un dispositivo Android físico con soporte USB OTG:
   ```bash
   ./gradlew :app:assembleDebug
   ```

---

## 🌐 Enlaces y Más Información

* **Sitio Web Oficial del Proyecto**: [https://regatas.jabegas.com/app.php](https://regatas.jabegas.com/app.php)
* **Desarrollador**: Ipe Romero ([Helioserver](http://www.helioserver.com/))
* **Aplicación en el deporte**: Regatas de barcas de jábega, remo tradicional y olímpico en Málaga / Andalucía.

---

*Desarrollado con ❤️ para el mundo del remo tradicional y de alta competición.*
