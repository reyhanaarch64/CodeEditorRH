package rh.code.editor.preview

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream

class PreviewServer(
    private val htmlSnapshot: String,
    private val baseDir: String? = null
) : NanoHTTPD(0) { // port 0 = OS pilih

    var actualPort: Int = 0
        private set

    override fun start() {
        super.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        actualPort = listeningPort
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        return if (uri == "/" || uri == "/index.html") {
            newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                htmlSnapshot
            )
        } else {
            // try relative asset if baseDir provided
            if (baseDir != null) {
                try {
                    val file = java.io.File(baseDir, uri.removePrefix("/"))
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
                } catch (e: Exception) {
                    // fall through
                }
            }
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "Not Found"
            )
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
