package com.jadeai.solvertracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jadeai.solvertracker.ui.theme.JellyCardBackground

@Composable
fun RetroFrame(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable BoxScope.() -> Unit
) {
    JellyCard(
        modifier = modifier,
        cornerRadius = 30.dp,
        elevation = 18.dp,
        contentPadding = contentPadding
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun RetroCard(
    modifier: Modifier = Modifier,
    containerColor: Color = JellyCardBackground,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    JellyCard(
        modifier = modifier,
        backgroundColor = containerColor,
        cornerRadius = 22.dp,
        elevation = 10.dp,
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun RetroSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color.Transparent
    ) {
        JellyBackground {
            content()
        }
    }
}
