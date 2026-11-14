package rh.code.editor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rh.code.editor.data.FileNode
import rh.code.editor.ui.theme.*

@Composable
fun ProjectDrawer(
    projectName: String,
    tree: List<FileNode>,
    onFileClick: (FileNode) -> Unit,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onExitProject: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(Surface)
            .padding(12.dp)
    ) {
        // header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(projectName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
            Icon(
                Icons.Default.Close, "Close",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp).clickable { onClose() }
            )
        }

        Spacer(Modifier.height(12.dp))

        // actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallAction(Icons.Default.NoteAdd, "File") { onCreateFile() }
            SmallAction(Icons.Default.CreateNewFolder, "Folder") { onCreateFolder() }
            SmallAction(Icons.Default.Logout, "Keluar") { onExitProject() }
        }

        Spacer(Modifier.height(12.dp))

        // tree
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(tree) { node ->
                TreeItem(node, 0, onFileClick)
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
private fun TreeItem(node: FileNode, depth: Int, onFileClick: (FileNode) -> Unit) {
    var expanded by remember { mutableStateOf(depth < 1) }
    val icon = getFileIcon(node)
    val color = getFileColor(node)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    if (node.isDirectory) expanded = !expanded
                    else onFileClick(node)
                }
                .padding(start = (depth * 14).dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.isDirectory) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                    null, tint = TextSecondary, modifier = Modifier.size(16.dp)
                )
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Spacer(Modifier.width(4.dp))
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(node.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
        }

        AnimatedVisibility(visible = expanded && node.isDirectory, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                node.children.forEach { child ->
                    TreeItem(child, depth + 1, onFileClick)
                }
            }
        }
    }
}

private fun getFileIcon(node: FileNode): ImageVector {
    if (node.isDirectory) return Icons.Default.Folder
    return when (node.name.substringAfterLast('.').lowercase()) {
        "js", "jsx", "mjs" -> Icons.Default.Javascript
        "html", "htm" -> Icons.Default.Html
        "css", "scss" -> Icons.Default.Css
        "kt", "kts" -> Icons.Default.Code
        "json" -> Icons.Default.DataObject
        "md" -> Icons.Default.Description
        "png", "jpg", "jpeg", "gif", "svg", "webp" -> Icons.Default.Image
        "gitignore", "git" -> Icons.Default.Source
        "xml" -> Icons.Default.Code
        "php" -> Icons.Default.Php
        "py" -> Icons.Default.Code
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileColor(node: FileNode): Color {
    if (node.isDirectory) return Primary
    return when (node.name.substringAfterLast('.').lowercase()) {
        "js", "jsx" -> Color(0xFFF7DF1E)
        "html", "htm" -> Color(0xFFE34F26)
        "css", "scss" -> Color(0xFF264DE4)
        "kt", "kts" -> Color(0xFF7F52FF)
        "json" -> Color(0xFFCBCB41)
        "md" -> Color(0xFF519ABA)
        "php" -> Color(0xFF777BB4)
        "py" -> Color(0xFF3572A5)
        "gitignore" -> Color(0xFFF05033)
        else -> TextSecondary
    }
}
