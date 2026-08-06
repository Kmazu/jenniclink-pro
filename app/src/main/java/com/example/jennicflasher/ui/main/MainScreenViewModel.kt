package com.example.jennicflasher.ui.main

import android.content.Context
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.jennicflasher.data.DataRepository
import com.example.jennicflasher.data.JennicProgrammer
import com.example.jennicflasher.data.LocalFirmware
import com.example.jennicflasher.data.PcFirmware
import com.example.jennicflasher.data.UsbDeviceItem
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream

enum class FlashingStatus {
    IDLE,
    SYNCING,
    DOWNLOADING,
    FLASHING,
    SUCCESS,
    ERROR
}

data class FlasherUiState(
    val pcIp: String = "192.168.1.134",
    val remoteFirmwares: List<PcFirmware> = emptyList(),
    val localFirmwares: List<LocalFirmware> = emptyList(),
    val usbDevices: List<UsbDeviceItem> = emptyList(),
    
    val selectedLocalFirmware: LocalFirmware? = null,
    val selectedUsbDevice: UsbDeviceItem? = null,
    val slowMode: Boolean = false,
    
    val status: FlashingStatus = FlashingStatus.IDLE,
    val progress: Int = 0,
    val logs: String = "Listo para iniciar...\n",
    val syncErrorMessage: String? = null,

    // Serial Console states (for 115200 baud Pancoordinator commands)
    val currentTab: Int = 0, // 0 = Flasher, 1 = Console
    val terminalConnected: Boolean = false,
    val terminalLogs: String = "Listo para conectar...\n",
    val moteIdInput: String = "1",
    val nameInput: String = "",
    val panIdInput: String = "",
    val intervalInput: String = "300",
    val levelsMinInput: String = "4.2",
    val levelsMaxInput: String = "5.0",
    val customCommandInput: String = "",
    val autoRespondWake: Boolean = true,
    val directNodeMode: Boolean = false,
    val contrastInput: String = "",

    // SFTP Sync configurations
    val useSftp: Boolean = true,
    val sftpUsername: String = "innovex",
    val sftpPassword: String = "",
    val sftpFolderPath: String = "/home/innovex"
)

fun extractVersion(filename: String): String {
    val cleanName = filename.substringBeforeLast(".")
    
    // Match _vX.Y.Z or _vX.Y or _vX
    val vRegex = Regex("""_v(\d+(?:\.\d+)*)""", RegexOption.IGNORE_CASE)
    val vMatch = vRegex.find(cleanName)
    if (vMatch != null) {
        return "v" + vMatch.groupValues[1]
    }

    // Match _rXXXX (e.g., _r984)
    val rRegex = Regex("""_r(\d+)""", RegexOption.IGNORE_CASE)
    val rMatch = rRegex.find(cleanName)
    if (rMatch != null) {
        return "r" + rMatch.groupValues[1]
    }

    // Match _1068_427
    val numRegex = Regex("""_(\d+_\d+)""")
    val numMatch = numRegex.find(cleanName)
    if (numMatch != null) {
        return numMatch.groupValues[1]
    }

    return "Otros"
}

val versionComparator = Comparator<String> { v1, v2 ->
    if (v1 == v2) return@Comparator 0
    if (v1 == "Otros") return@Comparator 1
    if (v2 == "Otros") return@Comparator -1

    val isV1 = v1.startsWith("v", ignoreCase = true)
    val isV2 = v2.startsWith("v", ignoreCase = true)
    val isR1 = v1.startsWith("r", ignoreCase = true)
    val isR2 = v2.startsWith("r", ignoreCase = true)

    if (isV1 && isV2) {
        val parts1 = v1.drop(1).split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.drop(1).split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) {
                return@Comparator p2.compareTo(p1) // Descending
            }
        }
        return@Comparator v2.compareTo(v1)
    }
    if (isV1) return@Comparator -1
    if (isV2) return@Comparator 1

    if (isR1 && isR2) {
        val r1 = v1.drop(1).toIntOrNull() ?: 0
        val r2 = v2.drop(1).toIntOrNull() ?: 0
        return@Comparator r2.compareTo(r1) // Descending
    }
    if (isR1) return@Comparator -1
    if (isR2) return@Comparator 1

    v2.compareTo(v1)
}

class MainScreenViewModel(private val repository: DataRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FlasherUiState())
    val uiState: StateFlow<FlasherUiState> = _uiState.asStateFlow()

    // For backwards compatibility
    val templatesUiState: StateFlow<MainScreenUiState> = MutableStateFlow(MainScreenUiState.Success(listOf("Jennic")))

    private var terminalActive = false

    fun selectTab(tab: Int) {
        if (tab == 0 && _uiState.value.terminalConnected) {
            disconnectTerminal()
        }
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updatePcIp(ip: String) {
        _uiState.update { it.copy(pcIp = ip) }
    }

    fun updateUseSftp(use: Boolean) {
        _uiState.update { it.copy(useSftp = use) }
    }

    fun updateSftpUsername(user: String) {
        _uiState.update { it.copy(sftpUsername = user) }
    }

    fun updateSftpPassword(pass: String) {
        _uiState.update { it.copy(sftpPassword = pass) }
    }

    fun updateSftpFolderPath(path: String) {
        _uiState.update { it.copy(sftpFolderPath = path) }
    }

    fun updateSlowMode(slow: Boolean) {
        _uiState.update { it.copy(slowMode = slow) }
    }

    fun selectLocalFirmware(firmware: LocalFirmware?) {
        _uiState.update { it.copy(selectedLocalFirmware = firmware) }
    }

    fun selectUsbDevice(device: UsbDeviceItem?) {
        _uiState.update { it.copy(selectedUsbDevice = device) }
    }

    fun refreshLocalFirmwares(context: Context) {
        viewModelScope.launch {
            val localList = repository.getLocalFirmwares(context)
            val sortedList = localList.sortedWith(compareBy<LocalFirmware, String>(versionComparator) { extractVersion(it.name) }.thenBy { it.name })
            _uiState.update {
                it.copy(
                    localFirmwares = sortedList,
                    selectedLocalFirmware = sortedList.firstOrNull() ?: it.selectedLocalFirmware
                )
            }
        }
    }

    fun scanDevices(context: Context) {
        viewModelScope.launch {
            val devices = repository.scanUsbDevices(context)
            _uiState.update {
                it.copy(
                    usbDevices = devices,
                    selectedUsbDevice = devices.firstOrNull() ?: it.selectedUsbDevice
                )
            }
        }
    }

    fun syncFromPc() {
        val state = _uiState.value
        val ip = state.pcIp
        if (ip.isBlank()) return
        
        _uiState.update { it.copy(status = FlashingStatus.SYNCING, syncErrorMessage = null) }
        viewModelScope.launch {
            try {
                val remoteList = if (state.useSftp) {
                    repository.fetchRemoteFirmwaresSftp(
                        ip = ip,
                        user = state.sftpUsername,
                        pass = state.sftpPassword,
                        remotePath = state.sftpFolderPath
                    )
                } else {
                    repository.fetchRemoteFirmwares(ip)
                }

                if (remoteList.isEmpty()) {
                    _uiState.update { 
                      it.copy(
                            status = FlashingStatus.IDLE,
                            syncErrorMessage = if (state.useSftp) {
                                "Conectado con éxito a $ip, pero no se encontraron archivos .bin en la ruta especificada."
                            } else {
                                "No se encontraron firmwares en $ip o no hay conexión."
                            }
                        )
                    }
                } else {
                    val sortedRemoteList = remoteList.sortedWith(compareBy<PcFirmware, String>(versionComparator) { extractVersion(it.name) }.thenBy { it.name })
                    _uiState.update {
                        it.copy(
                            remoteFirmwares = sortedRemoteList,
                            status = FlashingStatus.IDLE
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        status = FlashingStatus.IDLE,
                        syncErrorMessage = if (state.useSftp) {
                            "Error de conexión SSH/SFTP: ${e.localizedMessage ?: e.message}"
                        } else {
                            "Error de sincronización: ${e.message}"
                        }
                    )
                }
            }
        }
    }

    fun downloadFirmware(context: Context, pcFirmware: PcFirmware) {
        val state = _uiState.value
        val ip = state.pcIp
        _uiState.update { it.copy(status = FlashingStatus.DOWNLOADING) }
        viewModelScope.launch {
            try {
                if (state.useSftp) {
                    repository.downloadRemoteFirmwareSftp(
                        context = context,
                        ip = ip,
                        user = state.sftpUsername,
                        pass = state.sftpPassword,
                        remotePath = pcFirmware.path,
                        fileName = pcFirmware.name
                    )
                } else {
                    repository.downloadRemoteFirmware(context, ip, pcFirmware)
                }
                refreshLocalFirmwares(context)
                _uiState.update { it.copy(status = FlashingStatus.IDLE) }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        status = FlashingStatus.IDLE,
                        syncErrorMessage = "Error al descargar: ${e.message}"
                    )
                }
            }
        }
    }

    // Flashing workflow
    fun startFlashing(context: Context) {
        val state = _uiState.value
        val deviceItem = state.selectedUsbDevice
        val firmwareItem = state.selectedLocalFirmware
        
        if (deviceItem == null || firmwareItem == null) {
            _uiState.update { it.copy(logs = "ERROR: Debe seleccionar puerto USB y firmware.\n") }
            return
        }

        val device = deviceItem.port.driver.device
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        if (!usbManager.hasPermission(device)) {
            _uiState.update { it.copy(logs = "🔑 Solicitando permiso para acceder al dispositivo USB...\n") }
            
            val permissionReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: android.content.Intent) {
                    if ("com.example.jennicflasher.USB_PERMISSION" == intent.action) {
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        ctx.unregisterReceiver(this)
                        if (granted) {
                            runFlashingProcess(context, deviceItem, firmwareItem)
                        } else {
                            _uiState.update {
                                it.copy(
                                    status = FlashingStatus.ERROR,
                                    logs = it.logs + "❌ ERROR: Permiso denegado por el usuario.\n"
                                )
                            }
                        }
                    }
                }
            }

            val filter = android.content.IntentFilter("com.example.jennicflasher.USB_PERMISSION")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(permissionReceiver, filter)
            }

            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                android.app.PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val permissionIntent = android.app.PendingIntent.getBroadcast(
                context,
                0,
                android.content.Intent("com.example.jennicflasher.USB_PERMISSION"),
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
        } else {
            runFlashingProcess(context, deviceItem, firmwareItem)
        }
    }

    private fun runFlashingProcess(context: Context, deviceItem: UsbDeviceItem, firmwareItem: LocalFirmware) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                status = FlashingStatus.FLASHING,
                progress = 0,
                logs = it.logs + "🚀 INICIANDO GRABACIÓN LOCAL VIA USB OTG...\n"
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val connection = usbManager.openDevice(deviceItem.port.driver.device)
            if (connection == null) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            status = FlashingStatus.ERROR,
                            logs = it.logs + "❌ ERROR: No se pudo abrir la conexión con el dispositivo USB.\n"
                        )
                    }
                }
                return@launch
            }

            try {
                deviceItem.port.open(connection)
                
                val programmer = JennicProgrammer(
                    port = deviceItem.port,
                    onProgress = { percent ->
                        _uiState.update { it.copy(progress = percent) }
                    },
                    onLog = { logLine ->
                        _uiState.update { it.copy(logs = it.logs + logLine) }
                    }
                )

                val fileStream = FileInputStream(firmwareItem.file)
                val totalBytes = firmwareItem.file.length().toInt()
                
                val success = programmer.flash(
                    inputStream = fileStream,
                    slowMode = state.slowMode,
                    totalBytes = totalBytes
                )

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            status = if (success) FlashingStatus.SUCCESS else FlashingStatus.ERROR
                        )
                    }
                }
            } catch (e: java.lang.Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            status = FlashingStatus.ERROR,
                            logs = it.logs + "❌ ERROR: Falló la conexión serial: ${e.message}\n"
                        )
                    }
                }
            } finally {
                try {
                    deviceItem.port.close()
                } catch (e: java.lang.Exception) {}
            }
        }
    }

    // Console/Terminal workflow (115200 baud)
    fun connectTerminal(context: Context) {
        val deviceItem = _uiState.value.selectedUsbDevice
        if (deviceItem == null) {
            _uiState.update { it.copy(terminalLogs = "❌ ERROR: Seleccione un puerto USB OTG primero.\n") }
            return
        }

        _uiState.update { it.copy(terminalConnected = true, terminalLogs = "🔌 Conectando a ${deviceItem.name} a 115200 baudios...\n") }
        terminalActive = true

        viewModelScope.launch(Dispatchers.IO) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val connection = usbManager.openDevice(deviceItem.port.driver.device)
            if (connection == null) {
                withContext(Dispatchers.Main) {
                    _uiState.update { 
                        it.copy(
                            terminalConnected = false,
                            terminalLogs = it.terminalLogs + "❌ ERROR: Permiso USB denegado.\n"
                        )
                    }
                }
                return@launch
            }

            try {
                deviceItem.port.open(connection)
                deviceItem.port.setParameters(115200, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(terminalLogs = it.terminalLogs + "✅ Puerto conectado. Escriba un comando o use los botones rápidos.\n") }
                }

                val buffer = ByteArray(1024)
                var accumulatedText = ""
                while (terminalActive) {
                    val bytesRead = deviceItem.port.read(buffer, 20)
                    if (bytesRead > 0) {
                        val text = String(buffer, 0, bytesRead)

                        if (_uiState.value.autoRespondWake) {
                            accumulatedText += text.lowercase()
                            if (accumulatedText.contains("wake")) {
                                try {
                                    deviceItem.port.write("ok\r\n".toByteArray(), 500)
                                    withContext(Dispatchers.Main) {
                                        _uiState.update { it.copy(terminalLogs = it.terminalLogs + "\n⚡ [Auto-Respond: ok]\n") }
                                    }
                                } catch (e: Exception) {
                                    Log.e("Terminal", "Failed to auto-respond", e)
                                }
                                accumulatedText = ""
                            }
                            if (accumulatedText.length > 200) {
                                accumulatedText = accumulatedText.takeLast(50)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            _uiState.update { it.copy(terminalLogs = it.terminalLogs + text) }
                        }
                    } else if (bytesRead < 0) {
                        break
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { 
                        it.copy(
                            terminalConnected = false,
                            terminalLogs = it.terminalLogs + "\n❌ ERROR: Conexión finalizada: ${e.message}\n"
                        )
                    }
                }
            } finally {
                try {
                    deviceItem.port.close()
                } catch (e: Exception) {}
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(terminalConnected = false) }
                }
            }
        }
    }

    fun disconnectTerminal() {
        terminalActive = false
        _uiState.update { it.copy(terminalConnected = false, terminalLogs = it.terminalLogs + "\n🔌 Puerto serial cerrado.\n") }
    }

    fun sendTerminalCommand(command: String) {
        val deviceItem = _uiState.value.selectedUsbDevice ?: return
        if (!_uiState.value.terminalConnected) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fullCommand = command + "\r\n"
                deviceItem.port.write(fullCommand.toByteArray(), 1000)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(terminalLogs = it.terminalLogs + "\n> $command\n") }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(terminalLogs = it.terminalLogs + "\n❌ ERROR al enviar: ${e.message}\n") }
                }
            }
        }
    }

    fun updateMoteIdInput(moteId: String) { _uiState.update { it.copy(moteIdInput = moteId) } }
    fun updateNameInput(name: String) { _uiState.update { it.copy(nameInput = name) } }
    fun updatePanIdInput(panId: String) { _uiState.update { it.copy(panIdInput = panId) } }
    fun updateIntervalInput(interval: String) { _uiState.update { it.copy(intervalInput = interval) } }
    fun updateLevelsMinInput(minVal: String) { _uiState.update { it.copy(levelsMinInput = minVal) } }
    fun updateLevelsMaxInput(maxVal: String) { _uiState.update { it.copy(levelsMaxInput = maxVal) } }
    fun updateCustomCommandInput(cmd: String) { _uiState.update { it.copy(customCommandInput = cmd) } }
    fun updateAutoRespondWake(enabled: Boolean) { _uiState.update { it.copy(autoRespondWake = enabled) } }
    fun updateDirectNodeMode(enabled: Boolean) { _uiState.update { it.copy(directNodeMode = enabled) } }
    fun updateContrastInput(contrast: String) { _uiState.update { it.copy(contrastInput = contrast) } }
    fun clearTerminalLogs() { _uiState.update { it.copy(terminalLogs = "") } }
}

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val data: List<String>) : MainScreenUiState
}
