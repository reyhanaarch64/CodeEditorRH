package rh.code.editor.syntax

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import java.util.regex.Pattern

data class MatchRule(
    val name: String,
    val pattern: Pattern,
    val color: Color
)

data class RegionRule(
    val start: String,   // contoh: <script  (boleh tanpa > biar support atribut)
    val end: String,     // contoh: </script>
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
                val rules = parseFile(context, "syntaxHighlight/$file")
                for (rule in rules) {
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

    /** parser versi 3 — brace based, 1 file bisa banyak lang */
    private fun parseFile(context: Context, path: String): List<SyntaxRuleV2> {
        val result = mutableListOf<SyntaxRuleV2>()
        try {
            val text = context.assets.open(path).bufferedReader().use { it.readText() }
            val tokens = tokenize(text)
            var i = 0

            while (i < tokens.size) {
                if (tokens[i] == "lang" && i + 1 < tokens.size) {
                    val name = tokens[i + 1]
                    i += 2
                    if (i < tokens.size && tokens[i] == "{") {
                        i++
                        val (rule, next) = parseLangBlock(name, tokens, i)
                        if (rule != null) result.add(rule)
                        i = next
                    }
                } else {
                    i++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun tokenize(src: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val s = src
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c == '/' && i + 1 < s.length && s[i + 1] == '/' -> {
                    // skip line comment
                    while (i < s.length && s[i] != '\n') i++
                }
                c == '/' && i + 1 < s.length && s[i + 1] == '*' -> {
                    // skip block comment
                    i += 2
                    while (i + 1 < s.length && !(s[i] == '*' && s[i + 1] == '/')) i++
                    i += 2
                }
                c == '{' || c == '}' -> {
                    out.add(c.toString())
                    i++
                }
                c == '/' -> {
                    // regex literal /.../
                    val start = i
                    i++
                    while (i < s.length) {
                        if (s[i] == '\\' && i + 1 < s.length) {
                            i += 2
                            continue
                        }
                        if (s[i] == '/') break
                        i++
                    }
                    if (i < s.length) i++ // consume closing /
                    out.add(s.substring(start, i))
                }
                c == '"' || c == '\'' -> {
                    val quote = c
                    val start = i
                    i++
                    while (i < s.length && s[i] != quote) {
                        if (s[i] == '\\' && i + 1 < s.length) i++
                        i++
                    }
                    if (i < s.length) i++
                    out.add(s.substring(start, i))
                }
                else -> {
                    val start = i
                    while (i < s.length && !s[i].isWhitespace() && s[i] != '{' && s[i] != '}' && s[i] != '/') {
                        i++
                    }
                    if (i > start) out.add(s.substring(start, i))
                }
            }
        }
        return out
    }

    private fun parseLangBlock(name: String, tokens: List<String>, start: Int): Pair<SyntaxRuleV2?, Int> {
        val extensions = mutableListOf<String>()
        val colors = mutableMapOf<String, Color>()
        val keywords = mutableSetOf<String>()
        val matches = mutableListOf<MatchRule>()
        val regions = mutableListOf<RegionRule>()
        var i = start

        while (i < tokens.size && tokens[i] != "}") {
            when (tokens[i]) {
                "ext" -> {
                    i++
                    while (i < tokens.size && tokens[i] != "}" && tokens[i] != "color" &&
                        tokens[i] != "keyword" && tokens[i] != "match" && tokens[i] != "region" &&
                        tokens[i] != "ext" && tokens[i] != "lang") {
                        extensions.add(tokens[i].lowercase())
                        i++
                    }
                }
                "color" -> {
                    // color name #hex
                    if (i + 2 < tokens.size) {
                        colors[tokens[i + 1]] = parseColor(tokens[i + 2])
                        i += 3
                    } else i++
                }
                "keyword" -> {
                    i++
                    if (i < tokens.size && tokens[i] == "{") {
                        i++
                        while (i < tokens.size && tokens[i] != "}") {
                            // buang koma kalau ada
                            val w = tokens[i].trim().removeSuffix(",")
                            if (w.isNotEmpty()) keywords.add(w)
                            i++
                        }
                        if (i < tokens.size && tokens[i] == "}") i++
                    } else {
                        // fallback: keyword satu baris
                        while (i < tokens.size && tokens[i] != "}" && tokens[i] != "color" &&
                            tokens[i] != "match" && tokens[i] != "region" && tokens[i] != "keyword" &&
                            tokens[i] != "ext" && tokens[i] != "lang") {
                            val w = tokens[i].trim().removeSuffix(",")
                            if (w.isNotEmpty()) keywords.add(w)
                            i++
                        }
                    }
                }
                "match" -> {
                    // match name /regex/
                    if (i + 2 < tokens.size) {
                        val mName = tokens[i + 1]
                        val regexTok = tokens[i + 2]
                        if (regexTok.startsWith("/") && regexTok.endsWith("/") && regexTok.length > 2) {
                            val body = regexTok.substring(1, regexTok.length - 1)
                            try {
                                val color = colors[mName] ?: colors["default"] ?: Color.White
                                matches.add(MatchRule(mName, Pattern.compile(body), color))
                            } catch (_: Exception) {}
                        }
                        i += 3
                    } else i++
                }
                "region" -> {
                    i++
                    if (i < tokens.size && tokens[i] == "{") {
                        i++
                        var rStart = ""
                        var rEnd = ""
                        var rLang = ""
                        while (i < tokens.size && tokens[i] != "}") {
                            when (tokens[i]) {
                                "start" -> {
                                    if (i + 1 < tokens.size) {
                                        rStart = tokens[i + 1]
                                        i += 2
                                    } else i++
                                }
                                "end" -> {
                                    if (i + 1 < tokens.size) {
                                        rEnd = tokens[i + 1]
                                        i += 2
                                    } else i++
                                }
                                "lang" -> {
                                    if (i + 1 < tokens.size) {
                                        rLang = tokens[i + 1]
                                        i += 2
                                    } else i++
                                }
                                else -> i++
                            }
                        }
                        if (rStart.isNotEmpty() && rEnd.isNotEmpty() && rLang.isNotEmpty()) {
                            regions.add(RegionRule(rStart, rEnd, rLang))
                        }
                        if (i < tokens.size && tokens[i] == "}") i++
                    } else {
                        // fallback format lama: region name <start> <end> Lang
                        // skip
                        i++
                    }
                }
                else -> i++
            }
        }
        if (i < tokens.size && tokens[i] == "}") i++

        if (extensions.isEmpty() && name.isNotEmpty()) {
            extensions.add(name.lowercase())
        }
        return SyntaxRuleV2(name, extensions, colors, keywords, matches, regions) to i
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
                    appendHighlighted(text.substring(pos), rule, fallback)
                    break
                }

                if (nearestIdx > pos) {
                    appendHighlighted(text.substring(pos, nearestIdx), rule, fallback)
                }

                // cari penutup tag buka (support atribut <script type=...>)
                var contentStart = nearestIdx + nearest.start.length
                val gt = text.indexOf('>', contentStart)
                if (gt >= 0) contentStart = gt + 1

                val endIdx = lower.indexOf(nearest.end, contentStart, ignoreCase = true)

                if (endIdx < 0) {
                    appendHighlighted(text.substring(nearestIdx), rule, fallback)
                    break
                }

                // tag buka (termasuk atribut)
                withStyle(SpanStyle(color = rule.colors["tag"] ?: rule.colors["keyword"] ?: fallback)) {
                    append(text.substring(nearestIdx, contentStart))
                }

                // isi region
                val inner = text.substring(contentStart, endIdx)
                val innerRule = getRuleForExtension(nearest.lang.lowercase())
                    ?: cache[nearest.lang.lowercase()]
                if (innerRule != null) {
                    appendHighlighted(inner, innerRule, fallback)
                } else {
                    withStyle(SpanStyle(color = fallback)) { append(inner) }
                }

                // tag tutup
                val afterEnd = endIdx + nearest.end.length
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

        // regex matches dulu (string, comment, number dll)
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

        // keywords hanya di posisi yang masih default (jadi gak nimpah string/comment)
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
