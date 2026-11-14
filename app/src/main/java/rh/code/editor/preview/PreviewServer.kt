package rh.code.editor.preview

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File

class PreviewServer(
    private val htmlSnapshot: String,
    private val baseDir: String? = null,
    private val context: Context? = null
) : NanoHTTPD(0) {

    var actualPort: Int = 0
        private set

    override fun start() {
        super.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        actualPort = listeningPort
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        // serve eruda
        if (uri == "/__eruda.min.js" && context != null) {
            try {
                val bytes = context.assets.open("libs/eruda.min.js").readBytes()
                return newFixedLengthResponse(
                    Response.Status.OK,
                    "application/javascript",
                    ByteArrayInputStream(bytes),
                    bytes.size.toLong()
                )
            } catch (e: Exception) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "eruda not found")
            }
        }

        return if (uri == "/" || uri == "/index.html") {
            // inject eruda
            val injected = injectEruda(htmlSnapshot)
            newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                injected
            )
        } else {
            if (baseDir != null) {
                try {
                    val file = File(baseDir, uri.removePrefix("/"))
                    if (file.exists() && file.isFile) {
                        val mime = getMimeType(file.name)
                        val bytes = file.readBytes()
                        return newFixedLengthResponse(
                            Response.Status.OK,
                            mime,
                            ByteArrayInputStream(bytes),
                            bytes.size.toLong()
                        )
                    }
                } catch (_: Exception) {}
            }
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun injectEruda(html: String): String {
        val script = """
            <script src="/__eruda.min.js"></script>
            <script>eruda.init();</script>
        """.trimIndent()
        return if (html.contains("</body>", ignoreCase = true)) {
            html.replace(Regex("</body>", RegexOption.IGNORE_CASE), "$script</body>")
        } else {
            html + script
        }
    }

    private fun getMimeType(name: String): String {
        return when (name.substringAfterLast('.').lowercase()) {
            "css" -> "text/css"
            "js" -> "application/javascript"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "json" -> "application/json"
            "woff", "woff2" -> "font/woff"
            "ttf" -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
}
