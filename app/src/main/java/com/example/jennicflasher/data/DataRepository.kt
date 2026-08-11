package com.example.jennicflasher.data

import android.content.Context
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session

fun extractVersionTag(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        lower.contains("v2.0.2") || lower.contains("2.0.2") -> "v2.0.2"
        lower.contains("v2.0.1") || lower.contains("2.0.1") -> "v2.0.1"
        lower.contains("v2.0.0") || lower.contains("2.0.0") -> "v2.0.0"
        lower.contains("1068") || lower.contains("r1068") || lower.contains("r1058") -> "r1068"
        lower.contains("r984") -> "r984"
        else -> {
            val regex = Regex("v?\\d+\\.\\d+(\\.\\d+)?", RegexOption.IGNORE_CASE)
            val match = regex.find(fileName)
            if (match != null) {
                val tag = match.value
                if (tag.startsWith("v", ignoreCase = true)) tag else "v$tag"
            } else {
                "Sin versión"
            }
        }
    }
}

fun extractVersionWeight(versionTag: String): Int {
    val lower = versionTag.lowercase()
    return when {
        lower.contains("2.0.2") -> 20020
        lower.contains("2.0.1") -> 20010
        lower.contains("2.0.0") -> 20000
        lower.contains("1068") -> 10680
        lower.contains("984") -> 9840
        lower.startsWith("v") -> {
            val nums = lower.removePrefix("v").split(".")
            val major = nums.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = nums.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = nums.getOrNull(2)?.toIntOrNull() ?: 0
            major * 10000 + minor * 100 + patch
        }
        else -> 0
    }
}

data class PcFirmware(
    val name: String,
    val path: String,
    val size: String,
    val mtimeStr: String
)

data class LocalFirmware(
    val name: String,
    val file: File,
    val sizeStr: String,
    val versionTag: String = extractVersionTag(name),
    val versionWeight: Int = extractVersionWeight(extractVersionTag(name))
)

data class UsbDeviceItem(
    val name: String,
    val port: UsbSerialPort
)

interface DataRepository {
    val data: Flow<List<String>> // Compatibility with template
    fun scanUsbDevices(context: Context): List<UsbDeviceItem>
    fun getLocalFirmwares(context: Context): List<LocalFirmware>
    suspend fun scanPhoneStorageForFirmwares(context: Context): Int
    suspend fun importFirmwareFromUri(context: Context, uri: Uri): File
    suspend fun fetchRemoteFirmwares(pcIp: String): List<PcFirmware>
    suspend fun downloadRemoteFirmware(context: Context, pcIp: String, pcFirmware: PcFirmware): File
    suspend fun fetchRemoteFirmwaresSftp(ip: String, user: String, pass: String, remotePath: String): List<PcFirmware>
    suspend fun downloadRemoteFirmwareSftp(context: Context, ip: String, user: String, pass: String, remotePath: String, fileName: String): File
}

class DefaultDataRepository : DataRepository {
    
    override val data: Flow<List<String>> = flow {
        emit(listOf("Jennic Flasher"))
    }

    override fun scanUsbDevices(context: Context): List<UsbDeviceItem> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val result = mutableListOf<UsbDeviceItem>()
        
        for (driver in availableDrivers) {
            val device = driver.device
            val ports = driver.ports
            for (i in ports.indices) {
                val port = ports[i]
                val customName = "${device.manufacturerName ?: "Dispositivo USB"} ${device.productName ?: ""} (Puerto $i)"
                result.add(UsbDeviceItem(customName, port))
            }
        }
        return result
    }

    override fun getLocalFirmwares(context: Context): List<LocalFirmware> {
        val dir = context.filesDir
        val files = dir.listFiles { _, name -> name.endsWith(".bin") } ?: emptyArray()
        return files.map { file ->
            val size = file.length()
            val sizeStr = if (size > 1024 * 1024) {
                String.format("%.2f MB", size.toDouble() / (1024 * 1024))
            } else {
                String.format("%.1f KB", size.toDouble() / 1024)
            }
            LocalFirmware(file.name, file, sizeStr)
        }.sortedWith(
            compareByDescending<LocalFirmware> { it.versionWeight }
                .thenByDescending { it.file.lastModified() }
        )
    }

    override suspend fun scanPhoneStorageForFirmwares(context: Context): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        val targetDir = context.filesDir
        val seenFiles = mutableSetOf<String>()

        // 1. Query MediaStore for browser & downloaded .bin files across external storage
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME
            )
            val cursor = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                null,
                null,
                null
            )
            cursor?.use {
                val dataIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                val nameIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val idIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)

                while (it.moveToNext()) {
                    val fileName = if (nameIndex != -1) it.getString(nameIndex) else null
                    val filePath = if (dataIndex != -1) it.getString(dataIndex) else null
                    val id = if (idIndex != -1) it.getLong(idIndex) else -1L

                    val actualName = fileName ?: filePath?.substringAfterLast("/") ?: continue
                    if (!actualName.endsWith(".bin", ignoreCase = true)) continue

                    if (seenFiles.contains(actualName)) continue
                    seenFiles.add(actualName)

                    var copied = false

                    // Try direct file copy first
                    if (filePath != null) {
                        try {
                            val srcFile = File(filePath)
                            if (srcFile.exists() && srcFile.isFile) {
                                val destFile = File(targetDir, srcFile.name)
                                srcFile.copyTo(destFile, overwrite = true)
                                importedCount++
                                copied = true
                            }
                        } catch (e: Exception) {
                            // Fallback to ContentResolver stream
                        }
                    }

                    // Fallback to ContentResolver stream if direct file access is blocked by Scoped Storage
                    if (!copied && id != -1L) {
                        try {
                            val contentUri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), id.toString())
                            val destFile = File(targetDir, actualName)
                            context.contentResolver.openInputStream(contentUri)?.use { input ->
                                FileOutputStream(destFile).use { output ->
                                    input.copyTo(output)
                                }
                                importedCount++
                                copied = true
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DataRepository", "Error leyendo content URI de MediaStore para $actualName", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DataRepository", "Error consultando MediaStore", e)
        }

        // 2. Direct file system traversal for standard public folders
        val candidateFolders = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            File("/sdcard/Download"),
            File("/sdcard/Downloads"),
            File("/sdcard/Documents"),
            File("/storage/emulated/0/Download"),
            File("/storage/emulated/0/Downloads"),
            File("/storage/emulated/0/Documents"),
            File("/storage/emulated/0/WhatsApp/Media/WhatsApp Documents"),
            File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"),
            File("/storage/emulated/0/Telegram/Telegram Documents"),
            context.getExternalFilesDir(null)
        )

        for (folder in candidateFolders) {
            if (folder.exists() && folder.isDirectory) {
                try {
                    folder.walkTopDown()
                        .maxDepth(3)
                        .filter { file -> file.isFile && file.name.endsWith(".bin", ignoreCase = true) }
                        .forEach { sourceFile ->
                            if (!seenFiles.contains(sourceFile.name)) {
                                seenFiles.add(sourceFile.name)
                                val destFile = File(targetDir, sourceFile.name)
                                if (!destFile.exists() || destFile.length() != sourceFile.length()) {
                                    try {
                                        sourceFile.copyTo(destFile, overwrite = true)
                                        importedCount++
                                    } catch (e: Exception) {
                                        android.util.Log.e("DataRepository", "Error al copiar ${sourceFile.name}", e)
                                    }
                                }
                            }
                        }
                } catch (e: Exception) {
                    // Ignore folder traversal restrictions
                }
            }
        }
        importedCount
    }

    override suspend fun importFirmwareFromUri(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val targetDir = context.filesDir
        var displayName = "firmware_${System.currentTimeMillis()}.bin"

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val resolvedName = cursor.getString(nameIndex)
                        if (!resolvedName.isNullOrBlank()) {
                            displayName = resolvedName
                        }
                    }
                }
            }
        } catch (e: Exception) {
            uri.path?.let { path ->
                val name = path.substringAfterLast("/")
                if (name.endsWith(".bin", ignoreCase = true)) {
                    displayName = name
                }
            }
        }

        if (!displayName.endsWith(".bin", ignoreCase = true)) {
            displayName += ".bin"
        }

        val outFile = File(targetDir, displayName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("No se pudo abrir el archivo seleccionado")

        outFile
    }

    override suspend fun fetchRemoteFirmwares(pcIp: String): List<PcFirmware> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PcFirmware>()
        try {
            val url = URL("http://$pcIp:5000/api/firmwares")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(text)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    result.add(
                        PcFirmware(
                            name = obj.getString("name"),
                            path = obj.getString("path"),
                            size = obj.getString("size"),
                            mtimeStr = obj.getString("mtime_str")
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DataRepository", "Error fetching remote firmwares", e)
        }
        result
    }

    override suspend fun downloadRemoteFirmware(
        context: Context,
        pcIp: String,
        pcFirmware: PcFirmware
    ): File = withContext(Dispatchers.IO) {
        val urlString = "http://$pcIp:5000/api/firmwares/download?path=${java.net.URLEncoder.encode(pcFirmware.path, "UTF-8")}"
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        
        if (conn.responseCode != 200) {
            throw Exception("Failed to download file: HTTP ${conn.responseCode}")
        }
        
        val outFile = File(context.filesDir, pcFirmware.name)
        conn.inputStream.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
        outFile
    }

    override suspend fun fetchRemoteFirmwaresSftp(
        ip: String,
        user: String,
        pass: String,
        remotePath: String
    ): List<PcFirmware> = withContext(Dispatchers.IO) {
        val result = mutableListOf<PcFirmware>()
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            val jsch = JSch()
            session = jsch.getSession(user, ip, 22)
            session.setPassword(pass)
            
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)
            session.connect(10000)
            
            val openChannel = session.openChannel("sftp")
            openChannel.connect(10000)
            channel = openChannel as ChannelSftp
            
            var baseDir = remotePath.trim()
            if (baseDir.isEmpty()) {
                baseDir = "."
            }
            
            scanSftpDirectory(channel, baseDir, result, maxDepth = 4, currentDepth = 0)
        } catch (e: Exception) {
            android.util.Log.e("DataRepository", "Error fetching SFTP firmwares", e)
            throw e
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
        result
    }

    private fun scanSftpDirectory(
        channel: ChannelSftp,
        dirPath: String,
        result: MutableList<PcFirmware>,
        maxDepth: Int,
        currentDepth: Int
    ) {
        if (currentDepth > maxDepth || result.size >= 100) return
        
        try {
            val normalized = dirPath.lowercase()
            if (normalized.endsWith("/proc") || normalized.endsWith("/sys") || normalized.endsWith("/dev") || 
                normalized.endsWith("/var") || normalized.endsWith("/lib") || normalized.endsWith("/lib64") ||
                normalized.endsWith("/boot") || normalized.endsWith("/etc") || normalized.endsWith("/usr") ||
                normalized.contains("/.") || normalized.contains("/node_modules")) {
                return
            }
            
            val files = channel.ls(dirPath) ?: return
            val subdirs = mutableListOf<String>()
            
            for (obj in files) {
                if (result.size >= 100) break
                val entry = obj as? ChannelSftp.LsEntry ?: continue
                val name = entry.filename
                if (name == "." || name == "..") continue
                if (name.startsWith(".")) continue
                
                val fullPath = if (dirPath.endsWith("/")) "$dirPath$name" else "$dirPath/$name"
                val attrs = entry.attrs
                
                if (attrs.isDir) {
                    subdirs.add(fullPath)
                } else if (name.endsWith(".bin", ignoreCase = true)) {
                    val size = attrs.size
                    val sizeStr = if (size > 1024 * 1024) {
                        String.format("%.2f MB", size.toDouble() / (1024 * 1024))
                    } else {
                        String.format("%.1f KB", size.toDouble() / 1024)
                    }
                    result.add(PcFirmware(name, fullPath, sizeStr, ""))
                }
            }
            
            for (subdir in subdirs) {
                scanSftpDirectory(channel, subdir, result, maxDepth, currentDepth + 1)
            }
        } catch (e: Exception) {
            // Ignore permission/listing errors for specific subdirs
        }
    }

    override suspend fun downloadRemoteFirmwareSftp(
        context: Context,
        ip: String,
        user: String,
        pass: String,
        remotePath: String,
        fileName: String
    ): File = withContext(Dispatchers.IO) {
        var session: Session? = null
        var channel: ChannelSftp? = null
        val outFile = File(context.filesDir, fileName)
        try {
            val jsch = JSch()
            session = jsch.getSession(user, ip, 22)
            session.setPassword(pass)
            
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)
            session.connect(10000)
            
            val openChannel = session.openChannel("sftp")
            openChannel.connect(10000)
            channel = openChannel as ChannelSftp
            
            FileOutputStream(outFile).use { output ->
                channel.get(remotePath, output)
            }
        } catch (e: Exception) {
            android.util.Log.e("DataRepository", "Error downloading via SFTP", e)
            throw e
        } finally {
            channel?.disconnect()
            session?.disconnect()
        }
        outFile
    }
}

// Log utility fallback
object Log {
    fun e(tag: String, msg: String, t: Throwable) {
        android.util.Log.e(tag, msg, t)
    }
}
