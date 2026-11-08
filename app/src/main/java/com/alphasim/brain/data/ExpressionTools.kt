package com.alphasim.brain.data

/** Parsing and lightweight static checks for FASTEXPR alpha lists. */
object ExpressionTools {

    data class ParseResult(
        val expressions: List<String>,
        val duplicates: Int,
        val comments: Int,
        val warnings: List<String>,
    )

    /** One expression per line. Blank lines and lines starting with # or // are skipped. */
    fun parse(text: String, dedupe: Boolean = true, existing: Collection<String> = emptyList()): ParseResult {
        val out = mutableListOf<String>()
        val seen = HashSet<String>()
        existing.forEach { seen.add(normalize(it)) }
        var duplicates = 0
        var comments = 0
        val warnings = mutableListOf<String>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim().trimStart('﻿')
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("#") || line.startsWith("//")) {
                comments++
                return@forEachIndexed
            }
            if (dedupe && !seen.add(normalize(line))) {
                duplicates++
                return@forEachIndexed
            }
            validate(line)?.let { warnings += "Line ${index + 1}: $it" }
            out += line
        }
        return ParseResult(out, duplicates, comments, warnings)
    }

    fun normalize(expression: String): String = expression.filterNot { it.isWhitespace() }

    /** Returns a human readable problem, or null if nothing obvious is wrong. */
    fun validate(expression: String): String? {
        var depth = 0
        var inQuote = false
        for (c in expression) {
            if (c == '"') inQuote = !inQuote
            if (inQuote) continue
            when (c) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth < 0) return "unexpected ')'"
                }
            }
        }
        if (inQuote) return "unclosed quote"
        if (depth > 0) return "missing $depth closing ')'"
        if (expression.endsWith(",") || expression.endsWith("(")) return "expression looks truncated"
        return null
    }

    data class Tokens(val operators: List<String>, val fields: List<String>)

    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val keywords = setOf("true", "false", "nan", "NaN", "inf", "and", "or", "not")

    /** Splits an expression into operator names (identifiers followed by '(') and data fields. */
    fun tokens(expression: String): Tokens {
        val stripped = expression.replace(Regex("\"[^\"]*\""), "\"\"")
        val ops = LinkedHashSet<String>()
        val fields = LinkedHashSet<String>()
        for (m in identifier.findAll(stripped)) {
            val name = m.value
            val before = stripped.getOrNull(m.range.first - 1)
            if (before != null && (before.isDigit() || before == '.')) continue // exponent like 1e5
            var i = m.range.last + 1
            while (i < stripped.length && stripped[i] == ' ') i++
            val next = stripped.getOrNull(i)
            val nextNext = stripped.getOrNull(i + 1)
            when {
                next == '(' -> ops += name
                next == '=' && nextNext != '=' -> Unit // keyword argument such as range= or std=
                name in keywords -> Unit
                else -> fields += name
            }
        }
        return Tokens(ops.toList(), fields.toList())
    }
}
