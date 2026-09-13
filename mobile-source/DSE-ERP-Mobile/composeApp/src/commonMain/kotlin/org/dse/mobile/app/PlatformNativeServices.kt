package org.dse.mobile.app

import org.dse.mobile.core.api.SessionStore

data class BiometricAuthResult(
    val success: Boolean,
    val message: String = "",
)

expect fun platformSessionStore(): SessionStore
expect fun platformLoadLastServerUrl(): String?
expect fun platformSaveLastServerUrl(url: String)
expect fun platformBiometricAvailable(): Boolean
expect suspend fun platformAuthenticateBiometric(reason: String): BiometricAuthResult
expect fun platformSecurityLabel(): String
expect fun platformImportCapability(): String
expect fun platformLocalDateIso(): String
expect fun platformName(): String
expect fun platformDeviceClassLabel(): String
expect fun platformBiometricUnlockLabel(): String
expect fun platformImportFormatHint(): String


data class PushPermissionResult(
    val granted: Boolean,
    val message: String = "",
    val deviceToken: String? = null,
)

expect suspend fun platformRequestPushNotifications(): PushPermissionResult
expect fun platformPushCapability(): String
expect fun platformConsumeDeepLink(): String?

data class PickedAttachment(
    val fileName:String?=null,
    val base64:String?=null,
    val error:String?=null,
)

expect fun platformPickAttachment(onResult:(PickedAttachment)->Unit)
expect fun platformShareText(title:String,text:String): Boolean
expect fun platformShareFile(title:String,fileName:String,data:ByteArray): Boolean
expect fun platformOpenExternalUrl(url:String): Boolean


data class MobileUpdateInstallResult(
    val started: Boolean,
    val message: String = "",
)

expect suspend fun platformInstallMobileUpdate(version: String): MobileUpdateInstallResult

internal fun encodeBase64Portable(data:ByteArray):String{
    val alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    if(data.isEmpty())return ""
    val out=StringBuilder(((data.size+2)/3)*4)
    var i=0
    while(i<data.size){
        val b0=data[i].toInt() and 0xFF;val b1=if(i+1<data.size)data[i+1].toInt() and 0xFF else -1;val b2=if(i+2<data.size)data[i+2].toInt() and 0xFF else -1
        out.append(alphabet[b0 shr 2]);out.append(alphabet[((b0 and 3) shl 4) or if(b1>=0)b1 shr 4 else 0])
        if(b1>=0)out.append(alphabet[((b1 and 15) shl 2) or if(b2>=0)b2 shr 6 else 0]) else out.append('=')
        if(b2>=0)out.append(alphabet[b2 and 63]) else out.append('=');i+=3
    }
    return out.toString()
}

internal fun decodeBase64Portable(value:String):ByteArray{
    val alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val clean=value.filterNot{it.isWhitespace()}.trimEnd('=')
    if(clean.isEmpty())return ByteArray(0)
    val out=ArrayList<Byte>((clean.length*3)/4)
    var buffer=0;var bits=0
    for(ch in clean){
        val v=alphabet.indexOf(ch);if(v<0)continue
        buffer=(buffer shl 6) or v;bits+=6
        if(bits>=8){bits-=8;out.add(((buffer shr bits) and 0xFF).toByte())}
    }
    return out.toByteArray()
}


data class WidgetDashboardSnapshot(
    val salesToday: Double = 0.0,
    val receivables: Double = 0.0,
    val purchases: Double = 0.0,
    val payables: Double = 0.0,
    val bankBalance: Double = 0.0,
    val expenseMonth: Double = 0.0,
    val updatedAtMillis: Long = 0L,
)

data class LiveActivityResult(
    val success: Boolean,
    val message: String = "",
)

expect fun platformPublishWidgetSnapshot(snapshot: WidgetDashboardSnapshot)
expect fun platformSystemExperienceCapability(): String
expect suspend fun platformStartShippingLiveActivity(
    invoiceNo: String,
    customer: String,
    transporter: String,
    vehicle: String,
): LiveActivityResult
expect suspend fun platformUpdateShippingLiveActivity(invoiceNo: String, stage: String): LiveActivityResult
expect suspend fun platformEndShippingLiveActivity(invoiceNo: String): LiveActivityResult
