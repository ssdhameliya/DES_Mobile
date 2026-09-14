package org.dse.mobile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.dse.mobile.core.config.MobileUpdateRequirement
import org.dse.mobile.core.config.evaluateMobileCompatibility
import org.dse.mobile.core.model.RuntimeHealthResponse

class MobileCompatibilityTest {
    private fun policy(
        androidMin: String = "1.2.3",
        androidLatest: String = "1.2.5",
        iosMin: String = "1.2.3",
        iosLatest: String = "1.2.5",
    ) = RuntimeHealthResponse(
        ready = true,
        service = "dse-erp-server",
        version = "10.0.7",
        apiRevision = "spring-security-bearer-v5",
        minimumSupportedAndroidVersion = androidMin,
        latestAndroidVersion = androidLatest,
        minimumSupportedIosVersion = iosMin,
        latestIosVersion = iosLatest,
    )

    @Test fun currentMobileIsAccepted() {
        val result = evaluateMobileCompatibility(policy(), "Android", "1.2.5")
        assertEquals(MobileUpdateRequirement.CURRENT, result.requirement)
        assertTrue(result.loginAllowed)
    }

    @Test fun newerMobileIsOptionalWhenCurrentStillSupported() {
        val result = evaluateMobileCompatibility(policy(androidLatest = "1.3.0"), "Android", "1.2.5")
        assertEquals(MobileUpdateRequirement.OPTIONAL_UPDATE, result.requirement)
        assertTrue(result.loginAllowed)
    }

    @Test fun mobileBelowFloorIsRequired() {
        val result = evaluateMobileCompatibility(policy(androidMin = "1.3.0", androidLatest = "1.4.0"), "Android", "1.2.5")
        assertEquals(MobileUpdateRequirement.REQUIRED_UPDATE, result.requirement)
        assertFalse(result.loginAllowed)
    }

    @Test fun androidOnlyBuildIgnoresLegacyIosPolicyFields() {
        val result = evaluateMobileCompatibility(
            policy(androidMin = "1.2.3", androidLatest = "1.2.5", iosMin = "9.9.9", iosLatest = "9.9.9"),
            "Android",
            "1.2.5",
        )
        assertEquals(MobileUpdateRequirement.CURRENT, result.requirement)
        assertEquals("1.2.5", result.latestVersion)
    }

    @Test fun olderServerWithoutMobilePolicyRemainsCompatible() {
        val result = evaluateMobileCompatibility(RuntimeHealthResponse(version = "10.0.2"), "Android", "1.2.5")
        assertEquals(MobileUpdateRequirement.CURRENT, result.requirement)
        assertTrue(result.loginAllowed)
    }
}
