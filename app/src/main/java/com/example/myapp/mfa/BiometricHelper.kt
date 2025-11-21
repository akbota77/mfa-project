package com.example.myapp.mfa

import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val BIOMETRIC_TITLE = "Prototype Biometric Gate"
private const val BIOMETRIC_SUBTITLE = "Unlock DFA channel with fingerprint or face"
private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

class BiometricHelper(
    private val activity: FragmentActivity,
    private val onSuccess: (BiometricMode) -> Unit,
    private val onFailure: (String) -> Unit
) {

    private val executor = ContextCompat.getMainExecutor(activity)
    private val biometricManager = BiometricManager.from(activity)
    private val prompt: BiometricPrompt by lazy {
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess(realMode())
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailure("Error ${errorCode}: $errString")
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailure("Biometric did not match, try again")
                }
            }
        )
    }

    private val promptInfo: BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(BIOMETRIC_TITLE)
        .setSubtitle(BIOMETRIC_SUBTITLE)
        .setConfirmationRequired(false)
        .setAllowedAuthenticators(AUTHENTICATORS)
        .setNegativeButtonText("Cancel")
        .build()

    fun canUseBiometric(): Boolean =
        biometricManager.canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate() {
        prompt.authenticate(promptInfo)
    }

    private fun realMode(): BiometricMode {
        val pm = activity.packageManager
        return if (pm.hasSystemFeature(PackageManager.FEATURE_FACE)) {
            BiometricMode.Face
        } else {
            BiometricMode.Fingerprint
        }
    }
}


