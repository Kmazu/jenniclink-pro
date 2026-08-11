# Documentación de la Versión 1.0.0 (Histórico): JennicLink Pro

Este documento preserva de manera exacta la documentación técnica, comercial y de usuario de la **Versión 1.0.0** inicial de **JennicLink Pro** desarrollada para la empresa Innovex (autoría y propiedad de Glenn Montiel).

---

## 1. Redacción del Proyecto e Historial de Trabajo (Versión 1.0.0)

### Contexto y Problemática Original
En la industria acuícola y de control de fluidos (por ejemplo, en sistemas de oxigenación de jaulas de cultivo), se utilizan nodos inalámbricos basados en microcontroladores **Jennic JN5168** y **JN5169**. Estos nodos recopilan datos de sensores (Oxígeno, Salinidad, Corrientes) y controlan electroválvulas de inyección de oxígeno. 

Anteriormente, los técnicos en terreno debían realizar dos tareas críticas con herramientas complejas:
1. **Flasheo de Firmware**: Requería una laptop en terreno conectada por cable serial para correr scripts de Python o programas de consola (como `jn516xprog`).
2. **Configuración de Consola**: Requiere cambiar parámetros locales o remotos (a través de una antena centralizadora "Pancoordinator") usando programas de terminal serial a 115200 baudios.

**El objetivo del proyecto v1.0.0** fue centralizar y simplificar estas operaciones en una aplicación móvil Android corporativa para Innovex, protegida contra la ingeniería inversa, que permitiera realizar flasheo OTG local y control serial intuitivo con un solo toque.

---

### Hitos Logrados en la Versión 1.0.0

#### Hito 1: Creación del Grabador Físico (Flasher OTG)
* Se construyó el motor de flasheo nativo en Kotlin que implementa el protocolo del bootloader de Jennic.
* Se integró la comunicación USB Host mediante la librería `usb-serial-for-android` para comunicarse directamente con chips FTDI / CP2102.

#### Hito 2: Creación de la Consola Serial 115200 y el Auto-Responder WAKE
* Se creó una interfaz de terminal interactiva con una tasa de muestreo rápida (115200 baudios).
* Se implementó un bucle lector ultra optimizado con un intervalo de muestreo de **20ms** en un hilo de fondo (IO Dispatcher) para interceptar `"wake"` y responder `"ok\r\n"`.

#### Hito 3: Modos de Red (Antena vs Directo)
* Se implementó el interruptor *"Cable conectado directo al Nodo (Sin 'cmd')"*.

#### Hito 4: Re-branding y Firma Corporativa
* Nombre oficial: **JennicLink Pro v1.0.0**.
* Compilación Release ofuscada con R8 / ProGuard.

#### Hito 5: Sincronización Directa de Firmwares por SSH/SFTP (Sin Scripts)
* Cliente SSH/SFTP integrado directamente mediante la librería **JSch**.

#### Hito 6: Comando de Ajuste Rápido de Cable del Sensor
* Botones para configurar el largo del cable del sensor (`5m`, `10m`, `15m`).

---

## 2. Manual de Instalación y Uso (Versión 1.0.0)

### Pantalla 1: Grabador (Flasher)
1. Sincronización SSH/SFTP desde laptop a celular.
2. Selección de puerto USB OTG y firmware local.
3. Botón "Grabar Firmware".

### Pantalla 2: Consola Serial (115.2k)
1. Conexión serial con auto-responder WAKE.
2. Botones rápidos: `status`, `config`, `commit`, `reboot`.
3. Ajuste de parámetros de oxigenación y sensores.
