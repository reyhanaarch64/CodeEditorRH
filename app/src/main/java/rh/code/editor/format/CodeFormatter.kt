package rh.code.editor.format

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.github.javaparser.StaticJavaParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

object CodeFormatter {
    suspend fun format(context: Context, language: String, source: String): String {
        require(source.length <= MAX_SOURCE_LENGTH) { "file terlalu besar untuk diformat (maksimum 1 MB)" }
        val normalized = language.lowercase()
        if (normalized == "java") {
            return withContext(Dispatchers.Default) {
                StaticJavaParser.parse(source).toString()
            }
        }
        require(normalized in SUPPORTED_WEB_LANGUAGES) { "bahasa ini belum didukung formatter" }
        return withTimeout(FORMAT_TIMEOUT_MS) {
            formatInWebView(context, normalized, source)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun formatInWebView(context: Context, language: String, source: String): String =
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                val webView = WebView(context)
                var finished = false

                fun finish(result: Result<String>) {
                    if (finished) return
                    finished = true
                    webView.removeJavascriptInterface("AndroidFormatter")
                    webView.stopLoading()
                    webView.destroy()
                    if (continuation.isActive) continuation.resume(result)
                }

                webView.settings.javaScriptEnabled = true
                webView.settings.allowFileAccess = false
                webView.settings.allowContentAccess = false
                webView.settings.domStorageEnabled = false
                webView.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onFormatted(value: String) {
                        webView.post { finish(Result.success(value)) }
                    }

                    @JavascriptInterface
                    fun onError(message: String) {
                        webView.post { finish(Result.failure(IllegalStateException(message))) }
                    }
                }, "AndroidFormatter")
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): android.webkit.WebResourceResponse =
                        assetLoader.shouldInterceptRequest(request.url)
                            ?: android.webkit.WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), null)

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ) = true

                    override fun onPageFinished(view: WebView, url: String) {
                        val languageArg = JSONObject.quote(language)
                        val sourceArg = JSONObject.quote(source)
                        view.evaluateJavascript("window.formatCode($languageArg,$sourceArg)", null)
                    }
                }
                continuation.invokeOnCancellation {
                    webView.post {
                        if (!finished) {
                            finished = true
                            webView.removeJavascriptInterface("AndroidFormatter")
                            webView.stopLoading()
                            webView.destroy()
                        }
                    }
                }
                webView.loadUrl("https://appassets.androidplatform.net/assets/formatter/index.html")
            }
        }.getOrThrow()

    private val SUPPORTED_WEB_LANGUAGES = setOf("javascript", "html", "css", "php", "python")
    private const val MAX_SOURCE_LENGTH = 1_000_000
    private const val FORMAT_TIMEOUT_MS = 60_000L
}
