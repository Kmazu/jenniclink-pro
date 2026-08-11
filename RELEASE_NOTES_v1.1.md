# Notas de la Versión: JennicLink Pro v1.1.0

**Fecha de Lanzamiento**: 11 de Agosto de 2026  
**Versión de la Aplicación**: `v1.1.0` (`versionCode = 2`)  
**Código Base**: Producción Limpio (`forge/main` / `origin/main`)  
**Autor y Propietario del Software**: Glenn Montiel  
**Entorno de Aplicación**: Herramienta Oficial de Terreno para la empresa Innovex  

---

## 📌 Resumen Ejecutivo de la Versión 1.1

La versión **JennicLink Pro v1.1.0** introduce dos mejoras fundamentales orientadas a la usabilidad en terreno y la gestión inteligente de archivos de firmware en dispositivos móviles Android:

1. **Desglose y Clasificación de Firmwares por Versión (De Mayor a Menor)**.
2. **Sistema Híbrido de Búsqueda, Escaneo e Importación de Firmwares en el Celular (Auto-Escanear + Selector SAF)**.

---

## 🚀 Nuevas Características y Mejoras

### 1. Desglose y Ordenamiento por Versiones de Mayor a Menor
* **Algoritmo de Categorización**: Los archivos de firmware (`.bin`) son analizados mediante expresiones regulares y reconocimiento de patrones para identificar su etiqueta de versión (`v2.0.2`, `v2.0.1`, `v2.0.0`, `r1068`, `r984`, etc.).
* **Jerarquía Visual en la UI**: La lista desplegable de firmwares locales agrupa y separa visualmente los archivos ordenándolos desde la versión más reciente a la más antigua:
  * 📌 **Versión v2.0.2**
  * 📌 **Versión v2.0.1**
  * 📌 **Versión v2.0.0**
  * 📌 **Revisión r1068**
  * 📌 **Revisión r984**
  * 📌 **Otros (Sin versión)**

### 2. Auto-Escaneo de Almacenamiento en el Teléfono (`🔍 Auto-Escanear`)
* **Integración con MediaStore API**: Explora la base de datos multimedia del teléfono para indexar archivos de firmware descargados desde navegadores web (Chrome, Firefox, Opera), clientes de correo y aplicaciones de mensajería (WhatsApp Documents, Telegram).
* **Compatibilidad con Scoped Storage (Android 11 al 15)**:
  * Si la aplicación no posee el permiso especial `MANAGE_EXTERNAL_STORAGE` en Android 11+, al tocar el botón **`🔍 Auto-Escanear`**, redirige automáticamente a la pantalla nativa de Ajustes del sistema (*"Permitir acceso para administrar todos los archivos"*).
  * Una vez concedido el permiso, la app realiza un escaneo completo de carpetas públicas (`/sdcard/Download`, `Documentos`, `Telegram`, `WhatsApp`) e importa los firmwares encontrados a la memoria de la app.

### 3. Importación Directa por Selector Nativo (`📁 Seleccionar .bin`)
* **Storage Access Framework (SAF)**: Añadido un botón de acceso directo que abre el explorador de archivos nativo de Android.
* Permite al usuario seleccionar manualmente cualquier archivo `.bin` ubicado en descargas, memoria interna, tarjeta SD o servicios en la nube (Drive).

### 4. Interfaz Simétrica y Ajuste Estético
* Distribución adaptativa de botones en una fila simétrica y pareja (`height = 42dp`, `weight = 1f`).
* Indicadores visuales de estado y feedback en tiempo real al escanear e importar archivos.

### 5. Botón de Comando Rápido `sleep` en Consola Serial (Antena y Nodo)
* **Comando `sleep` en Antena Pancoordinator**: Añadido botón de suspensión directa `sleep` en la fila de comandos de la antena centralizadora (`status`, `motes`, `stats`, `sleep`, `reboot`).
* **Comando `sleep` en Nodo Jennic**: Añadido botón de suspensión `sleep` al lado de `commit` (`status`, `config`, `commit`, `sleep`, `reboot`), que enruta la orden por radio (`cmd [mote_id] sleep`) o limpia en modo directo.

---

## 📄 Archivos Modificados en el Repositorio

* `app/build.gradle.kts` (Bump de `versionCode = 2` y `versionName = "1.1"`)
* `app/src/main/AndroidManifest.xml` (Declaración de permisos `MANAGE_EXTERNAL_STORAGE` y `READ_EXTERNAL_STORAGE`)
* `app/src/main/java/com/example/jennicflasher/data/DataRepository.kt` (Parsing de versiones `extractVersionTag`, `extractVersionWeight`, escáner `MediaStore` e importación `importFirmwareFromUri`)
* `app/src/main/java/com/example/jennicflasher/ui/main/MainScreenViewModel.kt` (Lógica de estado y métodos `scanPhoneStorage` e `importFirmwareFromUri`)
* `app/src/main/java/com/example/jennicflasher/ui/main/MainScreen.kt` (Inclusión de botones simétricos, `filePickerLauncher`, `permissionLauncher` e insignias v1.1)
* `README.md` (Actualización de documentación y Hito 7)
* `especificacion_tecnica_jenniclink.md` (Ficha técnica y pasajes de arquitectura v1.1)
