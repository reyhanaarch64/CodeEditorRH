package rh.code.editor.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rh.code.editor.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

@Composable
fun WallpaperCropScreen(
    sourceUri: Uri,
    onCancel: () -> Unit,
    onCropped: (Uri) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(sourceUri) {
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                bitmap = BitmapFactory.decodeStream(input)
            }
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevated)
                    .clickable { onCancel() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Close, null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Batal", color = TextPrimary, fontSize = 14.sp)
            }
            Text("Crop Wallpaper", color = TextPrimary, fontSize = 15.sp)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Primary)
                    .clickable {
                        val bmp = bitmap ?: return@clickable
                        val cropped = cropCenter(bmp, scale, offset)
                        val out = File(context.cacheDir, "wallpaper_crop.jpg")
                        FileOutputStream(out).use { fos ->
                            cropped.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                        }
                        onCropped(Uri.fromFile(out))
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Check, null, tint = OnPrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pakai", color = OnPrimary, fontSize = 14.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 5f)
                        offset += pan
                    }
                }
        ) {
            val bmp = bitmap
            if (bmp != null) {
                val imageBitmap = bmp.asImageBitmap()
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasW = size.width
                    val canvasH = size.height
                    val baseScale = min(canvasW / bmp.width, canvasH / bmp.height)
                    val drawScale = baseScale * scale
                    val drawW = bmp.width * drawScale
                    val drawH = bmp.height * drawScale
                    val left = (canvasW - drawW) / 2f + offset.x
                    val top = (canvasH - drawH) / 2f + offset.y
                    drawImage(
                        image = imageBitmap,
                        dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                        dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1))
                    )
                    // frame crop (center area ~ 9:16 editor-ish)
                    val frameW = canvasW * 0.85f
                    val frameH = canvasH * 0.7f
                    val frameLeft = (canvasW - frameW) / 2f
                    val frameTop = (canvasH - frameH) / 2f
                    drawRect(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(frameLeft, frameTop),
                        size = androidx.compose.ui.geometry.Size(frameW, frameH),
                        style = Stroke(width = 3f)
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Memuat gambar...", color = TextSecondary)
                }
            }
        }
    }
}

private fun cropCenter(src: Bitmap, scale: Float, offset: Offset): Bitmap {
    // ambil bagian tengah proporsional sebagai hasil crop sederhana
    val targetRatio = 9f / 16f
    var w = src.width
    var h = src.height
    val srcRatio = w.toFloat() / h
    var x = 0
    var y = 0
    if (srcRatio > targetRatio) {
        // terlalu lebar
        val newW = (h * targetRatio).toInt()
        x = ((w - newW) / 2f + offset.x / scale).toInt().coerceIn(0, w - newW)
        w = newW
    } else {
        val newH = (w / targetRatio).toInt()
        y = ((h - newH) / 2f + offset.y / scale).toInt().coerceIn(0, h - newH)
        h = newH
    }
    val safeW = w.coerceAtMost(src.width - x).coerceAtLeast(1)
    val safeH = h.coerceAtMost(src.height - y).coerceAtLeast(1)
    return Bitmap.createBitmap(src, x.coerceAtLeast(0), y.coerceAtLeast(0), safeW, safeH)
}

