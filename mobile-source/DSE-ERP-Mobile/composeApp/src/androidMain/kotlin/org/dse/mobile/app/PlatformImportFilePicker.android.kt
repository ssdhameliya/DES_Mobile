package org.dse.mobile.app

import org.dse.mobile.core.model.ImportSheet

actual suspend fun pickImportSpreadsheet(): ImportSheet? {
    val (fileName, rawText, error) = nativePickImportTextFileAndroid()
    if (fileName == null) return null
    if (rawText == null) {
        return ImportSheet(
            fileName = fileName,
            sheetName = "Android Files",
            platformNotice = error ?: "This file format is not yet parsed natively on Android.",
        )
    }
    val records = parseCsvRecordsAndroid(rawText).filter { row -> row.any { it.isNotBlank() } }
    if (records.isEmpty()) {
        return ImportSheet(fileName, "CSV", rawCsv = rawText, platformNotice = "The selected CSV is empty.")
    }
    val headers = records.first().map(::normalizeHeaderAndroid)
    val rows = records.drop(1).mapNotNull { cells ->
        val row = linkedMapOf<String, String>()
        headers.forEachIndexed { index, header ->
            if (header.isNotBlank()) row[header] = cells.getOrElse(index) { "" }.trim()
        }
        row.takeIf { it.values.any(String::isNotBlank) }
    }
    return ImportSheet(
        fileName = fileName,
        sheetName = "CSV",
        headers = headers.filter(String::isNotBlank),
        rows = rows,
        rawCsv = rawText,
        platformNotice = error.orEmpty(),
    )
}

private fun parseCsvRecordsAndroid(text: String): List<List<String>> {
    val records = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    fun finishCell() { row.add(cell.toString()); cell.setLength(0) }
    fun finishRow() { finishCell(); records.add(row); row = mutableListOf() }
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            ch == ',' && !quoted -> finishCell()
            (ch == '\n' || ch == '\r') && !quoted -> {
                if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                finishRow()
            }
            else -> cell.append(ch)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) finishRow()
    return records
}

private fun normalizeHeaderAndroid(value: String) = value.trim().lowercase()
    .replace(Regex("[^a-z0-9]+"), "_")
    .trim('_')
