package com.mitenko.repkit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Spec revision 24: all user-facing text comes from resources, and `domain/` returns typed values.
 * This scans the Kotlin sources under `ui/` and `domain/` for string literals that contain a word
 * (three letters or more, after `$` templates are removed) and fails on any that isn't on a line
 * the rules below exempt: test tags, logs, exception and precondition messages, saved-state keys,
 * ViewModel keys and log tags. It is deliberately simple; an exemption is a line pattern, not a
 * file list, except for the two files whose literals are routes and date patterns.
 */
class HardCodedTextGuardTest {
    private val root = File("src/main/kotlin/com/mitenko/repkit")

    @Test
    fun `no user-facing English literal in ui or domain`() {
        assertTrue("Sources not found at ${root.absolutePath}", root.isDirectory)
        val offenders = listOf("ui", "domain").flatMap { dir ->
            File(root, dir).walk().filter { it.isFile && it.extension == "kt" }.flatMap(::offendersIn).toList()
        }
        assertEquals("Move these strings to strings.xml (or return a typed value from domain/):", emptyList<String>(), offenders)
    }

    @Test
    fun `the scanner finds literals and skips comments and templates`() {
        val src = """
            // "A comment"
            /* "Block comment" */
            val a = "Hello there"
            val b = "${'$'}{x.y}/${'$'}z"
            Modifier.testTag("tag_name")
            val c = "increased" // "trailing"
        """.trimIndent()
        assertEquals(
            listOf(3 to "Hello there", 5 to "tag_name", 6 to "increased"),
            literals(src).filter { (_, s) -> WORD.containsMatchIn(s) },
        )
    }

    private fun offendersIn(file: File): List<String> {
        val relative = file.relativeTo(root).invariantSeparatorsPath
        if (relative in EXEMPT_FILES) return emptyList()
        val lines = file.readLines()
        return literals(file.readText()).mapNotNull { (line, text) ->
            val code = lines[line - 1]
            if (WORD.containsMatchIn(text) && !EXEMPT_LINE.containsMatchIn(code)) "$relative:$line \"$text\"" else null
        }
    }

    /**
     * Each string literal's text with its `$` templates removed, with its 1-based line. Skips line
     * and block comments; a `${…}` template is scanned as code, so a literal nested inside one is
     * reported on its own.
     */
    private fun literals(src: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var i = 0
        var line = 1
        // Each open string on the stack collects its text; template depth counts braces inside ${…}.
        val strings = ArrayDeque<StringBuilder>()
        val startLines = ArrayDeque<Int>()
        val braceDepth = ArrayDeque<Int>()
        fun inString() = strings.isNotEmpty() && braceDepth.last() == 0
        while (i < src.length) {
            val c = src[i]
            if (c == '\n') line++
            if (inString()) {
                when {
                    c == '\\' -> i++
                    c == '"' -> {
                        out += startLines.removeLast() to strings.removeLast().toString()
                        braceDepth.removeLast()
                    }
                    c == '$' && src.getOrNull(i + 1) == '{' -> {
                        braceDepth[braceDepth.lastIndex] = 1
                        i++
                    }
                    c == '$' && src.getOrNull(i + 1)?.isLetter() == true -> {
                        while (src.getOrNull(i + 1)?.isLetterOrDigit() == true) i++
                    }
                    else -> strings.last().append(c)
                }
            } else {
                when {
                    // Stop before the newline, so the top of the loop counts it.
                    src.startsWith("//", i) -> i = src.indexOf('\n', i).let { if (it < 0) src.length else it } - 1
                    src.startsWith("/*", i) -> {
                        val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 1 }
                        line += src.substring(i, end).count { it == '\n' }
                        i = end
                    }
                    src.startsWith("\"\"\"", i) -> {
                        val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it }
                        val body = src.substring(i + 3, end)
                        out += line to body.replace(RAW_TEMPLATE, "")
                        line += body.count { it == '\n' }
                        i = end + 2
                    }
                    c == '\'' -> i =src.indexOf('\'', if (src.getOrNull(i + 1) == '\\') i + 3 else i + 2)
                    c == '"' -> {
                        strings.addLast(StringBuilder())
                        startLines.addLast(line)
                        braceDepth.addLast(0)
                    }
                    strings.isNotEmpty() && c == '{' -> braceDepth[braceDepth.lastIndex]++
                    strings.isNotEmpty() && c == '}' -> braceDepth[braceDepth.lastIndex]--
                }
            }
            i++
        }
        return out
    }

    private companion object {
        val WORD = Regex("[A-Za-z]{3,}")
        val RAW_TEMPLATE = Regex("""\$\{[^}]*}|\$\w+""")

        val EXEMPT_LINE = Regex(
            listOf(
                """testTag""", """\btag = """, """Log\.[a-z]""", """\brequire""", """\bcheck(NotNull)?\b""", """\berror\(""",
                """Exception\(""", """const val \w*(TAG|KEY|ARG)\b""", """hiltViewModel\(key""", """@(Suppress|Deprecated|OptIn)""",
            ).joinToString("|"),
        )

        /** Navigation routes and date patterns are code, not text. */
        val EXEMPT_FILES = setOf("ui/navigation/Routes.kt", "ui/common/DateFormats.kt")
    }
}
