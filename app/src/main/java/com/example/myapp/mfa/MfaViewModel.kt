package com.example.myapp.mfa

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID
import kotlin.random.Random

private val HC05_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class MfaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MfaUiState())
    val uiState: StateFlow<MfaUiState> = _uiState.asStateFlow()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = application.getSystemService(BluetoothManager::class.java)
        manager?.adapter
    }

    private var bluetoothSocket: BluetoothSocket? = null

    fun updateUserId(value: String) {
        _uiState.value = _uiState.value.copy(userId = value)
    }

    fun updateDeviceAddress(value: String) {
        _uiState.value = _uiState.value.copy(deviceAddress = value.uppercase().trim())
    }

    fun markBluetoothPermission(granted: Boolean) {
        if (granted != _uiState.value.hasBluetoothPermission) {
            appendLog(if (granted) "Bluetooth permissions granted" else "Bluetooth permissions denied")
        }
        _uiState.value = _uiState.value.copy(hasBluetoothPermission = granted)
    }

    fun connectToModule() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            updateBluetoothState(BluetoothState.Error("Bluetooth adapter not available"))
            return
        }
        val address = _uiState.value.deviceAddress
        if (address.length < 11) {
            updateBluetoothState(BluetoothState.Error("Invalid HC-05 MAC address"))
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            updateBluetoothState(BluetoothState.Connecting(address))
            try {
                val remoteDevice = adapter.getRemoteDevice(address)
                adapter.cancelDiscovery()
                bluetoothSocket?.close()
                val socket =
                    remoteDevice.createInsecureRfcommSocketToServiceRecord(HC05_UUID)
                socket.connect()
                bluetoothSocket = socket
                updateBluetoothState(BluetoothState.Connected(remoteDevice.name))
                appendLog("Connected to ${remoteDevice.name ?: "HC-05"}")
            } catch (e: Exception) {
                bluetoothSocket = null
                updateBluetoothState(
                    BluetoothState.Error("Connection failed: ${e.localizedMessage}")
                )
                appendLog("Connection error: ${e.localizedMessage}")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bluetoothSocket?.close()
            } catch (_: Exception) {
            } finally {
                bluetoothSocket = null
                updateBluetoothState(BluetoothState.Idle)
                appendLog("Disconnected from HC-05")
                _uiState.value = _uiState.value.copy(currentScreen = MfaScreen.Bluetooth)
            }
        }
    }
    fun navigateToBiometrics() {
        if (_uiState.value.canNavigateToBiometrics) {
            _uiState.value = _uiState.value.copy(currentScreen = MfaScreen.Biometrics)
        }
    }

    fun restartFlow() {
        _uiState.value = _uiState.value.copy(
            currentScreen = MfaScreen.Bluetooth,
            biometricState = BiometricState.Idle,
            biometricValue = null,
            receivedJson = null,
            sessionId = null,
            finalResult = null,
            lastDecision = null,
            lastJsonRequest = null
        )
        appendLog("Flow restarted")
    }

    fun onBiometricRequested() {
        _uiState.value = _uiState.value.copy(biometricState = BiometricState.Running)
    }

    fun recordBiometricSuccess(mode: BiometricMode) {
        val snapshot = buildBiometricSnapshot(mode)
        _uiState.value = _uiState.value.copy(
            biometricState = BiometricState.Success(mode, snapshot),
            biometricValue = "ok"
        )
        appendLog("Biometric success via ${mode.label}")
    }

    fun recordBiometricFailure(message: String) {
        _uiState.value = _uiState.value.copy(
            biometricState = BiometricState.Failure(message),
            biometricValue = "fail"
        )
        appendLog("Biometric failed: $message")
    }

    fun emulateBiometricResult(success: Boolean) {
        if (success) {
            recordBiometricSuccess(BiometricMode.Demo)
        } else {
            recordBiometricFailure("Emulated failure")
        }
    }

    fun sendAuthPacket() {
        val biometricValue = _uiState.value.biometricValue
        val socket = bluetoothSocket

        if (biometricValue == null) {
            appendLog("No biometric result available")
            return
        }
        if (socket == null) {
            appendLog("Bluetooth socket is null")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val payload = composeJsonPayload(biometricValue)
                socket.outputStream.write(payload.plus("\n").toByteArray())
                socket.outputStream.flush()
                updateLastJson(payload)
                appendLog("Sent auth packet (${payload.length} chars)")

                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                val response = reader.readLine()
                val decision = parseDecision(response)
                _uiState.value = _uiState.value.copy(
                    lastDecision = decision,
                    receivedJson = if (decision.rawJson.isNotBlank()) decision.rawJson else response.orEmpty(),
                    sessionId = decision.sessionId,
                    finalResult = decision.displayResult,
                    currentScreen = MfaScreen.Result
                )
                appendLog("Arduino response: ${decision.result}")
            } catch (e: Exception) {
                appendLog("I/O error: ${e.localizedMessage}")
            }
        }
    }

    private fun composeJsonPayload(biometricValue: String): String {
        return JSONObject()
            .put("biometric", biometricValue)
            .toString()
    }

    private fun parseDecision(response: String?): ArduinoDecision {
        if (response.isNullOrBlank()) {
            return ArduinoDecision(null, "deny", "")
        }
        return try {
            val json = JSONObject(response)
            val sessionId = json.opt("session_id")?.toString()
            val result = json.optString("result", "deny")
            ArduinoDecision(sessionId, result, json.toString(2))
        } catch (e: Exception) {
            ArduinoDecision(null, "deny", response)
        }
    }

    private fun buildBiometricSnapshot(mode: BiometricMode): BiometricSnapshot {
        val timestamp = System.currentTimeMillis()
        val tokenSeed = "${_uiState.value.userId}-${mode.name}-$timestamp-${UUID.randomUUID()}"
        return BiometricSnapshot(
            mode = mode,
            timestamp = timestamp,
            signalQuality = Random.nextInt(80, 101),
            token = tokenSeed.takeLast(16),
            dfaHash = tokenSeed.sha256()
        )
    }

    private fun updateBluetoothState(newState: BluetoothState) {
        _uiState.value = _uiState.value.copy(bluetoothState = newState)
    }

    private fun updateLastJson(payload: String) {
        _uiState.value = _uiState.value.copy(lastJsonRequest = payload)
    }

    private fun appendLog(entry: String) {
        _uiState.value = _uiState.value.copy(
            log = (_uiState.value.log + "${System.currentTimeMillis()}: $entry")
                .takeLast(8)
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            bluetoothSocket?.close()
        } catch (_: Exception) {
        }
        bluetoothSocket = null
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MfaViewModel::class.java)) {
                        return MfaViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}

private fun String.sha256(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(toByteArray())
    return hash.joinToString("") { "%02x".format(it) }
}

