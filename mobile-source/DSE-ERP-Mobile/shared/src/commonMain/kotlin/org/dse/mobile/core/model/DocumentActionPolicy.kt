package org.dse.mobile.core.model

/**
 * Shared Mobile policy for canonical server-owned document output.
 *
 * Desktop/server 10.0.16 permits PDF, XLSX and business-email generation for
 * pending/rejected records. Approval state still governs financial actions
 * such as payments and returns, but it must not block document rendering.
 */
fun canonicalBusinessDocumentAllowed(documentStatus: String?): Boolean {
    val state = documentStatus.orEmpty().trim().uppercase()
    return state !in setOf("CANCELLED", "DELETED")
}
