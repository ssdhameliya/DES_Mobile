from pathlib import Path
import re, sys, xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
common_bases=[root/'composeApp/src/commonMain',root/'shared/src/commonMain']

def function_decls(bases, keyword):
    """Return expected/actual function names plus suspend flag and parameter types.
    Uses balanced parentheses so callback/function-type parameters are handled."""
    out={}
    for base in bases:
        if not base.exists():
            continue
        for path in base.rglob('*.kt'):
            text=path.read_text(errors='replace')
            pattern=rf'\b{keyword}\s+(suspend\s+)?fun\s+(\w+)\s*\('
            for match in re.finditer(pattern,text):
                suspend=bool(match.group(1)); name=match.group(2); start=match.end()-1
                depth=0; end=None
                for i in range(start,len(text)):
                    c=text[i]
                    if c=='(': depth+=1
                    elif c==')':
                        depth-=1
                        if depth==0:
                            end=i; break
                if end is None:
                    continue
                params=text[start+1:end]
                pieces=[]; current=''; nested=0
                for c in params:
                    if c in '(<[{': nested+=1
                    elif c in ')>]}': nested-=1
                    if c==',' and nested==0:
                        pieces.append(current.strip()); current=''
                    else:
                        current+=c
                if current.strip(): pieces.append(current.strip())
                types=[]
                for piece in pieces:
                    piece=piece.strip().rstrip(',')
                    typ=piece.split(':',1)[1].strip() if ':' in piece else piece
                    types.append(re.sub(r'\s+','',typ))
                out[name]=(suspend,types,path)
    return out

expects=function_decls(common_bases,'expect')
sets={
 'androidMain': function_decls([root/'composeApp/src/androidMain',root/'shared/src/androidMain'],'actual'),
 'jvmMain': function_decls([root/'composeApp/src/jvmMain',root/'shared/src/jvmMain'],'actual'),
}
failed=False
print(f'expect_functions={len(expects)} unique={len(set(expects))}')
for target,actuals in sets.items():
    missing=sorted(set(expects)-set(actuals)); extra=sorted(set(actuals)-set(expects)); mismatched=[]
    for name in sorted(set(expects)&set(actuals)):
        exp_suspend,exp_types,_=expects[name]
        act_suspend,act_types,act_path=actuals[name]
        if exp_suspend!=act_suspend or exp_types!=act_types:
            mismatched.append((name,exp_suspend,exp_types,act_suspend,act_types,act_path))
    print(f'{target}: actual={len(actuals)} missing={len(missing)} extra={len(extra)} signature_mismatch={len(mismatched)}')
    if missing: print('  missing:',', '.join(missing)); failed=True
    if mismatched:
        for item in mismatched: print('  signature mismatch:',item)
        failed=True

xmls=list((root/'androidApp/src').rglob('*.xml'))
bad=[]
for path in xmls:
    try: ET.parse(path)
    except Exception as exc: bad.append((path,exc))
print(f'android_xml={len(xmls)} malformed={len(bad)}')
if bad:
    for path,exc in bad: print(path,exc)
    failed=True

android_gradle=(root/'androidApp/build.gradle.kts').read_text()
build_info_path=root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt'
build_info_text=build_info_path.read_text()
android_version_match=re.search(r'versionName\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"',android_gradle)
shared_version_match=re.search(r'CURRENT_MOBILE_VERSION_NAME\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"',build_info_text)
server_baseline_match=re.search(r'SERVER_BASELINE\s*=\s*"([0-9]+\.[0-9]+\.[0-9]+)"',build_info_text)
android_version=android_version_match.group(1) if android_version_match else ''
shared_version=shared_version_match.group(1) if shared_version_match else ''
server_baseline=server_baseline_match.group(1) if server_baseline_match else ''

checks={
 'androidApp module': 'include(":androidApp")' in (root/'settings.gradle.kts').read_text(),
 'AGP app plugin': 'com.android.application' in (root/'build.gradle.kts').read_text(),
 'KMP Android plugin': 'com.android.kotlin.multiplatform.library' in (root/'build.gradle.kts').read_text(),
 'release versions parse': bool(android_version and shared_version),
 'mobile version sources aligned': bool(android_version) and android_version == shared_version,
 '10.0.12 certified baseline': server_baseline == '10.0.12',
 'distribution identity uses baseline source': '$MOBILE_VERSION_NAME-V$SERVER_BASELINE-DISTRIBUTION' in build_info_text,
 'server-driven mobile policy': (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileCompatibility.kt').is_file(),
 '10.0.1 runtime floor': 'MINIMUM_COMPATIBLE_SERVER_VERSION = "10.0.1"' in build_info_text,
 'API revision bearer v5': 'EXPECTED_API_REVISION = "spring-security-bearer-v5"' in build_info_text,
 'desktop linkage resolver': (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/model/DesktopLinkage.kt').is_file(),
 'desktop linkage contract tests': (root/'shared/src/commonTest/kotlin/org/dse/mobile/core/DesktopLinkageContractTest.kt').is_file(),
}
document_text=(root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/DocumentScreens.kt').read_text()
api_routes=(root/'shared/src/commonMain/kotlin/org/dse/mobile/core/api/ApiRoutes.kt').read_text()
compat_text=(root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileCompatibility.kt').read_text()
shared_gradle=(root/'shared/build.gradle.kts').read_text()
compose_gradle=(root/'composeApp/build.gradle.kts').read_text()
android_only_checks={
 'native iosApp removed': not (root/'iosApp').exists(),
 'compose iosMain removed': not (root/'composeApp/src/iosMain').exists(),
 'shared iosMain removed': not (root/'shared/src/iosMain').exists(),
 'shared iOS targets removed': 'iosArm64()' not in shared_gradle and 'iosSimulatorArm64()' not in shared_gradle and 'ktor-client-darwin' not in shared_gradle,
 'compose iOS targets removed': 'iosArm64()' not in compose_gradle and 'iosSimulatorArm64()' not in compose_gradle and 'KotlinNativeTarget' not in compose_gradle,
 'Android policy only': 'status.minimumSupportedAndroidVersion' in compat_text and 'status.minimumSupportedIosVersion else' not in compat_text,
}
for key,value in android_only_checks.items():
    print(f'{key}: {"OK" if value else "FAIL"}')
    failed |= not value

document_contract_checks={
 'canonical document route exact': 'constvalCANONICAL_DOCUMENT="/api/documents/render"' in api_routes.replace(' ',''),
 'business email route exact': 'constvalBUSINESS_EMAIL="/api/authority/email"' in api_routes.replace(' ',''),
 'document contract test': (root/'shared/src/commonTest/kotlin/org/dse/mobile/core/DocumentContractTest.kt').is_file(),
 'sales PDF approval lock removed': 'SALES_INVOICE",r.invoiceNo,"PDF")}},enabled=active&&!approvalLocked' not in document_text,
 'sales Excel approval lock removed': 'SALES_INVOICE",r.invoiceNo,"XLSX")}},enabled=active&&!approvalLocked' not in document_text,
 'sales Email approval lock removed': 'Send Email",{loadFull(r){email=it}},enabled=active&&!approvalLocked' not in document_text,
 'purchase PDF approval lock removed': 'PURCHASE_INVOICE",r.invoiceNo,"PDF")}},enabled=active&&!approvalLocked' not in document_text,
 'purchase Excel approval lock removed': 'PURCHASE_INVOICE",r.invoiceNo,"XLSX")}},enabled=active&&!approvalLocked' not in document_text,
}
for key,value in document_contract_checks.items():
    print(f'{key}: {"OK" if value else "FAIL"}')
    failed |= not value

release_1211_behavior_checks={
 'master CATEGORY code': 'lookupValuesByCode("CATEGORY")' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),
 'master UNIT code': 'lookupValuesByCode("UNIT")' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),
 'master GST code': 'lookupValuesByCode("GST")' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),
 'master DISCOUNT code': 'lookupValuesByCode("DISCOUNT")' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),
 'legacy ITEM_CATEGORY removed': 'lookupValuesByCode("ITEM_CATEGORY")' not in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),
 'HTTP cancellation rethrown': 'catch(e:CancellationException){throw e}catch(e:Exception)' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/api/DseErpHttpClient.kt').read_text(),
 'biometric extends valid session': 'when(val extended=a.extendSession())' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt').read_text(),
 'biometric lock action': 'biometricLockAvailable' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt').read_text(),
 'secure logout disables stale biometric enrollment': 'OfflineRepository.setBiometricLoginEnabled(false)' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt').read_text(),
}
for key,value in release_1211_behavior_checks.items():
    print(f'{key}: {"OK" if value else "FAIL"}')
    failed |= not value

distribution_checks={
 'UAT product flavor': 'create("uat")' in android_gradle and 'applicationIdSuffix = ".uat"' in android_gradle,
 'PROD product flavor': 'create("prod")' in android_gradle and 'api.jasviindustries.in' in android_gradle,
 'Android update permission': 'REQUEST_INSTALL_PACKAGES' in (root/'androidApp/src/main/AndroidManifest.xml').read_text(),
 'SHA-256 updater': 'Downloaded APK checksum does not match' in (root/'androidApp/src/main/kotlin/org/dse/mobile/android/MainActivity.kt').read_text(),
 'environment lock': 'opposite Jasvi environment' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/security/MobileSecurity.kt').read_text(),
 'PROD debug disabled': 'variantBuilder.productFlavors.any { it.second == "prod" }' in android_gradle and 'variantBuilder.enable = false' in android_gradle,
 'PROD signing guard': 'jasviProdStoreFile' in android_gradle and 'PROD release signing is not configured' in android_gradle,
 'UAT deep-link separation': 'dseerp-uat://dashboard' in (root/'androidApp/src/uat/res/xml/shortcuts.xml').read_text(),
}
for key,value in distribution_checks.items():
    print(f'{key}: {"OK" if value else "FAIL"}')
    failed |= not value

for key,value in checks.items():
    print(f'{key}: {"OK" if value else "FAIL"}')
    failed |= not value

print(f'release_identity mobile={android_version or "UNKNOWN"} baseline={server_baseline or "UNKNOWN"}')

health_model=(root/'shared/src/commonMain/kotlin/org/dse/mobile/core/model/AuthModels.kt').read_text()
for field in ['minimumSupportedDesktopVersion','latestDesktopVersion','minimumSupportedAndroidVersion','latestAndroidVersion','minimumSupportedIosVersion','latestIosVersion','environment','databaseName','utcTime','dateFormat','timePolicy']:
    ok=f'val {field}:' in health_model
    print(f'runtime health field {field}: {"OK" if ok else "FAIL"}')
    failed |= not ok

app_text=(root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt').read_text()
actual_version_ui='connectedVersion=Regex' in app_text
startup_timeout='withTimeoutOrNull(10_000){probe.runtimeHealth()}' in app_text
server_mobile_policy='evaluateMobileCompatibility(status,platformName())' in app_text and 'OPTIONAL_UPDATE' in app_text and 'REQUIRED_UPDATE' in app_text
print(f'actual connected server version UI: {"OK" if actual_version_ui else "FAIL"}')
print(f'10s startup runtime-health timeout: {"OK" if startup_timeout else "FAIL"}')
print(f'server-driven optional/required mobile update policy: {"OK" if server_mobile_policy else "FAIL"}')
failed |= not actual_version_ui or not startup_timeout or not server_mobile_policy

ui_dialog_text=(root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/UiDialogState.kt').read_text() if (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/UiDialogState.kt').is_file() else ''
auth_coordinator_text=(root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/AuthenticationCoordinator.kt').read_text() if (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/AuthenticationCoordinator.kt').is_file() else ''
dialog_auth_checks={
 'central UiDialogState.Error': 'data class Error(' in ui_dialog_text,
 'central UiDialogState.Warning': 'data class Warning(' in ui_dialog_text,
 'central UiDialogState.Confirmation': 'data class Confirmation(' in ui_dialog_text,
 'central UiDialogState.Information': 'data class Information(' in ui_dialog_text,
 'single root dialog renderer': 'CompositionLocalProvider(LocalUiDialogController provides dialogs)' in app_text and 'UiDialogRenderer(dialogs)' in app_text,
 'ConfirmDialog routed to root controller': 'dialogs.confirmation(title,message,confirmLabel,danger,requiredPhrase,requiredPhraseLabel,onDismiss,onConfirm)' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/DseComponents.kt').read_text(),
 'typed destructive confirmation centralized': 'requiredPhrase: String? = null' in ui_dialog_text and 'typedPhrase by remember(dialog)' in ui_dialog_text,
 'shared authentication coordinator': 'class AuthenticationCoordinator' in auth_coordinator_text and 'completeAuthorizedSession(' in auth_coordinator_text,
 'password uses coordinator': 'authCoordinator.authenticatePassword' in app_text,
 'biometric uses coordinator after device proof': 'platformAuthenticateBiometric("Unlock Jasvi Industries Mobile")' in app_text and 'authCoordinator.authenticateBiometricSession' in app_text,
 'MFA uses coordinator': 'authCoordinator.authenticateMfa' in app_text,
 'auth profile linkage': 'api.currentProfile()' in auth_coordinator_text and 'sameErpUser(' in auth_coordinator_text,
 'auth permission validation': 'api.effectivePermissions()' in auth_coordinator_text,
}
for key,value in dialog_auth_checks.items():
    print(f'{key}: {"OK" if value else "FAIL"}')
    failed |= not value

forbidden=[
    'iOS System Experience',
    'iPhone & iPad',
    'Face ID / Touch ID',
    'Native iPhone/iPad',
    'with iOS widget',
    'iOS accepts CSV',
]
for term in forbidden:
    hits=[]
    for path in (root/'composeApp/src/commonMain').rglob('*.kt'):
        if term in path.read_text(errors='replace'): hits.append(str(path.relative_to(root)))
    if hits:
        print('forbidden_common_ui',term,hits); failed=True

old_baseline=[]
for base in [root/'composeApp/src',root/'shared/src',root/'api-contract']:
    if not base.exists(): continue
    for path in base.rglob('*'):
        if path.is_file() and path.suffix.lower() in {'.kt','.yaml','.yml','.json','.txt'}:
            text=path.read_text(errors='ignore')
            if '9.0.92' in text: old_baseline.append(str(path.relative_to(root)))
print(f'old_9.0.92_references={len(old_baseline)}')
if old_baseline:
    print('  stale baseline:',*old_baseline,sep='\n  '); failed=True

stale_100=[]
for base in [root/'composeApp/src',root/'shared/src',root/'api-contract']:
    if not base.exists(): continue
    for path in base.rglob('*'):
        if path.is_file() and path.suffix.lower() in {'.kt','.yaml','.yml','.json','.txt'}:
            text=path.read_text(errors='ignore').replace('10.0.0.50','TEST_IP')
            if '10.0.0' in text: stale_100.append(str(path.relative_to(root)))
print(f'stale_10.0.0_release_references={len(stale_100)}')
if stale_100:
    print('  stale release:',*stale_100,sep='\n  '); failed=True

if failed:
    print('ANDROID_SOURCE_PARITY_FAILED'); sys.exit(1)
print('ANDROID_SOURCE_PARITY_OK')
