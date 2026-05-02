package com.jadeai.solvertracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jadeai.solvertracker.ui.theme.JellyCardBackground
import com.jadeai.solvertracker.ui.theme.JellyCardBorder

@Composable
fun JellyCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = JellyCardBackground,
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 18.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                spotColor = Color(0x240F172A),
                ambientColor = Color(0x14FFFFFF)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.82f),
                        backgroundColor,
                        backgroundColor.copy(alpha = 0.74f)
                    ),
                    start = Offset.Zero,
                    end = Offset(360f, 520f)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        JellyCardBorder,
                        Color.White.copy(alpha = 0.38f),
                        Color.White.copy(alpha = 0.18f)
                    )
                ),
                shape = shape
            ),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            content()
        }
    }
}
