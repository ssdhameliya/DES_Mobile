package org.dse.mobile.core

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.dse.mobile.core.api.*
import org.dse.mobile.core.model.*
import kotlin.test.*

class ContractTest {
    @Test
    fun routesMatchServer9024() {
        assertEquals("/api/auth/login", ExistingErpRoutes.LOGIN)
        assertEquals("/api/runtime/health", ExistingErpRoutes.RUNTIME_HEALTH)
        assertEquals("/api/profile", ExistingErpRoutes.PROFILE)
        assertEquals("/api/operations/sales/page", ExistingErpRoutes.SALES_PAGE)
        assertEquals("/api/operations/purchases/page", ExistingErpRoutes.PURCHASES_PAGE)
        assertEquals("/api/operations/finance/page", ExistingErpRoutes.FINANCE_PAGE)
        assertEquals("/api/quotations/page", ExistingErpRoutes.QUOTATIONS_PAGE)
        assertEquals("/api/returns/page", ExistingErpRoutes.RETURNS_PAGE)
        assertEquals("/api/support/payments/with-id", ExistingErpRoutes.PAYMENTS_WITH_ID)
        assertEquals("/api/quotations/sources", ExistingErpRoutes.QUOTATION_SOURCES)
        assertEquals("/api/insights/dashboard", ExistingErpRoutes.INSIGHTS_DASHBOARD)
    }

    @Test
    fun rowVersionSurvivesSerialization() {
        val json = Json { encodeDefaults = true }
        val sale = SaleRecord(invoiceNo = "SAL-TEST", rowVersion = 44)
        val purchase = PurchaseRecord(invoiceNo = "PUR-TEST", rowVersion = 55)
        val finance = FinanceRecord(voucherNo = "BNK-TEST", rowVersion = 66)
        assertEquals(44L, json.decodeFromString<SaleRecord>(json.encodeToString(sale)).rowVersion)
        assertEquals(55L, json.decodeFromString<PurchaseRecord>(json.encodeToString(purchase)).rowVersion)
        assertEquals(66L, json.decodeFromString<FinanceRecord>(json.encodeToString(finance)).rowVersion)
    }

    @Test
    fun profileContractRoundTrips() {
        val json = Json { encodeDefaults = true }
        val profile = UserProfile(id = 7, username = "admin", fullName = "Administrator", role = "ADMIN", active = true)
        assertEquals(profile, json.decodeFromString<UserProfile>(json.encodeToString(profile)))
    }

    @Test
    fun syncCursorContractRoundTrips() {
        val json = Json { encodeDefaults = true }
        val page = SyncPage(
            cursor = 101,
            changes = listOf(
                SyncChange(101, ChangeEntity.SALE, "482", ChangeOperation.UPDATED, 9, "2026-08-27T03:30:00+05:30")
            )
        )
        assertEquals(page, json.decodeFromString<SyncPage>(json.encodeToString(page)))
    }

    @Test
    fun mapsHttp409ToConflict() = runTest {
        val engine = MockEngine {
            respond("{\"message\":\"Concurrent edit\"}", HttpStatusCode.Conflict, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = DseErpHttpClient("https://test", engine = engine)
        val result = api.saleByInvoice("SAL-1")
        assertTrue(result is ApiResult.Conflict)
        api.close()
    }

    @Test
    fun healthEndpointDeserializes() = runTest {
        val engine = MockEngine {
            respond("{\"status\":\"UP\"}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = DseErpHttpClient("https://test", engine = engine)
        val result = api.health()
        assertEquals("UP", (result as ApiResult.Success<HealthResponse>).value.status)
        api.close()
    }
    @Test
    fun decodeFailureIsNotClassifiedAsOfflineNetworkFailure() = runTest {
        val engine = MockEngine {
            respond("{not-valid-json", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = DseErpHttpClient("https://test", engine = engine)
        val result = api.health()
        assertTrue(result is ApiResult.DecodeError)
        api.close()
    }

    @Test
    fun unsafeRemoteHttpIsBlockedBeforeRequest() = runTest {
        var called = false
        val engine = MockEngine { called = true; respond("{\"status\":\"UP\"}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val api = DseErpHttpClient("http://10.0.0.50:8080", engine = engine)
        val result = api.health()
        assertTrue(result is ApiResult.UnsafeEndpoint)
        assertFalse(called)
        api.close()
    }

    @Test
    fun runtimeContractDeserializes1005() = runTest {
        val engine = MockEngine {
            respond("{\"ready\":true,\"service\":\"dse-erp-server\",\"version\":\"10.0.5\",\"apiRevision\":\"spring-security-bearer-v5\",\"buildRevision\":\"10.0.5\",\"minimumSupportedDesktopVersion\":\"10.0.4\",\"latestDesktopVersion\":\"10.0.5\",\"minimumSupportedAndroidVersion\":\"1.2.3\",\"latestAndroidVersion\":\"1.2.4\",\"minimumSupportedIosVersion\":\"1.2.3\",\"latestIosVersion\":\"1.2.4\",\"environment\":\"UAT\",\"database\":\"postgresql\",\"databaseName\":\"dse_erp_uat\",\"databaseTimeZone\":\"UTC\",\"businessZone\":\"Asia/Kolkata\",\"businessDate\":\"2026-09-12\",\"utcTime\":\"2026-09-11T22:03:30Z\",\"dateFormat\":\"dd/MM/yyyy\",\"timePolicy\":\"ISO_DATE_UTC_INSTANT\",\"message\":\"READY\"}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = DseErpHttpClient("https://test", engine = engine)
        val result = api.runtimeHealth() as ApiResult.Success<RuntimeHealthResponse>
        assertTrue(result.value.ready)
        assertEquals("10.0.5", result.value.version)
        assertEquals("spring-security-bearer-v5", result.value.apiRevision)
        assertEquals("10.0.4", result.value.minimumSupportedDesktopVersion)
        assertEquals("10.0.5", result.value.latestDesktopVersion)
        assertEquals("1.2.3", result.value.minimumSupportedAndroidVersion)
        assertEquals("1.2.4", result.value.latestAndroidVersion)
        assertEquals("1.2.3", result.value.minimumSupportedIosVersion)
        assertEquals("1.2.4", result.value.latestIosVersion)
        assertEquals("UAT", result.value.environment)
        assertEquals("dse_erp_uat", result.value.databaseName)
        assertEquals("ISO_DATE_UTC_INSTANT", result.value.timePolicy)
        api.close()
    }

    @Test
    fun nullableLegacyAuthAndAdminFieldsDecodeSafely() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val user = json.decodeFromString<UserPayload>("""{"id":1,"username":"legacy","fullName":null,"email":null,"role":"USER","active":true}""")
        assertNull(user.fullName)
        assertNull(user.email)
        val profile = json.decodeFromString<UserProfile>("""{"id":1,"username":"legacy","fullName":null,"email":null,"role":"USER","active":true}""")
        assertNull(profile.fullName)
        assertNull(profile.email)
        val notification = json.decodeFromString<InsightNotification>("""{"id":1,"title":"Info","message":"Test","targetFxml":null,"referenceNo":null}""")
        assertNull(notification.targetFxml)
        assertNull(notification.referenceNo)
        val adminUser = json.decodeFromString<AdminUser>("""{"id":1,"username":"legacy","fullName":null,"email":null,"department":null,"accessLevel":null,"branch":null}""")
        assertNull(adminUser.fullName)
        val role = json.decodeFromString<AdminRole>("""{"id":1,"code":"USER","description":null}""")
        assertNull(role.description)
    }

    @Test
    fun returnAndItemPathSegmentsAreEncoded() = runTest {
        val seen = mutableListOf<String>()
        val engine = MockEngine { request ->
            seen += request.url.toString()
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = DseErpHttpClient("https://test", engine = engine)
        api.returnDetails("RET/2026 A/001")
        api.deleteItem("ITEM/A B", 2)
        assertTrue(seen[0].contains("RET%2F2026%20A%2F001"), seen[0])
        assertTrue(seen[1].contains("ITEM%2FA%20B"), seen[1])
        api.close()
    }

    @Test
    fun accountRecoveryRoutesMatchServer9092() {
        assertEquals("/api/auth/login/mfa/resend", ExistingErpRoutes.MFA_RESEND)
        assertEquals("/api/auth/password-reset/request", ExistingErpRoutes.PASSWORD_RESET_REQUEST)
        assertEquals("/api/auth/password-reset/complete", ExistingErpRoutes.PASSWORD_RESET_COMPLETE)
        assertEquals("/api/auth/registration/captcha", ExistingErpRoutes.REGISTRATION_CAPTCHA)
        assertEquals("/api/auth/registration/request", ExistingErpRoutes.REGISTRATION_REQUEST)
        assertEquals("/api/auth/registration/email/verify", ExistingErpRoutes.REGISTRATION_EMAIL_VERIFY)
        assertEquals("/api/auth/registration/mfa/complete", ExistingErpRoutes.REGISTRATION_MFA_COMPLETE)
        assertEquals("/api/auth/registration-roles", ExistingErpRoutes.REGISTRATION_ROLES)
        assertEquals("/api/auth/session/extend", ExistingErpRoutes.SESSION_EXTEND)
    }

    @Test
    fun supportProofDeleteRoutesAreCallable() = runTest {
        val seen = mutableListOf<String>()
        val engine = MockEngine { request ->
            seen += "${request.method.value} ${request.url}"
            respond("{\"success\":true,\"message\":\"Removed\"}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = DseErpHttpClient("https://test", engine = engine)
        assertTrue(api.deleteReturnAttachment("RET/1") is ApiResult.Success)
        assertTrue(api.deletePaymentAttachment(7) is ApiResult.Success)
        assertTrue(api.deleteRefundAttachment(9) is ApiResult.Success)
        assertTrue(seen.any { it.contains("DELETE") && it.contains("RET%2F1") })
        assertTrue(seen.any { it.contains("/payments/7/attachment-file") })
        assertTrue(seen.any { it.contains("/return-refunds/9/attachment-file") })
        api.close()
    }

}
