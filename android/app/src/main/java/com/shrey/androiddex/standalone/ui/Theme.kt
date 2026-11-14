package com.shrey.androiddex.standalone.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val DexBackground = Color(0xFF090A0C)
val DexSurface = Color(0xFF16171B)
val DexRaised = Color(0xFF202228)
val DexBorder = Color(0xFF34373F)
val DexMuted = Color(0xFF9CA3AF)
val DexMint = Color(0xFF55E6C1)
val DexBlue = Color(0xFF38B6FF)
val DexWarning = Color(0xFFF6C76B)
val DexError = Color(0xFFFF8F8F)

@Composable
fun DexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = DexMint, onPrimary = Color(0xFF07352B),
            primaryContainer = Color(0xFF173E36), onPrimaryContainer = Color(0xFFC8FFF0),
            secondary = DexBlue, onSecondary = Color(0xFF062C43),
            background = DexBackground, onBackground = Color.White,
            surface = DexSurface, onSurface = Color.White,
            surfaceVariant = DexRaised, onSurfaceVariant = DexMuted,
            outline = DexBorder, error = DexError),
        shapes = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(9.dp), medium = RoundedCornerShape(13.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp)),
        content = content
    )
}
