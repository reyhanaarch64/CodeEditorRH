package rh.code.editor.ui.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rh.code.editor.data.Document
import rh.code.editor.syntax.SyntaxHighlighter
import rh.code.editor.ui.theme.*

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
    readOnly: Boolean = false
) {
    val scrollState = rememberScrollState()
    var textFieldValue by remember(document.id) {
        mutableStateOf(TextFieldValue(document.content))
    }

    LaunchedEffect(document.content) {
        if (document.content != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = document.content)
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

    val highlighted = remember(textFieldValue.text, document.language, isDark) {
        SyntaxHighlighter.highlight(textFieldValue.text, document.language, isDark)
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

        // auto-pair quotes
        if (newText.length == oldText.length + 1 && cursor > 0) {
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

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
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

        Row(modifier = Modifier.fillMaxSize()) {
            // line numbers
            Column(
                modifier = Modifier
                    .width(44.dp)
                    .fillMaxHeight()
                    .background(Surface)
                    .verticalScroll(scrollState)
                    .padding(top = 12.dp, end = 4.dp),
                horizontalAlignment = Alignment.End
            ) {
                for (i in 1..lineCount) {
                    Text(
                        text = i.toString(),
                        color = LineNumber,
                        fontSize = (fontSizeSp * 0.9f).sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = lh
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (readOnly) {
                    // mode baca: tampilkan highlight, tidak bisa diedit
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
