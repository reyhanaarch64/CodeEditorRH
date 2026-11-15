package rh.code.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rh.code.editor.ui.theme.*

@Composable
fun MoreDrawer(
    isInProject: Boolean,
    onSearch: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    onSave: () -> Unit,
    onSettings: () -> Unit,
    onFindReplace: () -> Unit,
    onExitProject: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(270.dp)
            .background(Surface)
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Menu", color = TextPrimary, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 16.sp)
            Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(20.dp).clickable { onClose() })
        }
        Spacer(Modifier.height(16.dp))
        DrawerItem(Icons.Default.Search, "Cari & Ganti", onFindReplace)
        DrawerItem(Icons.Default.Code, "Pilih Syntax", onLanguage)
        DrawerItem(Icons.Default.Palette, "Theme & Wallpaper", onTheme)
        DrawerItem(Icons.Default.Save, "Save", onSave)
        DrawerItem(Icons.Default.Settings, "Pengaturan", onSettings)
        if (isInProject) {
            Spacer(Modifier.height(8.dp))
            DrawerItem(Icons.Default.Logout, "Keluar Proyek", onExitProject)
        }
    }
}

@Composable
fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = TextPrimary, fontSize = 14.sp)
    }
}

@Composable
fun MenuBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = TextPrimary, fontSize = 14.sp)
    }
}

fun getFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = cursor.getString(idx)
        }
    }
    return name
}
