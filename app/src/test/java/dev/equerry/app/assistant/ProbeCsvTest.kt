package dev.equerry.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeCsvTest {

    private fun record(
        pkg: String = "com.example.app",
        nodeCount: Int = 5,
        arrived: Boolean = true,
        blocked: Boolean = false,
        width: Int? = 1080,
        height: Int? = 2400,
    ) = ProbeRecord(
        packageName = pkg,
        timestamp = 42L,
        structureProvided = true,
        nodeCount = nodeCount,
        screenshotArrived = arrived,
        screenshotBlocked = blocked,
        screenshotWidth = width,
        screenshotHeight = height,
    )

    // Minimal RFC-4180 line parser (no embedded newlines in test data).
    private fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    @Test
    fun header_then_one_row_per_record() {
        val lines = ProbeCsv.toCsv(listOf(record(), record())).split("\r\n")
        assertEquals(3, lines.size)
        assertEquals(
            listOf("package", "timestamp", "structure", "nodes", "screenshot", "width", "height"),
            parseLine(lines[0]),
        )
    }

    @Test
    fun comma_and_quote_fields_are_escaped_and_round_trip() {
        val pkg = "a,b\"c"
        val line = ProbeCsv.toCsv(listOf(record(pkg = pkg))).split("\r\n")[1]
        // The cell is quoted and the inner double-quote is doubled: "a,b""c"
        assertTrue(line.startsWith("\"a,b\"\"c\","))
        // Authoritative check: parsing the line recovers the original package string.
        assertEquals(pkg, parseLine(line)[0])
    }

    @Test
    fun blocked_screenshot_has_false_flag_and_empty_dimensions() {
        val line = ProbeCsv.toCsv(
            listOf(record(arrived = false, blocked = true, width = null, height = null)),
        ).split("\r\n")[1]
        val cells = parseLine(line)
        assertEquals("false", cells[4]) // screenshot
        assertEquals("", cells[5])      // width
        assertEquals("", cells[6])      // height
    }

    @Test
    fun empty_list_is_header_only_with_no_trailing_blank() {
        val csv = ProbeCsv.toCsv(emptyList())
        assertEquals("package,timestamp,structure,nodes,screenshot,width,height", csv)
    }
}
