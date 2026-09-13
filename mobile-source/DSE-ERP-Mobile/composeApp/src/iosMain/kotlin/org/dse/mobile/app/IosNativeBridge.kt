package org.dse.mobile.app

import kotlinx.coroutines.suspendCancellableCoroutine
import org.dse.mobile.core.api.SessionStore
import kotlin.coroutines.resume
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.currentLocale
import platform.Foundation.localTimeZone

private object IosNativeHandlers {
    var loadToken: () -> String? = { null }
    var saveToken: (String?) -> Unit = {}
    var clearToken: () -> Unit = {}
    var loadServerUrl: () -> String? = { null }
    var saveServerUrl: (String) -> Unit = {}
    var biometricState: () -> String = { "UNAVAILABLE" }
    var authenticateBiometric: (String, (String, String?) -> Unit) -> Unit = { _, done -> done("ERROR", "Native biometric service is not configured") }
    var pickImportFile: ((String?, String?, String?) -> Unit) -> Unit = { done -> done(null, null, "Native document picker is not configured") }
    var requestPush: ((String, String?) -> Unit) -> Unit = { done -> done("ERROR", "Native push service is not configured") }
    var pickAttachment: ((String?, String?, String?) -> Unit) -> Unit = { done -> done(null, null, "Native attachment picker is not configured") }
    var shareText: (String, String) -> Boolean = { _, _ -> false }
    var shareFile: (String, String, String) -> Boolean = { _, _, _ -> false }
    var openExternalUrl: (String) -> Boolean = { false }
    var consumeDeepLink: () -> String? = { null }
    var publishWidgetSnapshot: (Double, Double, Double, Double, Double, Double, Long) -> Unit = { _, _, _, _, _, _, _ -> }
    var startShippingActivity: (String, String, String, String, (String, String?) -> Unit) -> Unit = { _, _, _, _, done -> done("ERROR", "Live Activity service is not configured") }
    var updateShippingActivity: (String, String, (String, String?) -> Unit) -> Unit = { _, _, done -> done("ERROR", "Live Activity service is not configured") }
    var endShippingActivity: (String, (String, String?) -> Unit) -> Unit = { _, done -> done("ERROR", "Live Activity service is not configured") }
}

/**
 * Called by the Swift iOS shell before MainViewController is created.
 * Keeping Apple frameworks in Swift gives the shared Compose UI a small, testable native bridge.
 */
fun installIosNativeServices(
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
    consumeDeepLink: () -> String?,
    publishWidgetSnapshot: (Double, Double, Double, Double, Double, Double, Long) -> Unit,
    startShippingActivity: (String, String, String, String, (String, String?) -> Unit) -> Unit,
    updateShippingActivity: (String, String, (String, String?) -> Unit) -> Unit,
    endShippingActivity: (String, (String, String?) -> Unit) -> Unit,
) {
    IosNativeHandlers.loadToken = loadToken
    IosNativeHandlers.saveToken = saveToken
    IosNativeHandlers.clearToken = clearToken
    IosNativeHandlers.loadServerUrl = loadServerUrl
    IosNativeHandlers.saveServerUrl = saveServerUrl
    IosNativeHandlers.biometricState = biometricState
    IosNativeHandlers.authenticateBiometric = authenticateBiometric
    IosNativeHandlers.pickImportFile = pickImportFile
    IosNativeHandlers.requestPush = requestPush
    IosNativeHandlers.pickAttachment = pickAttachment
    IosNativeHandlers.shareText = shareText
    IosNativeHandlers.shareFile = shareFile
    IosNativeHandlers.openExternalUrl = openExternalUrl
    IosNativeHandlers.consumeDeepLink = consumeDeepLink
    IosNativeHandlers.publishWidgetSnapshot = publishWidgetSnapshot
    IosNativeHandlers.startShippingActivity = startShippingActivity
    IosNativeHandlers.updateShippingActivity = updateShippingActivity
    IosNativeHandlers.endShippingActivity = endShippingActivity
}

private class IosKeychainSessionStore : SessionStore {
    override fun accessToken(): String? = IosNativeHandlers.loadToken()?.takeIf { it.isNotBlank() }
    override fun saveAccessToken(token: String?) {
        if (token.isNullOrBlank()) IosNativeHandlers.clearToken() else IosNativeHandlers.saveToken(token)
    }
    override fun clear() = IosNativeHandlers.clearToken()
}

actual fun platformSessionStore(): SessionStore = IosKeychainSessionStore()
actual fun platformLoadLastServerUrl(): String? = IosNativeHandlers.loadServerUrl()?.takeIf { it.isNotBlank() }
actual fun platformSaveLastServerUrl(url: String) { if (url.isNotBlank()) IosNativeHandlers.saveServerUrl(url.trim()) }
actual fun platformBiometricAvailable(): Boolean = IosNativeHandlers.biometricState().equals("AVAILABLE", ignoreCase = true)
actual suspend fun platformAuthenticateBiometric(reason: String): BiometricAuthResult = suspendCancellableCoroutine { continuation ->
    IosNativeHandlers.authenticateBiometric(reason) { status, message ->
        if (continuation.isActive) continuation.resume(
            BiometricAuthResult(status.equals("OK", ignoreCase = true), message.orEmpty())
        )
    }
}
actual fun platformSecurityLabel(): String = "Protected by iOS Keychain • Face ID / Touch ID ready"
actual fun platformImportCapability(): String = "iPhone/iPad: native Files picker • CSV import enabled"
actual fun platformName(): String = "iOS"
actual fun platformDeviceClassLabel(): String = "iPhone/iPad"
actual fun platformBiometricUnlockLabel(): String = "Face ID / Touch ID"
actual fun platformImportFormatHint(): String = "iOS accepts CSV files through the native Files picker."
actual fun platformLocalDateIso(): String {
    val formatter=NSDateFormatter().apply { dateFormat="yyyy-MM-dd"; locale=NSLocale.currentLocale; timeZone=NSTimeZone.localTimeZone }
    return formatter.stringFromDate(NSDate())
}

internal suspend fun nativePickImportTextFile(): Triple<String?, String?, String?> = suspendCancellableCoroutine { continuation ->
    IosNativeHandlers.pickImportFile { fileName, text, error ->
        if (continuation.isActive) continuation.resume(Triple(fileName, text, error))
    }
}


actual suspend fun platformRequestPushNotifications(): PushPermissionResult = suspendCancellableCoroutine { continuation ->
    IosNativeHandlers.requestPush { status, token ->
        if (continuation.isActive) {
            val ok = status.equals("OK", ignoreCase = true)
            continuation.resume(
                PushPermissionResult(
                    granted = ok,
                    message = if (ok) "iOS notification permission enabled." else status,
                    deviceToken = token?.takeIf { it.isNotBlank() },
                )
            )
        }
    }
}
actual fun platformPushCapability(): String =
    "iOS notification permission and APNs token capture are available. Jasvi Industries v10.0.5 has no device-token registration endpoint, so remote ERP push delivery is not enabled in this build."
actual fun platformPickAttachment(onResult:(PickedAttachment)->Unit) {
    IosNativeHandlers.pickAttachment { name,base64,error -> onResult(PickedAttachment(name,base64,error)) }
}
actual fun platformShareText(title:String,text:String):Boolean=IosNativeHandlers.shareText(title,text)
actual fun platformShareFile(title:String,fileName:String,data:ByteArray):Boolean=IosNativeHandlers.shareFile(title,fileName,encodeBase64Portable(data))
actual fun platformOpenExternalUrl(url:String):Boolean=IosNativeHandlers.openExternalUrl(url)
actual suspend fun platformInstallMobileUpdate(version: String): MobileUpdateInstallResult = MobileUpdateInstallResult(false, "Direct APK updates are available only in the Android build.")
actual fun platformConsumeDeepLink(): String? = IosNativeHandlers.consumeDeepLink()?.takeIf { it.isNotBlank() }


actual fun platformPublishWidgetSnapshot(snapshot: WidgetDashboardSnapshot) {
    IosNativeHandlers.publishWidgetSnapshot(
        snapshot.salesToday,
        snapshot.receivables,
        snapshot.purchases,
        snapshot.payables,
        snapshot.bankBalance,
        snapshot.expenseMonth,
        snapshot.updatedAtMillis,
    )
}

actual fun platformSystemExperienceCapability(): String =
    "iOS 17+: Jasvi Industries dashboard widgets, read-only App Shortcuts and Shipping Live Activities."

actual suspend fun platformStartShippingLiveActivity(
    invoiceNo: String,
    customer: String,
    transporter: String,
    vehicle: String,
): LiveActivityResult = suspendCancellableCoroutine { continuation ->
    IosNativeHandlers.startShippingActivity(invoiceNo, customer, transporter, vehicle) { status, message ->
        if (continuation.isActive) continuation.resume(
            LiveActivityResult(status.equals("OK", ignoreCase = true), message ?: status)
        )
    }
}

actual suspend fun platformUpdateShippingLiveActivity(
    invoiceNo: String,
    stage: String,
): LiveActivityResult = suspendCancellableCoroutine { continuation ->
    IosNativeHandlers.updateShippingActivity(invoiceNo, stage) { status, message ->
        if (continuation.isActive) continuation.resume(
            LiveActivityResult(status.equals("OK", ignoreCase = true), message ?: status)
        )
    }
}

actual suspend fun platformEndShippingLiveActivity(
    invoiceNo: String,
): LiveActivityResult = suspendCancellableCoroutine { continuation ->
    IosNativeHandlers.endShippingActivity(invoiceNo) { status, message ->
        if (continuation.isActive) continuation.resume(
            LiveActivityResult(status.equals("OK", ignoreCase = true), message ?: status)
        )
    }
}
