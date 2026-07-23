# Informe Final de Desarrollo del Proyecto: JennicLink Pro

Este informe documenta de forma cronológica, técnica y ordenada cada una de las fases de desarrollo del sistema **JennicLink Pro**, detallando los hitos alcanzados, las decisiones de arquitectura de software, las librerías implementadas y el estado actual de los repositorios para su utilización en sistemas futuros.

---

## 1. Resumen y Objetivos del Proyecto

El proyecto **JennicLink Pro** nació con el propósito de simplificar, portar y automatizar el mantenimiento de nodos de sensores acuícolas basados en los microcontroladores **NXP Jennic JN5168 y JN5169**. 

### Objetivos Principales:
*   **Flasheo Móvil**: Permitir la actualización de firmware directamente en terreno usando un smartphone Android mediante conexión USB OTG, eliminando la necesidad de transportar laptops a las balsas-jaula.
*   **Consola de Diagnóstico**: Integrar una terminal serial de alta velocidad (115200 baudios) para interactuar de forma alámbrica (directa al nodo) o inalámbrica (a través del Pancoordinator/Antena) con los equipos.
*   **Automatización de Calibración**: Evitar la desalineación de los paquetes de datos de sensores asegurando la sincronización de parámetros clave (como el largo del cable).

---

## 2. Cronología Histórica de Desarrollo (Por Hitos)

El proyecto se estructuró y completó a través de **5 hitos principales**:

```mermaid
gantt
    title Cronología de Hitos de JennicLink Pro
    dateFormat  YYYY-MM-DD
    section Fase 1
    Hito 1: Flasher OTG Nativo           :active, h1, 2026-07-01, 5d
    section Fase 2
    Hito 2: Consola & Autoresponder WAKE :active, h2, after h1, 4d
    section Fase 3
    Hito 3: Ruteo de Red (Antena/Nodo)   :active, h3, after h2, 3d
    section Fase 4
    Hito 4: Cliente SFTP Directo (JSch)  :active, h4, after h3, 4d
    section Fase 5
    Hito 5: Macro de Largo de Cables    :active, h5, after h4, 3d
```

### Hito 1: Implementación del Motor de Flasheo OTG
*   **Objetivo**: Programar el protocolo de carga de firmware Jennic en la plataforma Android.
*   **Desarrollo**:
    *   Se implementó la comunicación USB Host nativa mediante la librería `usb-serial-for-android` para controlar el chip FTDI/CP2102.
    *   Se portó la lógica de sincronización binaria, borrado de sectores flash (Erase) y escritura de bloques de 128 bytes del microcontrolador.
    *   Se diseñó una barra de progreso visual y una consola de registro en tiempo real que detalla el éxito del proceso.
*   **Resultado**: Primer prototipo capaz de flashear el chip en 15 segundos a 115200 baudios, con opción de fallback lento a 38400 baudios para cables ruidosos.

### Hito 2: Consola de Diagnóstico y Autoresponder "WAKE" (Optimización Crítica)
*   **Objetivo**: Evitar que el chip Jennic entre en modo de suspensión automática mientras se configura.
*   **Desarrollo**:
    *   Se observó que los nodos entran en suspensión y envían la palabra `"wake"` por puerto serial, esperando una respuesta `"ok"` en un intervalo extremadamente corto.
    *   Se diseñó un bucle lector asíncrono ultra optimizado en un hilo de fondo (`Dispatchers.IO`) con una tasa de muestreo de **20ms**.
    *   Al detectar `"wake"`, el hilo responde de forma instantánea `"ok\r\n"` sin pasar por el hilo principal (Main Thread), logrando una respuesta en menos de 20ms que mantiene el nodo despierto de forma exitosa.
    *   Se eliminó el botón físico de reset por hardware en la interfaz, determinando que los conversores USB de las placas no poseen dicha conexión.

### Hito 3: Lógica de Modos de Red (Antena vs Directo)
*   **Objetivo**: Diferenciar si los comandos se envían directamente por cable al nodo o mediante radiofrecuencia por la antena.
*   **Desarrollo**:
    *   Se agregó la casilla *"Cable conectado directo al Nodo (Sin 'cmd')"*.
    *   Si está desactivado (Modo Antena), la app lee el Mote ID ingresado y añade automáticamente el prefijo `cmd [mote_id] ` a todos los comandos (ej: `cmd 1 status`).
    *   Si está activado (Modo Directo), los comandos se envían limpios al puerto serial.

### Hito 4: Cliente SFTP Integrado (Independencia de Servidores en PC)
*   **Objetivo**: Permitir al celular buscar y descargar los firmwares desde cualquier laptop Ubuntu por Wi-Fi sin ejecutar scripts de Python.
*   **Desarrollo**:
    *   Se integró la librería de SSH seguro **JSch**.
    *   Se diseñó una interfaz fija para ingresar la IP, usuario, contraseña y directorio de búsqueda.
    *   El motor realiza un escaneo recursivo automático (hasta 4 niveles) buscando archivos `.bin` y filtrando carpetas del sistema operativo Linux (`/proc`, `/sys`, `/dev`, etc.) para no degradar el rendimiento.
    *   *Resolución de error R8*: Se detectó un error `ClassNotFoundException` al optimizar el APK para distribución. Se agregaron reglas de preservación (`-keep`) y supresión de advertencias (`-dontwarn`) en ProGuard para asegurar que las clases criptográficas no fueran eliminadas por el compilador.

### Hito 5: Macro de Calibración Coordenada de Largo de Cables
*   **Objetivo**: Configurar el largo del cable en los sensores del nodo de forma rápida para evitar fallas de transmisión.
*   **Desarrollo**:
    *   La desalineación de datos ocurre si el sensor de oxígeno (`SENS1`) y el de conductividad (`SENS2`) tienen largos de cable diferentes en su memoria interna.
    *   Se agregaron botones rápidos de **5m, 10m y 15m**.
    *   Al presionar un botón, la aplicación ejecuta de forma secuencial y automatizada:
        1.  `spower` (Enciende los sensores).
        2.  `tunnel SENS1 cable <largo>` (Configura el cable en el sensor de oxígeno).
        3.  `tunnel SENS2 cable <largo>` (Configura el cable en el sensor de salinidad).

---

## 3. Ficha Técnica y Arquitectura del Software

### A. Estructura de Capas
El proyecto está estructurado bajo patrones de diseño limpios:
*   **Capa de Presentación (UI)**: Jetpack Compose (Kotlin). Se compone de [MainScreen.kt](file:///home/innovex/.gemini/antigravity/scratch/jennic_flasher_android/app/src/main/java/com/example/jennicflasher/ui/main/MainScreen.kt), el cual maneja la interfaz reactiva oscura de dos pestañas (Grabador y Consola).
*   **Capa de Lógica de Negocio (ViewModel)**: [MainScreenViewModel.kt](file:///home/innovex/.gemini/antigravity/scratch/jennic_flasher_android/app/src/main/java/com/example/jennicflasher/ui/main/MainScreenViewModel.kt), que gestiona el estado de la UI (`FlasherUiState`) mediante flujos reactivos `StateFlow` y despacha los comandos asíncronos.
*   **Capa de Datos (Repository & Drivers)**:
    *   [DataRepository.kt](file:///home/innovex/.gemini/antigravity/scratch/jennic_flasher_android/app/src/main/java/com/example/jennicflasher/data/DataRepository.kt): Gestiona el escaneo del puerto serial USB y la conexión SFTP mediante JSch.
    *   [JennicProgrammer.kt](file:///home/innovex/.gemini/antigravity/scratch/jennic_flasher_android/app/src/main/java/com/example/jennicflasher/data/JennicProgrammer.kt): Ejecuta el protocolo binario a bajo nivel con el microcontrolador.

### B. Dependencias de Compilación
Configuradas en `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.github.mik3y:usb-serial-for-android:3.8.0") // Control USB OTG
    implementation("com.github.mwiede:jsch:0.2.18")                 // Cliente SSH/SFTP
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // ... dependencias estándar de Jetpack Compose y AndroidX
}
```

---

## 4. Estado de los Repositorios y Distribución

Para asegurar la preservación del proyecto a futuro, se crearon y configuraron tres repositorios clave:

### 1. Repositorio Privado de Desarrollo (GitHub)
*   **URL**: `https://github.com/Kmazu/jenniclink-pro.git`
*   **Descripción**: Contiene el código fuente completo del proyecto Android (Kotlin). Está configurado como **Privado** para que solo personas autorizadas puedan ver o clonar el código de programación.

### 2. Repositorio Público de Distribución (GitHub)
*   **URL**: `https://github.com/Kmazu/jenniclink-app.git`
*   **Descripción**: No contiene código fuente. Solo contiene el instalador ejecutable compilado `jennic-flasher.apk` y los manuales de usuario. Permite a los técnicos descargar la aplicación directamente desde el navegador de su celular sin requerir inicio de sesión en GitHub.
*   **Enlace de descarga directa**: `https://github.com/Kmazu/jenniclink-app/releases/download/v1.0.0/jennic-flasher.apk`

### 3. Repositorio de la Intranet Corporativa (Innovex Forge)
*   **URL**: `https://forge.innovex.cl/glenn.montiel/jenniclink-pro.git`
*   **Descripción**: Respaldo completo del código fuente, el instalador APK y toda la documentación técnica dentro del servidor Git corporativo de Innovex.

---

## 5. Recomendaciones para el Desarrollo Futuro

Si en el futuro se decide integrar este desarrollo en un sistema mayor o añadir nuevas características, se recomienda:
1.  **Mantener la Ofuscación R8**: Al realizar compilaciones de prueba, asegurar que el archivo `proguard-rules.pro` conserve las exclusiones de JSch para no romper el módulo de sincronización.
2.  **Integrar Bluetooth/Wi-Fi local**: El motor de `JennicProgrammer.kt` está diseñado de forma abstracta sobre interfaces de flujo de datos (streams). Esto facilita reemplazar el canal USB OTG por un módulo Bluetooth a Serial en el futuro si las tarjetas integran soporte inalámbrico directo.
3.  **Monitoreo del handshaking**: Si se integran chips Jennic más recientes, validar si el string de reinicio `"wake"` cambia su formato, adaptando la expresión regular en el lector serial del ViewModel.
