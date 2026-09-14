from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
MOBILE = ROOT / "mobile-source/DSE-ERP-Mobile"


def replace(path: Path, old: str, new: str, count: int = -1):
    text = path.read_text()
    actual = text.count(old)
    if actual == 0:
        raise SystemExit(f"Required anchor not found in {path}: {old[:120]!r}")
    if count > 0 and actual < count:
        raise SystemExit(f"Expected at least {count} anchors in {path}; found {actual}")
    path.write_text(text.replace(old, new, count))

http = MOBILE / "shared/src/commonMain/kotlin/org/dse/mobile/core/api/DseErpHttpClient.kt"
t = http.read_text()
if "import kotlinx.coroutines.CancellationException" not in t:
    t = t.replace("import kotlinx.serialization.json.Json\n", "import kotlinx.serialization.json.Json\nimport kotlinx.coroutines.CancellationException\n")
t, cancellations = re.subn(r"catch\s*\(e:\s*Exception\)", "catch(e:CancellationException){throw e}catch(e:Exception)", t)
if cancellations < 3:
    raise SystemExit(f"Expected central HTTP Exception catches; found {cancellations}")
http.write_text(t)

more = MOBILE / "composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt"
t = more.read_text()
start = t.find("@Composable private fun ItemEditor(")
end = t.find("\n@Composable private fun LookupScreen", start)
if start < 0 or end < 0:
    raise SystemExit("ItemEditor boundaries not found")
item_editor = r'''@Composable private fun ItemEditor(api:DseErpHttpClient,current:MasterItem?,onClose:()->Unit,onSave:(MasterItem)->Unit){
    var d by remember{mutableStateOf(current?:MasterItem())}
    var code by remember{mutableStateOf(current?.itemCode.orEmpty())}
    var msg by remember{mutableStateOf("")}
    var categories by remember{mutableStateOf<List<String>>(emptyList())}
    var units by remember{mutableStateOf<List<String>>(emptyList())}
    var gstValues by remember{mutableStateOf<List<String>>(emptyList())}
    var discounts by remember{mutableStateOf<List<String>>(emptyList())}

    fun lookupNumber(value:String):Double=value.replace("%","").trim().toDoubleOrNull()?:0.0
    fun lookupDisplay(options:List<String>,number:Double):String =
        options.firstOrNull{kotlin.math.abs(lookupNumber(it)-number)<0.000001}
            ?: if(kotlin.math.abs(number-kotlin.math.round(number))<0.000001)number.toInt().toString() else number.toString()

    LaunchedEffect(current){
        if(current==null&&code.isBlank())when(val r=api.nextItemCode()){is ApiResult.Success->code=r.value.code;else->msg=r.readableMessage()}
        when(val r=api.lookupValuesByCode("CATEGORY")){is ApiResult.Success->categories=r.value;else->msg=r.readableMessage()}
        when(val r=api.lookupValuesByCode("UNIT")){is ApiResult.Success->units=r.value;else->msg=r.readableMessage()}
        when(val r=api.lookupValuesByCode("GST")){is ApiResult.Success->gstValues=r.value;else->msg=r.readableMessage()}
        when(val r=api.lookupValuesByCode("DISCOUNT")){is ApiResult.Success->discounts=r.value;else->msg=r.readableMessage()}
    }
    PremiumAlertDialog(
        onDismissRequest=onClose,
        title={Text(if(current==null)"New Item" else "Edit ${current.itemCode}")},
        text={Column(Modifier.heightIn(max=650.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            DseField("Item Code",code,readOnly=true,singleLine=true,required=true,onValue={})
            DseField("Description",d.description,required=true,onValue={d=d.copy(description=it)})
            DseSelect("Category",d.category.orEmpty(),categories,required=true,onValue={d=d.copy(category=it)})
            DseSelect("Unit",d.unit.orEmpty(),units,required=true,onValue={d=d.copy(unit=it)})
            DseField("HSN",d.hsn.orEmpty(),singleLine=true,required=true,onValue={d=d.copy(hsn=it)})
            DseSelect("GST %",lookupDisplay(gstValues,d.gst),gstValues,required=true,onValue={d=d.copy(gst=lookupNumber(it))})
            DseSelect("Discount %",lookupDisplay(discounts,d.discountPercent),discounts,required=true,onValue={d=d.copy(discountPercent=lookupNumber(it))})
            DseNumberField("Purchase Price",d.purchasePrice.toString(),min=0.0,onValue={d=d.copy(purchasePrice=it.toDoubleOrNull()?:0.0)})
            DseNumberField("Selling Price",d.sellingPrice.toString(),min=0.0,onValue={d=d.copy(sellingPrice=it.toDoubleOrNull()?:0.0)})
            DseNumberField("Opening Stock",d.openingStock.toString(),enabled=current==null,min=0.0,required=true,onValue={d=d.copy(openingStock=it.toDoubleOrNull()?:0.0)})
            DseNumberField("Minimum Stock",d.minimumStock.toString(),min=0.0,onValue={d=d.copy(minimumStock=it.toDoubleOrNull()?:0.0)})
            DseField("Location",d.location.orEmpty(),onValue={d=d.copy(location=it)})
            DseField("Remarks",d.remarks.orEmpty(),required=true,onValue={d=d.copy(remarks=it)})
            Row(verticalAlignment=Alignment.CenterVertically){Switch(d.active,{d=d.copy(active=it)});PremiumOptionLabel("Active",accent=DseSuccess)}
            DseMessageFeedback(msg)
        }},
        confirmButton={Button(
            enabled=code.isNotBlank()&&d.description.isNotBlank()&&!d.category.isNullOrBlank()&&!d.unit.isNullOrBlank()&&!d.hsn.isNullOrBlank()&&!d.remarks.isNullOrBlank()&&gstValues.isNotEmpty()&&discounts.isNotEmpty(),
            onClick={onSave(d.copy(itemCode=code,brand=null,material=null,size=null))},
        ){Text("Save")}},
        dismissButton={TextButton(onClick=onClose){Text("Cancel")}},
    )
}
'''
t = t[:start] + item_editor + t[end:]
t = t.replace("Fingerprint / biometric sign-in", "Fingerprint / biometric unlock")
t = t.replace("Enabled. Your encrypted signed-in session can be unlocked without re-entering the password.", "Enabled. Use Lock App to preserve the active encrypted session, then unlock it with biometrics without re-entering the password.")
t = t.replace("Off. Turn this on once to use ${platformBiometricUnlockLabel()} on the sign-in screen.", "Off. Turn this on to use ${platformBiometricUnlockLabel()} when unlocking a remembered active session. Secure Sign Out still requires password/MFA next time.")
t = t.replace("Biometric sign-in disabled", "Biometric unlock disabled")
t = t.replace("Enable biometric sign-in", "Enable biometric unlock")
t = t.replace("Biometric sign-in enabled", "Biometric unlock enabled")
more.write_text(t)

app = MOBILE / "composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt"
t = app.read_text()
old_bio = '''      }else{\n       val a=DseErpHttpClient(serverUrl,sessions);api?.close();api=a\n       applyAuthenticationResult(authCoordinator.authenticateBiometricSession(a,serverUrl))\n      }'''
new_bio = '''      }else{\n       val a=DseErpHttpClient(serverUrl,sessions);api?.close();api=a\n       when(val extended=a.extendSession()){\n        is ApiResult.Success->applyAuthenticationResult(authCoordinator.authenticateBiometricSession(a,serverUrl))\n        else->{\n         sessions.clear();saved=false;a.close();api=null\n         val text="Your remembered session has expired or was signed out. Sign in with password/MFA, then enable biometric unlock again."\n         message=text;dialogs.warning(text,"Biometric Session Expired")\n        }\n       }\n      }'''
if old_bio not in t: raise SystemExit("Biometric auth anchor not found")
t=t.replace(old_bio,new_bio,1)
old_main='''     if(a==null)Text("Session unavailable") else MainApp(a,PermissionContext(user,permissions),message){\n      a.logout()\n      OfflineRepository.clearAuthSnapshot(serverUrl)\n      OfflineRepository.clearScopeData()\n      OfflineRepository.deactivateScope()\n      a.close();api=null;user=null;permissions=emptyList();saved=false\n      message="Signed out securely; cached ERP data for this session was cleared"\n      root=RootPage.LOGIN\n      dialogs.information(message,"Signed Out")\n     }'''
new_main='''     if(a==null)Text("Session unavailable") else MainApp(\n      a,PermissionContext(user,permissions),message,\n      biometricLockAvailable=biometric&&OfflineRepository.biometricLoginEnabled(),\n      onLock={\n       a.close();api=null;user=null;permissions=emptyList();saved=sessions.accessToken()?.isNotBlank()==true\n       message="App locked. Unlock with biometrics or sign in with your password."\n       root=RootPage.LOGIN\n       dialogs.information(message,"App Locked")\n      },\n      onLogout={\n       a.logout()\n       OfflineRepository.setBiometricLoginEnabled(false)\n       OfflineRepository.clearAuthSnapshot(serverUrl)\n       OfflineRepository.clearScopeData()\n       OfflineRepository.deactivateScope()\n       a.close();api=null;user=null;permissions=emptyList();saved=false\n       message="Signed out securely; the server session was revoked and biometric unlock was disabled for this session."\n       root=RootPage.LOGIN\n       dialogs.information(message,"Signed Out")\n      },\n     )'''
if old_main not in t: raise SystemExit("MainApp logout anchor not found")
t=t.replace(old_main,new_main,1)
old_sig='@Composable private fun MainApp(api:DseErpHttpClient,permission:PermissionContext,banner:String,onLogout:suspend()->Unit){'
new_sig='@Composable private fun MainApp(api:DseErpHttpClient,permission:PermissionContext,banner:String,biometricLockAvailable:Boolean,onLock:suspend()->Unit,onLogout:suspend()->Unit){'
if old_sig not in t: raise SystemExit("MainApp signature anchor not found")
t=t.replace(old_sig,new_sig,1)
old_actions='''     ConnectionChip(online)\n     IconButton(onClick={scope.launch{onLogout()}}){Icon(Icons.Rounded.Logout,"Logout")}'''
new_actions='''     ConnectionChip(online)\n     if(biometricLockAvailable)IconButton(onClick={scope.launch{onLock()}}){Icon(Icons.Rounded.Lock,"Lock App for biometric unlock")}\n     IconButton(onClick={scope.launch{onLogout()}}){Icon(Icons.Rounded.Logout,"Sign Out and revoke session")}'''
if old_actions not in t: raise SystemExit("MainApp action anchor not found")
t=t.replace(old_actions,new_actions,1)
app.write_text(t)

test=MOBILE/"shared/src/jvmTest/kotlin/org/dse/mobile/core/Release1211BehaviorContractTest.kt"
test.parent.mkdir(parents=True,exist_ok=True)
test.write_text(r'''package org.dse.mobile.core

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Release1211BehaviorContractTest {
    private fun root():Path {
        var p=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while(true){
            if(p.resolve("composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt").toFile().exists())return p
            p=p.parent?:error("Project root not found")
        }
    }
    @Test fun itemMasterMatchesDesktopLookupContract(){
        val text=root().resolve("composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt").readText()
        val item=text.substringAfter("@Composable private fun ItemEditor(").substringBefore("@Composable private fun LookupScreen")
        for(code in listOf("CATEGORY","UNIT","GST","DISCOUNT"))assertTrue("lookupValuesByCode(\"$code\")" in item)
        assertFalse("ITEM_CATEGORY" in item)
        assertFalse("DseField(\"Brand\"" in item);assertFalse("DseField(\"Material\"" in item);assertFalse("DseField(\"Size\"" in item)
        assertTrue("brand=null,material=null,size=null" in item)
    }
    @Test fun cancelledLiveRequestsAreNotNetworkErrors(){
        val text=root().resolve("shared/src/commonMain/kotlin/org/dse/mobile/core/api/DseErpHttpClient.kt").readText()
        assertTrue("CancellationException" in text);assertTrue("catch(e:CancellationException){throw e}catch(e:Exception)" in text)
    }
    @Test fun biometricLockAndTrueSignOutHaveDistinctLifecycle(){
        val text=root().resolve("composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt").readText()
        assertTrue("when(val extended=a.extendSession())" in text);assertTrue("authCoordinator.authenticateBiometricSession" in text)
        assertTrue("biometricLockAvailable" in text);assertTrue("onLock" in text)
        assertTrue("OfflineRepository.setBiometricLoginEnabled(false)" in text);assertTrue("a.logout()" in text)
    }
}
''')

verifier=MOBILE/"scripts/verify-android-source.py"
t=verifier.read_text();anchor="distribution_checks={\n"
checks='''release_1211_behavior_checks={\n 'master CATEGORY code': 'lookupValuesByCode("CATEGORY")' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),\n 'master UNIT code': 'lookupValuesByCode("UNIT")' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),\n 'master GST code': 'lookupValuesByCode("GST")' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),\n 'master DISCOUNT code': 'lookupValuesByCode("DISCOUNT")' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),\n 'legacy ITEM_CATEGORY removed': 'lookupValuesByCode("ITEM_CATEGORY")' not in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt').read_text(),\n 'HTTP cancellation rethrown': 'catch(e:CancellationException){throw e}catch(e:Exception)' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/api/DseErpHttpClient.kt').read_text(),\n 'biometric extends valid session': 'when(val extended=a.extendSession())' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt').read_text(),\n 'biometric lock action': 'biometricLockAvailable' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt').read_text(),\n 'secure logout disables stale biometric enrollment': 'OfflineRepository.setBiometricLoginEnabled(false)' in (root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/App.kt').read_text(),\n}\nfor key,value in release_1211_behavior_checks.items():\n    print(f'{key}: {"OK" if value else "FAIL"}')\n    failed |= not value\n\n'''
if anchor not in t: raise SystemExit("Verifier behavior insertion anchor missing")
verifier.write_text(t.replace(anchor,checks+anchor,1))
print("MOBILE_1211_FINAL_FIXES_OK")
