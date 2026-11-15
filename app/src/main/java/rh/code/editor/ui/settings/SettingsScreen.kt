package rh.code.editor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rh.code.editor.data.EditorViewModel
import rh.code.editor.ui.theme.*

@Composable
fun SettingsScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceElevated)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("Pengaturan", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle("Editor")
            SettingSlider(
                title = "Ukuran font",
                subtitle = "${viewModel.fontSize.value.toInt()} sp  •  bisa juga cubit di editor",
                value = viewModel.fontSize.value,
                range = 10f..28f,
                onChange = { viewModel.fontSize.value = it }
            )
            SettingToggle(
                title = "Word wrap",
                subtitle = "potong baris panjang agar masuk layar",
                checked = viewModel.wordWrap.value,
                onChange = { viewModel.wordWrap.value = it }
            )
            SettingToggle(
                title = "Tampilkan nomor baris",
                checked = viewModel.showLineNumbers.value,
                onChange = { viewModel.showLineNumbers.value = it }
            )
            SettingToggle(
                title = "Auto pair bracket & quote",
                checked = viewModel.autoPair.value,
                onChange = { viewModel.autoPair.value = it }
            )
            SettingToggle(
                title = "Highlight bertahap (file besar)",
                subtitle = "highlight per bagian biar tidak lag",
                checked = viewModel.lazyHighlight.value,
                onChange = { viewModel.lazyHighlight.value = it }
            )

            SectionTitle("Preview")
            SettingToggle(
                title = "Eruda console",
                subtitle = "inject debugger di preview HTML",
                checked = viewModel.erudaEnabled.value,
                onChange = { viewModel.erudaEnabled.value = it }
            )
            SettingToggle(
                title = "Auto refresh preview",
                subtitle = "refresh otomatis saat save file html",
                checked = viewModel.autoRefreshPreview.value,
                onChange = { viewModel.autoRefreshPreview.value = it }
            )

            SectionTitle("Tampilan")
            SettingToggle(
                title = "Dark theme",
                checked = viewModel.isDarkTheme.value,
                onChange = { viewModel.isDarkTheme.value = it }
            )
            SettingToggle(
                title = "Wallpaper editor",
                subtitle = viewModel.wallpaperUri.value?.let { "aktif" } ?: "tidak ada",
                checked = viewModel.wallpaperUri.value != null,
                onChange = { if (!it) viewModel.wallpaperUri.value = null }
            )

            SectionTitle("Performa")
            SettingToggle(
                title = "Batasi file besar (>1MB)",
                subtitle = "hanya render bagian yang terlihat",
                checked = viewModel.limitHugeFiles.value,
                onChange = { viewModel.limitHugeFiles.value = it }
            )
            Text(
                "Device dengan RAM terbatas disarankan nyalakan semua opsi performa.",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SectionTitle("Tentang")
            InfoRow("Aplikasi", "Code Editor RH")
            InfoRow("Versi", "1.1")
            InfoRow("Package", "rh.code.editor")
            InfoRow("Syntax engine", "sxh v3")
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .clickable { onChange(!checked) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextSecondary, fontSize = 11.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OnPrimary,
                checkedTrackColor = Primary
            )
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(14.dp)
    ) {
        Text(title, color = TextPrimary, fontSize = 14.sp)
        Text(subtitle, color = TextSecondary, fontSize = 11.sp)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = (range.endInclusive - range.start).toInt() - 1,
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary
            )
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, fontSize = 13.sp)
        Text(value, color = TextPrimary, fontSize = 13.sp)
    }
}
