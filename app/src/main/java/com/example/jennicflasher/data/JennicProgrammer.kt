package com.example.jennicflasher.data

import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JennicProgrammer(
    private val port: UsbSerialPort,
    private val onProgress: (Int) -> Unit,
    private val onLog: (String) -> Unit
) {

    companion object {
        private const val TAG = "JennicProgrammer"
        
        // Commands
        private const val CHIP_ID_REQUEST: Byte = 0x32
        private const val READ_RAM_REQUEST: Byte = 0x1F
        private const val SELECT_FLASH_TYPE_REQUEST: Byte = 0x2C
        private const val FLASH_ID_REQUEST: Byte = 0x25
        private const val CHANGE_BAUD_RATE_REQUEST: Byte = 0x27
        private const val FLASH_ERASE_ALL_REQUEST: Byte = 0x07
        private const val FLASH_PROGRAM_REQUEST: Byte = 0x09
        private const val SET_RESET_REQUEST: Byte = 0x14
        
        private const val BLOCK_READ_SIZE = 128
    }

    private val readBuffer = ByteArray(1024)
    private val readQueue = ArrayList<Byte>()

    private fun readBytes(count: Int, timeoutMs: Int): ByteArray? {
        val startTime = System.currentTimeMillis()
        while (readQueue.size < count) {
            val elapsed = System.currentTimeMillis() - startTime
            val remainingTimeout = (timeoutMs - elapsed).toInt()
            if (remainingTimeout <= 0) {
                return null
            }
            
            try {
                // Siempre leemos usando un buffer de 1024 bytes para evitar "Read buffer too small"
                val bytesRead = port.read(readBuffer, Math.max(100, remainingTimeout))
                if (bytesRead > 0) {
                    for (i in 0 until bytesRead) {
                        readQueue.add(readBuffer[i])
                    }
                } else if (bytesRead < 0) {
                    return null
                } else {
                    Thread.sleep(10)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error en readBytes", e)
                return null
            }
        }

        val result = ByteArray(count)
        for (i in 0 until count) {
            result[i] = readQueue.removeAt(0)
        }
        return result
    }

    private fun calculateXorChecksum(data: ByteArray, length: Int): Byte {
        var checksum: Byte = 0
        for (i in 0 until length) {
            checksum = (checksum.toInt() xor data[i].toInt()).toByte()
        }
        return checksum
    }

    private fun sendRequest(cmd: Byte, payload: ByteArray = ByteArray(0)) {
        readQueue.clear() // Limpiar bytes huérfanos anteriores
        val len = 1 + payload.size + 1 // cmd_id + payload + checksum
        val buffer = ByteArray(1 + len)
        buffer[0] = len.toByte()
        buffer[1] = cmd
        System.arraycopy(payload, 0, buffer, 2, payload.size)
        buffer[buffer.size - 1] = calculateXorChecksum(buffer, buffer.size - 1)
        
        port.write(buffer, 2000)
    }

    private fun getResponse(timeoutMs: Int = 2000): ByteArray? {
        val lenBuf = readBytes(1, timeoutMs)
        if (lenBuf == null) {
            Log.w(TAG, "No response size received")
            return null
        }
        
        val responseSize = lenBuf[0].toInt() and 0xFF
        if (responseSize <= 0) return null

        val response = readBytes(responseSize, timeoutMs)
        if (response == null) {
            Log.w(TAG, "Timeout reading response payload")
            return null
        }

        // Reconstruct full packet for verification
        val fullPacket = ByteArray(1 + responseSize)
        fullPacket[0] = lenBuf[0]
        System.arraycopy(response, 0, fullPacket, 1, responseSize)

        // Verify checksum
        val checksum = calculateXorChecksum(fullPacket, fullPacket.size)
        if (checksum.toInt() != 0) {
            Log.e(TAG, "Checksum error: $checksum")
            onLog("ERROR: Error de Checksum en respuesta del nodo\n")
            return null
        }

        // Verify status byte
        if (responseSize < 3) return ByteArray(0)
        val status = fullPacket[2].toInt() and 0xFF
        if (status != 0) {
            Log.e(TAG, "Response status code error: $status")
            onLog("ERROR: El nodo retornó código de estado de error: $status\n")
            return null
        }

        // Return payload (skip length, cmd, status, and last checksum byte)
        if (responseSize < 3) return ByteArray(0)
        val payloadSize = responseSize - 3
        val payload = ByteArray(payloadSize)
        System.arraycopy(fullPacket, 3, payload, 0, payloadSize)
        return payload
    }

    fun flash(inputStream: InputStream, slowMode: Boolean, totalBytes: Int): Boolean {
        try {
            // Toggles RTS/DTR to enter bootloader mode automatically if hardware supports it
            try {
                port.setRTS(true)
                port.setDTR(true)
                Thread.sleep(100)
                port.setDTR(false)
                Thread.sleep(100)
                port.setRTS(false)
                Thread.sleep(100)
            } catch (e: Exception) {
                Log.w(TAG, "RTS/DTR not supported: ${e.message}")
            }

            onLog("🔌 Abriendo puerto serial a 38400 baudios...\n")
            port.setParameters(38400, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            Thread.sleep(100)

            onLog("🔍 Obteniendo Chip ID...\n")
            sendRequest(CHIP_ID_REQUEST)
            val chipId = getResponse()
            if (chipId == null) {
                onLog("❌ ERROR: No se pudo obtener el Chip ID. ¿El nodo está en modo programación?\n")
                return false
            }
            onLog("Chip ID: ${chipId.joinToString("") { String.format("%02X", it) }}\n")

            onLog("🔍 Leyendo dirección MAC...\n")
            // Address 0x01001580, Size 8
            val macPayload = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x01001580)
                .putShort(8.toShort())
                .array()
            sendRequest(READ_RAM_REQUEST, macPayload)
            val macAddress = getResponse()
            if (macAddress != null) {
                onLog("Dirección MAC: ${macAddress.joinToString(":") { String.format("%02X", it) }}\n")
            }

            onLog("⚙️ Seleccionando tipo de Flash (JN5168)...\n")
            // 0x08, 0x00, 0x00, 0x00, 0x00
            val selectFlashPayload = byteArrayOf(0x08, 0x00, 0x00, 0x00, 0x00)
            sendRequest(SELECT_FLASH_TYPE_REQUEST, selectFlashPayload)
            getResponse()

            onLog("🔍 Leyendo ID de Flash...\n")
            sendRequest(FLASH_ID_REQUEST)
            val flashId = getResponse()
            if (flashId != null) {
                onLog("Flash ID: ${flashId.joinToString("") { String.format("%02X", it) }}\n")
            }

            onLog("🧹 Borrando memoria Flash del nodo...\n")
            sendRequest(FLASH_ERASE_ALL_REQUEST)
            if (getResponse(10000) == null) { // Erase can take longer
                onLog("❌ ERROR: Falló el borrado de la memoria Flash.\n")
                return false
            }
            onLog("✨ Memoria Flash borrada.\n")

            // Baudrate acceleration
            if (!slowMode) {
                onLog("🚀 Acelerando velocidad de transmisión a 1,000,000 baudios...\n")
                sendRequest(CHANGE_BAUD_RATE_REQUEST, byteArrayOf(1)) // 1 = 1 Mbps
                getResponse()
                Thread.sleep(50)
                port.setParameters(1000000, UsbSerialPort.DATABITS_8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                Thread.sleep(100)
            }

            onLog("💾 Iniciando grabación de bloques...\n")
            inputStream.skip(4) // Omitir cabecera binaria de 4 bytes (idéntico a jn516xprog)
            val adjustedTotal = if (totalBytes > 4) totalBytes - 4 else totalBytes
            val buffer = ByteArray(BLOCK_READ_SIZE)
            var bytesRead: Int
            var address = 0
            var totalWritten = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val block = if (bytesRead < BLOCK_READ_SIZE) {
                    val trimmed = ByteArray(bytesRead)
                    System.arraycopy(buffer, 0, trimmed, 0, bytesRead)
                    trimmed
                } else {
                    buffer
                }

                // Command structure: address (4 bytes LE) + block data
                val programPayload = ByteArray(4 + block.size)
                ByteBuffer.wrap(programPayload).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(address)
                System.arraycopy(block, 0, programPayload, 4, block.size)

                sendRequest(FLASH_PROGRAM_REQUEST, programPayload)
                if (getResponse() == null) {
                    onLog("❌ ERROR: Falló la escritura en la dirección 0x${String.format("%08X", address)}\n")
                    return false
                }

                address += block.size
                totalWritten += block.size
                val percentage = (totalWritten * 100 / adjustedTotal)
                onProgress(percentage)
            }

            onLog("🔄 Reiniciando el nodo Jennic...\n")
            sendRequest(SET_RESET_REQUEST)
            getResponse()

            // Try to force a hardware reset toggle via DTR pin if supported
            try {
                port.setDTR(true)
                Thread.sleep(100)
                port.setDTR(false)
            } catch (e: Exception) {}

            onLog("🎉 ¡Grabación completada exitosamente!\n")
            return true
        } catch (e: Exception) {
            onLog("❌ ERROR: Excepción durante el proceso: ${e.message}\n")
            Log.e(TAG, "Error flashing", e)
            return false
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {}
        }
    }
}
