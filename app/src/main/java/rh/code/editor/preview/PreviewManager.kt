package rh.code.editor.preview

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PreviewState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING
}

data class PreviewSession(
    val htmlSnapshot: String,
    val port: Int,
    val url: String,
    val baseDir: String? = null
)

class PreviewManager {

    private var server: PreviewServer? = null
    private var currentSession: PreviewSession? = null
    var state: PreviewState = PreviewState.IDLE
        private set

    suspend fun startPreview(html: String, baseDir: String? = null): PreviewSession? {
        return withContext(Dispatchers.IO) {
            if (state == PreviewState.RUNNING || state == PreviewState.STARTING) {
                stopPreview()
            }
            state = PreviewState.STARTING
            try {
                val newServer = PreviewServer(html, baseDir)
                newServer.start()
                val port = newServer.actualPort
                val url = "http://127.0.0.1:$port/"
                server = newServer
                val session = PreviewSession(html, port, url, baseDir)
                currentSession = session
                state = PreviewState.RUNNING
                session
            } catch (e: Exception) {
                Log.e("PreviewManager", "Failed to start server", e)
                state = PreviewState.IDLE
                null
            }
        }
    }

    suspend fun stopPreview() {
        withContext(Dispatchers.IO) {
            if (state == PreviewState.IDLE || state == PreviewState.STOPPING) return@withContext
            state = PreviewState.STOPPING
            try {
                server?.stop()
            } catch (e: Exception) {
                Log.e("PreviewManager", "Error stopping server", e)
            } finally {
                server = null
                currentSession = null
                state = PreviewState.IDLE
            }
        }
    }

    fun getCurrentSession(): PreviewSession? = currentSession
}
