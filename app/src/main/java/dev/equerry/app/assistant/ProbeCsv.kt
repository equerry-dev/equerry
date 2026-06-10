package dev.equerry.app.assistant

/**
 * Renders probe records as RFC-4180 CSV for the share-sheet export (export_format:
 * CSV via share-sheet). Fixed column order; fields containing a comma, double-quote,
 * or newline are quoted, and embedded double-quotes are doubled.
 */
object ProbeCsv {

    private val HEADER = listOf(
        "package", "timestamp", "structure", "nodes", "screenshot", "width", "height",
    )

    fun toCsv(records: List<ProbeRecord>): String {
        val rows = ArrayList<String>(records.size + 1)
        rows.add(HEADER.joinToString(",") { escape(it) })
        for (r in records) {
            rows.add(
                listOf(
                    r.packageName,
                    r.timestamp.toString(),
                    r.structureProvided.toString(),
                    r.nodeCount.toString(),
                    r.screenshotArrived.toString(),
                    r.screenshotWidth?.toString() ?: "",
                    r.screenshotHeight?.toString() ?: "",
                ).joinToString(",") { escape(it) },
            )
        }
        return rows.joinToString(CRLF)
    }

    private fun escape(field: String): String {
        val needsQuote = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    private const val CRLF = "\r\n"
}
