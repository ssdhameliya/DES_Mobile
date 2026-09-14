package org.dse.mobile.core.config

private const val CURRENT_MOBILE_VERSION_NAME = "1.2.12"

data class MobileRuntimeConfig(
    val releaseChannel: String,
    val defaultServerUrl: String,
    val blockedServerHost: String,
    val serverEditingAllowed: Boolean,
    val updateApkBaseUrl: String,
    val installedVersionName: String = CURRENT_MOBILE_VERSION_NAME,
)

object MobileBuildInfo {
    const val APP_NAME = "Jasvi Industries Mobile"
    private const val FALLBACK_MOBILE_VERSION_NAME = CURRENT_MOBILE_VERSION_NAME
    val MOBILE_VERSION_NAME: String get() = runtimeConfig.installedVersionName.trim().ifBlank { FALLBACK_MOBILE_VERSION_NAME }
    val MOBILE_VERSION: String get() = "$MOBILE_VERSION_NAME-V$SERVER_BASELINE-DISTRIBUTION"
    const val SERVER_BASELINE = "10.0.12"
    // Desktop/server 10.0.12 keeps the bearer-v5 mobile API contract; older certified servers remain allowed by the compatibility floor.
    const val MINIMUM_COMPATIBLE_SERVER_VERSION = "10.0.1"
    const val API_CONTRACT_VERSION = "server-10.0.5-compatible-v4"
    const val MINIMUM_SUPPORTED_MOBILE_VERSION = "1.2.3"
    const val EXPECTED_SERVER_SERVICE = "dse-erp-server"
    const val EXPECTED_API_REVISION = "spring-security-bearer-v5"
    const val UAT_SERVER_URL = "https://api-uat.jasviindustries.in"
    const val PROD_SERVER_URL = "https://api.jasviindustries.in"
    const val LEGACY_TEST_SERVER_URL = "https://api-test.jasviindustries.in"

    private var runtimeConfig = MobileRuntimeConfig(
        releaseChannel = "UAT",
        defaultServerUrl = UAT_SERVER_URL,
        blockedServerHost = "api.jasviindustries.in",
        serverEditingAllowed = true,
        updateApkBaseUrl = "$UAT_SERVER_URL/mobile/android/uat",
        installedVersionName = FALLBACK_MOBILE_VERSION_NAME,
    )

    val RELEASE_CHANNEL: String get() = runtimeConfig.releaseChannel.trim().uppercase().ifBlank { "UAT" }
    val DEFAULT_DEV_SERVER_URL: String get() = runtimeConfig.defaultServerUrl.trim().trimEnd('/')
    val BLOCKED_SERVER_HOST: String get() = runtimeConfig.blockedServerHost.trim().lowercase()
    val SERVER_EDITING_ALLOWED: Boolean get() = runtimeConfig.serverEditingAllowed
    val UPDATE_APK_BASE_URL: String get() = runtimeConfig.updateApkBaseUrl.trim().trimEnd('/')
    val UAT_ONLY_TEST_BUILD: Boolean get() = RELEASE_CHANNEL == "UAT"

    fun installRuntimeConfig(config: MobileRuntimeConfig) {
        require(config.releaseChannel.equals("UAT", true) || config.releaseChannel.equals("PROD", true)) {
            "Mobile release channel must be UAT or PROD."
        }
        require(config.defaultServerUrl.startsWith("https://", true)) {
            "Mobile release server must use HTTPS."
        }
        require(config.updateApkBaseUrl.startsWith("https://", true)) {
            "Mobile update endpoint must use HTTPS."
        }
        runtimeConfig = config.copy(
            releaseChannel = config.releaseChannel.uppercase(),
            defaultServerUrl = config.defaultServerUrl.trim().trimEnd('/'),
            blockedServerHost = config.blockedServerHost.trim().lowercase(),
            updateApkBaseUrl = config.updateApkBaseUrl.trim().trimEnd('/'),
            installedVersionName = config.installedVersionName.trim().ifBlank { FALLBACK_MOBILE_VERSION_NAME },
        )
    }

    fun expectedEnvironment(): String = RELEASE_CHANNEL

    fun updateApkUrl(version: String): String {
        val safe = version.trim().takeIf { it.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}")) }
            ?: error("Invalid mobile update version.")
        return "$UPDATE_APK_BASE_URL/Jasvi-Mobile-$safe-$RELEASE_CHANNEL.apk"
    }
}

fun installMobileRuntimeConfig(config: MobileRuntimeConfig) = MobileBuildInfo.installRuntimeConfig(config)
