# Release Notes — JennicLink Pro v1.1.0

**Fecha**: 11 de Agosto de 2026  
**Versión de Aplicación**: `v1.1.0` (`versionCode = 2`, `versionName = "1.1"`)  
**Desarrollador / Propietario**: Glenn Montiel  
**Organización**: Innovex  

---

## 📌 Resumen Ejecutivo de la Versión 1.1.0

Esta actualización introduce el **Desglose Jerárquico de Firmwares por Versión**, el motor de **Auto-Escaneo en Almacenamiento Móvil (`MediaStore API`)**, el **Selector Nativo de Archivos (`SAF`)**, los **Nuevos Botones de Suspensión `sleep`** en la Consola Serial (Antena y Nodo), y la reestructuración completa de la documentación para preservar la Versión 1.0.0 previa.

---

## 🚀 Cambios y Mejoras por Módulo

### 1. Desglose y Clasificación de Firmwares (De Mayor a Menor)
* Parseo dinámico de versión (`v2.0.2`, `v2.0.1`, `v2.0.0`, `r1068`, `r984`, etc.).
* Ordenamiento descendente en la lista desplegable de la pestaña Grabador (Flasher).

### 2. Auto-Escaneo de Memoria Celular (`🔍 Auto-Escanear`)
* Escaneo masivo a través de `MediaStore` y carpetas de descargas/redes sociales.
* Redirección a Ajustes (`MANAGE_EXTERNAL_STORAGE`) en Android 11+.

### 3. Selector Nativo de Archivos (`📁 Seleccionar .bin`)
* Integración de Storage Access Framework (`SAF`) para abrir el explorador de archivos nativo de Android.

### 4. Nuevos Botones de Comando `sleep` en Consola Serial (Antena y Nodo)
* **En Antena Pancoordinator**: Botón `sleep` en los comandos locales (`status`, `motes`, `stats`, `sleep`, `reboot`).
* **En Nodo Jennic**: Botón `sleep` al lado de `commit` (`status`, `config`, `commit`, `sleep`, `reboot`), enviando `cmd [mote_id] sleep` por radio o directo.

---

## 📄 Archivos y Documentación en el Repositorio

* `README.md`: Índice de documentación institucional.
* `README_v1.0.0.md`: Documentación preservada de la Versión 1.0.0 original.
* `README_RELEASE_v1.1.0.md`: Documentación de la actualización de versión 1.1.0.
* `RELEASE_NOTES_v1.1.md`: Resumen ejecutivo de cambios de esta release.
