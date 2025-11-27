package com.example.myapp.mfa

private const val DEFAULT_ADDRESS = "00:00:00:00:00:00"

enum class BiometricMode(val label: String) {
    Fingerprint("Real Fingerprint"),
    Face("Real Face"),
    Demo("Emulated Demo")
}

enum class MfaScreen {
    Bluetooth,
    Biometrics,
    Result
}

sealed interface BluetoothState {
    data object Idle : BluetoothState
    data class Connecting(val target: String) : BluetoothState
    data class Connected(val deviceName: String?) : BluetoothState
    data class Error(val reason: String) : BluetoothState
}

sealed interface BiometricState {
    data object Idle : BiometricState
    data object Running : BiometricState
    data class Success(val mode: BiometricMode, val snapshot: BiometricSnapshot) : BiometricState
    data class Failure(val message: String) : BiometricState
}

data class BiometricSnapshot(
    val mode: BiometricMode,
    val timestamp: Long,
    val signalQuality: Int,
    val token: String,
    val dfaHash: String
)

data class ArduinoDecision(
    val sessionId: String?,
    val result: String,
    val rawJson: String
) {
    val allow: Boolean = result.equals("allow", ignoreCase = true)
    val displayResult: String = if (allow) "Access granted" else "Access denied"
}

data class MfaUiState(
    val userId: String = "researcher01",
    val deviceAddress: String = DEFAULT_ADDRESS,
    val bluetoothState: BluetoothState = BluetoothState.Idle,
    val biometricState: BiometricState = BiometricState.Idle,
    val lastDecision: ArduinoDecision? = null,
    val lastJsonRequest: String? = null,
    val hasBluetoothPermission: Boolean = false,
    val log: List<String> = emptyList(),
    val currentScreen: MfaScreen = MfaScreen.Bluetooth,
    val biometricValue: String? = null,
    val receivedJson: String? = null,
    val sessionId: String? = null,
    val finalResult: String? = null
) {
    val canNavigateToBiometrics: Boolean = bluetoothState is BluetoothState.Connected
    val canSendPacket: Boolean = canNavigateToBiometrics && biometricValue != null
}
