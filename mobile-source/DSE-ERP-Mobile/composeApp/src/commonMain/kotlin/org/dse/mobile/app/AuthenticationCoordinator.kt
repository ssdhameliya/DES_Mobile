package org.dse.mobile.app

import org.dse.mobile.core.api.ApiResult
import org.dse.mobile.core.api.DseErpHttpClient
import org.dse.mobile.core.api.SessionStore
import org.dse.mobile.core.model.EffectivePermission
import org.dse.mobile.core.model.UserPayload
import org.dse.mobile.core.model.UserProfile
import org.dse.mobile.core.offline.OfflineRepository

enum class AuthenticationEntry { PASSWORD, BIOMETRIC, MFA }

internal sealed interface AuthenticationResult {
    data class Authorized(
        val user: UserPayload,
        val permissions: List<EffectivePermission>,
        val message: String,
        val offlineReadOnly: Boolean = false,
    ) : AuthenticationResult

    data class MfaRequired(
        val user: UserPayload?,
        val challengeId: String,
        val maskedDestination: String,
        val message: String,
    ) : AuthenticationResult

    data class Rejected(
        val message: String,
        val clearStoredSession: Boolean = false,
    ) : AuthenticationResult
}

/**
 * Single authorization coordinator used by password, MFA and biometric entry.
 * Biometric proves local device-user presence only; it never replaces ERP authorization.
 */
internal class AuthenticationCoordinator(private val sessions: SessionStore) {

    suspend fun authenticatePassword(
        api: DseErpHttpClient,
        serverUrl: String,
        identity: String,
        password: String,
    ): AuthenticationResult {
        val runtime = when (val result = api.runtimeHealth()) {
            is ApiResult.Success -> {
                BusinessDateContext.update(result.value.businessDate, result.value.businessZone)
                result.value
            }
            else -> return AuthenticationResult.Rejected(
                "Server contract check failed. ${result.readableMessage()}",
                result is ApiResult.Unauthorized,
            )
        }
        runtimeCompatibilityProblem(runtime)?.let { return AuthenticationResult.Rejected(it) }

        return when (val login = api.login(identity.trim(), password)) {
            is ApiResult.Success -> {
                val value = login.value
                when {
                    !value.success -> AuthenticationResult.Rejected(value.message.ifBlank { "Login failed" })
                    value.mfaRequired -> AuthenticationResult.MfaRequired(
                        user = value.user,
                        challengeId = value.challengeId.orEmpty(),
                        maskedDestination = value.maskedDestination.orEmpty(),
                        message = value.message,
                    )
                    else -> completeAuthorizedSession(
                        api = api,
                        serverUrl = serverUrl,
                        expectedUser = value.user,
                        successMessage = "Connected to Jasvi Industries ${runtime.version}",
                        allowOfflineFallback = true,
                    )
                }
            }
            else -> AuthenticationResult.Rejected(login.readableMessage(), login is ApiResult.Unauthorized)
        }
    }

    suspend fun authenticateBiometricSession(
        api: DseErpHttpClient,
        serverUrl: String,
    ): AuthenticationResult {
        return when (val runtime = api.runtimeHealth()) {
            is ApiResult.Success -> {
                runtime.value.let { BusinessDateContext.update(it.businessDate, it.businessZone) }
                runtimeCompatibilityProblem(runtime.value)?.let { return AuthenticationResult.Rejected(it) }
                completeAuthorizedSession(
                    api = api,
                    serverUrl = serverUrl,
                    expectedUser = null,
                    successMessage = "Unlocked securely",
                    allowOfflineFallback = true,
                )
            }
            is ApiResult.NetworkError -> offlineAuthorizedSession(
                serverUrl,
                "Offline read mode • stored ERP authorization was validated previously",
            )
            else -> AuthenticationResult.Rejected(
                "Server contract check failed. ${runtime.readableMessage()}",
                runtime is ApiResult.Unauthorized,
            )
        }
    }

    suspend fun authenticateMfa(
        api: DseErpHttpClient,
        serverUrl: String,
        challengeId: String,
        otp: String,
        expectedUser: UserPayload?,
    ): AuthenticationResult {
        return when (val result = api.completeMfa(challengeId, otp.trim())) {
            is ApiResult.Success -> {
                val value = result.value
                if (!value.success || value.mfaRequired) {
                    AuthenticationResult.Rejected(value.message.ifBlank { "MFA verification failed" })
                } else {
                    completeAuthorizedSession(
                        api = api,
                        serverUrl = serverUrl,
                        expectedUser = value.user ?: expectedUser,
                        successMessage = "MFA verified",
                        allowOfflineFallback = true,
                    )
                }
            }
            else -> AuthenticationResult.Rejected(result.readableMessage(), result is ApiResult.Unauthorized)
        }
    }

    /**
     * Shared post-proof path: token/session -> authoritative profile linkage -> permissions -> offline snapshot -> APP.
     */
    private suspend fun completeAuthorizedSession(
        api: DseErpHttpClient,
        serverUrl: String,
        expectedUser: UserPayload?,
        successMessage: String,
        allowOfflineFallback: Boolean,
    ): AuthenticationResult {
        val profile = when (val result = api.currentProfile()) {
            is ApiResult.Success -> result.value
            is ApiResult.NetworkError -> {
                if (allowOfflineFallback) return offlineAuthorizedSession(serverUrl, successMessage)
                return AuthenticationResult.Rejected(result.readableMessage())
            }
            else -> {
                clearRejectedSession(api)
                return AuthenticationResult.Rejected(
                    "Authenticated session could not be linked to an ERP user. ${result.readableMessage()}",
                    true,
                )
            }
        }

        val linkedUser = profile.toUserPayload()
        if (expectedUser != null && !sameErpUser(expectedUser, linkedUser)) {
            clearRejectedSession(api)
            return AuthenticationResult.Rejected(
                "Authenticated user linkage mismatch. Sign in again so ERP can validate the correct user session.",
                true,
            )
        }
        if (!linkedUser.active || linkedUser.locked) {
            clearRejectedSession(api)
            return AuthenticationResult.Rejected(
                if (linkedUser.locked) "This ERP user is locked." else "This ERP user is inactive.",
                true,
            )
        }

        return when (val permissionResult = api.effectivePermissions()) {
            is ApiResult.Success -> {
                val permissions = permissionResult.value
                OfflineRepository.activateScope("$serverUrl|${linkedUser.username}")
                OfflineRepository.saveAuthSnapshot(serverUrl, linkedUser, permissions)
                AuthenticationResult.Authorized(linkedUser, permissions, successMessage)
            }
            is ApiResult.NetworkError -> {
                if (allowOfflineFallback) offlineAuthorizedSession(serverUrl, successMessage)
                else AuthenticationResult.Rejected(permissionResult.readableMessage())
            }
            else -> {
                clearRejectedSession(api)
                AuthenticationResult.Rejected(
                    "ERP permissions could not be validated. ${permissionResult.readableMessage()}",
                    true,
                )
            }
        }
    }

    private fun offlineAuthorizedSession(serverUrl: String, message: String): AuthenticationResult {
        val cached = OfflineRepository.readAuthSnapshot(serverUrl)
            ?: return AuthenticationResult.Rejected(
                "Server offline and no validated offline profile is available.",
            )
        OfflineRepository.activateScope("$serverUrl|${cached.user.username}")
        return AuthenticationResult.Authorized(
            user = cached.user,
            permissions = cached.permissions,
            message = "$message • permissions last validated ${cacheAgeLabel(cached.validatedAtMillis)}",
            offlineReadOnly = true,
        )
    }

    private suspend fun clearRejectedSession(api: DseErpHttpClient) {
        api.logout()
        sessions.clear()
    }
}

private fun sameErpUser(expected: UserPayload, actual: UserPayload): Boolean {
    if (expected.id > 0 && actual.id > 0) return expected.id == actual.id
    return expected.username.equals(actual.username, ignoreCase = true)
}

internal fun UserProfile.toUserPayload() = UserPayload(
    id = id,
    username = username,
    fullName = fullName,
    role = role,
    email = email,
    active = active,
    department = department,
    branch = branch,
    accessLevel = accessLevel,
    locked = locked,
    mfaEnabled = mfaEnabled,
)
