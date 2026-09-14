package org.dse.mobile.core

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceContractJvmTest {
    private fun projectRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        while (true) {
            if (cursor.resolve("composeApp/src/commonMain/kotlin/org/dse/mobile/app/DocumentScreens.kt").exists()) return cursor
            cursor = cursor.parent ?: break
        }
        error("Unable to locate DSE-ERP-Mobile project root from ${System.getProperty("user.dir")}")
    }

    @Test fun releaseIsAndroidOnly() {
        val root = projectRoot()
        val sharedGradle = root.resolve("shared/build.gradle.kts").readText()
        val composeGradle = root.resolve("composeApp/build.gradle.kts").readText()
        assertFalse(root.resolve("iosApp").exists())
        assertFalse(root.resolve("composeApp/src/iosMain").exists())
        assertFalse(root.resolve("shared/src/iosMain").exists())
        assertFalse("iosArm64()" in sharedGradle || "iosSimulatorArm64()" in sharedGradle || "ktor-client-darwin" in sharedGradle)
        assertFalse("iosArm64()" in composeGradle || "iosSimulatorArm64()" in composeGradle || "KotlinNativeTarget" in composeGradle)
    }

    @Test fun documentActionsDoNotReintroduceApprovalLock() {
        val text = projectRoot().resolve("composeApp/src/commonMain/kotlin/org/dse/mobile/app/DocumentScreens.kt").readText()
        assertTrue("canonicalBusinessDocumentAllowed(state)" in text)
        assertFalse("SALES_INVOICE\",r.invoiceNo,\"PDF\")}},enabled=active&&!approvalLocked" in text)
        assertFalse("SALES_INVOICE\",r.invoiceNo,\"XLSX\")}},enabled=active&&!approvalLocked" in text)
        assertFalse("Send Email\",{loadFull(r){email=it}},enabled=active&&!approvalLocked" in text)
    }

    @Test fun canonicalRoutesStayOnServerContract() {
        val routes = projectRoot().resolve("shared/src/commonMain/kotlin/org/dse/mobile/core/api/ApiRoutes.kt").readText().replace(" ", "")
        assertTrue("constvalCANONICAL_DOCUMENT=\"/api/documents/render\"" in routes)
        assertTrue("constvalBUSINESS_EMAIL=\"/api/authority/email\"" in routes)
    }
}
