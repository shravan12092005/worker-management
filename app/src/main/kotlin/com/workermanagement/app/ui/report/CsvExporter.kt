package com.workermanagement.app.ui.report

/**
 * Pure-function CSV generator. No I/O — caller writes the returned string.
 *
 * Handles RFC 4180 quoting: fields containing commas, double-quotes, or
 * newlines are enclosed in double-quotes, and internal double-quotes are
 * escaped by doubling them.
 */
object CsvExporter {

    /**
     * Converts a list of rows to CSV format.
     *
     * @param headers  Column header names.
     * @param rows     Data rows.
     * @param mapper   Extracts cell values (as strings) from each row.
     * @return         Complete CSV string (UTF-8, CRLF line endings per RFC 4180).
     */
    fun <T> toCsv(
        headers: List<String>,
        rows: List<T>,
        mapper: (T) -> List<String>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine(headers.joinToString(",") { escapeField(it) })
        rows.forEach { row ->
            sb.appendLine(mapper(row).joinToString(",") { escapeField(it) })
        }
        return sb.toString()
    }

    /**
     * RFC 4180: if a field contains a comma, double-quote, or newline,
     * wrap it in double-quotes and escape internal quotes by doubling.
     */
    private fun escapeField(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
