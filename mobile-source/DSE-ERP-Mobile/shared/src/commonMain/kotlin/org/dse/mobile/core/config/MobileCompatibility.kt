package org.dse.mobile.core.config

import org.dse.mobile.core.model.RuntimeHealthResponse

enum class MobileUpdateRequirement {
    CURRENT,
    OPTIONAL_UPDATE,
    REQUIRED_UPDATE,
}

data class MobileCompatibilityDecision(
    val requirement: MobileUpdateRequirement,
    val currentVersion: String,
    val minimumVersion: String,
    val latestVersion: String,
) {
    val loginAllowed: Boolean get() = requirement != MobileUpdateRequirement.REQUIRED_UPDATE
}

fun evaluateMobileCompatibility(
    status: RuntimeHealthResponse,
    clientType: String,
    currentVersion: String = MobileBuildInfo.MOBILE_VERSION_NAME,
): MobileCompatibilityDecision {
    val ios = clientType.equals("iOS", ignoreCase = true)
    val minimum = (if (ios) status.minimumSupportedIosVersion else status.minimumSupportedAndroidVersion).trim()
    val latest = (if (ios) status.latestIosVersion else status.latestAndroidVersion).trim()

    // 10.0.1/10.0.2 servers predate the server-owned mobile policy. Keep them usable
    // while API revision v5 is still accepted by the app.
    if (minimum.isBlank() && latest.isBlank()) {
        return MobileCompatibilityDecision(MobileUpdateRequirement.CURRENT, currentVersion, "", "")
    }

    val effectiveMinimum = minimum.ifBlank { currentVersion }
    val effectiveLatest = latest.ifBlank { effectiveMinimum }
    return when {
        compareVersion(currentVersion, effectiveMinimum) < 0 ->
            MobileCompatibilityDecision(MobileUpdateRequirement.REQUIRED_UPDATE, currentVersion, effectiveMinimum, effectiveLatest)
        compareVersion(currentVersion, effectiveLatest) < 0 ->
            MobileCompatibilityDecision(MobileUpdateRequirement.OPTIONAL_UPDATE, currentVersion, effectiveMinimum, effectiveLatest)
        else ->
            MobileCompatibilityDecision(MobileUpdateRequirement.CURRENT, currentVersion, effectiveMinimum, effectiveLatest)
    }
}

internal fun compareVersion(left: String, right: String): Int {
    val a = left.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val b = right.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val n = maxOf(a.size, b.size)
    for (i in 0 until n) {
        val av = a.getOrElse(i) { 0 }
        val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}
