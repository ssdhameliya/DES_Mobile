package org.dse.mobile.core

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
