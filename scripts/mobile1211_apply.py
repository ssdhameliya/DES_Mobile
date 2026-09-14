from pathlib import Path
import re
import shutil

ROOT = Path(__file__).resolve().parents[1]
MOBILE = ROOT / "mobile-source/DSE-ERP-Mobile"


def replace(path: Path, old: str, new: str, count: int = -1):
    text = path.read_text()
    actual = text.count(old)
    if actual == 0:
        raise SystemExit(f"Required anchor not found in {path}: {old[:100]!r}")
    if count > 0 and actual < count:
        raise SystemExit(f"Expected at least {count} anchors in {path}; found {actual}")
    path.write_text(text.replace(old, new, count))

# Release metadata: Android-only Mobile 1.2.11 against the current 10.0.11 server contract.
replace(MOBILE / "androidApp/build.gradle.kts", "versionCode = 130", "versionCode = 131")
replace(MOBILE / "androidApp/build.gradle.kts", 'versionName = "1.2.10"', 'versionName = "1.2.11"')
build_info = MOBILE / "shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt"
replace(build_info, 'installedVersionName: String = "1.2.10"', 'installedVersionName: String = "1.2.11"')
replace(build_info, 'FALLBACK_MOBILE_VERSION_NAME = "1.2.10"', 'FALLBACK_MOBILE_VERSION_NAME = "1.2.11"')
replace(build_info, '"$MOBILE_VERSION_NAME-V10.0.9-DISTRIBUTION"', '"$MOBILE_VERSION_NAME-V10.0.11-DISTRIBUTION"')
replace(build_info, 'SERVER_BASELINE = "10.0.9"', 'SERVER_BASELINE = "10.0.11"')
replace(build_info, '// Desktop/server 10.0.9 keeps the bearer-v5 mobile API contract; older certified servers remain allowed by the compatibility floor.', '// Desktop/server 10.0.11 keeps the bearer-v5 mobile API contract; older certified servers remain allowed by the compatibility floor.')

# Central document output policy: pending/rejected records remain renderable/email-able.
policy = MOBILE / "shared/src/commonMain/kotlin/org/dse/mobile/core/model/DocumentActionPolicy.kt"
policy.write_text('''package org.dse.mobile.core.model\n\n/**\n * Shared Mobile policy for canonical server-owned document output.\n *\n * Desktop/server 10.0.11 permits PDF, XLSX and business-email generation for\n * pending/rejected records. Approval state still governs financial actions\n * such as payments and returns, but it must not block document rendering.\n */\nfun canonicalBusinessDocumentAllowed(documentStatus: String?): Boolean {\n    val state = documentStatus.orEmpty().trim().uppercase()\n    return state !in setOf("CANCELLED", "DELETED")\n}\n''')

doc = MOBILE / "composeApp/src/commonMain/kotlin/org/dse/mobile/app/DocumentScreens.kt"
replace(doc, 'val active=state !in setOf("CANCELLED","DELETED")\n        val approvalLocked=', 'val active=state !in setOf("CANCELLED","DELETED")\n        val documentOutputAllowed=canonicalBusinessDocumentAllowed(state)\n        val approvalLocked=', 2)
replace(doc, 'add(PremiumActionSpec("Share PDF",{scope.launch{msg=shareCanonicalDocument(api,"SALES_INVOICE",r.invoiceNo,"PDF")}},enabled=active&&!approvalLocked,disabledReason="Document output is available after approval."))', 'add(PremiumActionSpec("Share PDF",{scope.launch{msg=shareCanonicalDocument(api,"SALES_INVOICE",r.invoiceNo,"PDF")}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Sales cannot be shared." else null))')
replace(doc, 'add(PremiumActionSpec("Excel",{scope.launch{msg=shareCanonicalDocument(api,"SALES_INVOICE",r.invoiceNo,"XLSX")}},enabled=active&&!approvalLocked,disabledReason="Document output is available after approval."))', 'add(PremiumActionSpec("Excel",{scope.launch{msg=shareCanonicalDocument(api,"SALES_INVOICE",r.invoiceNo,"XLSX")}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Sales cannot be exported." else null))')
replace(doc, 'add(PremiumActionSpec("Send Email",{loadFull(r){email=it}},enabled=active&&!approvalLocked,disabledReason="Email is available after approval."))', 'add(PremiumActionSpec("Send Email",{loadFull(r){email=it}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Sales cannot be emailed." else null))', 1)
replace(doc, 'add(PremiumActionSpec("Share PDF",{scope.launch{msg=shareCanonicalDocument(api,"PURCHASE_INVOICE",r.invoiceNo,"PDF")}},enabled=active&&!approvalLocked,disabledReason="Document output is available after approval."))', 'add(PremiumActionSpec("Share PDF",{scope.launch{msg=shareCanonicalDocument(api,"PURCHASE_INVOICE",r.invoiceNo,"PDF")}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Purchases cannot be shared." else null))')
replace(doc, 'add(PremiumActionSpec("Excel",{scope.launch{msg=shareCanonicalDocument(api,"PURCHASE_INVOICE",r.invoiceNo,"XLSX")}},enabled=active&&!approvalLocked,disabledReason="Document output is available after approval."))', 'add(PremiumActionSpec("Excel",{scope.launch{msg=shareCanonicalDocument(api,"PURCHASE_INVOICE",r.invoiceNo,"XLSX")}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Purchases cannot be exported." else null))')
replace(doc, 'add(PremiumActionSpec("Send Email",{loadFull(r){email=it}},enabled=active&&!approvalLocked,disabledReason="Email is available after approval."))', 'add(PremiumActionSpec("Send Email",{loadFull(r){email=it}},enabled=documentOutputAllowed,disabledReason=if(!documentOutputAllowed)"Cancelled or deleted Purchases cannot be emailed." else null))', 1)
replace(doc, 'val active=state !in setOf("CANCELLED","DELETED")\n    val approvalLocked=', 'val active=state !in setOf("CANCELLED","DELETED")\n    val documentOutputAllowed=canonicalBusinessDocumentAllowed(state)\n    val approvalLocked=', 2)
replace(doc, 'if(active&&!approvalLocked&&p.can("SALES","VIEW")){add(PremiumActionSpec("Share PDF",onPdf,icon=Icons.Rounded.PictureAsPdf));add(PremiumActionSpec("Excel",onExcel,icon=Icons.Rounded.TableChart));add(PremiumActionSpec("Email",onEmail));add(PremiumActionSpec("WhatsApp",onWhatsApp))}', 'if(documentOutputAllowed&&p.can("SALES","VIEW")){add(PremiumActionSpec("Share PDF",onPdf,icon=Icons.Rounded.PictureAsPdf));add(PremiumActionSpec("Excel",onExcel,icon=Icons.Rounded.TableChart));add(PremiumActionSpec("Email",onEmail));if(!approvalLocked)add(PremiumActionSpec("WhatsApp",onWhatsApp))}')
replace(doc, 'if(active&&!approvalLocked&&p.can("PURCHASE","VIEW")){add(PremiumActionSpec("Share PDF",onPdf,icon=Icons.Rounded.PictureAsPdf));add(PremiumActionSpec("Excel",onExcel,icon=Icons.Rounded.TableChart));add(PremiumActionSpec("Email",onEmail));add(PremiumActionSpec("WhatsApp",onWhatsApp))}', 'if(documentOutputAllowed&&p.can("PURCHASE","VIEW")){add(PremiumActionSpec("Share PDF",onPdf,icon=Icons.Rounded.PictureAsPdf));add(PremiumActionSpec("Excel",onExcel,icon=Icons.Rounded.TableChart));add(PremiumActionSpec("Email",onEmail));if(!approvalLocked)add(PremiumActionSpec("WhatsApp",onWhatsApp))}')

# Mobile is Android-only from 1.2.11. Keep legacy server health fields for wire compatibility only.
compat = MOBILE / "shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileCompatibility.kt"
replace(compat, '    val ios = clientType.equals("iOS", ignoreCase = true)\n    val minimum = (if (ios) status.minimumSupportedIosVersion else status.minimumSupportedAndroidVersion).trim()\n    val latest = (if (ios) status.latestIosVersion else status.latestAndroidVersion).trim()', '    // Mobile 1.2.11 is Android-only. Keep clientType in the public signature for source compatibility,\n    // but intentionally evaluate only the environment-owned Android policy.\n    val minimum = status.minimumSupportedAndroidVersion.trim()\n    val latest = status.latestAndroidVersion.trim()')

shared_gradle = MOBILE / "shared/build.gradle.kts"
t = shared_gradle.read_text()
t = re.sub(r'val isMacOs = System\.getProperty\("os\.name"\)\s*\.startsWith\("Mac", ignoreCase = true\)\s*\n', '', t)
t = re.sub(r'\n\s*if \(isMacOs\) \{\s*iosArm64\(\)\s*iosSimulatorArm64\(\)\s*\}\s*\n', '\n', t, count=1)
t = re.sub(r'\n\s*if \(isMacOs\) \{\s*val iosMain by getting \{\s*dependencies \{ implementation\("io\.ktor:ktor-client-darwin:3\.5\.2"\) \}\s*\}\s*\}\s*\n', '\n', t, count=1)
shared_gradle.write_text(t)

compose_gradle = MOBILE / "composeApp/build.gradle.kts"
t = compose_gradle.read_text().replace('import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget\n', '')
t = re.sub(r'val isMacOs = System\.getProperty\("os\.name"\)\s*\.startsWith\("Mac", ignoreCase = true\)\s*\n', '', t)
t = re.sub(r'\n\s*if \(isMacOs\) \{\s*iosArm64\(\)\s*iosSimulatorArm64\(\)\s*targets\.withType<KotlinNativeTarget>\(\)\.configureEach \{\s*binaries\.framework \{\s*baseName = "ComposeApp"\s*isStatic = true\s*\}\s*\}\s*\}\s*\n', '\n', t, count=1)
compose_gradle.write_text(t)

# Remove native Apple shells/source. A generic web apple-touch-icon is intentionally not part of native iOS app code.
for rel in ["iosApp", "composeApp/src/iosMain", "shared/src/iosMain"]:
    shutil.rmtree(MOBILE / rel, ignore_errors=True)
(ROOT / "README-IOS.txt").unlink(missing_ok=True)

# Android-only common UI icon semantics.
for rel in [
    "composeApp/src/commonMain/kotlin/org/dse/mobile/app/ImportSyncScreens.kt",
    "composeApp/src/commonMain/kotlin/org/dse/mobile/app/MoreScreens.kt",
    "composeApp/src/commonMain/kotlin/org/dse/mobile/app/DsePremium.kt",
]:
    p = MOBILE / rel
    text = p.read_text().replace("Icons.Rounded.PhoneIphone", "Icons.Rounded.PhoneAndroid")
    text = text.replace("import androidx.compose.material.icons.rounded.PhoneIphone\n", "")
    p.write_text(text)

# Tests: version/baseline, Android-only policy, and exact document/email server contract.
phase = MOBILE / "shared/src/commonTest/kotlin/org/dse/mobile/core/MobilePhaseContractTest.kt"
t = phase.read_text().replace('"1.2.10"', '"1.2.11"').replace('"1.2.10-V10.0.9-DISTRIBUTION"', '"1.2.11-V10.0.11-DISTRIBUTION"').replace('assertEquals("10.0.9",MobileBuildInfo.SERVER_BASELINE)', 'assertEquals("10.0.11",MobileBuildInfo.SERVER_BASELINE)')
phase.write_text(t)

mc = MOBILE / "shared/src/commonTest/kotlin/org/dse/mobile/core/MobileCompatibilityTest.kt"
replace(mc, '    @Test fun iosUsesItsOwnPolicy() {\n        val result = evaluateMobileCompatibility(policy(iosMin = "1.2.0", iosLatest = "1.2.6"), "iOS", "1.2.5")\n        assertEquals(MobileUpdateRequirement.OPTIONAL_UPDATE, result.requirement)\n        assertEquals("1.2.6", result.latestVersion)\n    }\n\n', '    @Test fun androidOnlyBuildIgnoresLegacyIosPolicyFields() {\n        val result = evaluateMobileCompatibility(\n            policy(androidMin = "1.2.3", androidLatest = "1.2.5", iosMin = "9.9.9", iosLatest = "9.9.9"),\n            "Android",\n            "1.2.5",\n        )\n        assertEquals(MobileUpdateRequirement.CURRENT, result.requirement)\n        assertEquals("1.2.5", result.latestVersion)\n    }\n\n')

(MOBILE / "shared/src/commonTest/kotlin/org/dse/mobile/core/DocumentContractTest.kt").write_text('''package org.dse.mobile.core\n\nimport kotlin.test.Test\nimport kotlin.test.assertEquals\nimport kotlinx.serialization.json.Json\nimport org.dse.mobile.core.api.ExistingErpRoutes\nimport org.dse.mobile.core.model.BusinessEmailRequest\nimport org.dse.mobile.core.model.canonicalBusinessDocumentAllowed\n\nclass DocumentContractTest {\n    @Test fun pendingAndRejectedDocumentsRemainRenderable() {\n        check(canonicalBusinessDocumentAllowed("PENDING APPROVAL"))\n        check(canonicalBusinessDocumentAllowed("REJECTED"))\n        check(canonicalBusinessDocumentAllowed("APPROVED"))\n        check(!canonicalBusinessDocumentAllowed("CANCELLED"))\n        check(!canonicalBusinessDocumentAllowed("DELETED"))\n    }\n\n    @Test fun canonicalDocumentAndEmailRoutesMatchCurrentServerContract() {\n        assertEquals("/api/documents/render", ExistingErpRoutes.CANONICAL_DOCUMENT)\n        assertEquals("/api/authority/email", ExistingErpRoutes.BUSINESS_EMAIL)\n    }\n\n    @Test fun businessEmailPayloadKeepsServerFieldNames() {\n        val encoded = Json.encodeToString(\n            BusinessEmailRequest.serializer(),\n            BusinessEmailRequest("customer@example.com", "Invoice", "Attached", "invoice.pdf", "AA=="),\n        )\n        for (field in listOf("recipient", "subject", "body", "attachmentName", "attachmentBase64")) {\n            check("\\\"$field\\\"" in encoded) { "Missing server email field: $field" }\n        }\n    }\n}\n''')

(MOBILE / "shared/src/jvmTest/kotlin/org/dse/mobile/core/SourceContractJvmTest.kt").write_text('''package org.dse.mobile.core\n\nimport java.nio.file.Path\nimport kotlin.io.path.exists\nimport kotlin.io.path.readText\nimport kotlin.test.Test\nimport kotlin.test.assertFalse\nimport kotlin.test.assertTrue\n\nclass SourceContractJvmTest {\n    private fun projectRoot(): Path {\n        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()\n        while (true) {\n            if (cursor.resolve("composeApp/src/commonMain/kotlin/org/dse/mobile/app/DocumentScreens.kt").exists()) return cursor\n            cursor = cursor.parent ?: break\n        }\n        error("Unable to locate DSE-ERP-Mobile project root from ${System.getProperty("user.dir")}")\n    }\n\n    @Test fun releaseIsAndroidOnly() {\n        val root = projectRoot()\n        val sharedGradle = root.resolve("shared/build.gradle.kts").readText()\n        val composeGradle = root.resolve("composeApp/build.gradle.kts").readText()\n        assertFalse(root.resolve("iosApp").exists())\n        assertFalse(root.resolve("composeApp/src/iosMain").exists())\n        assertFalse(root.resolve("shared/src/iosMain").exists())\n        assertFalse("iosArm64()" in sharedGradle || "iosSimulatorArm64()" in sharedGradle || "ktor-client-darwin" in sharedGradle)\n        assertFalse("iosArm64()" in composeGradle || "iosSimulatorArm64()" in composeGradle || "KotlinNativeTarget" in composeGradle)\n    }\n\n    @Test fun documentActionsDoNotReintroduceApprovalLock() {\n        val text = projectRoot().resolve("composeApp/src/commonMain/kotlin/org/dse/mobile/app/DocumentScreens.kt").readText()\n        assertTrue("canonicalBusinessDocumentAllowed(state)" in text)\n        assertFalse("SALES_INVOICE\\\",r.invoiceNo,\\\"PDF\\\")}},enabled=active&&!approvalLocked" in text)\n        assertFalse("SALES_INVOICE\\\",r.invoiceNo,\\\"XLSX\\\")}},enabled=active&&!approvalLocked" in text)\n        assertFalse("Send Email\\\",{loadFull(r){email=it}},enabled=active&&!approvalLocked" in text)\n    }\n\n    @Test fun canonicalRoutesStayOnServerContract() {\n        val routes = projectRoot().resolve("shared/src/commonMain/kotlin/org/dse/mobile/core/api/ApiRoutes.kt").readText().replace(" ", "")\n        assertTrue("constvalCANONICAL_DOCUMENT=\\\"/api/documents/render\\\"" in routes)\n        assertTrue("constvalBUSINESS_EMAIL=\\\"/api/authority/email\\\"" in routes)\n    }\n}\n''')

# Strengthen the existing source verifier used by developers/CI evidence.
verifier = MOBILE / "scripts/verify-android-source.py"
t = verifier.read_text()
t = t.replace(" 'iosMain': function_decls([root/'composeApp/src/iosMain',root/'shared/src/iosMain'],'actual'),\n", "")
t = t.replace(" '10.0.9 baseline': 'SERVER_BASELINE = \"10.0.9\"' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt').read_text(),", " '10.0.11 baseline': 'SERVER_BASELINE = \"10.0.11\"' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt').read_text(),")
t = t.replace(" 'mobile 1.2.10': 'FALLBACK_MOBILE_VERSION_NAME = \"1.2.10\"' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt').read_text(),", " 'mobile 1.2.11': 'FALLBACK_MOBILE_VERSION_NAME = \"1.2.11\"' in (root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileBuildInfo.kt').read_text(),")
t = t.replace(" 'desktop 10.0.9 linkage resolver':", " 'desktop linkage resolver':")
t = t.replace("10.0.9 runtime health field", "10.0.11 runtime health field")
needle = "android_gradle=(root/'androidApp/build.gradle.kts').read_text()\ndistribution_checks={\n"
insert = '''android_gradle=(root/'androidApp/build.gradle.kts').read_text()\ndocument_text=(root/'composeApp/src/commonMain/kotlin/org/dse/mobile/app/DocumentScreens.kt').read_text()\napi_routes=(root/'shared/src/commonMain/kotlin/org/dse/mobile/core/api/ApiRoutes.kt').read_text()\ncompat_text=(root/'shared/src/commonMain/kotlin/org/dse/mobile/core/config/MobileCompatibility.kt').read_text()\nshared_gradle=(root/'shared/build.gradle.kts').read_text()\ncompose_gradle=(root/'composeApp/build.gradle.kts').read_text()\nandroid_only_checks={\n 'native iosApp removed': not (root/'iosApp').exists(),\n 'compose iosMain removed': not (root/'composeApp/src/iosMain').exists(),\n 'shared iosMain removed': not (root/'shared/src/iosMain').exists(),\n 'shared iOS targets removed': 'iosArm64()' not in shared_gradle and 'iosSimulatorArm64()' not in shared_gradle and 'ktor-client-darwin' not in shared_gradle,\n 'compose iOS targets removed': 'iosArm64()' not in compose_gradle and 'iosSimulatorArm64()' not in compose_gradle and 'KotlinNativeTarget' not in compose_gradle,\n 'Android policy only': 'status.minimumSupportedAndroidVersion' in compat_text and 'status.minimumSupportedIosVersion else' not in compat_text,\n}\nfor key,value in android_only_checks.items():\n    print(f'{key}: {"OK" if value else "FAIL"}')\n    failed |= not value\n\ndocument_contract_checks={\n 'canonical document route exact': 'constvalCANONICAL_DOCUMENT="/api/documents/render"' in api_routes.replace(' ',''),\n 'business email route exact': 'constvalBUSINESS_EMAIL="/api/authority/email"' in api_routes.replace(' ',''),\n 'document contract test': (root/'shared/src/commonTest/kotlin/org/dse/mobile/core/DocumentContractTest.kt').is_file(),\n 'sales PDF approval lock removed': 'SALES_INVOICE",r.invoiceNo,"PDF")}},enabled=active&&!approvalLocked' not in document_text,\n 'sales Excel approval lock removed': 'SALES_INVOICE",r.invoiceNo,"XLSX")}},enabled=active&&!approvalLocked' not in document_text,\n 'sales Email approval lock removed': 'Send Email",{loadFull(r){email=it}},enabled=active&&!approvalLocked' not in document_text,\n 'purchase PDF approval lock removed': 'PURCHASE_INVOICE",r.invoiceNo,"PDF")}},enabled=active&&!approvalLocked' not in document_text,\n 'purchase Excel approval lock removed': 'PURCHASE_INVOICE",r.invoiceNo,"XLSX")}},enabled=active&&!approvalLocked' not in document_text,\n}\nfor key,value in document_contract_checks.items():\n    print(f'{key}: {"OK" if value else "FAIL"}')\n    failed |= not value\n\ndistribution_checks={\n'''
if needle not in t:
    raise SystemExit("Verifier insertion anchor missing")
t = t.replace(needle, insert)
verifier.write_text(t)

# Permanent same-repo PR gate: no environment secrets and no deployment.
pr_workflow = ROOT / ".github/workflows/mobile-pr.yml"
pr_workflow.parent.mkdir(parents=True, exist_ok=True)
pr_workflow.write_text('''name: Mobile PR Validation\n\non:\n  pull_request:\n    branches: [main]\n    paths:\n      - 'mobile-source/DSE-ERP-Mobile/**'\n      - '.github/workflows/mobile-pr.yml'\n\npermissions:\n  contents: read\n\nconcurrency:\n  group: mobile-pr-${{ github.event.pull_request.number }}\n  cancel-in-progress: true\n\njobs:\n  validate:\n    name: Android source, contracts and compile\n    runs-on: ubuntu-latest\n    timeout-minutes: 30\n    steps:\n      - uses: actions/checkout@v6\n      - name: Set up Java 25\n        uses: actions/setup-java@v5\n        with:\n          distribution: temurin\n          java-version: '25'\n          cache: gradle\n      - name: Set up Android SDK\n        uses: android-actions/setup-android@v3\n      - name: Install Android SDK packages\n        shell: bash\n        run: sdkmanager 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'\n      - name: Verify Android-only source and server document contract\n        working-directory: mobile-source/DSE-ERP-Mobile\n        run: python3 scripts/verify-android-source.py\n      - name: Run shared contracts and JVM compile\n        working-directory: mobile-source/DSE-ERP-Mobile\n        shell: bash\n        run: |\n          chmod +x gradlew\n          ./gradlew :shared:jvmTest :composeApp:compileKotlinJvm --console=plain\n''')

# Final hard assertions before CI starts.
assert 'versionName = "1.2.11"' in (MOBILE / 'androidApp/build.gradle.kts').read_text()
assert not (MOBILE / 'iosApp').exists()
assert not (MOBILE / 'composeApp/src/iosMain').exists()
assert not (MOBILE / 'shared/src/iosMain').exists()
assert 'iosArm64()' not in shared_gradle.read_text()
assert 'iosArm64()' not in compose_gradle.read_text()
print('MOBILE_1211_TRANSFORM_OK')
