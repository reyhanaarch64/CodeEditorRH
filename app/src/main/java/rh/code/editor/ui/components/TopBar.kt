package rh.code.editor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rh.code.editor.ui.theme.*

@Composable
fun TopBar(
    title: String,
    canPreview: Boolean,
    onMenuClick: () -> Unit,
    onSearch: () -> Unit,
    onSave: () -> Unit,
    onPreview: () -> Unit,
    onMore: () -> Unit,
    onUndo: (() -> Unit)? = null,
    onRedo: (() -> Unit)? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    isReadMode: Boolean = false,
    onToggleRead: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButtonBox(Icons.Default.Menu, onMenuClick)
        Spacer(Modifier.width(6.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (onUndo != null) {
                IconButtonBox(Icons.Default.Undo, onUndo, enabled = canUndo)
            }
            if (onRedo != null) {
                IconButtonBox(Icons.Default.Redo, onRedo, enabled = canRedo)
            }
            if (onToggleRead != null) {
                IconButtonBox(
                    if (isReadMode) Icons.Default.Edit else Icons.Default.Visibility,
                    onToggleRead
                )
            }
            IconButtonBox(Icons.Default.Search, onSearch)
            IconButtonBox(Icons.Default.Save, onSave)
            if (canPreview) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Primary)
                        .clickable { onPreview() }
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = OnPrimary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Run", color = OnPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (onSettings != null) {
                IconButtonBox(Icons.Default.Settings, onSettings)
            }
            IconButtonBox(Icons.Default.MoreVert, onMore)
        }
    }
}

@Composable
fun IconButtonBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) SurfaceElevated else SurfaceElevated.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, null,
            tint = if (enabled) TextPrimary else TextSecondary,
            modifier = Modifier.size(17.dp)
        )
    }
}
