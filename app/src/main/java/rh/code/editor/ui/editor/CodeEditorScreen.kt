package rh.code.editor.ui.editor

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import rh.code.editor.data.Document
import rh.code.editor.syntax.SyntaxHighlighter
import rh.code.editor.ui.theme.*
import rh.code.editor.util.MarkdownRenderer

@Composable
fun CodeEditorScreen(
    document: Document,
    onContentChange: (String) -> Unit,
    onCursorChange: (line: Int, col: Int) -> Unit,
    insertSymbol: String? = null,
    onSymbolInserted: () -> Unit = {},
    isDark: Boolean = true,
    wallpaperUri: android.net.Uri? = null,
    fontSizeSp: Float = 14f,
    onFontSizeChange: (Float) -> Unit = {},
    readOnly: Boolean = false,
    wordWrap: Boolean = false,
    showLineNumbers: Boolean = true,
    autoPair: Boolean = true,
    lazyHighlight: Boolean = true,
    limitHugeFiles: Boolean = true,
    highlightQuery: String = "",
    highlightRange: IntRange? = null
) {
    val isMarkdown = document.name.endsWith(".md", true) ||
        document.name.endsWith(".markdown", true) ||
        document.language == "markdown"

    // mode baca markdown → full render html
    if (readOnly && isMarkdown) {
        MarkdownReadView(content = document.content, isDark = isDark)
        return
    }

    val contentLen = document.content.length
    val isHuge = limitHugeFiles && contentLen > 1_000_000
    // untuk file sangat besar, potong tampilan edit biar tidak OOM
    val displayContent = if (isHuge && !readOnly) {
        document.content.take(800_000) + "\n\n/* ... file terlalu besar, ditampilkan sebagian ... */"
    } else document.content

    val scrollState = rememberScrollState()
    var textFieldValue by remember(document.id) {
        mutableStateOf(TextFieldValue(displayContent))
    }

    LaunchedEffect(document.content, isHuge) {
        val target = if (isHuge && !readOnly) displayContent else document.content
        if (target != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = target)
        }
    }

    LaunchedEffect(insertSymbol) {
        if (!insertSymbol.isNullOrEmpty() && !readOnly) {
            val tf = textFieldValue
            val start = tf.selection.min
            val newText = tf.text.substring(0, start) + insertSymbol + tf.text.substring(tf.selection.max)
            val newCursor = start + insertSymbol.length
            textFieldValue = TextFieldValue(text = newText, selection = TextRange(newCursor))
            onContentChange(newText)
            onSymbolInserted()
        }
    }

    LaunchedEffect(textFieldValue.selection) {
        val pos = textFieldValue.selection.start
        val textBefore = textFieldValue.text.substring(0, pos.coerceAtMost(textFieldValue.text.length))
        val line = textBefore.count { it == '\n' } + 1
        val lastNewLine = textBefore.lastIndexOf('\n')
        val col = if (lastNewLine == -1) pos + 1 else pos - lastNewLine
        onCursorChange(line, col)
    }

    // highlight: untuk file besar + lazy → hanya proses potongan sekitar cursor / awal
    val highlighted = remember(textFieldValue.text, document.language, isDark, lazyHighlight, contentLen) {
        val text = textFieldValue.text
        if (lazyHighlight && text.length > 80_000) {
            // highlight hanya ~60k karakter pertama + tandai sisanya plain
            val head = text.take(60_000)
            val rest = text.drop(60_000)
            val headAnnotated = SyntaxHighlighter.highlight(head, document.language, isDark)
            androidx.compose.ui.text.buildAnnotatedString {
                append(headAnnotated)
                if (rest.isNotEmpty()) {
                    withStyle(androidx.compose.ui.text.SpanStyle(color = if (isDark) Color(0xFFE8E8ED) else Color(0xFF1C1C1E))) {
                        append(rest)
                    }
                }
            }
        } else {
            SyntaxHighlighter.highlight(text, document.language, isDark)
        }
    }

    val lineCount = remember(textFieldValue.text) {
        textFieldValue.text.count { it == '\n' } + 1
    }

    val fs = fontSizeSp.sp
    val lh = (fontSizeSp * 1.55f).sp

    fun handleValueChange(newValue: TextFieldValue) {
        if (readOnly) return
        val oldText = textFieldValue.text
        val newText = newValue.text
        val cursor = newValue.selection.start

        if (autoPair && newText.length == oldText.length + 1 && cursor > 0) {
            val inserted = newText.getOrNull(cursor - 1)
            if (inserted == '"' || inserted == '\'' || inserted == '`') {
                val nextChar = newText.getOrNull(cursor)
                if (nextChar != inserted) {
                    val paired = newText.substring(0, cursor) + inserted + newText.substring(cursor)
                    textFieldValue = TextFieldValue(text = paired, selection = TextRange(cursor))
                    onContentChange(paired)
                    return
                }
            }
            val pairs = mapOf('(' to ')', '[' to ']', '{' to '}')
            if (inserted != null && pairs.containsKey(inserted)) {
                val close = pairs[inserted]!!
                val nextChar = newText.getOrNull(cursor)
                if (nextChar != close) {
                    val paired = newText.substring(0, cursor) + close + newText.substring(cursor)
                    textFieldValue = TextFieldValue(text = paired, selection = TextRange(cursor))
                    onContentChange(paired)
                    return
                }
            }
        }

        textFieldValue = newValue
        onContentChange(newText)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .pointerInput(fontSizeSp) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom != 1f) {
                        val next = (fontSizeSp * zoom).coerceIn(10f, 28f)
                        onFontSizeChange(next)
                    }
                }
            }
    ) {
        wallpaperUri?.let { uri ->
            val ctx = LocalContext.current
            val bmp = remember(uri) {
                try {
                    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                } catch (_: Exception) { null }
            }
            bmp?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    alpha = 0.25f
                )
            }
        }

        if (isHuge) {
            Text(
                "file besar (>1MB) — mode terbatas aktif",
                color = Primary,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .background(Surface.copy(alpha = 0.9f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        Row(modifier = Modifier.fillMaxSize().padding(top = if (isHuge) 22.dp else 0.dp)) {
            if (showLineNumbers) {
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .fillMaxHeight()
                        .background(Surface)
                        .verticalScroll(scrollState)
                        .padding(top = 12.dp, end = 4.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // batasi jumlah line number yang digambar untuk performa
                    val maxLinesDraw = if (lazyHighlight && lineCount > 4000) 4000 else lineCount
                    for (i in 1..maxLinesDraw) {
                        Text(
                            text = i.toString(),
                            color = LineNumber,
                            fontSize = (fontSizeSp * 0.9f).sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = lh
                        )
                    }
                    if (lineCount > maxLinesDraw) {
                        Text("…", color = LineNumber, fontSize = 12.sp)
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (readOnly) {
                    Text(
                        text = highlighted,
                        style = TextStyle(
                            fontSize = fs,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = lh
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = 6.dp, top = 12.dp, end = 10.dp, bottom = 12.dp)
                    )
                } else {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { handleValueChange(it) },
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = 6.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
                        textStyle = TextStyle(
                            color = Color.Transparent,
                            fontSize = fs,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = lh
                        ),
                        cursorBrush = SolidColor(Primary),
                        decorationBox = { innerTextField ->
                            Box {
                                Text(
                                    text = highlighted,
                                    style = TextStyle(
                                        fontSize = fs,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = lh
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                innerTextField()
                            }
                        }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MarkdownReadView(content: String, isDark: Boolean) {
    val html = remember(content, isDark) { MarkdownRenderer.toHtml(content, isDark) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(if (isDark) 0xFF0D0D0F.toInt() else 0xFFFFFFFF.toInt())
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        update = { wv ->
            wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        },
        modifier = Modifier.fillMaxSize()
    )
}
