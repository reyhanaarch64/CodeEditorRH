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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rh.code.editor.data.Document
import rh.code.editor.syntax.SyntaxHighlighter
import rh.code.editor.ui.theme.*

@Composable
fun CodeEditorScreen(
    document: Document,
    onContentChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    var text by remember(document.id) { mutableStateOf(document.content) }

    // sync external changes
    LaunchedEffect(document.content) {
        if (document.content != text) {
            text = document.content
        }
    }

    val highlighted = remember(text, document.language) {
        SyntaxHighlighter.highlight(text, document.language)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onContentChange(it)
            },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(12.dp),
            textStyle = TextStyle(
                color = Color.Transparent,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(Primary),
            decorationBox = { innerTextField ->
                Box {
                    // highlighted overlay
                    Text(
                        text = highlighted,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // editable field (teks transparan)
                    innerTextField()
                }
            }
        )
    }
}
