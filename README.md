# Documentación Completa del Proyecto: JennicLink Pro v1.1.0

Este documento contiene la recopilación técnica e institucional completa del desarrollo de **JennicLink Pro** en su **Versión 1.1.0**, integrada oficialmente para uso en terreno de la empresa **Innovex** (autoría y propiedad de Glenn Montiel). Está redactado de forma detallada y estructurada para que pueda ser cargado directamente en Forge y GitHub.
---

## 1. Redacción del Proyecto e Historial de Trabajo

### Contexto y Problemática Original
En la industria acuícola y de control de fluidos (por ejemplo, en sistemas de oxigenación de jaulas de cultivo), se utilizan nodos inalámbricos basados en microcontroladores **Jennic JN5168** y **JN5169**. Estos nodos recopilan datos de sensores (Oxígeno, Salinidad, Corrientes) y controlan electroválvulas de inyección de oxígeno. 
Anteriormente, los técnicos en terreno debían realizar dos tareas críticas con herramientas complejas y propensas a errores:
1.  **Flasheo de Firmware**: Requería una laptop en terreno conectada por cable serial para correr scripts de Python o programas de consola (como `jn516xprog`).
2.  **Configuración de Consola**: Requiere cambiar parámetros locales o remotos (a través de una antena centralizadora "Pancoordinator") usando programas de terminal serial a 115200 baudios, enviando comandos manuales de texto.

**El objetivo del proyecto** fue centralizar y simplificar estas operaciones en una aplicación móvil Android de nivel corporativo para **Innovex**, protegida contra la ingeniería inversa, que permitiera realizar flasheo OTG local y control serial intuitivo con un solo toque.

---

### Cronología de Desarrollo Paso a Paso (Hitos logrados)

#### Hito 1: Creación del Grabador Físico (Flasher OTG)
*   Se construyó el motor de flasheo nativo en Kotlin que implementa el protocolo del bootloader de Jennic.
*   Se integró la comunicación USB Host mediante la librería `usb-serial-for-android` para comunicarse directamente con chips FTDI / CP2102 conectados al puerto del celular a través de un adaptador OTG.
*   Se integró una lista de firmwares descargados localmente y un selector de baudrate rápido (115200) o lento (38400) para tarjetas con ruido electromagnético.

#### Hito 2: Creación de la Consola Serial 115200 y el Auto-Responder WAKE
*   Se creó una interfaz de terminal interactiva con una tasa de muestreo rápida (115200 baudios).
*   Se eliminó el botón de reinicio por hardware al comprobarse que las placas no cuentan con pines de reset físico conectados al conversor USB-Serial.
*   Se detectó que algunos chips Jennic entran en suspensión inmediatamente después de iniciarse, imprimiendo `"wake"` en la consola y esperando `"ok"` en un intervalo crítico de milisegundos.
*   **Solución**: Se implementó un bucle lector ultra optimizado con un intervalo de muestreo de **20ms** en un hilo de fondo (IO Dispatcher). Este bucle intercepta la palabra `"wake"` y responde de forma instantánea `"ok\r\n"` sin pasar por el hilo principal de la UI, logrando mantener los nodos despiertos de manera exitosa.

#### Hito 3: Modos de Red (Antena vs Directo)
*   Se implementó el interruptor *"Cable conectado directo al Nodo (Sin 'cmd')"*. 
*   **Comportamiento**: Cuando está desactivado (modo Antena), los botones de comandos rápidos añaden automáticamente el prefijo `cmd [mote_id]` para enrutar el comando de manera inalámbrica. Si está activado (modo Directo), los comandos se envían limpios directamente al puerto físico.

#### Hito 4: Re-branding y Comercialización de la App (Protección Anti-Copia)
*   Se renombró oficialmente la aplicación a **JennicLink Pro**.
*   Se agregó la firma de autoría estática en el pie de página: `"JennicLink Pro v1.1.0 — Desarrollado por Glenn M."`.
*   Se generó un logotipo industrial de alta resolución para el icono de lanzamiento de la aplicación.
*   Se configuró el compilador Gradle para compilar en modo **Release** con **R8 / ProGuard** activado. Todo el código de comunicación OTG, base de datos y diseño UI se ofusca (renombrando variables y funciones a letras aleatorias como `a.b.c()`), protegiendo la propiedad intelectual de Glenn M. frente a descompilaciones y plagios.

#### Hito 5: Sincronización Directa de Firmwares por SSH/SFTP (Sin Scripts)
*   Se reemplazó el antiguo servidor Flask/Python que requería ser iniciado en las laptops por un cliente SSH/SFTP integrado directamente en la app del celular mediante la librería **JSch**.
*   Se diseñó una interfaz fija con campos de texto para **IP**, **Usuario**, **Contraseña** y **Ruta de carpeta**.
*   El motor realiza un escaneo recursivo (hasta 4 niveles de profundidad) de todo el disco duro de la laptop configurada, buscando archivos `.bin` y filtrando directorios críticos del sistema para no saturar el rendimiento.

#### Hito 6: Comando de Ajuste Rápido de Cable del Sensor
*   Se agregó la fila de configuración de largo de cable del sensor con tres opciones fijas: **5 metros, 10 metros y 15 metros**.
*   Para evitar errores de desalineación (donde los sensores COND y OXY envían datos desajustados por tener largos de cable distintos configurados en sus microchips), el botón realiza de forma automatizada la secuencia `spower` -> `tunnel SENS1 cable <largo>` -> `tunnel SENS2 cable <largo>`.

#### Hito 7: Novedades de la Versión 1.1 (Desglose por Versión y Escaneo en Celular)
*   **Desglose y Ordenamiento de Firmwares de Mayor a Menor**:
    * Se desarrolló un parser inteligente (`extractVersionTag` y `extractVersionWeight`) que extrae la etiqueta de versión de cualquier archivo `.bin` y calcula su peso numérico.
    * La lista desplegable agrupa y ordena automáticamente los firmwares en secciones visuales divididas desde la más reciente a la más antigua:
      * `v2.0.2` -> `v2.0.1` -> `v2.0.0` -> `r1068` -> `r984` -> `Sin versión / Otros`.
*   **Escaneo Inteligente de Almacenamiento Local (Auto-Escanear)**:
    * Se implementó un motor de escaneo de memoria interna en `DataRepository` que consulta la API `MediaStore.Files` y recorre directorios públicos (`/sdcard/Download`, `Documents`, `WhatsApp Documents`, `Telegram`).
    * Para superar las restricciones de **Scoped Storage en Android 11+**, al presionar **`🔍 Auto-Escanear`**, la aplicación redirige de forma nativa a la pantalla de Ajustes del sistema (`MANAGE_EXTERNAL_STORAGE`) permitiendo activar el permiso *"Acceso a todos los archivos"*.
*   **Selector Nativo Directo (`📁 Seleccionar .bin`)**:
    * Se añadió un botón de importación directa mediante el sistema **Storage Access Framework (SAF)** de Android. Al tocarlo, abre el explorador de archivos nativo permitiendo al usuario seleccionar cualquier archivo `.bin` guardado en el teléfono e incorporarlo al instante.
*   **Rediseño de Interfaz Simétrica**:
    * Se rediseñó la sección de firmware local con una distribución limpia y botones alineados proporcionalmente.

---

## 2. Manual de Instalación y Configuración del Entorno

### A. Instalación de la App en el Celular Android forge

1.  **Descarga del archivo instalable (.apk)**:
   * Abre el navegador Chrome en tu teléfono e ingresa a la página oficial de publicaciones (Releases):
     👉 **`https://intranet.innovex.cl/`**
   * Ingresa al apartado donde dice Forge, o dirijete directo a este link 
     👉 **`https://forge.innovex.cl/glenn.montiel/jenniclink-pro`**
4. **Instalación**:
   * Abre el archivo `jennic-flasher.apk` desde la notificación o la carpeta de **Descargas**.
   * Si Android muestra una advertencia de "Permitir instalar aplicaciones de fuentes desconocidas", presiona **Permitir**.
5. **Listo**: El icono de **JennicLink Pro** aparecerá en tu menú de aplicaciones listo para trabajar.

---

### B. Configuración de la Laptop Ubuntu (Para Sincronización)
Para que el celular pueda ingresar a la computadora y escanear los firmwares por SSH/SFTP, debes preparar la laptop por primera y única vez siguiendo estos pasos:

1.  **Instalar el servidor SSH**:
    Abre una terminal (`Ctrl + Alt + T`) en Ubuntu y ejecuta:
    ```bash
    sudo apt update && sudo apt install -y openssh-server
    ```
2.  **Configurar el Firewall**:
    Permite la entrada al puerto 22 (SSH) con:
    ```bash
    sudo ufw allow 22/tcp
    ```
3.  **Permitir el inicio de sesión por contraseña**:
    Si la laptop de tu empresa tiene bloqueado el acceso SSH con contraseña, ejecútalo en consola:
    ```bash
    sudo nano /etc/ssh/sshd_config
    ```
    *   Busca la línea `PasswordAuthentication` y asegúrate de que esté configurada como **`yes`** (y que no tenga un símbolo `#` al inicio).
    *   Guarda presionando `Ctrl + O` -> `Enter`, y sal con `Ctrl + X`.
    *   Reinicia el servicio con:
        ```bash
        sudo systemctl restart ssh
        ```


---

## 3. Manual de Usuario de JennicLink Pro

### Pantalla 1: Grabador (Flasher)
Esta pestaña se utiliza para programar el firmware `.bin` en la memoria física de los nodos mediante un cable USB OTG.

*   **Paso 1: Sincronizar firmwares desde la PC**:
    1.  Escribe la IP de la laptop en el campo de arriba (ejemplo: `192.168.1.40`).
    2.  Ingresa el **Usuario SSH** de la laptop y la **Contraseña**.
    3.  Define la **Ruta en PC** (usa `/home/nombre_usuario` para escanear tus archivos, o `/` para buscar por todo el disco).
    4.  Presiona **"Sinc"**. La aplicación te listará todos los firmwares disponibles en la PC.
    5.  Presiona **"Bajar"** junto al firmware deseado. Esto lo guardará para siempre en la memoria interna de tu celular.
*   **Paso 2: Realizar la grabación (Flasheo)**:
    1.  Conecta la tarjeta Jennic al celular usando el cable FTDI y el adaptador OTG.
    2.  Presiona el botón de **Refrescar** en la sección "Puerto USB OTG" y selecciona el puerto detectado.
    3.  Selecciona el archivo `.bin` en la lista desplegable "Firmware local".
    4.  *(Opcional)* Si la tarjeta está ruidosa o el cable es largo, marca la casilla *"Baudrate lento (38400 baudios)"*.
    5.  Coloca físicamente el nodo Jennic en **modo de programación**: mantén presionado el botón `PROG`, luego presiona `RESET` una vez, y suelta `PROG`.
    6.  Presiona el botón **"Grabar Firmware"** en el celular. Verás el progreso en porcentaje y las bitácoras detalladas en el cuadro inferior.

---

### Pantalla 2: Consola Serial (115.2k)
Esta pestaña se utiliza para realizar diagnósticos, enviar comandos manuales y configurar de forma intuitiva los parámetros de inyección de oxígeno y sensores.

*   **Conexión**: Conecta el cable OTG de la antena o nodo, selecciona el puerto y toca **"Conectar Puerto"**.
*   **Auto-Responder 'ok'**: Mantenlo siempre activo para que la aplicación responda al comando de suspensión `"wake"` de los nodos en milisegundos y no se vayan a dormir mientras los configuras.
*   **Modo de Conexión**:
    *   **Marcar** *"Cable conectado directo al Nodo"* si tienes el teléfono cableado directamente a la placa del nodo Jennic.
    *   **Desmarcar** si estás conectado a través de la **Antena (Pancoordinator)**. Esto requiere indicar el número de **Mote ID (Nodo)** al que deseas enviar las señales por radio.
*   **Botones de Comandos Rápidos**:
    *   **Comandos de Antena (Pancoordinator)**: `status`, `motes`, `stats`, `sleep`, `reboot` (envían comandos locales directos a la antena centralizadora).
    *   **Comandos del Nodo Jennic**: `status`, `config`, `commit`, `sleep`, `reboot` (envían los comandos al nodo seleccionado utilizando el prefijo `cmd [mote_id]` por radio o directo).
*   **Configuración Avanzada (Un solo toque)**:
    *   **Nombre / PAN-ID / Muestreo / Contraste**: Escribe el valor deseado y presiona su botón correspondiente para enviarlo y guardarlo.
    *   **Inyección Oxígeno**: Permite ajustar los niveles de oxígeno de apertura y corte, así como activar los modos de inyección (`Auto`, `On`, `Off`).
    *   **Largo Cable (Nuevo)**: Presiona `5m`, `10m` o `15m` para configurar de manera sincronizada y automática los sensores ópticos y de conductividad en terreno.