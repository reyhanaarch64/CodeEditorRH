package rh.code.editor.syntax

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.io.BufferedReader
import java.io.InputStreamReader

data class SyntaxRule(
    val version: Int,
    val name: String,
    val extensions: List<String>,
    val keywords: Set<String>,
    val colors: Map<String, Color>,
    val rules: Map<String, String>
)

object SyntaxHighlighter {

    private val cache = mutableMapOf<String, SyntaxRule>()

    fun loadRules(context: Context) {
        if (cache.isNotEmpty()) return
        try {
            val assets = context.assets.list("syntaxHighlight") ?: return
            for (file in assets) {
                if (!file.endsWith(".sxh")) continue
                val rule = parseSxh(context, "syntaxHighlight/$file")
                if (rule != null) {
                    for (ext in rule.extensions) {
                        cache[ext.lowercase()] = rule
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseSxh(context: Context, path: String): SyntaxRule? {
        try {
            val input = context.assets.open(path)
            val reader = BufferedReader(InputStreamReader(input))
            var version = 1
            var name = "Unknown"
            val extensions = mutableListOf<String>()
            val keywords = mutableSetOf<String>()
            val colors = mutableMapOf<String, Color>()
            val rules = mutableMapOf<String, String>()
            var section = ""

            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEachLine

                when {
                    trimmed.startsWith("version:") -> {
                        version = trimmed.removePrefix("version:").trim().toIntOrNull() ?: 1
                    }
                    trimmed.startsWith("name:") -> {
                        name = trimmed.removePrefix("name:").trim()
                    }
                    trimmed.startsWith("extensions:") -> {
                        extensions.addAll(
                            trimmed.removePrefix("extensions:")
                                .split(",")
                                .map { it.trim().lowercase() }
                                .filter { it.isNotEmpty() }
                        )
                    }
                    trimmed.startsWith("keywords:") -> {
                        keywords.addAll(
                            trimmed.removePrefix("keywords:")
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                        )
                    }
                    trimmed.startsWith("colors:") -> {
                        section = "colors"
                    }
                    trimmed.startsWith("rules:") -> {
                        section = "rules"
                    }
                    section == "colors" && trimmed.contains(":") -> {
                        val parts = trimmed.split(":", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val colorStr = parts[1].trim()
                            colors[key] = parseColor(colorStr)
                        }
                    }
                    section == "rules" && trimmed.contains(":") -> {
                        val parts = trimmed.split(":", limit = 2)
                        if (parts.size == 2) {
                            rules[parts[0].trim()] = parts[1].trim()
                        }
                    }
                }
            }
            reader.close()
            return SyntaxRule(version, name, extensions, keywords, colors, rules)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun parseColor(hex: String): Color {
        return try {
            val clean = hex.removePrefix("#")
            val colorLong = clean.toLong(16)
            if (clean.length == 6) {
                Color(0xFF000000 or colorLong)
            } else {
                Color(colorLong)
            }
        } catch (e: Exception) {
            Color.White
        }
    }

    fun getRuleForExtension(ext: String): SyntaxRule? {
        return cache[ext.lowercase()]
    }

    fun highlight(text: String, extension: String): AnnotatedString {
        val rule = getRuleForExtension(extension) ?: return AnnotatedString(text)
        val defaultColor = rule.colors["default"] ?: Color.White
        val keywordColor = rule.colors["keyword"] ?: Color(0xFFFF7B72)
        val stringColor = rule.colors["string"] ?: Color(0xFFA5D6FF)
        val commentColor = rule.colors["comment"] ?: Color(0xFF8B949E)

        return buildAnnotatedString {
            val lines = text.split("\n")
            lines.forEachIndexed { index, line ->
                highlightLine(line, rule, keywordColor, stringColor, commentColor, defaultColor)
                if (index < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.highlightLine(
        line: String,
        rule: SyntaxRule,
        keywordColor: Color,
        stringColor: Color,
        commentColor: Color,
        defaultColor: Color
    ) {
        // simple tokenizer for version 1
        var i = 0
        val length = line.length

        while (i < length) {
            // check comment
            if (line.startsWith("//", i)) {
                withStyle(SpanStyle(color = commentColor)) {
                    append(line.substring(i))
                }
                return
            }
            if (line.startsWith("/*", i)) {
                val end = line.indexOf("*/", i + 2)
                if (end >= 0) {
                    withStyle(SpanStyle(color = commentColor)) {
                        append(line.substring(i, end + 2))
                    }
                    i = end + 2
                    continue
                } else {
                    withStyle(SpanStyle(color = commentColor)) {
                        append(line.substring(i))
                    }
                    return
                }
            }

            // check string
            if (line[i] == '"' || line[i] == '\'' || line[i] == '`') {
                val quote = line[i]
                var j = i + 1
                while (j < length && line[j] != quote) {
                    if (line[j] == '\\' && j + 1 < length) j++
                    j++
                }
                if (j < length) j++
                withStyle(SpanStyle(color = stringColor)) {
                    append(line.substring(i, j))
                }
                i = j
                continue
            }

            // check word (keyword)
            if (line[i].isLetter() || line[i] == '_') {
                var j = i + 1
                while (j < length && (line[j].isLetterOrDigit() || line[j] == '_')) {
                    j++
                }
                val word = line.substring(i, j)
                if (rule.keywords.contains(word)) {
                    withStyle(SpanStyle(color = keywordColor)) {
                        append(word)
                    }
                } else {
                    withStyle(SpanStyle(color = defaultColor)) {
                        append(word)
                    }
                }
                i = j
                continue
            }

            // default char
            withStyle(SpanStyle(color = defaultColor)) {
                append(line[i])
            }
            i++
        }
    }

    fun detectLanguage(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (cache.containsKey(ext)) ext else "plaintext"
    }
}
