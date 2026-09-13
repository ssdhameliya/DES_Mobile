package org.dse.mobile.core.security

import org.dse.mobile.core.config.MobileBuildInfo

object MobileSecurityPolicy {
    fun isLoopbackHost(host: String): Boolean {
        val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
        return h == "localhost" || h == "127.0.0.1" || h == "::1"
    }

    private fun endpointHost(url: String): String {
        val value = url.trim()
        val authority = value.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
        return when {
            authority.startsWith("[") -> authority.substringAfter('[').substringBefore(']')
            else -> authority.substringBefore(':')
        }.trim().lowercase()
    }

    fun isBlockedEnvironmentEndpoint(url: String): Boolean =
        MobileBuildInfo.BLOCKED_SERVER_HOST.isNotBlank() && endpointHost(url) == MobileBuildInfo.BLOCKED_SERVER_HOST

    fun isSafeEndpoint(url: String): Boolean {
        val value = url.trim()
        if (isBlockedEnvironmentEndpoint(value)) return false
        if (value.startsWith("https://", ignoreCase = true)) return true
        if (!value.startsWith("http://", ignoreCase = true)) return false
        return isLoopbackHost(endpointHost(value))
    }

    fun endpointProblem(url: String): String? = when {
        url.isBlank() -> "ERP server URL is required."
        !url.contains("://") -> "ERP server URL must include http:// or https://."
        isBlockedEnvironmentEndpoint(url) -> "This ${MobileBuildInfo.RELEASE_CHANNEL} mobile build cannot connect to the opposite Jasvi environment."
        !isSafeEndpoint(url) -> "For security, remote Jasvi Industries servers must use HTTPS. Plain HTTP is allowed only for localhost development."
        else -> null
    }
}
