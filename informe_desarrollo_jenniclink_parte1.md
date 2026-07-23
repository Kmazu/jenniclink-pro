# Informe de Desarrollo del Proyecto: JennicLink Pro (Parte 1 - Hitos 1 al 3)

Este informe documenta de forma cronológica, técnica y ordenada las primeras fases de desarrollo del sistema **JennicLink Pro**, detallando los hitos 1 al 3, las decisiones de arquitectura de software y las librerías implementadas.

---

## 1. Resumen y Objetivos del Proyecto

El proyecto **JennicLink Pro** nació con el propósito de simplificar, portar y automatizar el mantenimiento de nodos de sensores acuícolas basados en los microcontroladores **NXP Jennic JN5168 y JN5169**. 

### Objetivos Principales:
*   **Flasheo Móvil**: Permitir la actualización de firmware directamente en terreno usando un smartphone Android mediante conexión USB OTG, eliminando la necesidad de transportar laptops a las balsas-jaula.
*   **Consola de Diagnóstico**: Integrar una terminal serial de alta velocidad (115200 baudios) para interactuar de forma alámbrica (directa al nodo) o inalámbrica (a través del Pancoordinator/Antena) con los equipos.
*   **Automatización de Calibración**: Evitar la desalineación de los paquetes de datos de sensores asegurando la sincronización de parámetros clave.

---

## 2. Cronología Histórica de Desarrollo (Parte 1)

El proyecto se estructuró a través de hitos secuenciales de desarrollo. Esta primera entrega abarca los hitos del 1 al 3:

```mermaid
gantt
    title Cronología de Hitos de JennicLink Pro (Fase 1)
    dateFormat  YYYY-MM-DD
    section Fase 1
    Hito 1: Flasher OTG Nativo           :active, h1, 2026-07-01, 5d
    section Fase 2
    Hito 2: Consola & Autoresponder WAKE :active, h2, after h1, 4d
    section Fase 3
    Hito 3: Ruteo de Red (Antena/Nodo)   :active, h3, after h2, 3d
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
