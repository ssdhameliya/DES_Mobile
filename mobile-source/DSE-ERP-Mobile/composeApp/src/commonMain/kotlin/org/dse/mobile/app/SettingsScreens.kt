@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.dse.mobile.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.dse.mobile.core.api.*
import org.dse.mobile.core.config.MobileBuildInfo
import org.dse.mobile.core.model.*

private enum class SettingsSection(val label:String){COMPANY("Company"),PAYMENT("Payment & Bank"),INVOICE("Invoice & Delivery"),NOTIFICATIONS("Notifications"),EMAIL("Email / SMTP"),SECURITY("Security & Session"),SYSTEM("Workspace & Updates"),APPEARANCE("Appearance")}

@Composable internal fun SettingsWorkspace(api:DseErpHttpClient,p:PermissionContext){
    var section by remember{mutableStateOf(SettingsSection.COMPANY)}
    var values by remember{mutableStateOf<Map<String,String>>(emptyMap())}
    var notification by remember{mutableStateOf(NotificationPreferences())}
    var email by remember{mutableStateOf(EmailSettings())}
    var storage by remember{mutableStateOf<StorageStatus?>(null)}
    var update by remember{mutableStateOf<UpdateReleaseView?>(null)}
    var setup by remember{mutableStateOf<SetupStatus?>(null)}
    var resources by remember{mutableStateOf<List<ServerResourceMeta>>(emptyList())}
    var msg by remember{mutableStateOf("Loading 10.0.16 settings…")}
    var refresh by remember{mutableIntStateOf(0)}
    var busy by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val canEdit=p.isAdmin()||p.can("SETTINGS","EDIT")||p.can("SETTINGS","UPDATE")
    val keys=remember{listOf(
        "company.name","company.phone","company.email","company.gstin","company.pan","company.businessType","company.industry","company.financialYearStart",
        "application.displayName","application.tagline","application.startingText",
        "payment.upiId","payment.accountHolder","payment.bankName","payment.accountNumber","payment.ifsc","payment.branch","payment.accountType","payment.bankMatchRoundingTolerance",
        "company.address","company.state","company.website","company.tagline","company.shipAddress","company.terms","company.currency","company.timeZone","company.dateFormat",
        "security.session.timeout.minutes","security.session.warning.minutes","security.auth.mfa.policy"
    )}
    LaunchedEffect(api,refresh){
        busy=true
        val loaded=linkedMapOf<String,String>()
        for(k in keys){when(val r=api.setting(k,"")){is ApiResult.Success->loaded[k]=r.value.value;else->Unit}}
        values=loaded
        (api.notificationPreferences() as? ApiResult.Success)?.value?.let{notification=it}
        (api.emailSettings() as? ApiResult.Success)?.value?.let{email=it}
        (api.storageStatus() as? ApiResult.Success)?.value?.let{storage=it}
        (api.latestUpdate(false) as? ApiResult.Success)?.value?.let{update=it}
        (api.setupStatus() as? ApiResult.Success)?.value?.let{setup=it}
        (api.serverResources() as? ApiResult.Success)?.value?.let{resources=it}
        BusinessBrandingState.apply(loaded["company.name"],loaded["application.displayName"],loaded["application.tagline"]?:loaded["company.tagline"],loaded["application.startingText"])
        msg="Settings synchronized with server 10.0.16"
        busy=false
    }
    fun set(k:String,v:String){values=values.toMutableMap().also{it[k]=v}}
    fun saveCurrent(){scope.launch{
        if(!canEdit){msg="Permission denied: settings are read-only";return@launch}
        busy=true
        val payload=when(section){
            SettingsSection.COMPANY->values.filterKeys{it.startsWith("company.")&&it !in setOf("company.address","company.state","company.website","company.tagline","company.shipAddress","company.terms","company.currency","company.timeZone","company.dateFormat")}+values.filterKeys{it.startsWith("application.")}
            SettingsSection.PAYMENT->values.filterKeys{it.startsWith("payment.")}
            SettingsSection.INVOICE->values.filterKeys{it in setOf("company.address","company.state","company.website","company.tagline","company.shipAddress","company.terms","company.currency","company.timeZone","company.dateFormat")}
            SettingsSection.SECURITY->values.filterKeys{it.startsWith("security.")}
            else->emptyMap()
        }
        val result=when(section){
            SettingsSection.NOTIFICATIONS->api.saveNotificationPreferences(notification)
            SettingsSection.EMAIL->api.saveEmailSettings(email)
            SettingsSection.APPEARANCE,SettingsSection.SYSTEM->ApiResult.Success(OperationResponse(true,"Saved locally / read-only system section"),ApiDataSource.LIVE)
            else->api.saveSettings(payload)
        }
        when(result){is ApiResult.Success->{msg=result.value.message.ifBlank{"Settings saved"};refreshBusinessBranding(api)};else->msg=result.readableMessage()}
        busy=false
    }}

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        DseHeroKpi("Settings","Desktop 10.0.16 parity","Server-owned configuration • Android presentation",Icons.Rounded.Settings)
        ScrollableTabRow(SettingsSection.entries.indexOf(section),edgePadding=0.dp){SettingsSection.entries.forEach{s->Tab(section==s,{section=s},text={Text(s.label)})}}
        DseMessageFeedback(msg)
        when(section){
            SettingsSection.COMPANY->SettingFields(values,::set,listOf(
                "company.name" to "Company Name","company.phone" to "Phone","company.email" to "Company Email","company.gstin" to "GSTIN","company.pan" to "PAN","company.businessType" to "Business Type","company.industry" to "Industry","company.financialYearStart" to "Financial Year Start","application.displayName" to "Application Display Name","application.tagline" to "Tagline","application.startingText" to "Startup Message"))
            SettingsSection.PAYMENT->SettingFields(values,::set,listOf(
                "payment.upiId" to "UPI ID","payment.accountHolder" to "Account Holder","payment.bankName" to "Bank Name","payment.accountNumber" to "Account Number","payment.ifsc" to "IFSC","payment.branch" to "Branch","payment.accountType" to "Account Type","payment.bankMatchRoundingTolerance" to "Bank Match Tolerance"))
            SettingsSection.INVOICE->SettingFields(values,::set,listOf(
                "company.address" to "Company Address","company.shipAddress" to "Delivery / Ship-to Address","company.state" to "State / Place of Supply","company.currency" to "Currency","company.timeZone" to "Timezone","company.dateFormat" to "Date Format","company.website" to "Website","company.tagline" to "Invoice Tagline","company.terms" to "Invoice Terms"))
            SettingsSection.NOTIFICATIONS->NotificationPreferenceEditor(notification){notification=it}
            SettingsSection.EMAIL->EmailSettingsEditor(email,{email=it}){recipient->scope.launch{busy=true;when(val r=api.testEmail(recipient)){is ApiResult.Success->msg=r.value.message;else->msg=r.readableMessage()};busy=false}}
            SettingsSection.SECURITY->{
                SettingFields(values,::set,listOf("security.session.timeout.minutes" to "Session Timeout (minutes)","security.session.warning.minutes" to "Warning Before Timeout (minutes)"))
                DseSelect("MFA Policy",values["security.auth.mfa.policy"].orEmpty().ifBlank{"REQUIRED"},listOf("REQUIRED","ADMIN_CONTROLLED","DISABLED"),onValue={set("security.auth.mfa.policy",it)})
                Text("Authentication policy is server-owned and applies consistently to desktop and Android.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsSection.SYSTEM->{
                val s=storage
                if(s!=null){
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){DseMetricTile("Managed",formatBytes(s.totalManagedBytes),Icons.Rounded.Storage,Modifier.weight(1f),DseInfo);DseMetricTile("Attachments",formatBytes(s.attachmentsBytes),Icons.Rounded.AttachFile,Modifier.weight(1f),DsePurple)}
                    PremiumCard(Modifier.fillMaxWidth(),padding=11.dp){Text("Workspace",fontWeight=FontWeight.ExtraBold);Text(s.workspace.ifBlank{"Server-managed workspace"});Text("Documents ${formatBytes(s.documentsBytes)} • Logs ${formatBytes(s.logsBytes)} • Temp ${formatBytes(s.tempBytes)}",style=MaterialTheme.typography.bodySmall);if(s.lastCleanupAt.isNotBlank())Text("Last cleanup: ${s.lastCleanupAt}",style=MaterialTheme.typography.bodySmall)}
                }
                setup?.let{PremiumCard(Modifier.fillMaxWidth(),padding=11.dp){Text("Setup",fontWeight=FontWeight.ExtraBold);Text(if(it.required)"Initial server setup is required" else "Configured • ${it.userCount} users • ${it.adminCount} admins")}}
                update?.let{u->PremiumCard(Modifier.fillMaxWidth(),padding=11.dp){Text("Update Center",fontWeight=FontWeight.ExtraBold);Text(u.name.ifBlank{u.tagName});Text(u.publishedAt,style=MaterialTheme.typography.bodySmall);if(u.body.isNotBlank())Text(u.body.take(400),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
                PremiumCard(Modifier.fillMaxWidth(),padding=11.dp){Text("Server Resources",fontWeight=FontWeight.ExtraBold);Text("${resources.size} managed resource(s)",style=MaterialTheme.typography.bodySmall);resources.take(8).forEach{Text("${it.key} • ${it.fileName} • ${formatBytes(it.size)}",style=MaterialTheme.typography.bodySmall)}}
                PremiumCard(Modifier.fillMaxWidth(),padding=11.dp){Text("Android Release",fontWeight=FontWeight.ExtraBold);Text("Mobile ${MobileBuildInfo.MOBILE_VERSION}");Text("Server baseline ${MobileBuildInfo.SERVER_BASELINE} • ${MobileBuildInfo.EXPECTED_API_REVISION}",style=MaterialTheme.typography.bodySmall)}
            }
            SettingsSection.APPEARANCE->{
                PremiumCard(Modifier.fillMaxWidth(),padding=12.dp){
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Dark theme",fontWeight=FontWeight.Bold);Text("Stored on this Android device",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(MobileThemeState.dark,{MobileThemeState.setDark(it)})}
                }
                Text("Company-facing identity is loaded from the server instead of being hard-coded per screen.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(section !in setOf(SettingsSection.SYSTEM,SettingsSection.APPEARANCE))PremiumPrimaryButton(if(busy)"Saving…" else "Save ${section.label}",{saveCurrent()},Modifier.fillMaxWidth(),enabled=canEdit&&!busy,leadingIcon=Icons.Rounded.Save)
        PremiumSecondaryButton("Refresh from server",{refresh++},Modifier.fillMaxWidth(),enabled=!busy,icon=Icons.Rounded.Refresh)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun SettingFields(values:Map<String,String>,onValue:(String,String)->Unit,fields:List<Pair<String,String>>){fields.forEach{(key,label)->DseField(label,values[key].orEmpty(),singleLine=key !in setOf("company.address","company.shipAddress","company.terms","application.startingText"),onValue={onValue(key,it)})}}

@Composable private fun NotificationPreferenceEditor(value:NotificationPreferences,onValue:(NotificationPreferences)->Unit){
    val categories=listOf("sales","purchases","quotations","returns","payments","inventory","banking","reminders","communication","approval","imports","update","security","system")
    PremiumCard(Modifier.fillMaxWidth(),padding=11.dp){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Notifications enabled",Modifier.weight(1f),fontWeight=FontWeight.Bold);Switch(value.enabled,{onValue(value.copy(enabled=it))})}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("In-app toasts",Modifier.weight(1f));Switch(value.toasts,{onValue(value.copy(toasts=it))})}
    }
    categories.chunked(2).forEach{pair->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){pair.forEach{c->PremiumCard(Modifier.weight(1f),padding=9.dp){Row(verticalAlignment=Alignment.CenterVertically){Text(c.replaceFirstChar{it.uppercase()},Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold);Switch(value.categories[c]?:true,{checked->onValue(value.copy(categories=value.categories.toMutableMap().also{it[c]=checked}))})}}};if(pair.size==1)Spacer(Modifier.weight(1f))}}
}

@Composable private fun EmailSettingsEditor(value:EmailSettings,onValue:(EmailSettings)->Unit,onTest:(String)->Unit){
    var recipient by remember{mutableStateOf(value.email)}
    DseField("Sending Email",value.email,singleLine=true,onValue={onValue(value.copy(email=it));if(recipient.isBlank())recipient=it})
    DseField(if(value.passwordConfigured)"App Password (leave blank to keep current)" else "App Password",value.appPassword,singleLine=true,onValue={onValue(value.copy(appPassword=it))})
    DseField("SMTP Host",value.host,singleLine=true,onValue={onValue(value.copy(host=it))})
    DseField("SMTP Port",value.port?.toString().orEmpty(),singleLine=true,onValue={onValue(value.copy(port=it.toIntOrNull()))})
    DseField("Test Recipient",recipient,singleLine=true,onValue={recipient=it})
    PremiumSecondaryButton("Send Test Email",{if(recipient.isNotBlank())onTest(recipient)},Modifier.fillMaxWidth(),enabled=recipient.isNotBlank(),icon=Icons.Rounded.Send)
}

private fun formatBytes(bytes:Long):String{
    val b=bytes.coerceAtLeast(0)
    fun one(v:Double):String{val x=kotlin.math.round(v*10.0)/10.0;return if(x==kotlin.math.round(x))kotlin.math.round(x).toLong().toString() else x.toString()}
    return when{b>=1024L*1024L*1024L->"${one(b.toDouble()/(1024L*1024L*1024L))} GB";b>=1024L*1024L->"${one(b.toDouble()/(1024L*1024L))} MB";b>=1024L->"${one(b.toDouble()/1024L)} KB";else->"$b B"}
}
