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
 'iosMain': function_decls([root/'composeApp/src/iosMain',root/'shared/src/iosMain'],'actual'),
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

checks={
 'androidApp module': 'include(":androidApp")' in (root/'settings.gradle.kts').read_text(),
 'AGP app plugin': 'com.android.application' in (root/'build.gradle.kts').read_text(),
 'KMP Android plugin': 'com.android.kotlin.multiplatform.library' in (root/'build.gradle.kts').read_text(),
 '10.0.9 baseline': 'SERVER_BASELINE = "10.0.9"' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt').read_text(),
 'mobile 1.2.10': 'FALLBACK_MOBILE_VERSION_NAME = "1.2.10"' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt').read_text(),
 'server-driven mobile policy': (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileCompatibility.kt').is_file(),
 '10.0.1 runtime floor': 'MINIMUM_COMPATIBLE_SERVER_VERSION = "10.0.1"' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt').read_text(),
 'API revision bearer v5': 'EXPECTED_API_REVISION = "spring-security-bearer-v5"' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt').read_text(),
 'desktop 10.0.9 linkage resolver': (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/model/DesktopLinkage.kt').is_file(),
 'desktop linkage contract tests': (root/'shared/src/commonTest/kotlin/org/dse/mobile/core/DesktopLinkageContractTest.kt').is_file(),
}
android_gradle=(root/'androidApp/build.gradle.kts').read_text()
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

health_model=(root/'shared/src/commonMain/kotlin/org/dse/mobile/core/model/AuthModels.kt').read_text()
for field in ['minimumSupportedDesktopVersion','latestDesktopVersion','minimumSupportedAndroidVersion','latestAndroidVersion','minimumSupportedIosVersion','latestIosVersion','environment','databaseName','utcTime','dateFormat','timePolicy']:
    ok=f'val {field}:' in health_model
    print(f'10.0.9 runtime health field {field}: {"OK" if ok else "FAIL"}')
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
