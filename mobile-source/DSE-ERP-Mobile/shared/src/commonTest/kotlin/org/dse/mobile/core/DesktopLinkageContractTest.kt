package org.dse.mobile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.dse.mobile.core.model.*

class DesktopLinkageContractTest {
    @Test fun bankStatementSaleAndPurchaseOpenPaymentsExactlyLikeDesktop1009() {
        val sale = resolveDesktopLinkedTarget(DesktopLinkSource.BANK_STATEMENT, "SALE", 14, "SI-14", sourceLabel = "Bank Statement #9")
        val purchase = resolveDesktopLinkedTarget(DesktopLinkSource.BANK_STATEMENT, "PURCHASE", 21, "PI-21", sourceLabel = "Bank Statement #9")
        assertEquals("SALE", sale.target?.moduleKey); assertEquals(LinkedRecordAction.PAYMENT, sale.target?.action); assertEquals("SI-14", sale.target?.reference)
        assertEquals("PURCHASE", purchase.target?.moduleKey); assertEquals(LinkedRecordAction.PAYMENT, purchase.target?.action); assertEquals("PI-21", purchase.target?.reference)
    }

    @Test fun bankStatementReturnOpensRefundAndFinanceUsesExactFinanceId() {
        val returned = resolveDesktopLinkedTarget(DesktopLinkSource.BANK_STATEMENT, "SALES_RETURN", 31, "SR-31")
        val expense = resolveDesktopLinkedTarget(DesktopLinkSource.BANK_STATEMENT, "EXPENSE", 5, "EXP-5", financeEntryId = 77)
        assertEquals(LinkedRecordAction.REFUND, returned.target?.action); assertEquals("SALES_RETURN", returned.target?.moduleKey)
        assertEquals(77L, expense.target?.recordId); assertEquals("EXPENSE", expense.target?.moduleKey)
    }

    @Test fun bankStatementPurchaseReconRequiresIdAndMissingSaleNumberWarns() {
        val recon = resolveDesktopLinkedTarget(DesktopLinkSource.BANK_STATEMENT, "PURCHASE_RECON", null, "PR-1")
        val sale = resolveDesktopLinkedTarget(DesktopLinkSource.BANK_STATEMENT, "SALE", 2, "")
        assertNull(recon.target); assertTrue(recon.warning.orEmpty().contains("record id"))
        assertNull(sale.target); assertTrue(sale.warning.orEmpty().contains("invoice number"))
    }

    @Test fun financeEntrySaleUsesRegisterViewNotPayment() {
        val link = resolveDesktopLinkedTarget(DesktopLinkSource.FINANCE_ENTRY, "SALE", 88, "SI-88")
        assertNotNull(link.target); assertEquals(LinkedRecordAction.VIEW, link.target.action); assertEquals("SALE", link.target.moduleKey)
    }
}
