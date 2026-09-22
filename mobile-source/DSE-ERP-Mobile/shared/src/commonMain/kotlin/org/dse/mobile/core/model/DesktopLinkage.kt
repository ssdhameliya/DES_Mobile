package org.dse.mobile.core.model

/**
 * Mobile representation of the exact cross-screen linkage contract used by Desktop 10.0.26.
 * The source context matters: Bank Statement intentionally opens Sale/Purchase payment flows,
 * while Bank & Expense opens the linked Sale/Purchase register record itself.
 */
enum class DesktopLinkSource { BANK_STATEMENT, FINANCE_ENTRY }
enum class LinkedRecordAction { VIEW, PAYMENT, REFUND }

data class DesktopLinkedTarget(
    val moduleKey: String,
    val reference: String = "",
    val recordId: Long? = null,
    val action: LinkedRecordAction = LinkedRecordAction.VIEW,
    val source: String = "",
)

data class DesktopLinkResolution(
    val target: DesktopLinkedTarget? = null,
    val warning: String? = null,
)

fun resolveDesktopLinkedTarget(
    sourceContext: DesktopLinkSource,
    targetType: String?,
    targetId: Int?,
    documentNo: String?,
    financeEntryId: Int? = null,
    sourceLabel: String = "",
): DesktopLinkResolution {
    val type = targetType.orEmpty().trim().uppercase()
    val document = documentNo.orEmpty().trim()
    fun warning(message: String) = DesktopLinkResolution(warning = message)
    fun target(module: String, id: Int? = targetId, action: LinkedRecordAction = LinkedRecordAction.VIEW) =
        DesktopLinkResolution(DesktopLinkedTarget(module, document, id?.toLong(), action, sourceLabel))

    return when (sourceContext) {
        DesktopLinkSource.BANK_STATEMENT -> when (type) {
            "EXPENSE" -> {
                val id = financeEntryId ?: targetId
                if (id == null) warning("The linked Expense entry no longer has a usable record id.")
                else target("EXPENSE", id)
            }
            "BANK_ENTRY" -> {
                val id = financeEntryId ?: targetId
                if (id == null) warning("The linked Bank entry no longer has a usable record id.")
                else target("BANK", id)
            }
            "SALE" -> if (document.isBlank()) warning("The linked Sale no longer has a usable invoice number.") else target("SALE", targetId, LinkedRecordAction.PAYMENT)
            "PURCHASE" -> if (document.isBlank()) warning("The linked Purchase no longer has a usable invoice number.") else target("PURCHASE", targetId, LinkedRecordAction.PAYMENT)
            "PURCHASE_RECON" -> if (targetId == null) warning("The linked Purchase Recon no longer has a usable record id.") else target("PURCHASE_RECON")
            "SALES_RETURN" -> if (document.isBlank()) warning("The linked Sales Return no longer has a document number.") else target("SALES_RETURN", targetId, LinkedRecordAction.REFUND)
            "PURCHASE_RETURN" -> if (document.isBlank()) warning("The linked Purchase Return no longer has a document number.") else target("PURCHASE_RETURN", targetId, LinkedRecordAction.REFUND)
            else -> warning("This linked record type is not available for direct navigation.")
        }
        DesktopLinkSource.FINANCE_ENTRY -> when (type) {
            "SALE" -> if (document.isBlank() && targetId == null) warning("No linked Sale document is available for this entry.") else target("SALE")
            "PURCHASE" -> if (document.isBlank() && targetId == null) warning("No linked Purchase document is available for this entry.") else target("PURCHASE")
            "PURCHASE_RECON" -> if (targetId == null) warning("No linked Purchase Recon record is available for this entry.") else target("PURCHASE_RECON")
            else -> warning("No linked ERP document is available for this entry.")
        }
    }
}
