package com.shrey.androiddex.standalone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DexCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = DexSurface, border = BorderStroke(1.dp, DexBorder)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = DexMuted)
    }
}

@Composable
fun StatusLine(label: String, ok: Boolean, detail: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(if (ok) DexMint else DexWarning, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = DexMuted)
        }
    }
}

@Composable
fun ActionButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)) }
        Text(text, fontSize = 13.sp)
    }
}

@Composable
fun SettingSwitch(title: String, description: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = DexMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, color = DexMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(132.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppIcon(repository: com.shrey.androiddex.standalone.data.AppRepository, packageName: String, size: Dp = 40.dp) {
    var bitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) { bitmap = withContext(Dispatchers.Default) { repository.icon(packageName) } }
    Box(Modifier.size(size).background(Color.White.copy(alpha = .06f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image, null, Modifier.size(size - 4.dp))
        else Icon(Icons.Default.Android, null, Modifier.size(size * .65f), tint = DexMint)
    }
}

@Composable
fun EmptyMessage(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = DexMuted)
    }
}

@Composable
fun SmallText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = DexMuted, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
}
