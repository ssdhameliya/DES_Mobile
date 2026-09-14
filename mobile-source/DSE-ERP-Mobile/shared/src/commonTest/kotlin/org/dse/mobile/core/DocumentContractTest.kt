package org.dse.mobile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import org.dse.mobile.core.api.ExistingErpRoutes
import org.dse.mobile.core.model.BusinessEmailRequest
import org.dse.mobile.core.model.canonicalBusinessDocumentAllowed

class DocumentContractTest {
    @Test fun pendingAndRejectedDocumentsRemainRenderable() {
        check(canonicalBusinessDocumentAllowed("PENDING APPROVAL"))
        check(canonicalBusinessDocumentAllowed("REJECTED"))
        check(canonicalBusinessDocumentAllowed("APPROVED"))
        check(!canonicalBusinessDocumentAllowed("CANCELLED"))
        check(!canonicalBusinessDocumentAllowed("DELETED"))
    }

    @Test fun canonicalDocumentAndEmailRoutesMatchCurrentServerContract() {
        assertEquals("/api/documents/render", ExistingErpRoutes.CANONICAL_DOCUMENT)
        assertEquals("/api/authority/email", ExistingErpRoutes.BUSINESS_EMAIL)
    }

    @Test fun businessEmailPayloadKeepsServerFieldNames() {
        val encoded = Json.encodeToString(
            BusinessEmailRequest.serializer(),
            BusinessEmailRequest("customer@example.com", "Invoice", "Attached", "invoice.pdf", "AA=="),
        )
        for (field in listOf("recipient", "subject", "body", "attachmentName", "attachmentBase64")) {
            check("\"$field\"" in encoded) { "Missing server email field: $field" }
        }
    }
}
