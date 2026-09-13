package org.dse.mobile.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val identity: String,
    val password: String,
)

@Serializable
data class MfaCompleteRequest(
    val challengeId: String,
    val otp: String,
)


@Serializable
data class MfaResendRequest(val challengeId: String)

@Serializable
data class MfaChallengeResponse(
    val success: Boolean = false,
    val challengeId: String = "",
    val message: String = "",
    val maskedDestination: String? = null,
)

@Serializable data class PasswordResetOtpRequest(val identity:String="")
@Serializable data class PasswordResetCompleteRequest(val challengeId:String="",val otp:String="",val totp:String="",val password:String="")
@Serializable data class CaptchaResponse(val challengeId:String="",val question:String="",val expiresIn:String="")
@Serializable data class RegistrationOtpRequest(
    val username:String="",val fullName:String="",val email:String="",val role:String="",val mfaEnabled:Boolean=true,
    val captchaChallengeId:String="",val captchaAnswer:String=""
)
@Serializable data class RegistrationEmailVerifyRequest(val challengeId:String="",val otp:String="",val username:String="",val password:String="",val fullName:String="",val email:String="",val role:String="",val mfaEnabled:Boolean=true)
@Serializable data class RegistrationMfaSetupResponse(val success:Boolean=false,val registrationId:Long?=null,val manualSecret:String="",val provisioningUri:String="",val message:String="")
@Serializable data class RegistrationMfaCompleteRequest(val registrationId:Long=0,val otp:String="")
@Serializable data class SessionExtendResponse(val success:Boolean=false,val message:String="",val accessToken:String="",val expiresAt:String="")
@Serializable data class RegisterRequest(val username:String="",val password:String="",val fullName:String="",val email:String="",val role:String="",val mfaEnabled:Boolean=false)
@Serializable data class ChallengeResponse(val success:Boolean=false,val challengeId:String="",val message:String="")
@Serializable data class RoleOption(val code:String="",val displayName:String="")

@Serializable
data class UserPayload(
    val id: Int = 0,
    val username: String = "",
    val fullName: String? = null,
    val role: String = "",
    val roleId: Int? = null,
    val email: String? = null,
    val active: Boolean = false,
    val department: String? = null,
    val branch: String? = null,
    val accessLevel: String? = null,
    val locked: Boolean = false,
    val mfaEnabled: Boolean = false,
)

@Serializable
data class LoginResponse(
    val success: Boolean = false,
    val user: UserPayload? = null,
    val message: String = "",
    val accessToken: String? = null,
    val expiresAt: String? = null,
    val mfaRequired: Boolean = false,
    val challengeId: String? = null,
    val maskedDestination: String? = null,
)

@Serializable
data class EffectivePermission(
    val module: String = "",
    val action: String = "",
    val description: String = "",
)

@Serializable
data class HealthResponse(val status: String = "UNKNOWN")

@Serializable
data class RuntimeHealthResponse(
    val ready: Boolean = false,
    val service: String = "",
    val version: String = "",
    val apiRevision: String = "",
    val buildRevision: String = "",
    val minimumSupportedDesktopVersion: String = "",
    val latestDesktopVersion: String = "",
    val minimumSupportedAndroidVersion: String = "",
    val latestAndroidVersion: String = "",
    val minimumSupportedIosVersion: String = "",
    val latestIosVersion: String = "",
    val environment: String = "",
    val database: String = "",
    val databaseName: String = "",
    val databaseTimeZone: String = "",
    val businessZone: String = "",
    val businessDate: String = "",
    val utcTime: String = "",
    val dateFormat: String = "",
    val timePolicy: String = "",
    val message: String = "",
)

@Serializable
data class OperationResponse(
    val success: Boolean = false,
    val message: String = "",
)

@Serializable
data class UserProfile(
    val id: Int = 0,
    val username: String = "",
    val fullName: String? = null,
    val email: String? = null,
    val role: String = "",
    val department: String? = null,
    val branch: String? = null,
    val accessLevel: String? = null,
    val active: Boolean = false,
    val locked: Boolean = false,
    val mfaEnabled: Boolean = false,
    val lastLogin: String? = null,
)
