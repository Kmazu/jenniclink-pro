# Documentación de Actualización de Release: JennicLink Pro v1.1.0

**Fecha de Publicación**: 11 de Agosto de 2026  
**Versión del Sistema**: `v1.1.0` (`versionCode = 2`, `versionName = "1.1"`)  
**Autor y Propietario del Software**: Glenn Montiel  
**Empresa / Aplicación**: Herramienta Oficial de Terreno para la empresa Innovex  

---

## 📌 Resumen de la Nueva Actualización (Versión 1.1.0)

Esta actualización importante (**Versión 1.1.0**) expande el núcleo de **JennicLink Pro** agregando funcionalidades avanzadas de escaneo de memoria en teléfonos Android, clasificación jerárquica de versiones y comandos ampliados en consola serial.

---

## 🚀 Nuevas Funcionalidades y Mejoras Incorporadas

### 1. Desglose y Clasificación de Firmwares por Versión (De Mayor a Menor)
* **Categorización Automática**: El sistema parsea la versión de cada archivo `.bin` (`v2.0.2`, `v2.0.1`, `v2.0.0`, `r1068`, `r984`, etc.).
* **Jerarquía de Mayor a Menor**: La interfaz organiza los firmwares desde la versión más reciente a la más antigua con títulos y separadores visuales:
  * 📌 **Versión v2.0.2**
  * 📌 **Versión v2.0.1**
  * 📌 **Versión v2.0.0**
  * 📌 **Revisión r1068**
  * 📌 **Revisión r984**
  * 📌 **Otros (Sin versión)**

### 2. Auto-Escaneo de Memoria en Celular (`🔍 Auto-Escanear`)
* **Integración con MediaStore API**: Explora la base de datos de Android para localizar firmwares `.bin` en `/sdcard/Download`, `Documentos`, `WhatsApp` y `Telegram`.
* **Acceso a Todos los Archivos (Android 11 al 15)**: Redirige automáticamente a los Ajustes del sistema (`MANAGE_EXTERNAL_STORAGE`) para conceder el permiso *"Acceso a todos los archivos"*.

### 3. Importación Nativa de Archivos (`📁 Seleccionar .bin`)
* **Storage Access Framework (SAF)**: Botón de acceso directo que abre el explorador de archivos nativo de Android para seleccionar manualmente cualquier archivo `.bin`.

### 4. Nuevos Botones de Comando `sleep` en Consola Serial
* **En Antena Pancoordinator**: Botón `sleep` en la fila de comandos locales de la antena (`status`, `motes`, `stats`, `sleep`, `reboot`).
* **En Nodo Jennic**: Botón `sleep` al lado de `commit` (`status`, `config`, `commit`, `sleep`, `reboot`), enviando `cmd [mote_id] sleep` por radio o directo.

---

## 📑 Enlaces e Histórico de Releases

* 📌 [Documentación Histórica de la Versión 1.0.0](file:///home/innovex/.gemini/antigravity/scratch/jenniclink-pro/README_v1.0.0.md) (Tag Git `v1.0.0`)
* 📌 [Notas de Lanzamiento v1.1.0](file:///home/innovex/.gemini/antigravity/scratch/jenniclink-pro/RELEASE_NOTES_v1.1.md) (Tag Git `v1.1.0`)
