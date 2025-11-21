package com.example.myapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapp.mfa.BiometricHelper
import com.example.myapp.mfa.BiometricState
import com.example.myapp.mfa.BluetoothState
import com.example.myapp.mfa.MfaScreen
import com.example.myapp.mfa.MfaUiState
import com.example.myapp.mfa.MfaViewModel
import com.example.myapp.ui.theme.MyAppTheme
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyAppTheme {
                val viewModel: MfaViewModel =
                    viewModel(factory = MfaViewModel.factory(application))
                val uiState by viewModel.uiState.collectAsState()
                val biometricHelper = remember {
                    BiometricHelper(
                        activity = this@MainActivity,
                        onSuccess = { viewModel.recordBiometricSuccess(it) },
                        onFailure = { viewModel.recordBiometricFailure(it) }
                    )
                }

                val permissionLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                        val granted = requiredBluetoothPermissions().all { result[it] == true }
                        viewModel.markBluetoothPermission(granted)
                    }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                        viewModel.markBluetoothPermission(true)
                    }
                }

                val supportsBiometric = remember { biometricHelper.canUseBiometric() }

                MfaPrototypeApp(
                    uiState = uiState,
                    supportsBiometric = supportsBiometric,
                    requestPermissions = { permissionLauncher.launch(requiredBluetoothPermissions()) },
                    onAddressChange = viewModel::updateDeviceAddress,
                    connectAction = viewModel::connectToModule,
                    disconnectAction = viewModel::disconnect,
                    navigateToBiometrics = viewModel::navigateToBiometrics,
                    onBiometricRequest = viewModel::onBiometricRequested,
                    startRealBiometric = { biometricHelper.authenticate() },
                    onEmulatedBiometricResult = viewModel::emulateBiometricResult,
                    sendPacket = viewModel::sendAuthPacket,
                    restartFlow = viewModel::restartFlow
                )
            }
        }
    }
}

private fun requiredBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MfaPrototypeApp(
    uiState: MfaUiState,
    supportsBiometric: Boolean,
    requestPermissions: () -> Unit,
    onAddressChange: (String) -> Unit,
    connectAction: () -> Unit,
    disconnectAction: () -> Unit,
    navigateToBiometrics: () -> Unit,
    onBiometricRequest: () -> Unit,
    startRealBiometric: () -> Unit,
    onEmulatedBiometricResult: (Boolean) -> Unit,
    sendPacket: () -> Unit,
    restartFlow: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("MFA + Arduino Prototype") }) }
    ) { padding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp)

        when (uiState.currentScreen) {
            MfaScreen.Bluetooth -> BluetoothScreen(
                modifier = screenModifier,
                uiState = uiState,
                requestPermissions = requestPermissions,
                onAddressChange = onAddressChange,
                connectAction = connectAction,
                disconnectAction = disconnectAction,
                navigateToBiometrics = navigateToBiometrics
            )

            MfaScreen.Biometrics -> BiometricScreen(
                modifier = screenModifier,
                uiState = uiState,
                supportsBiometric = supportsBiometric,
                onBiometricRequest = onBiometricRequest,
                startRealBiometric = startRealBiometric,
                onEmulatedBiometricResult = onEmulatedBiometricResult,
                sendPacket = sendPacket
            )

            MfaScreen.Result -> ResultScreen(
                modifier = screenModifier,
                uiState = uiState,
                restartFlow = restartFlow
            )
        }
    }
}

@Composable
private fun BluetoothScreen(
    modifier: Modifier,
    uiState: MfaUiState,
    requestPermissions: () -> Unit,
    onAddressChange: (String) -> Unit,
    connectAction: () -> Unit,
    disconnectAction: () -> Unit,
    navigateToBiometrics: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Screen 1 — Bluetooth",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = uiState.deviceAddress,
            onValueChange = onAddressChange,
            label = { Text("HC-05 MAC (AA:BB:CC:DD:EE:FF)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = requestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Request Bluetooth Permission")
        }
        Button(
            onClick = connectAction,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.hasBluetoothPermission
        ) {
            Text("Connect to HC-05")
        }
        Button(
            onClick = disconnectAction,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.bluetoothState is BluetoothState.Connected
        ) {
            Text("Disconnect")
        }
        Text("Status", style = MaterialTheme.typography.labelLarge)
        StatusBadge(label = bluetoothStatusLabel(uiState.bluetoothState))
        Button(
            onClick = navigateToBiometrics,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.canNavigateToBiometrics
        ) {
            Text("Go to biometrics")
        }
    }
}

@Composable
private fun BiometricScreen(
    modifier: Modifier,
    uiState: MfaUiState,
    supportsBiometric: Boolean,
    onBiometricRequest: () -> Unit,
    startRealBiometric: () -> Unit,
    onEmulatedBiometricResult: (Boolean) -> Unit,
    sendPacket: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Screen 2 — Biometrics",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        StatusBadge(label = biometricStatusLabel(uiState.biometricState))
        Text(
            text = "Biometric result: ${uiState.biometricValue ?: "Waiting"}",
            style = MaterialTheme.typography.bodyLarge
        )
        Button(
            onClick = {
                onBiometricRequest()
                if (supportsBiometric) {
                    startRealBiometric()
                } else {
                    showDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start biometric")
        }
        Button(
            onClick = sendPacket,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.canSendPacket
        ) {
            Text("Send to Arduino")
        }
    }

    if (!supportsBiometric && showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Emulate biometric") },
            text = { Text("Choose Success or Fail to emulate the fingerprint gate.") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onEmulatedBiometricResult(true)
                }) {
                    Text("Success")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    onEmulatedBiometricResult(false)
                }) {
                    Text("Fail")
                }
            }
        )
    }
}

@Composable
private fun ResultScreen(
    modifier: Modifier,
    uiState: MfaUiState,
    restartFlow: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Screen 3 — Result",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text("Data from Arduino", style = MaterialTheme.typography.labelLarge)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = uiState.receivedJson ?: "Waiting for Bluetooth data...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
        Text(
            text = "Session ID: ${uiState.sessionId ?: "--"}",
            style = MaterialTheme.typography.bodyLarge
        )
        StatusBadge(
            label = uiState.finalResult ?: "Final result pending"
        )
        Button(
            onClick = restartFlow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Restart")
        }
    }
}

@Composable
private fun StatusBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            )
            .padding(8.dp)
    )
}

private fun bluetoothStatusLabel(state: BluetoothState): String = when (state) {
    BluetoothState.Idle -> "Bluetooth: Idle"
    is BluetoothState.Connecting -> "Connecting to ${state.target}"
    is BluetoothState.Connected -> "Connected to ${state.deviceName ?: "HC-05"}"
    is BluetoothState.Error -> "Error: ${state.reason}"
}

private fun biometricStatusLabel(state: BiometricState): String = when (state) {
    BiometricState.Idle -> "Biometric: Waiting"
    BiometricState.Running -> "Biometric: Running..."
    is BiometricState.Success -> "Success via ${state.mode.label}"
    is BiometricState.Failure -> "Failed: ${state.message}"
}
