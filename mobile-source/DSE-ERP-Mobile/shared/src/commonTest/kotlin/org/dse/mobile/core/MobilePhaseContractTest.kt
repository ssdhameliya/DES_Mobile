package org.dse.mobile.core
import kotlin.test.*
import org.dse.mobile.core.api.*
import org.dse.mobile.core.security.MobileSecurityPolicy
import org.dse.mobile.core.config.MobileBuildInfo
class MobilePhaseContractTest{
 @Test fun writeRoutesRemainServerOwned(){assertEquals("/api/operations/sales",ExistingErpRoutes.SALES);assertEquals("/api/operations/purchases",ExistingErpRoutes.PURCHASES);assertEquals("/api/operations/finance",ExistingErpRoutes.FINANCE)}
 @Test fun productionNetworkingRequiresHttps(){assertTrue(MobileSecurityPolicy.isSafeEndpoint("https://erp.example.com"));assertFalse(MobileSecurityPolicy.isSafeEndpoint("http://erp.example.com"))}
 @Test fun mobileV1IsVersioned(){assertTrue(MobileV1Routes.BASE.endsWith("/v1"))}
 @Test fun parityMobileVersionPinsLiveServerBaseline(){assertEquals("1.2.10",MobileBuildInfo.MOBILE_VERSION_NAME);assertEquals("1.2.10-V10.0.9-DISTRIBUTION",MobileBuildInfo.MOBILE_VERSION);assertEquals("10.0.9",MobileBuildInfo.SERVER_BASELINE);assertEquals("10.0.1",MobileBuildInfo.MINIMUM_COMPATIBLE_SERVER_VERSION);assertEquals("server-10.0.5-compatible-v4",MobileBuildInfo.API_CONTRACT_VERSION);assertEquals("1.2.3",MobileBuildInfo.MINIMUM_SUPPORTED_MOBILE_VERSION)}
 @Test fun productionSecurityRejectsPlainRemoteHttp(){assertFalse(MobileSecurityPolicy.isSafeEndpoint("http://10.0.0.50:8080"));assertTrue(MobileSecurityPolicy.isSafeEndpoint("http://127.0.0.1:8080"))}
 @Test fun uatTestBuildBlocksKnownProdHost(){assertTrue(MobileBuildInfo.UAT_ONLY_TEST_BUILD);assertFalse(MobileSecurityPolicy.isSafeEndpoint("https://api.jasviindustries.in"));assertTrue(MobileSecurityPolicy.endpointProblem("https://api.jasviindustries.in")?.contains("opposite Jasvi environment")==true)}
}
