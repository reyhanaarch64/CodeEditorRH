package rh.code.editor.util

/**
 * simple markdown -> html converter untuk mode baca.
 * support: heading, bold, italic, code, code block, list, link, hr, quote
 */
object MarkdownRenderer {

    fun toHtml(md: String, dark: Boolean = true): String {
        val bg = if (dark) "#0d0d0f" else "#ffffff"
        val fg = if (dark) "#e8e8ed" else "#1c1c1e"
        val codeBg = if (dark) "#1e1e22" else "#f2f2f7"
        val link = if (dark) "#64b5f6" else "#007aff"
        val border = if (dark) "#2c2c2e" else "#e5e5ea"

        val body = convert(md)
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
            <style>
            *{box-sizing:border-box;margin:0;padding:0}
            body{font-family:system-ui,-apple-system,sans-serif;background:$bg;color:$fg;
                 padding:16px;line-height:1.6;font-size:15px;word-wrap:break-word}
            h1,h2,h3,h4{margin:1.2em 0 0.5em;font-weight:600;line-height:1.3}
            h1{font-size:1.6em;border-bottom:1px solid $border;padding-bottom:6px}
            h2{font-size:1.35em}
            h3{font-size:1.15em}
            p{margin:0.7em 0}
            a{color:$link;text-decoration:none}
            code{background:$codeBg;padding:2px 6px;border-radius:4px;font-family:monospace;font-size:0.9em}
            pre{background:$codeBg;padding:12px;border-radius:8px;overflow-x:auto;margin:0.8em 0}
            pre code{background:none;padding:0}
            ul,ol{margin:0.6em 0 0.6em 1.4em}
            li{margin:0.25em 0}
            blockquote{border-left:3px solid $link;padding:4px 12px;margin:0.8em 0;opacity:0.9}
            hr{border:none;border-top:1px solid $border;margin:1.2em 0}
            img{max-width:100%;border-radius:8px}
            table{border-collapse:collapse;width:100%;margin:0.8em 0}
            th,td{border:1px solid $border;padding:6px 10px;text-align:left}
            th{background:$codeBg}
            </style>
            </head>
            <body>$body</body>
            </html>
        """.trimIndent()
    }

    private fun convert(src: String): String {
        val lines = src.replace("\r\n", "\n").lines()
        val out = StringBuilder()
        var i = 0
        var inCode = false
        val codeBuf = StringBuilder()

        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("```") -> {
                    if (!inCode) {
                        inCode = true
                        codeBuf.clear()
                    } else {
                        inCode = false
                        out.append("<pre><code>").append(esc(codeBuf.toString())).append("</code></pre>\n")
                    }
                    i++
                }
                inCode -> {
                    if (codeBuf.isNotEmpty()) codeBuf.append('\n')
                    codeBuf.append(line)
                    i++
                }
                line.startsWith("# ") -> {
                    out.append("<h1>").append(inline(line.removePrefix("# "))).append("</h1>\n"); i++
                }
                line.startsWith("## ") -> {
                    out.append("<h2>").append(inline(line.removePrefix("## "))).append("</h2>\n"); i++
                }
                line.startsWith("### ") -> {
                    out.append("<h3>").append(inline(line.removePrefix("### "))).append("</h3>\n"); i++
                }
                line.startsWith("#### ") -> {
                    out.append("<h4>").append(inline(line.removePrefix("#### "))).append("</h4>\n"); i++
                }
                line.trim() == "---" || line.trim() == "***" -> {
                    out.append("<hr/>\n"); i++
                }
                line.startsWith("> ") -> {
                    out.append("<blockquote>").append(inline(line.removePrefix("> "))).append("</blockquote>\n"); i++
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    out.append("<ul>\n")
                    while (i < lines.size && (lines[i].startsWith("- ") || lines[i].startsWith("* "))) {
                        out.append("<li>").append(inline(lines[i].substring(2))).append("</li>\n")
                        i++
                    }
                    out.append("</ul>\n")
                }
                line.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    out.append("<ol>\n")
                    while (i < lines.size && lines[i].matches(Regex("^\\d+\\.\\s+.*"))) {
                        val t = lines[i].replaceFirst(Regex("^\\d+\\.\\s+"), "")
                        out.append("<li>").append(inline(t)).append("</li>\n")
                        i++
                    }
                    out.append("</ol>\n")
                }
                line.isBlank() -> { out.append("\n"); i++ }
                else -> {
                    out.append("<p>").append(inline(line)).append("</p>\n"); i++
                }
            }
        }
        if (inCode) {
            out.append("<pre><code>").append(esc(codeBuf.toString())).append("</code></pre>\n")
        }
        return out.toString()
    }

    private fun inline(s: String): String {
        var t = esc(s)
        // code
        t = Regex("`([^`]+)`").replace(t) { "<code>${it.groupValues[1]}</code>" }
        // bold
        t = Regex("\\*\\*([^*]+)\\*\\*").replace(t) { "<strong>${it.groupValues[1]}</strong>" }
        t = Regex("__([^_]+)__").replace(t) { "<strong>${it.groupValues[1]}</strong>" }
        // italic
        t = Regex("\\*([^*]+)\\*").replace(t) { "<em>${it.groupValues[1]}</em>" }
        t = Regex("_([^_]+)_").replace(t) { "<em>${it.groupValues[1]}</em>" }
        // link
        t = Regex("\\[([^\\]]+)\\]\\(([^)]+)\\)").replace(t) {
            "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>"
        }
        return t
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
