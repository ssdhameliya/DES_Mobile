@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.dse.mobile.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.dse.mobile.core.api.*
import org.dse.mobile.core.config.MobileBuildInfo
import org.dse.mobile.core.config.MobileUpdateRequirement
import org.dse.mobile.core.config.evaluateMobileCompatibility
import org.dse.mobile.core.model.*
import org.dse.mobile.core.offline.OfflineRepository
import org.dse.mobile.core.offline.platformOfflineNowMillis
import org.dse.mobile.core.security.MobileSecurityPolicy

enum class MainTab(val label:String){DASHBOARD("Dashboard"),SALES("Sales"),PURCHASE("Purchase"),BANK("Bank Statement"),IMPORT("Import"),MORE("More")}
enum class MoreDestination(val label:String){PURCHASE("Purchase"),QUOTATIONS("Quotations"),SALES_RETURNS("Sales Returns"),PURCHASE_RETURNS("Purchase Returns"),MASTERS("Master Data"),INVENTORY("Inventory"),PURCHASE_RECON("Purchase Reconciliation"),COMMUNICATIONS("Communication Center"),REMINDERS("Reminders"),NOTIFICATIONS("Notifications"),REPORTS("Reports"),PROFILE("Profile & Password"),ADMIN("User Access & Roles"),IMPORT("Data Import"),SYNC("Sync Center"),ABOUT("About")}
private enum class RootPage { STARTUP,LOGIN,MFA,APP }

data class RecordTarget(val moduleKey:String,val reference:String="",val recordId:Long?=null,val openActions:Boolean=false)

data class PermissionContext(val user:UserPayload?,val permissions:List<EffectivePermission>){
    private val admin:Boolean get()=user?.role.equals("ADMIN",true)
    fun can(module:String,action:String="VIEW"):Boolean=admin||permissions.any{it.module.equals(module,true)&&it.action.equals(action,true)}
    fun isAdmin():Boolean=admin
}

@Composable fun App(){
 DseErpTheme{
  val sessions=remember{platformSessionStore()};var api by remember{mutableStateOf<DseErpHttpClient?>(null)};var root by remember{mutableStateOf(RootPage.STARTUP)}
  var serverUrl by remember{mutableStateOf(normalizeInitialServerUrl(platformLoadLastServerUrl()))};var user by remember{mutableStateOf<UserPayload?>(null)};var permissions by remember{mutableStateOf<List<EffectivePermission>>(emptyList())}
  var saved by remember{mutableStateOf(sessions.accessToken()?.isNotBlank()==true)};val biometric=platformBiometricAvailable();var challenge by remember{mutableStateOf("")};var destination by remember{mutableStateOf("")};var message by remember{mutableStateOf("")};var resetOpen by remember{mutableStateOf(false)};var registerOpen by remember{mutableStateOf(false)}
  DisposableEffect(api){val a=api;onDispose{a?.close()}}
  when(root){
   RootPage.STARTUP->StartupScreen(serverUrl){startupMessage->message=startupMessage;root=RootPage.LOGIN}
   RootPage.LOGIN->LoginScreen(serverUrl,{serverUrl=it},message,saved&&biometric&&OfflineRepository.biometricLoginEnabled(),{identity,password->
    val a=DseErpHttpClient(serverUrl,sessions);api?.close();api=a
    when(val runtime=a.runtimeHealth()){
     is ApiResult.Success->{BusinessDateContext.update(runtime.value.businessDate,runtime.value.businessZone);val incompat=runtimeCompatibilityProblem(runtime.value);if(incompat!=null)message=incompat else when(val r=a.login(identity.trim(),password)){
      is ApiResult.Success->if(!r.value.success)message=r.value.message.ifBlank{"Login failed"} else if(r.value.mfaRequired){user=r.value.user;challenge=r.value.challengeId.orEmpty();destination=r.value.maskedDestination.orEmpty();root=RootPage.MFA}else{user=r.value.user;when(val perms=a.effectivePermissions()){is ApiResult.Success->{permissions=perms.value;activateOffline(serverUrl,user,permissions);platformSaveLastServerUrl(serverUrl);saved=sessions.accessToken()?.isNotBlank()==true;message="Connected to Jasvi Industries ${runtime.value.version}";root=RootPage.APP};else->{message="Login succeeded, but permissions could not be loaded safely. ${perms.readableMessage()}";a.logout();sessions.clear();saved=false}}}
      else->message=r.readableMessage()
     }}
     else->message="Server contract check failed. ${runtime.readableMessage()}"
    }
   },{val probe=DseErpHttpClient(serverUrl,InMemorySessionStore());val r=probe.runtimeHealth();probe.close();message=when(r){is ApiResult.Success->{BusinessDateContext.update(r.value.businessDate,r.value.businessZone);runtimeCompatibilityProblem(r.value)?:mobileOptionalUpdateNotice(r.value)?.let{"Connected: ${r.value.service} ${r.value.version} • $it"}?:"Connected: ${r.value.service} ${r.value.version} • ${r.value.message}"};else->r.readableMessage()}},{
    val auth=platformAuthenticateBiometric("Unlock Jasvi Industries Mobile")
    if(!auth.success)message=auth.message.ifBlank{"Biometric unlock was not completed"} else {val a=DseErpHttpClient(serverUrl,sessions);api?.close();api=a;val runtime=a.runtimeHealth();(runtime as? ApiResult.Success)?.value?.let{BusinessDateContext.update(it.businessDate,it.businessZone)};val incompat=(runtime as? ApiResult.Success)?.value?.let(::runtimeCompatibilityProblem);if(incompat!=null)message=incompat else if(runtime !is ApiResult.Success&&runtime !is ApiResult.NetworkError)message="Server contract check failed. ${runtime.readableMessage()}" else when(val p=a.currentProfile()){
      is ApiResult.Success->{user=p.value.toUserPayload();when(val perms=a.effectivePermissions()){is ApiResult.Success->{permissions=perms.value;activateOffline(serverUrl,user,permissions);message="Unlocked securely";root=RootPage.APP};else->{message="Could not validate permissions. ${perms.readableMessage()}";a.logout();sessions.clear();saved=false}}}
      is ApiResult.NetworkError->{val cached=OfflineRepository.readAuthSnapshot(serverUrl);if(cached!=null){user=cached.user;permissions=cached.permissions;OfflineRepository.activateScope("$serverUrl|${cached.user.username}");message="Offline read mode • permissions last validated ${cacheAgeLabel(cached.validatedAtMillis)}";root=RootPage.APP}else message="Server offline and no validated offline profile is available"}
      else->{if(p is ApiResult.Unauthorized){sessions.clear();saved=false};message=p.readableMessage()}
    }}
   },{resetOpen=true},{registerOpen=true})
   RootPage.MFA->MfaScreen(destination,message,{root=RootPage.LOGIN},{scopeMessage->message=scopeMessage},{scopeChallenge,scopeDestination->challenge=scopeChallenge;if(scopeDestination.isNotBlank())destination=scopeDestination},{val a=api;if(a==null){message="Authentication session is unavailable";null}else when(val r=a.resendMfa(challenge)){is ApiResult.Success->{if(r.value.success){message=r.value.message.ifBlank{"Verification code resent"};Pair(r.value.challengeId.ifBlank{challenge},r.value.maskedDestination.orEmpty())}else{message=r.value.message;null}};else->{message=r.readableMessage();null}}},{otp->val a=api?:return@MfaScreen;when(val r=a.completeMfa(challenge,otp.trim())){is ApiResult.Success->if(r.value.success&&!r.value.mfaRequired){user=r.value.user?:user;when(val perms=a.effectivePermissions()){is ApiResult.Success->{permissions=perms.value;activateOffline(serverUrl,user,permissions);saved=true;message="MFA verified";root=RootPage.APP};else->{message="MFA succeeded, but permissions could not be validated. ${perms.readableMessage()}";a.logout();sessions.clear();saved=false;root=RootPage.LOGIN}}}else message=r.value.message;else->message=r.readableMessage()}})
   RootPage.APP->{val a=api;if(a==null)Text("Session unavailable")else MainApp(a,PermissionContext(user,permissions),message){a.logout();OfflineRepository.clearAuthSnapshot(serverUrl);OfflineRepository.clearScopeData();OfflineRepository.deactivateScope();a.close();api=null;user=null;permissions=emptyList();saved=false;message="Signed out securely; cached ERP data for this session was cleared";root=RootPage.LOGIN}}
  }
  if(resetOpen)PasswordResetDialog(serverUrl,{resetOpen=false}){message=it;resetOpen=false}
  if(registerOpen)RegistrationDialog(serverUrl,{registerOpen=false}){message=it;registerOpen=false}
 }
}

private fun normalizeInitialServerUrl(saved:String?):String{
 val defaultUrl=MobileBuildInfo.DEFAULT_DEV_SERVER_URL
 if(!MobileBuildInfo.SERVER_EDITING_ALLOWED)return defaultUrl
 val value=saved?.trim().orEmpty()
 return when{
  value.isBlank()->defaultUrl
  value.equals(MobileBuildInfo.LEGACY_TEST_SERVER_URL,ignoreCase=true)->defaultUrl
  MobileSecurityPolicy.endpointProblem(value)!=null->defaultUrl
  else->value
 }
}

private fun runtimeCompatibilityProblem(status:RuntimeHealthResponse):String?{
 val baseProblem=when{
  !status.ready->"Jasvi Industries server is not ready: ${status.message.ifBlank{"database health check failed"}}"
  status.service!=MobileBuildInfo.EXPECTED_SERVER_SERVICE->"This address is not the expected Jasvi Industries server (${status.service.ifBlank{"unknown service"}})."
  status.apiRevision!=MobileBuildInfo.EXPECTED_API_REVISION->"Jasvi Industries API revision mismatch. Mobile requires ${MobileBuildInfo.EXPECTED_API_REVISION}; server reports ${status.apiRevision.ifBlank{"unknown"}}."
  status.environment.isNotBlank()&&!status.environment.equals(MobileBuildInfo.expectedEnvironment(),true)->"This ${MobileBuildInfo.RELEASE_CHANNEL} app cannot sign in to the ${status.environment} environment."
  compareServerVersion(status.version,MobileBuildInfo.MINIMUM_COMPATIBLE_SERVER_VERSION)<0->"Mobile ${MobileBuildInfo.MOBILE_VERSION} requires compatible server ${MobileBuildInfo.MINIMUM_COMPATIBLE_SERVER_VERSION} or newer with API revision ${MobileBuildInfo.EXPECTED_API_REVISION}; server reports ${status.version.ifBlank{"unknown"}}."
  else->null
 }
 if(baseProblem!=null)return baseProblem
 val mobile=evaluateMobileCompatibility(status,platformName())
 return if(mobile.requirement==MobileUpdateRequirement.REQUIRED_UPDATE)
  "${platformName()} Mobile ${mobile.currentVersion} is no longer supported by this server. Minimum supported mobile version is ${mobile.minimumVersion}; latest available is ${mobile.latestVersion}. Update the mobile app before signing in."
 else null
}

private fun mobileOptionalUpdateNotice(status:RuntimeHealthResponse):String?{
 val mobile=evaluateMobileCompatibility(status,platformName())
 return if(mobile.requirement==MobileUpdateRequirement.OPTIONAL_UPDATE)
  "Optional mobile update: ${mobile.latestVersion} is available. Your ${mobile.currentVersion} remains supported, so you can continue and update when convenient."
 else null
}

private fun mobileUpdateVersion(message:String):String?{
 val optional=Regex("""Optional mobile update:\s*([0-9]+(?:\.[0-9]+){1,3})""",RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)
 if(!optional.isNullOrBlank())return optional
 return Regex("""latest available is\s*([0-9]+(?:\.[0-9]+){1,3})""",RegexOption.IGNORE_CASE).find(message)?.groupValues?.getOrNull(1)
}

private fun compareServerVersion(actual:String,minimum:String):Int{
 val a=actual.split('.').map{it.takeWhile(Char::isDigit).toIntOrNull()?:0};val b=minimum.split('.').map{it.takeWhile(Char::isDigit).toIntOrNull()?:0};val n=maxOf(a.size,b.size)
 for(i in 0 until n){val av=a.getOrElse(i){0};val bv=b.getOrElse(i){0};if(av!=bv)return av.compareTo(bv)}
 return 0
}

private fun activateOffline(server:String,user:UserPayload?,permissions:List<EffectivePermission>){OfflineRepository.activateScope("$server|${user?.username.orEmpty()}");user?.let{OfflineRepository.saveAuthSnapshot(server,it,permissions)}}

@Composable private fun StartupScreen(server:String,onComplete:(String)->Unit){
 var status by remember{mutableStateOf("Preparing your secure workspace")}
 LaunchedEffect(server){
  val started=platformOfflineNowMillis()
  val probe=DseErpHttpClient(server,InMemorySessionStore())
  val result=try{withTimeoutOrNull(10_000){probe.runtimeHealth()}}finally{probe.close()}
  val outcome=when(result){
   is ApiResult.Success->{
    BusinessDateContext.update(result.value.businessDate,result.value.businessZone)
    runtimeCompatibilityProblem(result.value) ?: mobileOptionalUpdateNotice(result.value)?.let{"Connected: ${result.value.service} ${result.value.version} • $it"} ?: "Connected: ${result.value.service} ${result.value.version} • ${result.value.message}"
   }
   null->"${MobileBuildInfo.RELEASE_CHANNEL} server unavailable. Connection check timed out."
   else->"${MobileBuildInfo.RELEASE_CHANNEL} server unavailable. ${result.readableMessage()}"
  }
  status=if(outcome.startsWith("Connected:",ignoreCase=true))"Workspace ready" else "Secure sign in ready"
  val elapsed=platformOfflineNowMillis()-started
  if(elapsed<1_150)delay(1_150-elapsed)
  onComplete(outcome)
 }
 PremiumBackdrop(dark=true){
  Column(
   Modifier.align(Alignment.Center).padding(horizontal=28.dp),
   horizontalAlignment=Alignment.CenterHorizontally,
   verticalArrangement=Arrangement.spacedBy(18.dp),
  ){
   Box(contentAlignment=Alignment.Center){
    Surface(color=Color.White.copy(.07f),shape=androidx.compose.foundation.shape.CircleShape,modifier=Modifier.size(112.dp)){}
    DseBrandMark(dark=true)
   }
   Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)){
    Text("Jasvi Industries",color=Color.White,style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.ExtraBold)
    Text("Business. Anywhere.",color=Color.White.copy(.78f),style=MaterialTheme.typography.titleMedium)
   }
   Spacer(Modifier.height(8.dp))
   LinearProgressIndicator(
    modifier=Modifier.width(170.dp).height(5.dp),
    color=Color.White,
    trackColor=Color.White.copy(.13f),
   )
   Text(status,color=Color.White.copy(.74f),style=MaterialTheme.typography.labelMedium)
  }
  Column(
   Modifier.align(Alignment.BottomCenter).padding(bottom=28.dp),
   horizontalAlignment=Alignment.CenterHorizontally,
   verticalArrangement=Arrangement.spacedBy(5.dp),
  ){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)){
    Icon(Icons.Rounded.VerifiedUser,null,Modifier.size(15.dp),tint=Color.White.copy(.70f))
    Text("Protected enterprise workspace",color=Color.White.copy(.70f),style=MaterialTheme.typography.bodySmall)
   }
   Text("PEOPLE  •  PROCESS  •  PROGRESS",color=Color.White.copy(.48f),style=MaterialTheme.typography.labelSmall)
  }
 }
}

private fun friendlyLoginMessage(message:String):String{
 val serverMessage=Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(message)?.groupValues?.getOrNull(1)?.replace("\\n"," ")?.replace("\\\"","\"")?.trim()
 return when{
  message.isBlank()->""
  message.startsWith("Connected:",ignoreCase=true)->{
   val connectedVersion=Regex("""Connected:\s+\S+\s+([0-9][0-9A-Za-z._-]*)""").find(message)?.groupValues?.getOrNull(1)
   val optional=message.substringAfter("•","").trim().takeIf{it.startsWith("Optional mobile update:",ignoreCase=true)}
   "${MobileBuildInfo.RELEASE_CHANNEL} server online • Jasvi Industries ${connectedVersion?.takeIf{it.isNotBlank()}?:MobileBuildInfo.SERVER_BASELINE}${optional?.let{" • $it"}.orEmpty()}"
  }
  message.contains("Network error",ignoreCase=true)->"Unable to reach the ${MobileBuildInfo.RELEASE_CHANNEL} server. Check your internet connection or Server Settings."
  message.contains("No such host",ignoreCase=true)->"The ${MobileBuildInfo.RELEASE_CHANNEL} server address could not be resolved. Open Server Settings and verify the address."
  !serverMessage.isNullOrBlank()->serverMessage
  message.startsWith("Authentication failed:",ignoreCase=true)->"Sign in failed. Check your username and password and try again."
  else->message
 }
}

@Composable private fun LoginScreen(server:String,onServer:(String)->Unit,message:String,biometricReady:Boolean,onLogin:suspend(String,String)->Unit,onHealth:suspend()->Unit,onBiometric:suspend()->Unit,onForgot:()->Unit,onRegister:()->Unit){
 var identity by remember{mutableStateOf("")}
 var password by remember{mutableStateOf("")}
 var serverSettings by remember{mutableStateOf(false)}
 var busy by remember{mutableStateOf(false)}
 var localUpdateMessage by remember{mutableStateOf("")}
 val scope=rememberCoroutineScope()
 val focusManager=LocalFocusManager.current
 val feedback=localUpdateMessage.ifBlank{friendlyLoginMessage(message)}
 val updateVersion=mobileUpdateVersion(message)
 val positive=feedback.startsWith("${MobileBuildInfo.RELEASE_CHANNEL} server online",ignoreCase=true)||feedback.startsWith("Connected",ignoreCase=true)||feedback.startsWith("Unlocked",ignoreCase=true)||feedback.startsWith("APK verified",ignoreCase=true)
 val errorLike=feedback.contains("failed",ignoreCase=true)||feedback.contains("unable",ignoreCase=true)||feedback.contains("invalid",ignoreCase=true)||feedback.contains("mismatch",ignoreCase=true)||feedback.contains("not ready",ignoreCase=true)||feedback.contains("unavailable",ignoreCase=true)||feedback.contains("no longer supported",ignoreCase=true)

 PremiumBackdrop{
  BoxWithConstraints(Modifier.fillMaxSize()){
   val wide=maxWidth>700.dp
   val viewportHeight=maxHeight
   Column(
    Modifier.fillMaxSize().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()),
   ){
    Row(
     Modifier.fillMaxWidth().heightIn(min=viewportHeight).padding(horizontal=if(wide)48.dp else 18.dp,vertical=18.dp),
     horizontalArrangement=Arrangement.Center,
     verticalAlignment=Alignment.CenterVertically,
    ){
    if(wide){
     Column(Modifier.widthIn(max=390.dp).padding(end=42.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
      DseBrandMark()
      Text("Run your business\nfrom anywhere.",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.ExtraBold)
      Text("Sales, purchases, inventory, finance and customer intelligence — one secure mobile workspace.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){DseStatus("","SECURE");DseStatus("","OFFLINE READY")}
     }
    }
    PremiumCard(
     modifier=Modifier.widthIn(max=460.dp).fillMaxWidth(),
     padding=22.dp,
    ){
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
      Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){
       DseBrandMark(compact=true)
       Column{
        Text("Jasvi Industries",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold)
        Text("Enterprise mobile",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
       }
      }
      IconButton(onClick={serverSettings=true}){Icon(Icons.Rounded.Tune,"Server settings",tint=MaterialTheme.colorScheme.onSurfaceVariant)}
     }

     Spacer(Modifier.height(2.dp))
     Column(verticalArrangement=Arrangement.spacedBy(3.dp)){
      Text("Welcome back",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.ExtraBold)
      Text("Sign in to continue where you left off.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }

     DseField("Email or Username",identity,singleLine=true,required=true,icon=Icons.Rounded.Person,onValue={identity=it})
     DsePasswordField("Password",password,required=true,onValue={password=it},onDone={if(!busy&&identity.isNotBlank()&&password.isNotBlank()){focusManager.clearFocus();scope.launch{busy=true;onLogin(identity,password);busy=false}}})

     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
      UatStatusPill(online=if(errorLike)false else if(positive)true else null,modifier=Modifier.weight(1f,false))
      Spacer(Modifier.weight(1f))
      TextButton(enabled=!busy,onClick=onForgot){Text("Forgot password?")}
     }

     PremiumPrimaryButton(
      text=if(busy)"Signing in…" else "Sign In",
      enabled=!busy&&identity.isNotBlank()&&password.isNotBlank(),
      onClick={focusManager.clearFocus();scope.launch{busy=true;onLogin(identity,password);busy=false}},
      modifier=Modifier.fillMaxWidth(),
      leadingIcon=if(busy)null else Icons.Rounded.Login,
      trailingIcon=if(busy)null else Icons.Rounded.ArrowForward,
     )

     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
      HorizontalDivider(Modifier.weight(1f),color=MaterialTheme.colorScheme.outlineVariant)
      Text("QUICK & SECURE",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.SemiBold)
      HorizontalDivider(Modifier.weight(1f),color=MaterialTheme.colorScheme.outlineVariant)
     }
     PremiumBiometricPanel(
      enabled=biometricReady&&!busy,
      onClick={focusManager.clearFocus();scope.launch{busy=true;onBiometric();busy=false}},
     )

     if(feedback.isNotBlank()&&!positive){
      val container=if(errorLike)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
      val content=if(errorLike)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant
      Surface(color=container,shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){
       Row(Modifier.padding(11.dp),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Icon(if(errorLike)Icons.Rounded.ErrorOutline else Icons.Rounded.Info,null,modifier=Modifier.size(18.dp),tint=content)
        Text(feedback,color=content,style=MaterialTheme.typography.bodySmall)
       }
      }
     }

     if(updateVersion!=null&&platformName().equals("Android",true)){
      PremiumPrimaryButton(
       text=if(busy)"Preparing update…" else "Update Now • $updateVersion",
       enabled=!busy,
       onClick={scope.launch{
        busy=true
        val update=platformInstallMobileUpdate(updateVersion)
        localUpdateMessage=update.message
        busy=false
       }},
       modifier=Modifier.fillMaxWidth(),
       leadingIcon=Icons.Rounded.Download,
      )
     }

     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
      Text("Need a Jasvi account?",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
      TextButton(enabled=!busy,onClick=onRegister){Text("Register")}
     }
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){
      Icon(Icons.Rounded.Shield,null,modifier=Modifier.size(14.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.width(5.dp))
      Text(platformSecurityLabel(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
   }
  }
  }
 }

 if(serverSettings){
  PremiumAlertDialog(
   onDismissRequest={serverSettings=false},
   title={Text("${MobileBuildInfo.RELEASE_CHANNEL} Server Settings")},
   text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
    Text(if(MobileBuildInfo.SERVER_EDITING_ALLOWED)"Connection settings are available for controlled ${MobileBuildInfo.RELEASE_CHANNEL} diagnostics. The opposite environment remains blocked." else "Production server routing is locked by the signed PROD build.",style=MaterialTheme.typography.bodySmall)
    DseField("ERP server URL",server,singleLine=true,required=true,readOnly=!MobileBuildInfo.SERVER_EDITING_ALLOWED,onValue=onServer)
    UatStatusPill(if(errorLike)false else if(positive)true else null)
   }},
   confirmButton={PremiumPrimaryButton("Test Connection",{scope.launch{busy=true;onHealth();busy=false}},enabled=!busy,leadingIcon=Icons.Rounded.WifiTethering)},
   dismissButton={TextButton(onClick={serverSettings=false}){Text("Done")}},
  )
 }
}



@Composable private fun MfaScreen(destination:String,message:String,onBack:()->Unit,onMessage:(String)->Unit,onChallenge:(String,String)->Unit,onResend:suspend()->Pair<String,String>?,onVerify:suspend(String)->Unit){
 var otp by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 PremiumBackdrop{
  Box(Modifier.fillMaxSize().padding(18.dp),contentAlignment=Alignment.Center){
   PremiumCard(Modifier.widthIn(max=440.dp).fillMaxWidth(),padding=22.dp){
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)){
     PremiumIconTile(Icons.Rounded.VerifiedUser,MaterialTheme.colorScheme.primary,size=46.dp)
     Column{Text("Verify it’s you",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.ExtraBold);Text("Multi-factor authentication",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    Text(if(destination.isBlank())"Enter the verification code to continue." else "We sent a verification code to $destination",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    DseField("Verification code",otp,singleLine=true,required=true,onValue={otp=it})
    Button(enabled=!busy&&otp.isNotBlank(),onClick={scope.launch{busy=true;onVerify(otp);busy=false}},modifier=Modifier.fillMaxWidth().height(54.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(18.dp)){
     if(busy)CircularProgressIndicator(Modifier.size(18.dp),color=MaterialTheme.colorScheme.onPrimary,strokeWidth=2.dp) else Icon(Icons.Rounded.LockOpen,null)
     Spacer(Modifier.width(8.dp));Text("Verify & Continue")
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(enabled=!busy,onClick=onBack){Text("Back")};TextButton(enabled=!busy,onClick={scope.launch{busy=true;val update=onResend();if(update!=null)onChallenge(update.first,update.second);busy=false}}){Text("Resend code")}}
    if(message.isNotBlank())Surface(color=MaterialTheme.colorScheme.surfaceVariant.copy(.55f),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()){Text(message,Modifier.padding(10.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   }
  }
 }
}

@Composable private fun PasswordResetDialog(serverUrl:String,onClose:()->Unit,onDone:(String)->Unit){
 var identity by remember{mutableStateOf("")};var challenge by remember{mutableStateOf("")};var otp by remember{mutableStateOf("")};var totp by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var msg by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 val client=remember(serverUrl){DseErpHttpClient(serverUrl,InMemorySessionStore())};DisposableEffect(client){onDispose{client.close()}}
 PremiumAlertDialog(onDismissRequest=onClose,title={Text("Reset Password")},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
  if(challenge.isBlank()){
   DseField("Username / email",identity,singleLine=true,required=true,onValue={identity=it})
   Text("Jasvi Industries 10.0.5 sends a verification code to the registered email.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
  } else {
   DseField("Email Verification Code",otp,singleLine=true,required=true,onValue={otp=it})
   DseField("Authenticator Code",totp,singleLine=true,supporting="Required when MFA is enrolled for this account",onValue={totp=it})
   DsePasswordField("New Password",password,required=true,onValue={password=it})
   DsePasswordField("Confirm Password",confirm,required=true,onValue={confirm=it})
  }
  if(msg.isNotBlank())Text(msg,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
 }},confirmButton={Button(enabled=!busy&&if(challenge.isBlank())identity.isNotBlank() else otp.isNotBlank()&&password.length>=8&&password==confirm,onClick={scope.launch{
  busy=true
  if(challenge.isBlank())when(val r=client.requestPasswordReset(identity)){
   is ApiResult.Success->{if(r.value.success){challenge=r.value.challengeId;msg=r.value.message.ifBlank{"Verification code sent"}}else msg=r.value.message}
   else->msg=r.readableMessage()
  } else when(val r=client.completePasswordReset(challenge,otp,totp,password)){
   is ApiResult.Success->{if(r.value.success)onDone(r.value.message.ifBlank{"Password reset completed. Sign in with your new password."})else msg=r.value.message}
   else->msg=r.readableMessage()
  }
  busy=false
 }}){Text(if(challenge.isBlank())"Send Code" else "Reset Password")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable private fun RegistrationDialog(serverUrl:String,onClose:()->Unit,onDone:(String)->Unit){
 var username by remember{mutableStateOf("")};var fullName by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var role by remember{mutableStateOf("")}
 var roles by remember{mutableStateOf<List<RoleOption>>(emptyList())}
 var captcha by remember{mutableStateOf<CaptchaResponse?>(null)};var captchaAnswer by remember{mutableStateOf("")}
 var challenge by remember{mutableStateOf("")};var emailOtp by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")}
 var registrationId by remember{mutableStateOf<Long?>(null)};var manualSecret by remember{mutableStateOf("")};var provisioningUri by remember{mutableStateOf("")};var authenticatorOtp by remember{mutableStateOf("")}
 var msg by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 val client=remember(serverUrl){DseErpHttpClient(serverUrl,InMemorySessionStore())};DisposableEffect(client){onDispose{client.close()}}
 suspend fun refreshCaptcha(){when(val r=client.registrationCaptcha()){is ApiResult.Success->{captcha=r.value;captchaAnswer=""};else->msg=r.readableMessage()}}
 LaunchedEffect(client){
  when(val r=client.registrationRoles()){is ApiResult.Success->{roles=r.value;if(role.isBlank())role=r.value.firstOrNull()?.code.orEmpty()};else->msg=r.readableMessage()}
  refreshCaptcha()
 }
 val stage=when{registrationId!=null->3;challenge.isNotBlank()->2;else->1}
 PremiumAlertDialog(onDismissRequest=onClose,title={Text("Register Jasvi Industries User")},text={Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){
  when(stage){
   1->{
    DseField("Username",username,singleLine=true,required=true,onValue={username=it})
    DseField("Full Name",fullName,singleLine=true,required=true,onValue={fullName=it})
    DseField("Email",email,singleLine=true,required=true,onValue={email=it})
    DseSelect("Role",role,roles.map{it.code},emptyMessage="Self-registration is not enabled on this server",required=true,onValue={role=it})
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
     Text("Security Check",fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
     Text(captcha?.question ?: "Loading CAPTCHA…")
     DseField("Answer",captchaAnswer,singleLine=true,required=true,icon=Icons.Rounded.VerifiedUser,onValue={captchaAnswer=it})
     TextButton(enabled=!busy,onClick={scope.launch{refreshCaptcha()}}){Icon(Icons.Rounded.Refresh,null);Spacer(Modifier.width(6.dp));Text("Refresh CAPTCHA")}
    }}
    Text("Jasvi Industries 10.0.5 requires email verification, authenticator enrollment and administrator approval before first sign-in.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
   2->{
    Text("Email verification",style=MaterialTheme.typography.titleMedium)
    DseField("Email Verification Code",emailOtp,singleLine=true,required=true,onValue={emailOtp=it})
    DsePasswordField("Password",password,required=true,onValue={password=it})
    DsePasswordField("Confirm Password",confirm,required=true,onValue={confirm=it})
    Text("After email verification, Jasvi Industries will provide the authenticator setup secret.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
   else->{
    Text("Authenticator enrollment",style=MaterialTheme.typography.titleMedium)
    Text(msg.ifBlank{"Add Jasvi Industries to Google Authenticator or Microsoft Authenticator, then enter the current 6-digit code."},style=MaterialTheme.typography.bodyMedium)
    if(manualSecret.isNotBlank())DseSection("Manual Setup Key",Icons.Rounded.Security){Text(manualSecret,style=MaterialTheme.typography.titleMedium);if(provisioningUri.isNotBlank())Text("The provisioning URI was received securely from Jasvi Industries.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    DseField("6-digit Authenticator Code",authenticatorOtp,singleLine=true,required=true,onValue={authenticatorOtp=it})
    Text("After verification, the account remains pending until an administrator approves it.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
  if(msg.isNotBlank()&&stage!=3)Text(msg,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
 }},confirmButton={Button(
  enabled=!busy&&when(stage){1->username.isNotBlank()&&fullName.isNotBlank()&&email.contains("@")&&role.isNotBlank()&&captcha?.challengeId?.isNotBlank()==true&&captchaAnswer.isNotBlank();2->emailOtp.isNotBlank()&&password.length>=8&&password==confirm;else->authenticatorOtp.length>=6},
  onClick={scope.launch{
   busy=true
   when(stage){
    1->when(val r=client.requestRegistration(RegistrationOtpRequest(username.trim(),fullName.trim(),email.trim(),role,true,captcha?.challengeId.orEmpty(),captchaAnswer.trim()))){
     is ApiResult.Success->{if(r.value.success){challenge=r.value.challengeId;msg=r.value.message.ifBlank{"Verification code sent"}}else{msg=r.value.message;refreshCaptcha()}}
     else->{msg=r.readableMessage();refreshCaptcha()}
    }
    2->when(val r=client.verifyRegistrationEmail(RegistrationEmailVerifyRequest(challenge,emailOtp.trim(),username.trim(),password,fullName.trim(),email.trim(),role,true))){
     is ApiResult.Success->{if(r.value.success&&r.value.registrationId!=null){registrationId=r.value.registrationId;manualSecret=r.value.manualSecret;provisioningUri=r.value.provisioningUri;msg=r.value.message}else msg=r.value.message.ifBlank{"Registration setup could not be created"}}
     else->msg=r.readableMessage()
    }
    else->when(val r=client.completeRegistrationMfa(registrationId?:0,authenticatorOtp.trim())){
     is ApiResult.Success->{if(r.value.success)onDone(r.value.message.ifBlank{"Registration submitted and awaiting administrator approval."})else msg=r.value.message}
     else->msg=r.readableMessage()
    }
   }
   busy=false
  }}
 ){Text(when(stage){1->"Send Email Code";2->"Verify Email";else->"Submit Registration"})}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}


@Composable private fun MainApp(api:DseErpHttpClient,permission:PermissionContext,banner:String,onLogout:suspend()->Unit){
 var tab by remember{mutableStateOf(MainTab.DASHBOARD)}
 var more by remember{mutableStateOf<MoreDestination?>(null)}
 var online by remember{mutableStateOf<Boolean?>(null)}
 var globalSearch by remember{mutableStateOf(false)}
 var recordTarget by remember{mutableStateOf<RecordTarget?>(null)}
 val scope=rememberCoroutineScope()
 fun allowed(t:MainTab):Boolean=when(t){
  MainTab.DASHBOARD->true
  MainTab.SALES->permission.can("SALES")
  MainTab.PURCHASE->permission.can("PURCHASE")
  MainTab.BANK->permission.can("BANK_EXPENSE")||permission.can("FINANCE")||permission.isAdmin()
  MainTab.IMPORT->permission.can("INVENTORY","CREATE")||permission.can("CUSTOMERS","CREATE")||permission.can("SUPPLIERS","CREATE")||permission.can("PURCHASE_RECON","IMPORT")||permission.isAdmin()
  MainTab.MORE->true
 }
 fun routeModule(moduleKey:String){
  when(moduleKey.uppercase()){
   "SALE","SALES"->{if(permission.can("SALES")){tab=MainTab.SALES;more=null}}
   "PURCHASE","PURCHASES"->{if(permission.can("PURCHASE")){tab=MainTab.MORE;more=MoreDestination.PURCHASE}}
   "SALES_RETURN","SALE_RETURN"->{tab=MainTab.MORE;more=MoreDestination.SALES_RETURNS}
   "PURCHASE_RETURN"->{tab=MainTab.MORE;more=MoreDestination.PURCHASE_RETURNS}
   "QUOTATION","QUOTATIONS"->{tab=MainTab.MORE;more=MoreDestination.QUOTATIONS}
   "BANK","FINANCE","EXPENSE","BANK_STATEMENT"->{if(allowed(MainTab.BANK)){tab=MainTab.BANK;more=null}}
   "ITEM","INVENTORY"->{tab=MainTab.MORE;more=MoreDestination.INVENTORY}
   "PURCHASE_RECON","RECON"->{tab=MainTab.MORE;more=MoreDestination.PURCHASE_RECON}
   "REMINDER"->{tab=MainTab.MORE;more=MoreDestination.REMINDERS}
   "NOTIFICATION"->{tab=MainTab.MORE;more=MoreDestination.NOTIFICATIONS}
   "REPORT","REPORTS"->{tab=MainTab.MORE;more=MoreDestination.REPORTS}
   "CUSTOMER","SUPPLIER","PARTY","MASTER"->{tab=MainTab.MORE;more=MoreDestination.MASTERS}
   else->{tab=MainTab.MORE;more=MoreDestination.MASTERS}
  }
 }
 fun routeDeepLink(raw:String){
  val withoutScheme=raw.substringAfter("://",raw).substringBefore('?').trim('/')
  val parts=withoutScheme.split('/').filter{it.isNotBlank()}
  val segment=parts.firstOrNull().orEmpty().lowercase()
  val reference=parts.drop(1).joinToString("/").let(::urlDecode)
  val moduleKey=when{
   segment in setOf("sales-return","sale-return")->"SALES_RETURN"
   segment=="purchase-return"->"PURCHASE_RETURN"
   segment.startsWith("quotation")->"QUOTATION"
   segment.startsWith("inventory")||segment=="item"->"INVENTORY"
   segment.contains("recon")->"PURCHASE_RECON"
   segment.startsWith("reminder")->"REMINDER"
   segment.startsWith("notification")->"NOTIFICATION"
   segment.startsWith("report")->"REPORTS"
   segment.startsWith("sales")||segment=="sale"||segment=="shipping"->"SALES"
   segment.startsWith("purchase")->"PURCHASE"
   segment.startsWith("bank")||segment.startsWith("finance")||segment=="expense"->"FINANCE"
   segment.startsWith("profile")->"PROFILE"
   segment.startsWith("import")->"IMPORT"
   segment.startsWith("dashboard")->"DASHBOARD"
   else->segment.uppercase()
  }
  when(moduleKey){
   "PROFILE"->{tab=MainTab.MORE;more=MoreDestination.PROFILE;recordTarget=null}
   "IMPORT"->{if(allowed(MainTab.IMPORT)){tab=MainTab.IMPORT;more=null};recordTarget=null}
   "DASHBOARD"->{tab=MainTab.DASHBOARD;more=null;recordTarget=null}
   else->{routeModule(moduleKey);if(reference.isNotBlank()){
    recordTarget=RecordTarget(moduleKey,reference,null)
    scope.launch{when(val resolved=api.resolveRecord(moduleKey,reference)){is ApiResult.Success->if(resolved.value.found)recordTarget=RecordTarget(resolved.value.moduleKey.ifBlank{moduleKey},resolved.value.reference.ifBlank{reference},resolved.value.recordId);else->Unit}}
   }}
  }
 }
 fun routeSearch(row:GlobalSearchRow){
  routeModule(row.moduleKey.ifBlank{row.module})
  recordTarget=RecordTarget(row.moduleKey.ifBlank{row.module},row.reference,row.recordId)
  globalSearch=false
 }
 LaunchedEffect(api){var ticks=0;while(true){online=api.health() is ApiResult.Success;if(ticks%30==0){(api.runtimeHealth() as? ApiResult.Success)?.value?.let{BusinessDateContext.update(it.businessDate,it.businessZone)}};ticks++;delay(30_000)}}
 LaunchedEffect(Unit){while(true){platformConsumeDeepLink()?.let(::routeDeepLink);delay(900)}}
 Scaffold(
  containerColor=MaterialTheme.colorScheme.background,
  topBar={
   TopAppBar(
    navigationIcon={Box(Modifier.padding(start=12.dp),contentAlignment=Alignment.Center){DseBrandMark(compact=true)}},
    title={Column(Modifier.padding(start=6.dp)){Text("Jasvi Industries",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold);Text(tab.label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
    actions={
     IconButton(onClick={globalSearch=true}){Icon(Icons.Rounded.Search,"Global Search")}
     IconButton(onClick={tab=MainTab.MORE;more=MoreDestination.NOTIFICATIONS}){Icon(Icons.Rounded.NotificationsNone,"Notifications")}
     ConnectionChip(online)
     IconButton(onClick={scope.launch{onLogout()}}){Icon(Icons.Rounded.Logout,"Logout")}
    },
    colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.surface),
   )
  },
  bottomBar={
   NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp){
    listOf(MainTab.DASHBOARD,MainTab.SALES,MainTab.BANK,MainTab.IMPORT,MainTab.MORE).filter(::allowed).forEach{t->
     NavigationBarItem(
      selected=tab==t,
      onClick={tab=t;if(t!=MainTab.MORE)more=null},
      icon={Icon(tabIcon(t),t.label)},
      label={Text(t.label)},
      colors=NavigationBarItemDefaults.colors(
       selectedIconColor=MaterialTheme.colorScheme.primary,
       selectedTextColor=MaterialTheme.colorScheme.primary,
       indicatorColor=MaterialTheme.colorScheme.primaryContainer,
       unselectedIconColor=MaterialTheme.colorScheme.onSurfaceVariant,
       unselectedTextColor=MaterialTheme.colorScheme.onSurfaceVariant,
      ),
     )
    }
   }
  },
 ){pad->
  Column(Modifier.padding(pad).fillMaxSize()){
   val showBanner=banner.isNotBlank()&&!banner.startsWith("Connected",true)&&!banner.startsWith("Unlocked",true)&&!banner.startsWith("MFA verified",true)
   if(showBanner)Surface(color=MaterialTheme.colorScheme.primaryContainer.copy(.60f),shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp).fillMaxWidth()){
    Row(Modifier.padding(horizontal=10.dp,vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){Icon(Icons.Rounded.Info,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary);Text(banner,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   }
   when(tab){
    MainTab.DASHBOARD->DashboardScreen(api,permission,{if(allowed(it))tab=it},{dest->tab=MainTab.MORE;more=dest},{globalSearch=true},{target->routeModule(target.moduleKey);recordTarget=target})
    MainTab.SALES->if(permission.can("SALES"))SalesWorkspace(api,permission,permission.user?.username.orEmpty(),recordTarget,{recordTarget=null}) else PermissionDenied("Sales")
    MainTab.PURCHASE->if(permission.can("PURCHASE"))PurchaseWorkspace(api,permission,permission.user?.username.orEmpty(),recordTarget,{recordTarget=null}) else PermissionDenied("Purchase")
    MainTab.BANK->if(allowed(MainTab.BANK))BankFinanceWorkspace(api,permission,permission.user?.username.orEmpty(),recordTarget,{recordTarget=null}) else PermissionDenied("Bank & Finance")
    MainTab.IMPORT->if(allowed(MainTab.IMPORT))DataImportWorkspace(api,permission.user?.username.orEmpty()) else PermissionDenied("Data Import")
    MainTab.MORE->MoreWorkspace(api,permission,permission.user?.username.orEmpty(),more,{more=it},{routeDeepLink(it)},recordTarget,{recordTarget=null})
   }
  }
 }
 if(globalSearch)GlobalSearchDialog(api,{globalSearch=false},::routeSearch)
}

@Composable private fun PermissionDenied(module:String){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text("You do not have permission to view $module.")}}

@Composable private fun ConnectionChip(online:Boolean?){val icon=when(online){true->Icons.Rounded.CloudDone;false->Icons.Rounded.CloudOff;null->Icons.Rounded.Sync};Icon(icon,when(online){true->"Online";false->"Offline";null->"Checking"},tint=if(online==false)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)}
private fun tabIcon(t:MainTab)=when(t){MainTab.DASHBOARD->Icons.Rounded.Dashboard;MainTab.SALES->Icons.Rounded.ReceiptLong;MainTab.PURCHASE->Icons.Rounded.ShoppingCart;MainTab.BANK->Icons.Rounded.AccountBalance;MainTab.IMPORT->Icons.Rounded.UploadFile;MainTab.MORE->Icons.Rounded.MoreHoriz}
private fun displayName(u:UserPayload?)=u?.fullName?.takeIf{it.isNotBlank()}?:u?.username.orEmpty()
private fun UserProfile.toUserPayload()=UserPayload(id=id,username=username,fullName=fullName,role=role,email=email,active=active,department=department,branch=branch,accessLevel=accessLevel,locked=locked,mfaEnabled=mfaEnabled)
private fun cacheAgeLabel(last:Long):String{val min=((platformOfflineNowMillis()-last).coerceAtLeast(0)/60_000);return when{min<1->"just now";min<60->"$min min ago";min<1440->"${min/60} hr ago";else->"${min/1440} day(s) ago"}}
