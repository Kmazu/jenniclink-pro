package com.example.jennicflasher.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.jennicflasher.data.DefaultDataRepository
import com.example.jennicflasher.data.LocalFirmware
import com.example.jennicflasher.data.PcFirmware
import com.example.jennicflasher.data.UsbDeviceItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Trigger initial scan
    LaunchedEffect(Unit) {
        viewModel.scanDevices(context)
        viewModel.refreshLocalFirmwares(context)
    }

    val bgGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F1016), Color(0xFF1E1F29))
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F1016)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgGradient)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Jennic Logo",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "JennicLink Pro",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Tabs Selector
            TabRow(
                selectedTabIndex = uiState.currentTab,
                containerColor = Color(0xFF13141C),
                contentColor = Color(0xFF6366F1),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.currentTab]),
                        color = Color(0xFF6366F1)
                    )
                }
            ) {
                Tab(
                    selected = uiState.currentTab == 0,
                    enabled = uiState.status != FlashingStatus.FLASHING,
                    onClick = { viewModel.selectTab(0) },
                    text = { Text("Grabador (Flasher)", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.currentTab == 1,
                    enabled = uiState.status != FlashingStatus.FLASHING,
                    onClick = { viewModel.selectTab(1) },
                    text = { Text("Consola Serial (115.2k)", fontWeight = FontWeight.Bold) }
                )
            }

            // Scrollable Content depending on Tab
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.currentTab == 0) {
                    FlasherLayout(uiState, viewModel, context)
                } else {
                    ConsoleLayout(uiState, viewModel, context)
                }
            }

            // Fixed Footer
            Text(
                text = "JennicLink Pro v1.0.0\nDesarrollado por Glenn M.",
                color = Color.DarkGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1016))
                    .padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
fun FlasherLayout(
    uiState: FlasherUiState,
    viewModel: MainScreenViewModel,
    context: android.content.Context
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importFirmwareFromUri(context, uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.scanPhoneStorage(context)
    }

    // Section 1: PC Connection & Synchronization
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Sincronización de PC (Wi-Fi)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF22D3EE),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.pcIp,
                    onValueChange = { viewModel.updatePcIp(it) },
                    label = { Text("IP de tu PC", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.weight(1.5f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { viewModel.syncFromPc() },
                    enabled = uiState.status != FlashingStatus.SYNCING && uiState.status != FlashingStatus.FLASHING,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    modifier = Modifier.height(56.dp)
                ) {
                    if (uiState.status == FlashingStatus.SYNCING) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Text("Sinc")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = uiState.sftpUsername,
                    onValueChange = { viewModel.updateSftpUsername(it) },
                    label = { Text("Usuario SSH", color = Color.Gray, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = uiState.sftpPassword,
                    onValueChange = { viewModel.updateSftpPassword(it) },
                    label = { Text("Contraseña", color = Color.Gray, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.Gray
                    ),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.sftpFolderPath,
                onValueChange = { viewModel.updateSftpFolderPath(it) },
                label = { Text("Ruta en PC (ej: /home/innovex o /)", color = Color.Gray, fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.LightGray,
                    focusedBorderColor = Color(0xFF6366F1),
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true
            )

            if (uiState.syncErrorMessage != null) {
                Text(
                    text = uiState.syncErrorMessage,
                    color = Color(0xFFF87171),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // Remote files listing if fetched
            if (uiState.remoteFirmwares.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Firmwares disponibles en PC:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Box(modifier = Modifier.height(130.dp).fillMaxWidth().padding(top = 8.dp)) {
                    LazyColumn(state = rememberLazyListState()) {
                        items(uiState.remoteFirmwares) { firmware ->
                            RemoteFirmwareRow(
                                firmware = firmware,
                                isDownloading = uiState.status == FlashingStatus.DOWNLOADING,
                                onDownloadClick = { viewModel.downloadFirmware(context, firmware) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Section 2: Device and Flashing Setup
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Configuración de Grabación",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF22D3EE),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // USB Device Dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedBox(
                        label = "Puerto USB OTG",
                        value = uiState.selectedUsbDevice?.name ?: "No se detectaron puertos",
                        onClick = { expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(Color(0xFF1E1F29))
                    ) {
                        uiState.usbDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name, color = Color.White) },
                                onClick = {
                                    viewModel.selectUsbDevice(device)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(
                    onClick = { viewModel.scanDevices(context) },
                    modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Phone Storage Scan & Import Header & Buttons
            Text(
                text = "Firmware Local (.bin):",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (Environment.isExternalStorageManager()) {
                                viewModel.scanPhoneStorage(context)
                            } else {
                                Toast.makeText(context, "Por favor habilita el permiso 'Acceso a todos los archivos' para escanear firmwares", Toast.LENGTH_LONG).show()
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            }
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                            if (!hasPerm) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                            } else {
                                viewModel.scanPhoneStorage(context)
                            }
                        }
                    },
                    enabled = !uiState.isScanningStorage,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (uiState.isScanningStorage) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Buscando...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("🔍 Auto-Escanear", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    enabled = !uiState.isScanningStorage,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("📁 Seleccionar .bin", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (uiState.scanFeedbackMessage != null) {
                Surface(
                    color = Color(0x336366F1),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = uiState.scanFeedbackMessage!!,
                        color = Color(0xFFA5B4FC),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            // Local Firmware Dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                var expanded by remember { mutableStateOf(false) }
                OutlinedBox(
                    label = "Seleccionar Firmware",
                    value = uiState.selectedLocalFirmware?.name ?: "Escanea, selecciona o sincroniza un firmware",
                    onClick = { expanded = true }
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f).background(Color(0xFF1E1F29))
                ) {
                    var lastVersion = ""
                    uiState.localFirmwares.forEach { firmware ->
                        val currentVersion = extractVersion(firmware.name)
                        if (currentVersion != lastVersion) {
                            if (lastVersion.isNotEmpty()) {
                                HorizontalDivider(
                                    color = Color(0x26FFFFFF),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            Text(
                                text = if (currentVersion.startsWith("r")) "📌 Revisión $currentVersion" else if (currentVersion == "Otros") "📌 Otros (Sin versión)" else "📌 Versión $currentVersion",
                                color = Color(0xFF22D3EE),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                            lastVersion = currentVersion
                        }
                        DropdownMenuItem(
                            text = { Text(firmware.name, color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                viewModel.selectLocalFirmware(firmware)
                                expanded = false
                            }
                        )
                    }
                }
            }

                Spacer(modifier = Modifier.height(14.dp))

                // Options Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { viewModel.updateSlowMode(!uiState.slowMode) }
                ) {
                    Checkbox(
                        checked = uiState.slowMode,
                        onCheckedChange = { viewModel.updateSlowMode(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF6366F1),
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        text = "Baudrate lento (38400 baudios)",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }

                // Main Action Button
                Spacer(modifier = Modifier.height(20.dp))
                val isActionEnabled = uiState.selectedUsbDevice != null && 
                                      uiState.selectedLocalFirmware != null && 
                                      uiState.status != FlashingStatus.FLASHING &&
                                      !uiState.terminalConnected
                
                Button(
                    onClick = { viewModel.startFlashing(context) },
                    enabled = isActionEnabled,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1),
                        disabledContainerColor = Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Flash")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.status == FlashingStatus.FLASHING) "Grabando..." else "Grabar Firmware",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                if (uiState.terminalConnected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ Desconecte la consola serial antes de grabar firmware",
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Section 3: Flashing Console Output
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF05060B)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0x14FFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Salida de Consola",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(uiState.status)
                }

                if (uiState.status == FlashingStatus.FLASHING) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        LinearProgressIndicator(
                            progress = { uiState.progress.toFloat() / 100f },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Color(0xFF22D3EE),
                            trackColor = Color(0x33FFFFFF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Progreso: ${uiState.progress}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                // Logs Terminal
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    val scrollState = rememberScrollState()
                    LaunchedEffect(uiState.logs) {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }

                    Text(
                        text = uiState.logs,
                        color = Color(0xFF34D399),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    )
                }
            }
        }
}

@Composable
fun ConsoleLayout(
    uiState: FlasherUiState,
    viewModel: MainScreenViewModel,
    context: android.content.Context
) {
    // Connection Row
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Conexión a Consola (115.200 baudios)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF22D3EE),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // USB Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    var expanded by remember { mutableStateOf(false) }
                    OutlinedBox(
                        label = "Puerto USB OTG",
                        value = uiState.selectedUsbDevice?.name ?: "No se detectaron puertos",
                        onClick = { if (!uiState.terminalConnected) expanded = true }
                    )
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.8f).background(Color(0xFF1E1F29))
                    ) {
                        uiState.usbDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name, color = Color.White) },
                                onClick = {
                                    viewModel.selectUsbDevice(device)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                IconButton(
                    onClick = { viewModel.scanDevices(context) },
                    enabled = !uiState.terminalConnected,
                    modifier = Modifier.background(Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateAutoRespondWake(!uiState.autoRespondWake) }
                    .padding(bottom = 12.dp)
            ) {
                Checkbox(
                    checked = uiState.autoRespondWake,
                    onCheckedChange = { viewModel.updateAutoRespondWake(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF6366F1),
                        checkmarkColor = Color.White
                    )
                )
                Text(
                    text = "Auto-responder 'ok' al detectar 'wake'",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = {
                    if (uiState.terminalConnected) {
                        viewModel.disconnectTerminal()
                    } else {
                        viewModel.connectTerminal(context)
                    }
                },
                enabled = uiState.status != FlashingStatus.FLASHING,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.terminalConnected) Color(0xFFEF4444) else Color(0xFF6366F1),
                    disabledContainerColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text(
                    text = if (uiState.terminalConnected) "Desconectar Puerto" else "Conectar Puerto",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Terminal Monitor Screen
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF05060B)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monitor de Puerto", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Button(
                    onClick = { viewModel.clearTerminalLogs() },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Limpiar", color = Color.LightGray, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Logs Terminal
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                val scrollState = rememberScrollState()
                LaunchedEffect(uiState.terminalLogs) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }

                Text(
                    text = uiState.terminalLogs,
                    color = Color(0xFF34D399),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Command Send Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.customCommandInput,
                    onValueChange = { viewModel.updateCustomCommandInput(it) },
                    placeholder = { Text("Escribe comando...", color = Color.DarkGray) },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.LightGray,
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.DarkGray
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (uiState.customCommandInput.isNotBlank()) {
                            viewModel.sendTerminalCommand(uiState.customCommandInput)
                            viewModel.updateCustomCommandInput("")
                        }
                    },
                    enabled = uiState.terminalConnected,
                    modifier = Modifier.background(
                        if (uiState.terminalConnected) Color(0xFF6366F1) else Color(0x1AFFFFFF),
                        RoundedCornerShape(12.dp)
                    )
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }

    // Quick Command Buttons Card
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x14FFFFFF))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Acciones Rápidas (Comandos más usados)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF22D3EE),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Direct connection checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateDirectNodeMode(!uiState.directNodeMode) }
                    .padding(bottom = 12.dp)
            ) {
                Checkbox(
                    checked = uiState.directNodeMode,
                    onCheckedChange = { viewModel.updateDirectNodeMode(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF6366F1),
                        checkmarkColor = Color.White
                    )
                )
                Text(
                    text = "Cable conectado directo al Nodo (Sin 'cmd')",
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }

            // Selector Mote ID
            if (!uiState.directNodeMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Text("Dirección Corta Mote ID (Nodo):", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = uiState.moteIdInput,
                        onValueChange = { viewModel.updateMoteIdInput(it) },
                        modifier = Modifier.width(60.dp).height(50.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1)
                        ),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                    )
                }
            }

            val cmdPrefix = if (uiState.directNodeMode) "" else "cmd ${uiState.moteIdInput.trim()} "

            // Group: Pancoordinator commands
            if (!uiState.directNodeMode) {
                Text("Comandos locales Pancoordinator (Antena)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    QuickCmdBtn("status", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("status") }
                    QuickCmdBtn("motes", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("motes") }
                    QuickCmdBtn("stats", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("statistics") }
                    QuickCmdBtn("sleep", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("sleep") }
                    QuickCmdBtn("reboot", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("reboot") }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Group: Node commands
            Text(
                text = if (uiState.directNodeMode) "Comandos locales del Nodo Jennic" else "Comandos remotos del Nodo Jennic",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                QuickCmdBtn("status", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("${cmdPrefix}status") }
                QuickCmdBtn("config", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("${cmdPrefix}config") }
                QuickCmdBtn("commit", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("${cmdPrefix}commit") }
                QuickCmdBtn("sleep", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("${cmdPrefix}sleep") }
                QuickCmdBtn("reboot", enabled = uiState.terminalConnected, modifier = Modifier.weight(1f)) { viewModel.sendTerminalCommand("${cmdPrefix}reboot") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Group: Advanced Node Config
            Text("Configuración Avanzada del Nodo", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))

            // Change name
            ConfigRow(
                label = "Nombre:",
                value = uiState.nameInput,
                onValueChange = { viewModel.updateNameInput(it) },
                placeholder = "NODO_VALV",
                buttonText = "Cambiar",
                enabled = uiState.terminalConnected
            ) {
                if (uiState.nameInput.isNotBlank()) {
                    viewModel.sendTerminalCommand("${cmdPrefix}name ${uiState.nameInput.trim()}")
                    viewModel.sendTerminalCommand("${cmdPrefix}name")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Change panid
            ConfigRow(
                label = "PAN-ID:",
                value = uiState.panIdInput,
                onValueChange = { viewModel.updatePanIdInput(it) },
                placeholder = "1234",
                buttonText = "Establecer",
                enabled = uiState.terminalConnected
            ) {
                if (uiState.panIdInput.isNotBlank()) {
                    viewModel.sendTerminalCommand("${cmdPrefix}panid ${uiState.panIdInput.trim()}")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Change Interval
            ConfigRow(
                label = "Muestreo (seg):",
                value = uiState.intervalInput,
                onValueChange = { viewModel.updateIntervalInput(it) },
                placeholder = "300",
                buttonText = "Muestrear",
                enabled = uiState.terminalConnected
            ) {
                if (uiState.intervalInput.isNotBlank()) {
                    viewModel.sendTerminalCommand("${cmdPrefix}interval ${uiState.intervalInput.trim()}")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Change Contrast
            ConfigRow(
                label = "Contraste:",
                value = uiState.contrastInput,
                onValueChange = { viewModel.updateContrastInput(it) },
                placeholder = "10",
                buttonText = "Ajustar",
                enabled = uiState.terminalConnected
            ) {
                if (uiState.contrastInput.isNotBlank()) {
                    viewModel.sendTerminalCommand("${cmdPrefix}contrast ${uiState.contrastInput.trim()}")
                    viewModel.sendTerminalCommand("${cmdPrefix}contrast")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Change Levels (Oxygen limits)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Inyección Oxg (min/max):", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                OutlinedTextField(
                    value = uiState.levelsMinInput,
                    onValueChange = { viewModel.updateLevelsMinInput(it) },
                    placeholder = { Text("4.2", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, textAlign = TextAlign.Center)
                )
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = uiState.levelsMaxInput,
                    onValueChange = { viewModel.updateLevelsMaxInput(it) },
                    placeholder = { Text("5.0", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, textAlign = TextAlign.Center)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = {
                        viewModel.sendTerminalCommand("${cmdPrefix}levels ${uiState.levelsMinInput.trim()} ${uiState.levelsMaxInput.trim()}")
                    },
                    enabled = uiState.terminalConnected,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Ok", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Change IMode
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Modo Inyección:", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { viewModel.sendTerminalCommand("${cmdPrefix}imode auto") },
                        enabled = uiState.terminalConnected,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Auto", color = Color.White, fontSize = 10.sp)
                    }
                    Button(
                        onClick = { viewModel.sendTerminalCommand("${cmdPrefix}imode on") },
                        enabled = uiState.terminalConnected,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("On", color = Color.White, fontSize = 10.sp)
                    }
                    Button(
                        onClick = { viewModel.sendTerminalCommand("${cmdPrefix}imode off") },
                        enabled = uiState.terminalConnected,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("Off", color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cable Length Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Largo Cable:", color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.width(130.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(5, 10, 15).forEach { length ->
                        Button(
                            onClick = {
                                viewModel.sendTerminalCommand("${cmdPrefix}spower")
                                viewModel.sendTerminalCommand("${cmdPrefix}tunnel SENS1 cable $length")
                                viewModel.sendTerminalCommand("${cmdPrefix}tunnel SENS2 cable $length")
                            },
                            enabled = uiState.terminalConnected,
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("${length}m", color = Color.White, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    buttonText: String,
    enabled: Boolean,
    onButtonClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.LightGray, fontSize = 11.sp, modifier = Modifier.width(130.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 11.sp, color = Color.DarkGray) },
            modifier = Modifier.weight(1f).height(48.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF6366F1),
                unfocusedBorderColor = Color.DarkGray
            ),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Button(
            onClick = onButtonClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 8.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
            modifier = Modifier.height(36.dp)
        ) {
            Text(buttonText, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun QuickCmdBtn(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF6366F1),
            disabledContainerColor = Color(0x1AFFFFFF)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(34.dp)
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else Color.Gray)
    }
}

@Composable
fun borderStroke(): BorderStroke {
    return BorderStroke(1.dp, Color(0x14FFFFFF))
}

@Composable
fun OutlinedBox(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Column {
        Text(text = label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = value, color = Color.White, fontSize = 14.sp)
            Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = Color.LightGray)
        }
    }
}

@Composable
fun RemoteFirmwareRow(
    firmware: PcFirmware,
    isDownloading: Boolean,
    onDownloadClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .background(Color(0x0AFFFFFF), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(firmware.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Peso: ${firmware.size}  |  Modificado: ${firmware.mtimeStr}", color = Color.Gray, fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onDownloadClick,
            enabled = !isDownloading,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22D3EE)),
            modifier = Modifier.height(32.dp)
        ) {
            Text("Bajar", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatusBadge(status: FlashingStatus) {
    val (text, color) = when (status) {
        FlashingStatus.IDLE -> "Listo" to Color.Gray
        FlashingStatus.SYNCING -> "Sincronizando" to Color(0xFF6366F1)
        FlashingStatus.DOWNLOADING -> "Descargando" to Color(0xFF22D3EE)
        FlashingStatus.FLASHING -> "Grabando" to Color(0xFFF59E0B)
        FlashingStatus.SUCCESS -> "Éxito" to Color(0xFF10B981)
        FlashingStatus.ERROR -> "Error" to Color(0xFFEF4444)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(99.dp))
            .border(1.dp, color, RoundedCornerShape(99.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(50.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
