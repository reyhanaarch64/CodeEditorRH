package rh.code.editor.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
    onSymbolInserted: () -> Unit = {}
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
        if (!insertSymbol.isNullOrEmpty()) {
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

    val highlighted = remember(textFieldValue.text, document.language) {
        SyntaxHighlighter.highlight(textFieldValue.text, document.language)
    }

    val lineCount = remember(textFieldValue.text) {
        textFieldValue.text.count { it == '\n' } + 1
    }

    fun handleValueChange(newValue: TextFieldValue) {
        val oldText = textFieldValue.text
        val newText = newValue.text
        val cursor = newValue.selection.start

        // auto-pair quotes
        if (newText.length == oldText.length + 1 && cursor > 0) {
            val inserted = newText.getOrNull(cursor - 1)
            if (inserted == '"' || inserted == '\'' || inserted == '`') {
                // jangan double kalau sudah ada penutup di kanan
                val nextChar = newText.getOrNull(cursor)
                if (nextChar != inserted) {
                    val paired = newText.substring(0, cursor) + inserted + newText.substring(cursor)
                    textFieldValue = TextFieldValue(
                        text = paired,
                        selection = TextRange(cursor)
                    )
                    onContentChange(paired)
                    return
                }
            }
            // auto-pair brackets
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // line numbers
        Column(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight()
                .background(Surface)
                .verticalScroll(scrollState)
                .padding(top = 12.dp, end = 4.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.End
        ) {
            for (i in 1..lineCount) {
                Text(
                    text = i.toString(),
                    color = LineNumber,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { handleValueChange(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(start = 6.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
                textStyle = TextStyle(
                    color = Color.Transparent,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(Primary),
                decorationBox = { innerTextField ->
                    Box {
                        Text(
                            text = highlighted,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 20.sp
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
