package rh.code.editor.syntax

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern

data class MatchRule(
    val name: String,
    val pattern: Pattern,
    val color: Color
)

data class RegionRule(
    val start: String,
    val end: String,
    val lang: String
)

data class SyntaxRuleV2(
    val name: String,
    val extensions: List<String>,
    val colors: Map<String, Color>,
    val keywords: Set<String>,
    val matches: List<MatchRule>,
    val regions: List<RegionRule>
)

object SyntaxHighlighter {

    private val cache = mutableMapOf<String, SyntaxRuleV2>()
    private var loaded = false

    fun loadRules(context: Context) {
        cache.clear()
        loaded = false
        try {
            val files = context.assets.list("syntaxHighlight") ?: return
            for (file in files) {
                if (!file.endsWith(".sxh")) continue
                val rule = parseV2(context, "syntaxHighlight/$file")
                if (rule != null) {
                    for (ext in rule.extensions) {
                        cache[ext.lowercase()] = rule
                    }
                    cache[rule.name.lowercase()] = rule
                }
            }
            loaded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseColor(hex: String): Color {
        return try {
            val clean = hex.trim().removePrefix("#")
            val v = clean.toLong(16)
            if (clean.length == 6) Color(0xFF000000 or v) else Color(v)
        } catch (_: Exception) {
            Color.White
        }
    }

    /** Format v2 - custom language, bukan yaml */
    private fun parseV2(context: Context, path: String): SyntaxRuleV2? {
        try {
            val text = context.assets.open(path).bufferedReader().use { it.readText() }
            if (!text.contains("lang ") && !text.contains("version 2")) {
                // skip file v1 lama
                return null
            }

            var name = "Unknown"
            val extensions = mutableListOf<String>()
            val colors = mutableMapOf<String, Color>()
            val keywords = mutableSetOf<String>()
            val matches = mutableListOf<MatchRule>()
            val regions = mutableListOf<RegionRule>()

            val lines = text.lines()
            var i = 0
            while (i < lines.size) {
                val raw = lines[i].trim()
                i++
                if (raw.isEmpty() || raw.startsWith("//") || raw.startsWith("#")) continue

                when {
                    raw.startsWith("lang ") -> {
                        // lang JavaScript {
                        name = raw.removePrefix("lang ").substringBefore("{").trim()
                    }
                    raw.startsWith("ext ") -> {
                        extensions.addAll(
                            raw.removePrefix("ext ").split(Regex("\\s+")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                        )
                    }
                    raw.startsWith("color ") -> {
                        // color keyword #FF7B72
                        val parts = raw.removePrefix("color ").trim().split(Regex("\\s+"))
                        if (parts.size >= 2) colors[parts[0]] = parseColor(parts[1])
                    }
                    raw.startsWith("keyword ") -> {
                        keywords.addAll(
                            raw.removePrefix("keyword ").split(Regex("\\s+|,")).map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    }
                    raw.startsWith("match ") -> {
                        // match string /regex/
                        val rest = raw.removePrefix("match ").trim()
                        val namePart = rest.substringBefore(" ").trim()
                        val regexPart = rest.substringAfter(" ").trim()
                        if (regexPart.startsWith("/") && regexPart.length > 2) {
                            val body = regexPart.removePrefix("/").let {
                                if (it.endsWith("/")) it.dropLast(1) else it
                            }
                            try {
                                val color = colors[namePart] ?: colors["default"] ?: Color.White
                                matches.add(MatchRule(namePart, Pattern.compile(body), color))
                            } catch (_: Exception) {}
                        }
                    }
                    raw.startsWith("region ") -> {
                        // region script <script> </script> JavaScript
                        val rest = raw.removePrefix("region ").trim()
                        val tokens = tokenizeRegion(rest)
                        if (tokens.size >= 4) {
                            regions.add(RegionRule(tokens[1], tokens[2], tokens[3]))
                        }
                    }
                }
            }

            if (extensions.isEmpty() && name != "Unknown") {
                extensions.add(name.lowercase())
            }
            return SyntaxRuleV2(name, extensions, colors, keywords, matches, regions)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun tokenizeRegion(s: String): List<String> {
        // region name <start> <end> LangName
        val result = mutableListOf<String>()
        var i = 0
        while (i < s.length) {
            while (i < s.length && s[i].isWhitespace()) i++
            if (i >= s.length) break
            if (s[i] == '<') {
                val end = s.indexOf('>', i)
                if (end > i) {
                    result.add(s.substring(i, end + 1))
                    i = end + 1
                } else break
            } else {
                val start = i
                while (i < s.length && !s[i].isWhitespace()) i++
                result.add(s.substring(start, i))
            }
        }
        return result
    }

    fun getRuleForExtension(ext: String): SyntaxRuleV2? = cache[ext.lowercase()]

    fun detectLanguage(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (cache.containsKey(ext)) ext else "plaintext"
    }

    fun highlight(text: String, extension: String, isDark: Boolean = true): AnnotatedString {
        val fallback = if (isDark) Color(0xFFE8E8ED) else Color(0xFF1C1C1E)
        val rule = getRuleForExtension(extension)
            ?: getRuleForExtension(extension.substringAfterLast('.'))

        if (rule == null) {
            return buildAnnotatedString {
                withStyle(SpanStyle(color = fallback)) { append(text) }
            }
        }

        // handle regions dulu (html script/style dll)
        if (rule.regions.isNotEmpty()) {
            return highlightWithRegions(text, rule, isDark, fallback)
        }

        return highlightPlain(text, rule, fallback)
    }

    private fun highlightWithRegions(
        text: String,
        rule: SyntaxRuleV2,
        isDark: Boolean,
        fallback: Color
    ): AnnotatedString {
        return buildAnnotatedString {
            var pos = 0
            val lower = text

            while (pos < text.length) {
                var nearest: RegionRule? = null
                var nearestIdx = Int.MAX_VALUE

                for (r in rule.regions) {
                    val idx = lower.indexOf(r.start, pos, ignoreCase = true)
                    if (idx >= 0 && idx < nearestIdx) {
                        nearestIdx = idx
                        nearest = r
                    }
                }

                if (nearest == null) {
                    // sisa pakai rule utama
                    appendHighlighted(text.substring(pos), rule, fallback)
                    break
                }

                // sebelum region
                if (nearestIdx > pos) {
                    appendHighlighted(text.substring(pos, nearestIdx), rule, fallback)
                }

                val endTag = nearest.end
                val contentStart = nearestIdx + nearest.start.length
                val endIdx = lower.indexOf(endTag, contentStart, ignoreCase = true)

                if (endIdx < 0) {
                    // tag penutup tidak ketemu
                    appendHighlighted(text.substring(nearestIdx), rule, fallback)
                    break
                }

                // tag buka
                withStyle(SpanStyle(color = rule.colors["tag"] ?: rule.colors["keyword"] ?: fallback)) {
                    append(text.substring(nearestIdx, contentStart))
                }

                // isi region → bahasa lain
                val inner = text.substring(contentStart, endIdx)
                val innerRule = getRuleForExtension(nearest.lang.lowercase())
                    ?: cache[nearest.lang.lowercase()]
                if (innerRule != null) {
                    appendHighlighted(inner, innerRule, fallback)
                } else {
                    withStyle(SpanStyle(color = fallback)) { append(inner) }
                }

                // tag tutup
                val afterEnd = endIdx + endTag.length
                withStyle(SpanStyle(color = rule.colors["tag"] ?: rule.colors["keyword"] ?: fallback)) {
                    append(text.substring(endIdx, afterEnd))
                }
                pos = afterEnd
            }
        }
    }

    private fun AnnotatedString.Builder.appendHighlighted(text: String, rule: SyntaxRuleV2, fallback: Color) {
        val defaultColor = rule.colors["default"] ?: fallback
        val keywordColor = rule.colors["keyword"] ?: Color(0xFFFF7B72)
        val spans = Array(text.length) { defaultColor }

        // regex matches
        for (m in rule.matches) {
            val matcher = m.pattern.matcher(text)
            while (matcher.find()) {
                val s = matcher.start()
                val e = matcher.end()
                for (i in s until e) {
                    if (i < spans.size) spans[i] = m.color
                }
            }
        }

        // keywords (word boundary)
        if (rule.keywords.isNotEmpty()) {
            val word = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
            val matcher = word.matcher(text)
            while (matcher.find()) {
                val w = matcher.group()
                if (rule.keywords.contains(w)) {
                    for (i in matcher.start() until matcher.end()) {
                        if (i < spans.size && spans[i] == defaultColor) {
                            spans[i] = keywordColor
                        }
                    }
                }
            }
        }

        var i = 0
        while (i < text.length) {
            val c = spans[i]
            var j = i + 1
            while (j < text.length && spans[j] == c) j++
            withStyle(SpanStyle(color = c)) {
                append(text.substring(i, j))
            }
            i = j
        }
    }

    private fun highlightPlain(text: String, rule: SyntaxRuleV2, fallback: Color): AnnotatedString {
        return buildAnnotatedString {
            appendHighlighted(text, rule, fallback)
        }
    }
}
