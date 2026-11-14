package rh.code.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rh.code.editor.ui.theme.*

@Composable
fun StatusBar(
    line: Int,
    column: Int,
    language: String,
    totalLines: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Ln $line, Col $column",
            color = TextSecondary,
            fontSize = 11.sp
        )
        Text(
            text = "$totalLines lines  •  ${language.uppercase()}",
            color = TextSecondary,
            fontSize = 11.sp
        )
    }
}
