package com.jadeai.solvertracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jadeai.solvertracker.ui.theme.JellyTextDark

@Composable
fun JellyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    cornerRadius: Dp = 22.dp,
    backgroundColor: Color = Color(0xFFF3F7FF),
    glowColor: Color = backgroundColor,
    contentColor: Color = JellyTextDark,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    JellyButtonSurface(
        onClick = onClick,
        modifier = modifier.size(size),
        cornerRadius = cornerRadius,
        backgroundColor = backgroundColor,
        glowColor = glowColor,
        contentColor = contentColor,
        enabled = enabled
    ) {
        ClayIconBubble(
            size = size * 0.74f,
            backgroundColor = Color.White.copy(alpha = 0.38f),
            contentColor = contentColor,
            content = content
        )
    }
}

@Composable
fun JellyPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    cornerRadius: Dp = 24.dp,
    backgroundColor: Color = Color(0xFFF3F7FF),
    glowColor: Color = backgroundColor,
    contentColor: Color = JellyTextDark,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    JellyButtonSurface(
        onClick = onClick,
        modifier = modifier.height(height),
        cornerRadius = cornerRadius,
        backgroundColor = backgroundColor,
        glowColor = glowColor,
        contentColor = contentColor,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun JellyButtonSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    cornerRadius: Dp,
    backgroundColor: Color,
    glowColor: Color,
    contentColor: Color,
    enabled: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "jelly-button-scale"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 18.dp,
                shape = shape,
                spotColor = glowColor.copy(alpha = 0.42f),
                ambientColor = Color.White.copy(alpha = 0.48f)
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.92f),
                        backgroundColor.copy(alpha = 0.98f),
                        glowColor.copy(alpha = 0.78f)
                    ),
                    start = Offset.Zero,
                    end = Offset(260f, 340f)
                )
            )
            .drawBehind {
                val radius = cornerRadius.toPx()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.96f),
                            Color.White.copy(alpha = 0.12f)
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height)
                    ),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.44f),
                    radius = size.minDimension * 0.24f,
                    center = Offset(size.width * 0.28f, size.height * 0.18f)
                )
                drawCircle(
                    color = glowColor.copy(alpha = 0.26f),
                    radius = size.minDimension * 0.40f,
                    center = Offset(size.width * 0.82f, size.height * 0.86f)
                )
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.98f),
                        Color.White.copy(alpha = 0.34f),
                        glowColor.copy(alpha = 0.18f)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor.copy(alpha = if (enabled) 1f else 0.45f)
        ) {
            content()
        }
    }
}
