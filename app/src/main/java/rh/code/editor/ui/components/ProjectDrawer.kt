package rh.code.editor.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
    onCreateInPath: (parentPath: String, isFolder: Boolean) -> Unit,
    onExitProject: () -> Unit,
    onClose: () -> Unit,
    treeVersion: Int = 0
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(Surface)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(projectName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            }
            Icon(Icons.Default.Close, "Close", tint = TextSecondary,
                modifier = Modifier.size(20.dp).clickable { onClose() })
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceElevated)
                .clickable { onExitProject() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Logout, null, tint = Error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Keluar Proyek", color = Error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(12.dp))

        // root + button
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Files", color = TextSecondary, fontSize = 12.sp)
            Row {
                Icon(Icons.Default.NoteAdd, "file", tint = Primary,
                    modifier = Modifier.size(18.dp).clickable {
                        // create at project root - parent path empty means root, handled outside
                        onCreateInPath("", false)
                    })
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.CreateNewFolder, "folder", tint = Primary,
                    modifier = Modifier.size(18.dp).clickable {
                        onCreateInPath("", true)
                    })
            }
        }

        key(treeVersion) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(tree, key = { it.path }) { node ->
                    TreeItem(
                        node = node,
                        depth = 0,
                        onFileClick = onFileClick,
                        onCreateInPath = onCreateInPath
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeItem(
    node: FileNode,
    depth: Int,
    onFileClick: (FileNode) -> Unit,
    onCreateInPath: (parentPath: String, isFolder: Boolean) -> Unit
) {
    var expanded by remember(node.path) { mutableStateOf(depth < 2) }
    val icon = getFileIcon(node)
    val color = getFileColor(node)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    if (node.isDirectory) {
                        expanded = !expanded
                    } else {
                        onFileClick(node)
                    }
                }
                .padding(start = (depth * 12).dp, top = 5.dp, bottom = 5.dp, end = 4.dp),
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
            Spacer(Modifier.width(2.dp))
            Icon(icon, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                node.name,
                color = TextPrimary,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            // + hanya di folder
            if (node.isDirectory) {
                Icon(
                    Icons.Default.Add, "tambah di folder ini",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable {
                            onCreateInPath(node.path, false) // default file; long-ish bisa folder
                        }
                )
            }
        }

        AnimatedVisibility(
            visible = expanded && node.isDirectory,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                // tombol buat file/folder di dalam folder ini
                if (expanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = ((depth + 1) * 12 + 18).dp, top = 2.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "+ file",
                            color = Primary,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { onCreateInPath(node.path, false) }
                        )
                        Text(
                            "+ folder",
                            color = Primary,
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { onCreateInPath(node.path, true) }
                        )
                    }
                }
                node.children.forEach { child ->
                    TreeItem(child, depth + 1, onFileClick, onCreateInPath)
                }
            }
        }
    }
}

private fun getFileIcon(node: FileNode): ImageVector {
    // hanya icon yang pasti ada di material icons
    if (node.isDirectory) return Icons.Default.Folder
    val ext = node.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "js", "jsx", "mjs", "ts", "tsx", "kt", "kts", "java", "py", "php",
        "go", "c", "cpp", "h", "rs", "rb", "swift", "html", "htm", "xml",
        "css", "scss", "sass", "json", "yml", "yaml", "sql", "sh", "bash" -> Icons.Default.Code
        "md", "markdown", "txt", "log" -> Icons.Default.Description
        "png", "jpg", "jpeg", "gif", "webp", "svg", "ico" -> Icons.Default.Image
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileColor(node: FileNode): Color {
    if (node.isDirectory) return Primary
    return when (node.name.substringAfterLast('.').lowercase()) {
        "js", "jsx" -> Color(0xFFF7DF1E)
        "ts", "tsx" -> Color(0xFF3178C6)
        "html", "htm" -> Color(0xFFE34F26)
        "css", "scss" -> Color(0xFF264DE4)
        "kt", "kts" -> Color(0xFF7F52FF)
        "json" -> Color(0xFFCBCB41)
        "md" -> Color(0xFF519ABA)
        "php" -> Color(0xFF777BB4)
        "py" -> Color(0xFF3572A5)
        "java" -> Color(0xFFB07219)
        "gitignore" -> Color(0xFFF05033)
        else -> TextSecondary
    }
}
