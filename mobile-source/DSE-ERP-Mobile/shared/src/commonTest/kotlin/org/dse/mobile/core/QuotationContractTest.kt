package org.dse.mobile.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.dse.mobile.core.model.QuotationLine
import org.dse.mobile.core.model.QuotationRecord
import org.dse.mobile.core.model.QuotationSaveRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuotationContractTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    @Test
    fun quotationRecordPreservesServerRowVersion() {
        val record = QuotationRecord(id = 42, no = "QT-2026-0042", rowVersion = 17)
        val roundTrip = json.decodeFromString<QuotationRecord>(json.encodeToString(record))
        assertEquals(17L, roundTrip.rowVersion)
    }

    @Test
    fun quotationSaveMatchesDesktopServerContract() {
        val request = QuotationSaveRequest(
            id = 42,
            date = "2026-09-14",
            valid = "2026-09-29",
            customerId = 7,
            subtotal = 100.0,
            gstAmount = 18.0,
            total = 118.0,
            source = "DIRECT",
            lines = listOf(
                QuotationLine(
                    code = "ITEM-1",
                    description = "Test item",
                    quantity = 1.0,
                    rate = 100.0,
                    gst = 18.0,
                    total = 118.0,
                    category = "GENERAL",
                    hsn = "1234",
                    unit = "PCS",
                )
            ),
            rowVersion = 17,
        )

        val encoded = json.encodeToString(request)
        val root = json.parseToJsonElement(encoded).jsonObject
        val line = root.getValue("lines").jsonArray.first().jsonObject

        assertEquals("17", root.getValue("rowVersion").toString())
        assertTrue(line.containsKey("category"))
        assertTrue(line.containsKey("hsn"))
        assertTrue(line.containsKey("unit"))
        assertEquals(request, json.decodeFromString<QuotationSaveRequest>(encoded))
    }
}
