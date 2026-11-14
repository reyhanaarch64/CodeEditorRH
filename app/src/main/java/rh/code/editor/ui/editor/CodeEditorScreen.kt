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

    // sync external content change
    LaunchedEffect(document.content) {
        if (document.content != textFieldValue.text) {
            textFieldValue = textFieldValue.copy(text = document.content)
        }
    }

    // insert symbol from bottom bar
    LaunchedEffect(insertSymbol) {
        if (!insertSymbol.isNullOrEmpty()) {
            val tf = textFieldValue
            val start = tf.selection.min
            val newText = tf.text.substring(0, start) + insertSymbol + tf.text.substring(tf.selection.max)
            val newCursor = start + insertSymbol.length
            textFieldValue = TextFieldValue(
                text = newText,
                selection = androidx.compose.ui.text.TextRange(newCursor)
            )
            onContentChange(newText)
            onSymbolInserted()
        }
    }

    // update cursor info
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

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // line numbers
        Column(
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight()
                .background(Surface)
                .verticalScroll(scrollState)
                .padding(top = 12.dp, end = 6.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.End
        ) {
            for (i in 1..lineCount) {
                Text(
                    text = i.toString(),
                    color = LineNumber,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )
            }
        }

        // editor
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = {
                    textFieldValue = it
                    onContentChange(it.text)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(start = 8.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
                textStyle = TextStyle(
                    color = Color.Transparent,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(Primary),
                decorationBox = { innerTextField ->
                    Box {
                        Text(
                            text = highlighted,
                            style = TextStyle(
                                fontSize = 14.sp,
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
