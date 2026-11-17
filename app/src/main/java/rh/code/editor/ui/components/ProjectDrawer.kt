package rh.code.editor.ui.components

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
    // track folder path mana yang lagi di-expand
    val expandedPaths = remember(treeVersion) { mutableStateOf(setOf<String>()) }

    // default: expand semua folder di root level
    LaunchedEffect(treeVersion, tree) {
        if (expandedPaths.value.isEmpty() && tree.isNotEmpty()) {
            expandedPaths.value = tree.filter { it.isDirectory }.map { it.path }.toSet()
        }
    }

    fun toggle(path: String) {
        expandedPaths.value = if (path in expandedPaths.value) {
            expandedPaths.value - path
        } else {
            expandedPaths.value + path
        }
    }

    // flatten tree sesuai expanded state
    val flatRows = remember(tree, expandedPaths.value, treeVersion) {
        buildFlatList(tree, expandedPaths.value, depth = 0)
    }

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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Folder, null, tint = Primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    projectName,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1
                )
            }
            Icon(
                Icons.Default.Close, "Close", tint = TextSecondary,
                modifier = Modifier.size(20.dp).clickable { onClose() }
            )
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

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Files (${flatRows.count { !it.node.isDirectory }})", color = TextSecondary, fontSize = 12.sp)
            Row {
                Icon(
                    Icons.Default.NoteAdd, "file", tint = Primary,
                    modifier = Modifier.size(18.dp).clickable { onCreateInPath("", false) }
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Default.CreateNewFolder, "folder", tint = Primary,
                    modifier = Modifier.size(18.dp).clickable { onCreateInPath("", true) }
                )
            }
        }

        key(treeVersion) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(flatRows, key = { it.node.path }) { row ->
                    FlatTreeRow(
                        row = row,
                        expanded = row.node.path in expandedPaths.value,
                        onToggle = { toggle(row.node.path) },
                        onFileClick = onFileClick,
                        onCreateInPath = onCreateInPath
                    )
                }
            }
        }
    }
}

private data class FlatRow(
    val node: FileNode,
    val depth: Int
)

private fun buildFlatList(
    nodes: List<FileNode>,
    expanded: Set<String>,
    depth: Int
): List<FlatRow> {
    val out = mutableListOf<FlatRow>()
    for (n in nodes) {
        out.add(FlatRow(n, depth))
        if (n.isDirectory && n.path in expanded && n.children.isNotEmpty()) {
            out.addAll(buildFlatList(n.children, expanded, depth + 1))
        }
    }
    return out
}

@Composable
private fun FlatTreeRow(
    row: FlatRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFileClick: (FileNode) -> Unit,
    onCreateInPath: (parentPath: String, isFolder: Boolean) -> Unit
) {
    val node = row.node
    val icon = getFileIcon(node)
    val color = getFileColor(node)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                if (node.isDirectory) onToggle()
                else onFileClick(node)
            }
            .padding(start = (row.depth * 14).dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.isDirectory) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
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
        if (node.isDirectory) {
            Icon(
                Icons.Default.Add, "tambah",
                tint = TextSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onCreateInPath(node.path, false) }
            )
        }
    }
}

private fun getFileIcon(node: FileNode): ImageVector {
    if (node.isDirectory) return Icons.Default.Folder
    val ext = node.name.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "js", "jsx", "mjs", "ts", "tsx", "kt", "kts", "java", "py", "php",
        "go", "c", "cpp", "h", "rs", "rb", "html", "htm", "xml", "css",
        "scss", "json", "yml", "yaml", "sql", "sh", "bash" -> Icons.Default.Code
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
        else -> TextSecondary
    }
}
