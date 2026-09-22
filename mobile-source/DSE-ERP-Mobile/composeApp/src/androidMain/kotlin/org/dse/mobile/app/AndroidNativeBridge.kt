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
    var loadThemeMode: () -> String? = { null }
    var saveThemeMode: (String) -> Unit = {}
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
    loadThemeMode: () -> String?,
    saveThemeMode: (String) -> Unit,
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
    AndroidNativeHandlers.loadThemeMode = loadThemeMode
    AndroidNativeHandlers.saveThemeMode = saveThemeMode
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
actual fun platformLoadThemeMode(): String? = AndroidNativeHandlers.loadThemeMode()
actual fun platformSaveThemeMode(mode: String) { AndroidNativeHandlers.saveThemeMode(mode) }
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
    "Android notification permission is available. Remote ERP delivery requires a registered Android device token on the 10.0.26 server."
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

actual fun platformShareTabularExport(title:String,baseName:String,headers:List<String>,rows:List<List<String>>,format:String):Boolean = runCatching {
    val safeBase=baseName.replace(Regex("[^A-Za-z0-9._-]+"),"-").trim('-').ifBlank{"export"}
    when(format.uppercase()){
        "XLSX"->{
            val bytes=androidXlsx(headers,rows)
            platformShareFile(title,"$safeBase.xlsx",bytes)
        }
        "PDF"->{
            val bytes=androidTablePdf(title,headers,rows)
            platformShareFile(title,"$safeBase.pdf",bytes)
        }
        else->{
            val text=buildString{appendLine(headers.joinToString(","){csvCell(it)});rows.forEach{r->appendLine(r.joinToString(","){csvCell(it)})}}
            platformShareFile(title,"$safeBase.csv",text.encodeToByteArray())
        }
    }
}.getOrDefault(false)

private fun csvCell(value:String):String{val v=value.replace("\"","\"\"");return if(v.any{it==','||it=='\n'||it=='\r'||it=='\"'})"\"$v\"" else v}
private fun xmlCell(value:String):String=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
private fun excelColumn(index:Int):String{var n=index+1;val s=StringBuilder();while(n>0){val r=(n-1)%26;s.append(('A'.code+r).toChar());n=(n-1)/26};return s.reverse().toString()}
private fun androidXlsx(headers:List<String>,rows:List<List<String>>):ByteArray{
    val out=java.io.ByteArrayOutputStream();val zip=java.util.zip.ZipOutputStream(out)
    fun entry(name:String,text:String){zip.putNextEntry(java.util.zip.ZipEntry(name));zip.write(text.encodeToByteArray());zip.closeEntry()}
    entry("[Content_Types].xml","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""")
    entry("_rels/.rels","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
    entry("xl/workbook.xml","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Export" sheetId="1" r:id="rId1"/></sheets></workbook>""")
    entry("xl/_rels/workbook.xml.rels","""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
    val all=listOf(headers)+rows
    val sheet=buildString{append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");all.forEachIndexed{ri,row->append("<row r=\"${ri+1}\">");row.forEachIndexed{ci,v->append("<c r=\"${excelColumn(ci)}${ri+1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlCell(v)}</t></is></c>")};append("</row>")};append("</sheetData></worksheet>")}
    entry("xl/worksheets/sheet1.xml",sheet);zip.finish();zip.close();return out.toByteArray()
}
private fun androidTablePdf(title:String,headers:List<String>,rows:List<List<String>>):ByteArray{
    val doc=android.graphics.pdf.PdfDocument();val paint=android.graphics.Paint().apply{isAntiAlias=true;textSize=9f;color=android.graphics.Color.BLACK};val bold=android.graphics.Paint(paint).apply{isFakeBoldText=true;textSize=11f};val width=842;val height=595;val margin=28f;val line=14f;val colWidth=((width-2*margin)/headers.size.coerceAtLeast(1)).coerceAtLeast(55f);var pageNo=0;var canvas:android.graphics.Canvas?=null;var y=0f;var currentPage:android.graphics.pdf.PdfDocument.Page?=null
    fun newPage(){pageNo++;val page=doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(width,height,pageNo).create());canvas=page.canvas;y=margin;canvas!!.drawText(title.take(100),margin,y,bold);y+=20f;headers.forEachIndexed{i,h->canvas!!.drawText(h.take(18),margin+i*colWidth,y,bold)};y+=line;currentPage=page}
    fun closePage(){currentPage?.let{doc.finishPage(it)};currentPage=null}
    newPage()
    rows.forEach{row->if(y>height-margin){closePage();newPage()};row.forEachIndexed{i,v->if(i<headers.size)canvas!!.drawText(v.replace('\n',' ').take(22),margin+i*colWidth,y,paint)};y+=line}
    closePage();val out=java.io.ByteArrayOutputStream();doc.writeTo(out);doc.close();return out.toByteArray()
}
