package org.dse.mobile.android

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Base64
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import org.dse.mobile.app.App
import org.dse.mobile.app.installAndroidNativeServices
import org.dse.mobile.core.offline.installAndroidPlatformContext
import org.dse.mobile.core.config.MobileRuntimeConfig
import org.dse.mobile.core.config.installMobileRuntimeConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.security.MessageDigest
import java.util.concurrent.Executor

class MainActivity : FragmentActivity() {
    private lateinit var secureTokenStore: AndroidSecureTokenStore
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var attachmentLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var credentialLauncher: ActivityResultLauncher<Intent>

    private var importCompletion: ((String?, String?, String?) -> Unit)? = null
    private var attachmentCompletion: ((String?, String?, String?) -> Unit)? = null
    private var notificationCompletion: ((String, String?) -> Unit)? = null
    private var credentialCompletion: ((String, String?) -> Unit)? = null

    private val nativePrefs by lazy { getSharedPreferences("dse.erp.native", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installMobileRuntimeConfig(
            MobileRuntimeConfig(
                releaseChannel = BuildConfig.RELEASE_CHANNEL,
                defaultServerUrl = BuildConfig.ERP_SERVER_URL,
                blockedServerHost = BuildConfig.BLOCKED_SERVER_HOST,
                serverEditingAllowed = BuildConfig.ALLOW_SERVER_EDIT,
                updateApkBaseUrl = BuildConfig.UPDATE_APK_BASE_URL,
            )
        )
        installAndroidPlatformContext(applicationContext)
        secureTokenStore = AndroidSecureTokenStore(applicationContext)
        registerNativeLaunchers()
        captureDeepLink(intent)
        installBridge()
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureDeepLink(intent)
    }

    private fun registerNativeLaunchers() {
        importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val callback = importCompletion.also { importCompletion = null } ?: return@registerForActivityResult
            if (uri == null) {
                callback(null, null, null)
                return@registerForActivityResult
            }
            val name = displayName(uri) ?: "import.csv"
            val lower = name.lowercase()
            if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) {
                callback(name, null, "Select a CSV file for mobile Data Import.")
                return@registerForActivityResult
            }
            runCatching {
                val data = readUriWithLimit(uri, MAX_MOBILE_FILE_BYTES)
                val text = decodeText(data)
                callback(name, text, null)
            }.onFailure { callback(name, null, it.message ?: "Unable to read selected import file.") }
        }

        attachmentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val callback = attachmentCompletion.also { attachmentCompletion = null } ?: return@registerForActivityResult
            if (uri == null) {
                callback(null, null, null)
                return@registerForActivityResult
            }
            val name = displayName(uri) ?: "attachment.bin"
            runCatching {
                val data = readUriWithLimit(uri, MAX_MOBILE_FILE_BYTES)
                callback(name, Base64.encodeToString(data, Base64.NO_WRAP), null)
            }.onFailure { callback(name, null, it.message ?: "Unable to read selected attachment.") }
        }

        notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val callback = notificationCompletion.also { notificationCompletion = null }
            callback?.invoke(if (granted) "OK" else "Notification permission was not granted.", null)
        }

        credentialLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = credentialCompletion.also { credentialCompletion = null } ?: return@registerForActivityResult
            if (result.resultCode == Activity.RESULT_OK) {
                callback("OK", "Authenticated with device credential.")
            } else {
                callback("ERROR", "Device credential authentication was cancelled or unsuccessful.")
            }
        }
    }

    private fun installBridge() {
        installAndroidNativeServices(
            loadToken = { secureTokenStore.load() },
            saveToken = { secureTokenStore.save(it) },
            clearToken = { secureTokenStore.clear() },
            loadServerUrl = { nativePrefs.getString(KEY_SERVER_URL, null) },
            saveServerUrl = { nativePrefs.edit().putString(KEY_SERVER_URL, it).apply() },
            biometricState = { if (biometricAvailable()) "AVAILABLE" else "UNAVAILABLE" },
            authenticateBiometric = { reason, completion -> authenticateBiometric(reason, completion) },
            pickImportFile = { completion ->
                importCompletion?.invoke(null, null, "A file picker is already active.")
                importCompletion = completion
                importLauncher.launch(arrayOf("text/csv", "text/plain", "text/comma-separated-values"))
            },
            requestPush = { completion -> requestNotifications(completion) },
            pickAttachment = { completion ->
                attachmentCompletion?.invoke(null, null, "An attachment picker is already active.")
                attachmentCompletion = completion
                attachmentLauncher.launch(arrayOf("*/*"))
            },
            shareText = { title, text -> shareText(title, text) },
            shareFile = { title, fileName, base64 -> shareFile(title, fileName, base64) },
            openExternalUrl = { openExternalUrl(it) },
            installMobileUpdate = { version, completion -> installMobileUpdate(version, completion) },
            consumeDeepLink = { consumeDeepLink() },
            publishWidgetSnapshot = { salesToday, receivables, purchases, payables, bankBalance, expenseMonth, updatedAtMillis ->
                DashboardWidgetPublisher.publish(
                    applicationContext,
                    salesToday,
                    receivables,
                    purchases,
                    payables,
                    bankBalance,
                    expenseMonth,
                    updatedAtMillis,
                )
            },
            startShippingActivity = { invoiceNo, customer, transporter, vehicle, completion ->
                val result = ShippingNotificationController.start(applicationContext, invoiceNo, customer, transporter, vehicle)
                completion(if (result.first) "OK" else "ERROR", result.second)
            },
            updateShippingActivity = { invoiceNo, stage, completion ->
                val result = ShippingNotificationController.update(applicationContext, invoiceNo, stage)
                completion(if (result.first) "OK" else "ERROR", result.second)
            },
            endShippingActivity = { invoiceNo, completion ->
                val result = ShippingNotificationController.end(applicationContext, invoiceNo)
                completion(if (result.first) "OK" else "ERROR", result.second)
            },
        )
    }

    private fun biometricAvailable(): Boolean {
        val manager = BiometricManager.from(this)
        return if (Build.VERSION.SDK_INT >= 30) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            val biometric = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
            biometric || deviceCredentialAvailable()
        }
    }

    private fun authenticateBiometric(reason: String, completion: (String, String?) -> Unit) {
        val manager = BiometricManager.from(this)
        if (Build.VERSION.SDK_INT >= 30) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
                completion("ERROR", "Biometric or device-credential unlock is unavailable on this Android device.")
                return
            }
            showBiometricPrompt(reason, authenticators, false, completion)
            return
        }

        val strongBiometric = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val hasCredential = deviceCredentialAvailable()
        when {
            strongBiometric -> showBiometricPrompt(
                reason,
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
                hasCredential,
                completion,
            )
            hasCredential -> launchDeviceCredential(reason, completion)
            else -> completion("ERROR", "Biometric or device-credential unlock is unavailable on this Android device.")
        }
    }

    private fun showBiometricPrompt(
        reason: String,
        authenticators: Int,
        allowCredentialFallback: Boolean,
        completion: (String, String?) -> Unit,
    ) {
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                completion("OK", "Authenticated securely.")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (Build.VERSION.SDK_INT < 30 && allowCredentialFallback && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    launchDeviceCredential(reason, completion)
                } else {
                    completion("ERROR", errString.toString())
                }
            }

            override fun onAuthenticationFailed() = Unit
        })
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("DSE ERP Mobile")
            .setSubtitle(reason)
            .setAllowedAuthenticators(authenticators)
        if (Build.VERSION.SDK_INT < 30) {
            builder.setNegativeButtonText(if (allowCredentialFallback) "Use device PIN/pattern/password" else "Cancel")
        }
        prompt.authenticate(builder.build())
    }

    @Suppress("DEPRECATION")
    private fun launchDeviceCredential(reason: String, completion: (String, String?) -> Unit) {
        val manager = getSystemService(KeyguardManager::class.java)
        val intent = manager.createConfirmDeviceCredentialIntent("DSE ERP Mobile", reason)
        if (intent == null) {
            completion("ERROR", "No secure device credential is configured.")
            return
        }
        credentialCompletion?.invoke("ERROR", "Another device-credential request replaced this request.")
        credentialCompletion = completion
        credentialLauncher.launch(intent)
    }

    private fun deviceCredentialAvailable(): Boolean =
        getSystemService(KeyguardManager::class.java).isDeviceSecure

    private fun requestNotifications(completion: (String, String?) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            completion("OK", null)
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            completion("OK", null)
            return
        }
        notificationCompletion?.invoke("ERROR", null)
        notificationCompletion = completion
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun shareText(title: String, text: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, title))
        true
    }.getOrDefault(false)

    private fun shareFile(title: String, fileName: String, base64: String): Boolean = runCatching {
        val data = Base64.decode(base64, Base64.DEFAULT)
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._() -]"), "_").ifBlank { "attachment.bin" }
        val shareDir = File(cacheDir, "dse-share").apply { mkdirs() }
        val file = File(shareDir, safeName).apply { writeBytes(data) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = URLConnection.guessContentTypeFromName(safeName) ?: "application/octet-stream"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
        true
    }.getOrDefault(false)

    private fun openExternalUrl(value: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value))
        if (intent.resolveActivity(packageManager) == null) return@runCatching false
        startActivity(intent)
        true
    }.getOrDefault(false)

    private fun installMobileUpdate(version: String, completion: (String, String?) -> Unit) {
        val safeVersion = version.trim()
        if (!safeVersion.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}"))) {
            completion("ERROR", "Invalid mobile update version.")
            return
        }
        val channel = BuildConfig.RELEASE_CHANNEL.uppercase()
        val apkName = "Jasvi-Mobile-$safeVersion-$channel.apk"
        val apkUrl = BuildConfig.UPDATE_APK_BASE_URL.trimEnd('/') + "/" + apkName
        val shaUrl = "$apkUrl.sha256"

        Thread {
            runCatching {
                val updateDir = File(cacheDir, "dse-update").apply { mkdirs() }
                val apkFile = File(updateDir, apkName)
                downloadToFile(apkUrl, apkFile, MAX_UPDATE_APK_BYTES)
                val checksumText = downloadText(shaUrl, MAX_CHECKSUM_BYTES)
                val expectedSha = Regex("[A-Fa-f0-9]{64}").find(checksumText)?.value?.lowercase()
                    ?: error("Update checksum is missing or invalid.")
                val actualSha = sha256(apkFile)
                check(expectedSha == actualSha) { "Downloaded APK checksum does not match the published SHA-256." }

                @Suppress("DEPRECATION")
                val archive = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                    ?: error("Downloaded file is not a valid Android APK.")
                check(archive.packageName == packageName) {
                    "Update package identity does not match this ${BuildConfig.RELEASE_CHANNEL} app."
                }
                val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
                @Suppress("DEPRECATION")
                val installed = packageManager.getPackageInfo(packageName, 0)
                val installedCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
                check(archiveCode > installedCode) { "The downloaded APK is not newer than the installed app." }

                runOnUiThread {
                    if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                        val settingsIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:$packageName"),
                        )
                        startActivity(settingsIntent)
                        completion(
                            "ERROR",
                            "Android opened Install unknown apps. Allow Jasvi Industries once, then press Update Now again.",
                        )
                        return@runOnUiThread
                    }
                    val uri = FileProvider.getUriForFile(this, "$packageName.files", apkFile)
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(installIntent)
                    completion("OK", "APK verified. Confirm the Android update installation.")
                }
            }.onFailure { error ->
                runOnUiThread { completion("ERROR", error.message ?: "Unable to download or verify the mobile update.") }
            }
        }.start()
    }

    private fun downloadToFile(url: String, target: File, maxBytes: Int) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        try {
            check(connection.responseCode in 200..299) { "Update download failed with HTTP ${connection.responseCode}." }
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        check(total <= maxBytes) { "Update APK exceeds the mobile download safety limit." }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadText(url: String, maxBytes: Int): String {
        val temp = File.createTempFile("jasvi-update-checksum-", ".txt", cacheDir)
        return try {
            downloadToFile(url, temp, maxBytes)
            temp.readText(Charsets.UTF_8)
        } finally {
            temp.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun captureDeepLink(intent: Intent?) {
        intent?.dataString?.takeIf { it.startsWith("${BuildConfig.DEEP_LINK_SCHEME}://", true) }?.let {
            nativePrefs.edit().putString(KEY_PENDING_DEEP_LINK, it).apply()
        }
    }

    private fun consumeDeepLink(): String? {
        val value = nativePrefs.getString(KEY_PENDING_DEEP_LINK, null)
        if (value != null) nativePrefs.edit().remove(KEY_PENDING_DEEP_LINK).apply()
        return value
    }

    private fun displayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0)
        }
    }.getOrNull()

    private fun readUriWithLimit(uri: Uri, limit: Int): ByteArray {
        val stream = contentResolver.openInputStream(uri) ?: error("Unable to open selected file.")
        stream.use { input ->
            val out = java.io.ByteArrayOutputStream(minOf(limit, 64 * 1024))
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) error("File exceeds the 20 MB mobile safety limit.")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }
    }

    private fun decodeText(data: ByteArray): String {
        val charset = when {
            data.size >= 2 && data[0] == 0xFF.toByte() && data[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            data.size >= 2 && data[0] == 0xFE.toByte() && data[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return data.toString(charset)
    }

    companion object {
        private const val KEY_SERVER_URL = "dse.erp.lastServerUrl"
        private const val KEY_PENDING_DEEP_LINK = "dse.erp.pendingDeepLink"
        private const val MAX_MOBILE_FILE_BYTES = 20 * 1024 * 1024
        private const val MAX_UPDATE_APK_BYTES = 200 * 1024 * 1024
        private const val MAX_CHECKSUM_BYTES = 8 * 1024
    }
}
