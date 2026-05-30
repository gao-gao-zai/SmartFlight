package com.gaozay.smartflight.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gaozay.smartflight.apps.isOnline
import com.gaozay.smartflight.data.local.entity.InstalledAppEntity

@Composable
internal fun AppIcon(
    packageName: String,
    label: String,
    app: InstalledAppEntity,
) {
    val packageManager = LocalContext.current.packageManager
    val bitmap = remember(packageName) {
        runCatching {
            packageManager.getApplicationIcon(packageName).toBitmap()
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(app.iconFallbackContainerColor()),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap == null) {
            Text(
                text = label.take(1).ifBlank { "?" },
                fontWeight = FontWeight.Bold,
                color = app.iconFallbackContentColor(),
            )
        } else {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val width = intrinsicWidth.takeIf { it > 0 } ?: 48
    val height = intrinsicHeight.takeIf { it > 0 } ?: 48
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

@Composable
private fun InstalledAppEntity.iconFallbackContainerColor(): Color = when {
    isInBlacklist -> MaterialTheme.colorScheme.errorContainer
    isOnline() -> MaterialTheme.colorScheme.primaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun InstalledAppEntity.iconFallbackContentColor(): Color = when {
    isInBlacklist -> MaterialTheme.colorScheme.onErrorContainer
    isOnline() -> MaterialTheme.colorScheme.onPrimaryContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
