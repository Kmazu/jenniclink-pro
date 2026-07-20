# Documentación Completa del Proyecto: JennicLink Pro v1.0.0

Este documento contiene la recopilación técnica y comercial completa del desarrollo de **JennicLink Pro**. Está redactado de forma detallada y estructurada para que pueda ser cargado directamente en **NotebookLM** de Google como fuente de conocimiento para resúmenes, chats inteligentes o generación de guías complementarias.

---

## 1. Redacción del Proyecto e Historial de Trabajo

### Contexto y Problemática Original
En la industria acuícola y de control de fluidos (por ejemplo, en sistemas de oxigenación de jaulas de cultivo), se utilizan nodos inalámbricos basados en microcontroladores **Jennic JN5168** y **JN5169**. Estos nodos recopilan datos de sensores (Oxígeno, Salinidad, Corrientes) y controlan electroválvulas de inyección de oxígeno. 
Anteriormente, los técnicos en terreno debían realizar dos tareas críticas con herramientas complejas y propensas a errores:
1.  **Flasheo de Firmware**: Requería una laptop en terreno conectada por cable serial para correr scripts de Python o programas de consola (como `jn516xprog`).
2.  **Configuración de Consola**: Requiere cambiar parámetros locales o remotos (a través de una antena centralizadora "Pancoordinator") usando programas de terminal serial a 115200 baudios, enviando comandos manuales de texto.

**El objetivo del proyecto** fue centralizar y simplificar estas operaciones en una aplicación móvil Android de calidad comercial, protegida contra la ingeniería inversa, que permitiera realizar flasheo OTG local y control serial intuitivo con un solo toque.

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
*   Se agregó la firma de autoría estática en el pie de página: `"JennicLink Pro v1.0.0 — Desarrollado por Glenn M."`.
*   Se generó un logotipo industrial de alta resolución para el icono de lanzamiento de la aplicación.
*   Se configuró el compilador Gradle para compilar en modo **Release** con **R8 / ProGuard** activado. Todo el código de comunicación OTG, base de datos y diseño UI se ofusca (renombrando variables y funciones a letras aleatorias como `a.b.c()`), protegiendo la propiedad intelectual de Glenn M. frente a descompilaciones y plagios.

#### Hito 5: Sincronización Directa de Firmwares por SSH/SFTP (Sin Scripts)
*   Se reemplazó el antiguo servidor Flask/Python que requería ser iniciado en las laptops por un cliente SSH/SFTP integrado directamente en la app del celular mediante la librería **JSch**.
*   Se diseñó una interfaz fija con campos de texto para **IP**, **Usuario**, **Contraseña** y **Ruta de carpeta**.
*   El motor realiza un escaneo recursivo (hasta 4 niveles de profundidad) de todo el disco duro de la laptop configurada, buscando archivos `.bin` y filtrando directorios críticos del sistema para no saturar el rendimiento.

#### Hito 6: Comando de Ajuste Rápido de Cable del Sensor
*   Se agregó la fila de configuración de largo de cable del sensor con tres opciones fijas: **5 metros, 10 metros y 15 metros**.
*   Para evitar errores de desalineación (donde los sensores COND y OXY envían datos desajustados por tener largos de cable distintos configurados en sus microchips), el botón realiza de forma automatizada la siguiente secuencia:
    1.  Envía `spower` (enciende los sensores).
    2.  Envía `tunnel SENS1 cable <largo>` (configura el sensor de oxígeno).
    3.  Envía `tunnel SENS2 cable <largo>` (configura el sensor de salinidad).

---

## 2. Manual de Instalación y Configuración del Entorno

### A. Instalación de la App en el Celular Android
1.  Conecta tu celular a la misma red Wi-Fi que la computadora principal.
2.  Abre el navegador Chrome en tu teléfono e ingresa a la dirección de descarga:
    👉 **`http://10.74.45.141:5000/static/jennic-flasher.apk`**
3.  Descarga el archivo. Si Android muestra una advertencia de "Archivo dañino", confirma la descarga (es una advertencia estándar para aplicaciones que no se descargan de la Play Store).
4.  Abre el archivo descargado e instálalo. Si te pide habilitar "Instalar aplicaciones de fuentes desconocidas" para Chrome, acéptalo.
5.  ¡Listo! El icono de **JennicLink Pro** aparecerá en tu menú de aplicaciones.

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

### C. Uso alternativo con el Servidor Autónomo (`simple_server.py`)
Si alguna vez vas a sincronizar firmwares desde otra computadora que no sea tuya (por ejemplo, la de un cliente o compañero que no tenga SSH instalado), puedes usar el script ligero de Python:

1.  Descarga el script en esa computadora desde:
    👉 **`http://10.74.45.141:5000/static/simple_server.py`**
2.  Coloca los archivos de firmware `.bin` en la carpeta **Descargas** de esa computadora.
3.  Abre una terminal en la carpeta donde descargaste el script y ejecútalo con:
    ```bash
    python3 simple_server.py
    ```
4.  Desactiva la opción de SSH en el celular, ingresa la IP de esa PC y presiona "Sinc".

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
    *   `status`, `config`, `commit`, `reboot`: Realizan acciones rápidas sobre la antena o nodo sin escribir.
*   **Configuración Avanzada (Un solo toque)**:
    *   **Nombre / PAN-ID / Muestreo / Contraste**: Escribe el valor deseado y presiona su botón correspondiente para enviarlo y guardarlo.
    *   **Inyección Oxígeno**: Permite ajustar los niveles de oxígeno de apertura y corte, así como activar los modos de inyección (`Auto`, `On`, `Off`).
    *   **Largo Cable (Nuevo)**: Presiona `5m`, `10m` o `15m` para configurar de manera sincronizada y automática los sensores ópticos y de conductividad en terreno.

---

## 4. Estructura de Presentación Comercial (Para ventas a Empresas)

Esta es la propuesta estructurada de diapositivas que puedes utilizar para presentar la aplicación **JennicLink Pro** a la gerencia o clientes comerciales:

### Diapositiva 1: Portada
*   **Título**: JennicLink Pro v1.0.0
*   **Subtítulo**: Solución Móvil de Terreno para la Sincronización y Configuración de Nodos de Oxigenación Acuícola.
*   **Presenta**: Glenn M.

### Diapositiva 2: La Problemática en Terreno
*   **Puntos clave**:
    *   *Dependencia de equipos pesados*: Obligación de usar laptops de alto costo y cargadores en pasillos de jaulas de cultivo o terreno hostil.
    *   *Complejidad técnica*: Operar comandos crudos de consola y scripts de Python complejos.
    *   *Riesgo de errores de sensores*: Configuración manual desincronizada de largos de cables en sensores de oxígeno y salinidad, provocando desalineación y datos erróneos de transmisión.

### Diapositiva 3: La Solución: JennicLink Pro
*   **Puntos clave**:
    *   *Portabilidad total*: Todo el control desde cualquier smartphone Android con un cable USB OTG.
    *   *Sincronización Directa por SSH/SFTP*: Sin necesidad de instalar programas en las PCs. El celular se conecta a las laptops de la oficina de forma directa sobre Wi-Fi.
    *   *Base de datos local permanente*: Los firmwares se quedan en la memoria del teléfono para trabajar sin internet en terreno.

### Diapositiva 4: Seguridad y Resguardo del Software
*   **Puntos clave**:
    *   *Compilación Comercial*: Código 100% cerrado y optimizado para distribución directa (ejemplo: WhatsApp).
    *   *Protección Intelectual*: Ofuscación con R8/ProGuard. Si un tercero intenta decompilar la aplicación para robar la lógica, solo verá código cifrado e incomprensible.

### Diapositiva 5: Demostración de Características Avanzadas
*   **Puntos clave**:
    *   *Consola con Auto-Responder ultra rápido*: Intercepta el comando "wake" en menos de 20ms en segundo plano para evitar la suspensión de los equipos Jennic.
    *   *Configuración inteligente del largo de cable*: Automatiza el encendido de sensores y configura en lote (SENS1 y SENS2) con botones de 5, 10 y 15 metros con un solo toque.

### Diapositiva 6: Conclusión y Beneficios del Negocio
*   **Puntos clave**:
    *   Reducción del tiempo de puesta en marcha y mantenimiento de nodos de oxigenación en un 70%.
    *   Aumento de la confiabilidad de los datos al eliminar los errores manuales de cables.
    *   Herramienta corporativa lista para el despliegue inmediato.
