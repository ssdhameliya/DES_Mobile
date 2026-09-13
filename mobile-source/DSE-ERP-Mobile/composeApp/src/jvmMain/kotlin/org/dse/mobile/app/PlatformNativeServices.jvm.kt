package org.dse.mobile.app

import org.dse.mobile.core.api.InMemorySessionStore
import org.dse.mobile.core.api.SessionStore
import java.util.prefs.Preferences
import java.time.LocalDate
import java.awt.Desktop
import java.net.URI
import java.util.Base64
import javax.swing.JFileChooser

private val jvmSessionStore = InMemorySessionStore()
private val jvmPrefs = Preferences.userRoot().node("org/dse/erp/mobile/native")
private var jvmDeepLinkConsumed = false

actual fun platformSessionStore(): SessionStore = jvmSessionStore
actual fun platformLoadLastServerUrl(): String? = jvmPrefs.get("lastServerUrl", null)
actual fun platformSaveLastServerUrl(url: String) {
    url.trim().takeIf { it.isNotEmpty() }?.let { jvmPrefs.put("lastServerUrl", it) }
}
actual fun platformBiometricAvailable(): Boolean = false
actual suspend fun platformAuthenticateBiometric(reason: String): BiometricAuthResult =
    BiometricAuthResult(false, "Biometric unlock is available in the native iOS build.")
actual fun platformSecurityLabel(): String = "Secure UAT session • iPhone/iPad uses protected Keychain storage"
actual fun platformImportCapability(): String = "IntelliJ: Excel (.xlsx/.xls) and CSV file picker enabled"
actual fun platformLocalDateIso(): String = LocalDate.now().toString()
actual fun platformName(): String = "IntelliJ/JVM"
actual fun platformDeviceClassLabel(): String = "desktop preview"
actual fun platformBiometricUnlockLabel(): String = "Biometric unlock"
actual fun platformImportFormatHint(): String = "IntelliJ accepts Excel (.xlsx/.xls) and CSV files."
actual suspend fun platformRequestPushNotifications(): PushPermissionResult =
    PushPermissionResult(false, "Push notifications activate in the native iOS build.")
actual fun platformPushCapability(): String =
    "IntelliJ: push/deep-link architecture visible; APNs activates on iPhone/iPad"
actual fun platformPickAttachment(onResult:(PickedAttachment)->Unit) {
    val result = try {
        val chooser = JFileChooser()
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            runCatching {
                PickedAttachment(file.name, Base64.getEncoder().encodeToString(file.readBytes()), null)
            }.getOrElse { PickedAttachment(file.name, null, it.message ?: "Unable to read selected file") }
        } else PickedAttachment()
    } catch (t: Throwable) {
        PickedAttachment(error = t.message ?: "Unable to open attachment picker")
    }
    onResult(result)
}

actual fun platformShareText(title:String,text:String):Boolean {
    println("$title\n$text")
    return true
}
actual fun platformShareFile(title:String,fileName:String,data:ByteArray):Boolean=runCatching{
    val safe=fileName.replace(Regex("[^A-Za-z0-9._() -]"),"_").ifBlank{"attachment.bin"}
    val dir=java.nio.file.Files.createTempDirectory("dse-erp-mobile-");val file=dir.resolve(safe);java.nio.file.Files.write(file,data)
    if(Desktop.isDesktopSupported()){Desktop.getDesktop().open(file.toFile());true}else false
}.getOrDefault(false)
actual fun platformOpenExternalUrl(url:String):Boolean=runCatching{if(Desktop.isDesktopSupported()){Desktop.getDesktop().browse(URI(url));true}else false}.getOrDefault(false)
actual suspend fun platformInstallMobileUpdate(version: String): MobileUpdateInstallResult = MobileUpdateInstallResult(false, "Direct APK updates are available only in the Android build.")
actual fun platformConsumeDeepLink(): String? {
    if (jvmDeepLinkConsumed) return null
    val value = System.getProperty("dse.mobile.deepLink")?.trim()?.takeIf { it.isNotEmpty() }
    if (value != null) jvmDeepLinkConsumed = true
    return value
}


actual fun platformPublishWidgetSnapshot(snapshot: WidgetDashboardSnapshot) = Unit
actual fun platformSystemExperienceCapability(): String =
    "IntelliJ preview: iOS Home Screen widgets, App Shortcuts and Shipping Live Activities activate in the signed iPhone/iPad build."
actual suspend fun platformStartShippingLiveActivity(
    invoiceNo: String,
    customer: String,
    transporter: String,
    vehicle: String,
): LiveActivityResult = LiveActivityResult(false, "Live Activities are available in the native iOS build.")
actual suspend fun platformUpdateShippingLiveActivity(invoiceNo: String, stage: String): LiveActivityResult =
    LiveActivityResult(false, "Live Activities are available in the native iOS build.")
actual suspend fun platformEndShippingLiveActivity(invoiceNo: String): LiveActivityResult =
    LiveActivityResult(false, "Live Activities are available in the native iOS build.")
