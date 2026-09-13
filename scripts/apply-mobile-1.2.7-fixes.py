#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MOBILE = ROOT / "mobile-source" / "DSE-ERP-Mobile"

changed = []


def load(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def save(rel: str, text: str) -> None:
    path = ROOT / rel
    old = path.read_text(encoding="utf-8")
    if old != text:
        path.write_text(text, encoding="utf-8", newline="\n")
        changed.append(rel)


def replace_required(text: str, old: str, new: str, label: str, count: int = 1) -> str:
    if new in text:
        return text
    found = text.count(old)
    if found < count:
        raise RuntimeError(f"{label}: expected at least {count} occurrence(s), found {found}")
    return text.replace(old, new, count)


def replace_regex_required(text: str, pattern: str, replacement: str, label: str, count: int = 1) -> str:
    updated, n = re.subn(pattern, replacement, text, count=count, flags=re.S)
    if n < count:
        if replacement in text:
            return text
        raise RuntimeError(f"{label}: expected {count} regex replacement(s), found {n}")
    return updated


# -----------------------------------------------------------------------------
# Version source of truth: Android injects the installed APK version into shared
# compatibility logic, preventing the 1.2.6 label from continuing to advertise
# itself as 1.2.5 after a successful in-place update.
# -----------------------------------------------------------------------------
rel = "mobile-source/DSE-ERP-Mobile/androidApp/build.gradle.kts"
t = load(rel)
t = t.replace('versionCode = 126', 'versionCode = 127')
t = t.replace('versionName = "1.2.6"', 'versionName = "1.2.7"')
save(rel, t)

rel = "mobile-source/DSE-ERP-Mobile/shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt"
t = load(rel)
t = replace_required(
    t,
    "    val updateApkBaseUrl: String,\n)",
    "    val updateApkBaseUrl: String,\n    val installedVersionName: String = \"1.2.7\",\n)",
    "runtime config installed version",
)
t = replace_required(
    t,
    '    const val MOBILE_VERSION_NAME = "1.2.5"\n    const val MOBILE_VERSION = "1.2.5-V10.0.7-DISTRIBUTION"',
    '    private const val FALLBACK_MOBILE_VERSION_NAME = "1.2.7"\n    val MOBILE_VERSION_NAME: String get() = runtimeConfig.installedVersionName.trim().ifBlank { FALLBACK_MOBILE_VERSION_NAME }\n    val MOBILE_VERSION: String get() = "$MOBILE_VERSION_NAME-V10.0.7-DISTRIBUTION"',
    "dynamic mobile version",
)
t = replace_required(
    t,
    '        updateApkBaseUrl = "$UAT_SERVER_URL/mobile/android/uat",\n    )',
    '        updateApkBaseUrl = "$UAT_SERVER_URL/mobile/android/uat",\n        installedVersionName = FALLBACK_MOBILE_VERSION_NAME,\n    )',
    "default runtime version",
)
t = replace_required(
    t,
    "            updateApkBaseUrl = config.updateApkBaseUrl.trim().trimEnd('/'),\n        )",
    "            updateApkBaseUrl = config.updateApkBaseUrl.trim().trimEnd('/'),\n            installedVersionName = config.installedVersionName.trim().ifBlank { FALLBACK_MOBILE_VERSION_NAME },\n        )",
    "installed runtime version normalization",
)
save(rel, t)

# -----------------------------------------------------------------------------
# Shared Compose fixes: keyboard/IME behavior, complete swipe actions, dialog
# resize behavior, and financial values that do not silently abbreviate.
# -----------------------------------------------------------------------------
rel = "mobile-source/DSE-ERP-Mobile/composeApp/src/commonMain/kotlin/org/dse/mobile/app/DseComponents.kt"
t = load(rel)
t = replace_required(
    t,
    "import androidx.compose.ui.platform.LocalDensity\n",
    "import androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.LocalFocusManager\n",
    "focus manager import",
)

# DseField: hide IME on Done for single-line fields.
t = replace_required(
    t,
    "    val accent=semanticFieldAccent(label)\n    OutlinedTextField(\n        value=value,\n        onValueChange=onValue,",
    "    val accent=semanticFieldAccent(label)\n    val focusManager=LocalFocusManager.current\n    OutlinedTextField(\n        value=value,\n        onValueChange=onValue,",
    "DseField focus manager",
)
t = replace_required(
    t,
    "        enabled=enabled,\n        supportingText=supporting?.let{{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant)}},",
    "        enabled=enabled,\n        keyboardOptions=KeyboardOptions(imeAction=if(singleLine)ImeAction.Done else ImeAction.Default),\n        keyboardActions=KeyboardActions(onDone={focusManager.clearFocus()}),\n        supportingText=supporting?.let{{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant)}},",
    "DseField keyboard action",
)

# DseNumberField.
t = replace_required(
    t,
    "    val accent=semanticFieldAccent(label)\n    OutlinedTextField(\n        value=value,\n        onValueChange={next->\n            val numericPattern=if(allowNegative)",
    "    val accent=semanticFieldAccent(label)\n    val focusManager=LocalFocusManager.current\n    OutlinedTextField(\n        value=value,\n        onValueChange={next->\n            val numericPattern=if(allowNegative)",
    "DseNumberField focus manager",
)
t = replace_required(
    t,
    "        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),\n        textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium),",
    "        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal,imeAction=ImeAction.Done),\n        keyboardActions=KeyboardActions(onDone={focusManager.clearFocus()}),\n        textStyle=MaterialTheme.typography.bodyLarge.copy(fontWeight=FontWeight.Medium),",
    "DseNumberField keyboard action",
)

# DsePasswordField: expose a Done callback so Login can submit from the keyboard.
t = replace_required(
    t,
    "    enabled:Boolean=true,\n    onValue:(String)->Unit,\n){\n    var visible by remember{mutableStateOf(false)}\n    val accent=semanticFieldAccent(label)",
    "    enabled:Boolean=true,\n    onValue:(String)->Unit,\n    onDone:()->Unit={},\n){\n    var visible by remember{mutableStateOf(false)}\n    val accent=semanticFieldAccent(label)\n    val focusManager=LocalFocusManager.current",
    "DsePasswordField done callback",
)
t = replace_required(
    t,
    "        enabled=enabled,\n        visualTransformation=if(visible)androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),",
    "        enabled=enabled,\n        keyboardOptions=KeyboardOptions(imeAction=ImeAction.Done),\n        keyboardActions=KeyboardActions(onDone={focusManager.clearFocus();onDone()}),\n        visualTransformation=if(visible)androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),",
    "DsePasswordField keyboard action",
)

# Complete swipe action set. The old implementation deliberately discarded all
# actions after item 3; a compact action width keeps the full set accessible.
pattern = r"@Composable\ninternal fun SwipeActionContainer\(.*?\n}\n\n@Composable\nprivate fun SwipeActionButton"
replacement = '''@Composable
internal fun SwipeActionContainer(
    startActions:List<SwipeAction> = emptyList(),
    endActions:List<SwipeAction> = emptyList(),
    modifier:Modifier=Modifier,
    content:@Composable ()->Unit,
){
    if(startActions.isEmpty()&&endActions.isEmpty()){
        Box(modifier){content()}
        return
    }
    val density=LocalDensity.current
    val actionWidth=56.dp
    val startMax=with(density){(actionWidth*startActions.size.toFloat()).toPx()}
    val endMax=with(density){(actionWidth*endActions.size.toFloat()).toPx()}
    var offset by remember{mutableFloatStateOf(0f)}
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))){
        Row(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(.55f)),verticalAlignment=Alignment.CenterVertically){
            Row(Modifier.width(actionWidth*startActions.size.toFloat()).fillMaxHeight(),verticalAlignment=Alignment.CenterVertically){
                startActions.forEach{action->SwipeActionButton(action,actionWidth){offset=0f;action.onClick()}}
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.width(actionWidth*endActions.size.toFloat()).fillMaxHeight(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.End){
                endActions.forEach{action->SwipeActionButton(action,actionWidth){offset=0f;action.onClick()}}
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset{IntOffset(offset.roundToInt(),0)}
                .pointerInput(startActions.size,endActions.size){
                    detectHorizontalDragGestures(
                        onHorizontalDrag={change,dragAmount->
                            change.consume()
                            offset=(offset+dragAmount).coerceIn(if(endActions.isEmpty())0f else -endMax,if(startActions.isEmpty())0f else startMax)
                        },
                        onDragEnd={
                            offset=when{
                                offset>startMax*.28f&&startActions.isNotEmpty()->startMax
                                offset<(-endMax*.28f)&&endActions.isNotEmpty()->-endMax
                                else->0f
                            }
                        },
                        onDragCancel={offset=0f},
                    )
                }
        ){content()}
    }
}

@Composable
private fun SwipeActionButton'''
t = replace_regex_required(t, pattern, replacement, "complete swipe action container")

# Register search and dialogs stay reachable above the IME.
t = replace_required(
    t,
    "    val pageScroll=rememberScrollState()\n    var dismissedMessage",
    "    val pageScroll=rememberScrollState()\n    val focusManager=LocalFocusManager.current\n    var dismissedMessage",
    "register focus manager",
)
t = replace_required(
    t,
    "            .verticalScroll(pageScroll)\n            .padding(horizontal=10.dp,vertical=7.dp),",
    "            .verticalScroll(pageScroll)\n            .imePadding()\n            .padding(horizontal=10.dp,vertical=7.dp),",
    "register IME padding",
)
t = replace_required(
    t,
    "            keyboardActions=KeyboardActions(onSearch={onRefresh()}),",
    "            keyboardActions=KeyboardActions(onSearch={focusManager.clearFocus();onRefresh()}),",
    "register search keyboard",
)
t = replace_required(
    t,
    "        BoxWithConstraints(Modifier.fillMaxSize()){",
    "        BoxWithConstraints(Modifier.fillMaxSize().imePadding()){",
    "dialog IME padding",
)

# Financial KPI values may wrap rather than being visually clipped.
t = t.replace(
    "Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,color=accent,maxLines=1)",
    "Text(value,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,color=accent,maxLines=2)",
)
t = t.replace(
    "Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold,color=Color.White,maxLines=1)",
    "Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold,color=Color.White,maxLines=2)",
)
save(rel, t)

# -----------------------------------------------------------------------------
# Login: keyboard-safe card, visible Sign In, keyboard Done submits, and biometric
# action dismisses the IME before opening Android authentication.
# -----------------------------------------------------------------------------
rel = "mobile-source/DSE-ERP-Mobile/composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt"
t = load(rel)
t = replace_required(
    t,
    "import androidx.compose.ui.graphics.Color\n",
    "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.platform.LocalFocusManager\n",
    "App focus import",
)
t = replace_required(
    t,
    " var localUpdateMessage by remember{mutableStateOf(\"\")}\n val scope=rememberCoroutineScope()",
    " var localUpdateMessage by remember{mutableStateOf(\"\")}\n val scope=rememberCoroutineScope()\n val focusManager=LocalFocusManager.current",
    "Login focus manager",
)
t = replace_required(
    t,
    "    Modifier.fillMaxSize().padding(horizontal=if(wide)48.dp else 18.dp,vertical=18.dp),",
    "    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal=if(wide)48.dp else 18.dp,vertical=18.dp),",
    "Login scroll and IME",
)
t = replace_required(
    t,
    '     DsePasswordField("Password",password,required=true,onValue={password=it})',
    '     DsePasswordField("Password",password,required=true,onValue={password=it},onDone={if(!busy&&identity.isNotBlank()&&password.isNotBlank()){focusManager.clearFocus();scope.launch{busy=true;onLogin(identity,password);busy=false}}})',
    "Login keyboard submit",
)
t = replace_required(
    t,
    "      onClick={scope.launch{busy=true;onLogin(identity,password);busy=false}},",
    "      onClick={focusManager.clearFocus();scope.launch{busy=true;onLogin(identity,password);busy=false}},",
    "Login button keyboard dismiss",
)
t = replace_required(
    t,
    "      onClick={scope.launch{busy=true;onBiometric();busy=false}},",
    "      onClick={focusManager.clearFocus();scope.launch{busy=true;onBiometric();busy=false}},",
    "Biometric keyboard dismiss",
)
save(rel, t)

# -----------------------------------------------------------------------------
# Dashboard: use full financial values and remove duplicated per-result View button.
# Search dismisses the keyboard on submit/open.
# -----------------------------------------------------------------------------
rel = "mobile-source/DSE-ERP-Mobile/composeApp/src/commonMain/kotlin/org/dse/mobile/app/DashboardScreens.kt"
t = load(rel)
t = replace_required(
    t,
    "import androidx.compose.ui.Modifier\n",
    "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalFocusManager\n",
    "Dashboard focus import",
)
t = t.replace("compactMoney(s?.", "money(s?.")
t = replace_required(
    t,
    "    val scope=rememberCoroutineScope()\n    suspend fun searchNow",
    "    val scope=rememberCoroutineScope()\n    val focusManager=LocalFocusManager.current\n    suspend fun searchNow",
    "Global search focus manager",
)
t = replace_required(
    t,
    "                keyboardActions=KeyboardActions(onSearch={scope.launch{searchNow()}}),",
    "                keyboardActions=KeyboardActions(onSearch={focusManager.clearFocus();scope.launch{searchNow()}}),",
    "Global search IME search",
)
t = t.replace(
    '                        swipeStartActions=listOf(SwipeAction("View",Icons.Rounded.Visibility){onOpen(r)}),',
    '                        swipeStartActions=listOf(SwipeAction("View",Icons.Rounded.Visibility){focusManager.clearFocus();onOpen(r)}),',
)
t = t.replace('                    ){onOpen(r)}\n                    PremiumSecondaryButton("View ${r.reference.ifBlank{r.description}}",{onOpen(r)},Modifier.fillMaxWidth(),icon=Icons.Rounded.Visibility)',
              '                    ){focusManager.clearFocus();onOpen(r)}')
save(rel, t)

# -----------------------------------------------------------------------------
# Remove mobile Saved View controls (requested) and clarify that PDF output opens
# Android share. Keep dormant server APIs untouched for compatibility.
# -----------------------------------------------------------------------------
app_dir = MOBILE / "composeApp" / "src" / "commonMain" / "kotlin" / "org" / "dse" / "mobile" / "app"
for path in app_dir.glob("*.kt"):
    text = path.read_text(encoding="utf-8")
    text = text.replace('"PDF / Print"', '"Share PDF"').replace('"Print PDF"', '"Share PDF"')
    lines = []
    for line in text.splitlines():
        if 'DseSelect("Saved View"' in line:
            continue
        if 'Text("Save View")' in line and 'saveViewPrompt' in line:
            continue
        if '"Save View Ops"' in line:
            continue
        lines.append(line)
    updated = "\n".join(lines) + ("\n" if text.endswith("\n") else "")
    relpath = str(path.relative_to(ROOT)).replace("\\", "/")
    save(relpath, updated)

# -----------------------------------------------------------------------------
# Bank reconciliation: opening an already matched transaction now opens its audit
# view rather than reloading every match candidate. Unmatched/review items retain
# the existing matching UI. No server/API changes.
# -----------------------------------------------------------------------------
rel = "mobile-source/DSE-ERP-Mobile/composeApp/src/commonMain/kotlin/org/dse/mobile/app/BankFinanceScreens.kt"
t = load(rel)
t = replace_required(
    t,
    '    var mode by remember { mutableStateOf(if (canRecon) "match" else "audit") }',
    '    val reconEditable = canRecon && t.status.uppercase() in setOf("UNMATCHED", "SUGGESTED", "REVIEW")\n    var mode by remember(t.id,t.status,canRecon) { mutableStateOf(if (reconEditable) "match" else "audit") }',
    "matched bank dialog default mode",
)
t = replace_required(
    t,
    "    LaunchedEffect(t.id) {\n        if (canRecon) {",
    "    LaunchedEffect(t.id) {\n        if (reconEditable) {",
    "matched bank candidate load",
)
t = replace_required(
    t,
    '    val options = if (canRecon) listOf("match", "expense", "entry", "review", "ignore", "note", "audit") else listOf("audit")',
    '    val options = if (reconEditable) listOf("match", "expense", "entry", "review", "ignore", "note", "audit") else listOf("audit")',
    "matched bank mode options",
)
save(rel, t)

# -----------------------------------------------------------------------------
# Android native fixes: actual APK version injection, more reliable biometric
# availability/callback delivery, correct UAT deep-link scheme, WhatsApp/Business
# routing, and robust content URI sharing.
# -----------------------------------------------------------------------------
rel = "mobile-source/DSE-ERP-Mobile/androidApp/src/main/kotlin/org/dse/mobile/android/MainActivity.kt"
t = load(rel)
t = replace_required(t, "import android.content.Intent\n", "import android.content.ClipData\nimport android.content.Intent\n", "ClipData import")
t = replace_required(t, "import java.util.concurrent.Executor\n", "import java.util.concurrent.Executor\nimport java.util.concurrent.atomic.AtomicBoolean\n", "AtomicBoolean import")
t = replace_required(
    t,
    "                updateApkBaseUrl = BuildConfig.UPDATE_APK_BASE_URL,\n            )",
    "                updateApkBaseUrl = BuildConfig.UPDATE_APK_BASE_URL,\n                installedVersionName = BuildConfig.VERSION_NAME,\n            )",
    "Android installed version injection",
)
# Use weak-or-better biometric so devices whose hardware is not classified STRONG
# still get a consistent biometric option; secure device credential remains allowed.
t = t.replace("BiometricManager.Authenticators.BIOMETRIC_STRONG or\n                BiometricManager.Authenticators.DEVICE_CREDENTIAL",
              "BiometricManager.Authenticators.BIOMETRIC_WEAK or\n                BiometricManager.Authenticators.DEVICE_CREDENTIAL")
t = t.replace("manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==",
              "manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==")
t = t.replace("BiometricManager.Authenticators.BIOMETRIC_STRONG,\n                hasCredential,",
              "BiometricManager.Authenticators.BIOMETRIC_WEAK,\n                hasCredential,")

pattern = r"    private fun showBiometricPrompt\(.*?\n    @Suppress\(\"DEPRECATION\"\)"
replacement = '''    private fun showBiometricPrompt(
        reason: String,
        authenticators: Int,
        allowCredentialFallback: Boolean,
        completion: (String, String?) -> Unit,
    ) {
        val delivered = AtomicBoolean(false)
        fun finish(status: String, message: String?) {
            if (delivered.compareAndSet(false, true)) completion(status, message)
        }
        val executor: Executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                finish("OK", "Authenticated securely.")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (Build.VERSION.SDK_INT < 30 && allowCredentialFallback && errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    launchDeviceCredential(reason) { status, message -> finish(status, message) }
                } else {
                    finish("ERROR", errString.toString())
                }
            }

            override fun onAuthenticationFailed() = Unit
        })
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Jasvi Industries Mobile")
            .setSubtitle(reason)
            .setAllowedAuthenticators(authenticators)
        if (Build.VERSION.SDK_INT < 30) {
            builder.setNegativeButtonText(if (allowCredentialFallback) "Use device PIN/pattern/password" else "Cancel")
        }
        prompt.authenticate(builder.build())
    }

    @Suppress("DEPRECATION")'''
t = replace_regex_required(t, pattern, replacement, "one-shot biometric prompt")

pattern = r"    private fun shareFile\(title: String, fileName: String, base64: String\): Boolean = runCatching \{.*?\n    \}.getOrDefault\(false\)\n\n    private fun openExternalUrl"
replacement = '''    private fun shareFile(title: String, fileName: String, base64: String): Boolean = runCatching {
        val data = Base64.decode(base64, Base64.DEFAULT)
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._() -]"), "_").ifBlank { "attachment.bin" }
        val shareDir = File(cacheDir, "dse-share").apply { mkdirs() }
        val file = File(shareDir, safeName).apply { writeBytes(data) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val mime = when (safeName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "csv" -> "text/csv"
            "txt" -> "text/plain"
            else -> URLConnection.guessContentTypeFromName(safeName) ?: "application/octet-stream"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, safeName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, title))
        true
    }.getOrDefault(false)

    private fun openExternalUrl'''
t = replace_regex_required(t, pattern, replacement, "robust file sharing")

pattern = r"    private fun openExternalUrl\(value: String\): Boolean = runCatching \{.*?\n    \}.getOrDefault\(false\)"
replacement = '''    private fun openExternalUrl(value: String): Boolean = runCatching {
        val normalized = if (value.startsWith("dseerp://", ignoreCase = true)) {
            BuildConfig.DEEP_LINK_SCHEME + "://" + value.substringAfter("://")
        } else value
        val uri = Uri.parse(normalized)
        val isWhatsApp = normalized.startsWith("whatsapp://", true) ||
            uri.host.equals("wa.me", true) || uri.host.equals("api.whatsapp.com", true)
        if (isWhatsApp) {
            for (target in listOf("com.whatsapp", "com.whatsapp.w4b")) {
                val direct = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(target) }
                if (direct.resolveActivity(packageManager) != null) {
                    startActivity(direct)
                    return@runCatching true
                }
            }
        }
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(packageManager) == null) return@runCatching false
        startActivity(intent)
        true
    }.getOrDefault(false)'''
t = replace_regex_required(t, pattern, replacement, "deep link and WhatsApp routing")
save(rel, t)

# Final hygiene checks.
build = load("mobile-source/DSE-ERP-Mobile/androidApp/build.gradle.kts")
assert 'versionCode = 127' in build and 'versionName = "1.2.7"' in build
mobile_info = load("mobile-source/DSE-ERP-Mobile/shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt")
assert 'installedVersionName' in mobile_info
assert 'const val MOBILE_VERSION_NAME = "1.2.5"' not in mobile_info

print("MOBILE_1_2_7_FIXES_APPLIED")
for item in changed:
    print(item)
