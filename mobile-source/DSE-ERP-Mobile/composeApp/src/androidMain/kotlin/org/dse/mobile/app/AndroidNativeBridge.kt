package org.dse.mobile.app

import kotlinx.coroutines.suspendCancellableCoroutine
import org.dse.mobile.core.api.SessionStore
import java.time.LocalDate
import kotlin.coroutines.resume

private object AndroidNativeHandlers {
    var loadToken: () -> String? = { null }
    var saveToken: (String?) -> Unit = {}
    var clearToken: () -> Unit = {}
    var loadServerUrl: () -> String? = { null }
    var saveServerUrl: (String) -> Unit = {}
    var biometricState: () -> String = { "UNAVAILABLE" }
    var authenticateBiometric: (String, (String, String?) -> Unit) -> Unit = { _, done -> done("ERROR", "Android biometric service is not configured") }
    var pickImportFile: ((String?, String?, String?) -> Unit) -> Unit = { done -> done(null, null, "Android document picker is not configured") }
    var requestPush: ((String, String?) -> Unit) -> Unit = { done -> done("ERROR", "Android notification service is not configured") }
    var pickAttachment: ((String?, String?, String?) -> Unit) -> Unit = { done -> done(null, null, "Android attachment picker is not configured") }
    var shareText: (String, String) -> Boolean = { _, _ -> false }
    var shareFile: (String, String, String) -> Boolean = { _, _, _ -> false }
    var openExternalUrl: (String) -> Boolean = { false }
    var installMobileUpdate: (String, (String, String?) -> Unit) -> Unit = { _, done -> done("ERROR", "Android mobile updater is not configured") }
    var consumeDeepLink: () -> String? = { null }
    var publishWidgetSnapshot: (Double, Double, Double, Double, Double, Double, Long) -> Unit = { _, _, _, _, _, _, _ -> }
    var startShippingActivity: (String, String, String, String, (String, String?) -> Unit) -> Unit = { _, _, _, _, done -> done("ERROR", "Android shipping status service is not configured") }
    var updateShippingActivity: (String, String, (String, String?) -> Unit) -> Unit = { _, _, done -> done("ERROR", "Android shipping status service is not configured") }
    var endShippingActivity: (String, (String, String?) -> Unit) -> Unit = { _, done -> done("ERROR", "Android shipping status service is not configured") }
}

/** Installs the Android shell implementations used by the shared Compose UI. */
fun installAndroidNativeServices(
    loadToken: () -> String?,
    saveToken: (String?) -> Unit,
    clearToken: () -> Unit,
    loadServerUrl: () -> String?,
    saveServerUrl: (String) -> Unit,
    biometricState: () -> String,
    authenticateBiometric: (String, (String, String?) -> Unit) -> Unit,
    pickImportFile: ((String?, String?, String?) -> Unit) -> Unit,
    requestPush: ((String, String?) -> Unit) -> Unit,
    pickAttachment: ((String?, String?, String?) -> Unit) -> Unit,
    shareText: (String, String) -> Boolean,
    shareFile: (String, String, String) -> Boolean,
    openExternalUrl: (String) -> Boolean,
    installMobileUpdate: (String, (String, String?) -> Unit) -> Unit,
    consumeDeepLink: () -> String?,
    publishWidgetSnapshot: (Double, Double, Double, Double, Double, Double, Long) -> Unit,
    startShippingActivity: (String, String, String, String, (String, String?) -> Unit) -> Unit,
    updateShippingActivity: (String, String, (String, String?) -> Unit) -> Unit,
    endShippingActivity: (String, (String, String?) -> Unit) -> Unit,
) {
    AndroidNativeHandlers.loadToken = loadToken
    AndroidNativeHandlers.saveToken = saveToken
    AndroidNativeHandlers.clearToken = clearToken
    AndroidNativeHandlers.loadServerUrl = loadServerUrl
    AndroidNativeHandlers.saveServerUrl = saveServerUrl
    AndroidNativeHandlers.biometricState = biometricState
    AndroidNativeHandlers.authenticateBiometric = authenticateBiometric
    AndroidNativeHandlers.pickImportFile = pickImportFile
    AndroidNativeHandlers.requestPush = requestPush
    AndroidNativeHandlers.pickAttachment = pickAttachment
    AndroidNativeHandlers.shareText = shareText
    AndroidNativeHandlers.shareFile = shareFile
    AndroidNativeHandlers.openExternalUrl = openExternalUrl
    AndroidNativeHandlers.installMobileUpdate = installMobileUpdate
    AndroidNativeHandlers.consumeDeepLink = consumeDeepLink
    AndroidNativeHandlers.publishWidgetSnapshot = publishWidgetSnapshot
    AndroidNativeHandlers.startShippingActivity = startShippingActivity
    AndroidNativeHandlers.updateShippingActivity = updateShippingActivity
    AndroidNativeHandlers.endShippingActivity = endShippingActivity
}

private class AndroidSecureSessionStore : SessionStore {
    override fun accessToken(): String? = AndroidNativeHandlers.loadToken()?.takeIf { it.isNotBlank() }
    override fun saveAccessToken(token: String?) {
        if (token.isNullOrBlank()) AndroidNativeHandlers.clearToken() else AndroidNativeHandlers.saveToken(token)
    }
    override fun clear() = AndroidNativeHandlers.clearToken()
}

actual fun platformSessionStore(): SessionStore = AndroidSecureSessionStore()
actual fun platformLoadLastServerUrl(): String? = AndroidNativeHandlers.loadServerUrl()?.takeIf { it.isNotBlank() }
actual fun platformSaveLastServerUrl(url: String) { if (url.isNotBlank()) AndroidNativeHandlers.saveServerUrl(url.trim()) }
actual fun platformBiometricAvailable(): Boolean = AndroidNativeHandlers.biometricState().equals("AVAILABLE", ignoreCase = true)
actual suspend fun platformAuthenticateBiometric(reason: String): BiometricAuthResult = suspendCancellableCoroutine { continuation ->
    AndroidNativeHandlers.authenticateBiometric(reason) { status, message ->
        if (continuation.isActive) continuation.resume(BiometricAuthResult(status.equals("OK", true), message.orEmpty()))
    }
}
actual fun platformSecurityLabel(): String = "Android Keystore token • biometric/device credential unlock • ERP offline records use app-private storage"
actual fun platformImportCapability(): String = "Android: native Files picker • CSV import enabled"
actual fun platformLocalDateIso(): String = LocalDate.now().toString()
actual fun platformName(): String = "Android"
actual fun platformDeviceClassLabel(): String = "Android phone/tablet"
actual fun platformBiometricUnlockLabel(): String = "Unlock with biometrics"
actual fun platformImportFormatHint(): String = "Android accepts CSV files through the native Files picker."

internal suspend fun nativePickImportTextFileAndroid(): Triple<String?, String?, String?> = suspendCancellableCoroutine { continuation ->
    AndroidNativeHandlers.pickImportFile { fileName, text, error ->
        if (continuation.isActive) continuation.resume(Triple(fileName, text, error))
    }
}

actual suspend fun platformRequestPushNotifications(): PushPermissionResult = suspendCancellableCoroutine { continuation ->
    AndroidNativeHandlers.requestPush { status, token ->
        if (continuation.isActive) {
            val ok = status.equals("OK", true)
            continuation.resume(PushPermissionResult(ok, if (ok) "Android notification permission enabled." else status, token?.takeIf { it.isNotBlank() }))
        }
    }
}
actual fun platformPushCapability(): String =
    "Android notification permission is available. Jasvi Industries v10.0.7 has no device-token registration endpoint, so remote ERP push delivery is not enabled in this build."
actual fun platformPickAttachment(onResult:(PickedAttachment)->Unit) {
    AndroidNativeHandlers.pickAttachment { name, base64, error ->
        onResult(PickedAttachment(name, base64, error))
    }
}
actual fun platformShareText(title: String, text: String): Boolean = AndroidNativeHandlers.shareText(title, text)
actual fun platformShareFile(title: String, fileName: String, data: ByteArray): Boolean = AndroidNativeHandlers.shareFile(title, fileName, encodeBase64Portable(data))
actual fun platformOpenExternalUrl(url: String): Boolean = AndroidNativeHandlers.openExternalUrl(url)
actual suspend fun platformInstallMobileUpdate(version: String): MobileUpdateInstallResult = suspendCancellableCoroutine { continuation ->
    AndroidNativeHandlers.installMobileUpdate(version) { status, message ->
        if (continuation.isActive) {
            continuation.resume(MobileUpdateInstallResult(status.equals("OK", true), message ?: status))
        }
    }
}
actual fun platformConsumeDeepLink(): String? = AndroidNativeHandlers.consumeDeepLink()?.takeIf { it.isNotBlank() }

actual fun platformPublishWidgetSnapshot(snapshot: WidgetDashboardSnapshot) {
    AndroidNativeHandlers.publishWidgetSnapshot(
        snapshot.salesToday, snapshot.receivables, snapshot.purchases,
        snapshot.payables, snapshot.bankBalance, snapshot.expenseMonth,
        snapshot.updatedAtMillis,
    )
}
actual fun platformSystemExperienceCapability(): String =
    "Android: Jasvi Industries dashboard home-screen widget, app shortcuts/deep links and ongoing shipping status notification."
actual suspend fun platformStartShippingLiveActivity(
    invoiceNo: String,
    customer: String,
    transporter: String,
    vehicle: String,
): LiveActivityResult = suspendCancellableCoroutine { continuation ->
    AndroidNativeHandlers.startShippingActivity(invoiceNo, customer, transporter, vehicle) { status, message ->
        if (continuation.isActive) continuation.resume(LiveActivityResult(status.equals("OK", true), message ?: status))
    }
}
actual suspend fun platformUpdateShippingLiveActivity(invoiceNo: String, stage: String): LiveActivityResult = suspendCancellableCoroutine { continuation ->
    AndroidNativeHandlers.updateShippingActivity(invoiceNo, stage) { status, message ->
        if (continuation.isActive) continuation.resume(LiveActivityResult(status.equals("OK", true), message ?: status))
    }
}
actual suspend fun platformEndShippingLiveActivity(invoiceNo: String): LiveActivityResult = suspendCancellableCoroutine { continuation ->
    AndroidNativeHandlers.endShippingActivity(invoiceNo) { status, message ->
        if (continuation.isActive) continuation.resume(LiveActivityResult(status.equals("OK", true), message ?: status))
    }
}
