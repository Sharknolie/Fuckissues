package com.jadeai.solvertracker.ui.components

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ClayMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cornerRadius = 35.dp
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(164.dp)
            .graphicsLayer {
                scaleX = if (isPressed) 0.985f else 1f
                scaleY = if (isPressed) 0.985f else 1f
            }
            .drawBehind {
                drawCssClayOuterShadows(
                    backgroundColor = backgroundColor,
                    cornerRadius = cornerRadius
                )
            }
            .clip(shape)
            .drawWithContent {
                val radius = cornerRadius.toPx()

                drawRoundRect(
                    color = backgroundColor,
                    cornerRadius = CornerRadius(radius, radius)
                )

                drawCssClayInnerShadows(cornerRadius = cornerRadius)

                drawContent()
            }
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                }
            )
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(12.dp))

            ClayIconBubble(
                size = 40.dp,
                backgroundColor = Color.White.copy(alpha = 0.40f),
                contentColor = contentColor
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColor
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor.copy(alpha = 0.70f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCssClayInnerShadows(
    cornerRadius: Dp
) {
    val baseRadius = cornerRadius.toPx()
    val shadowColor = Color(0xFFA3B1C6)
    val layers = 5

    for (layer in 0 until layers) {
        val inset = layer.dp.toPx()
        val layerProgress = (layers - layer).toFloat() / layers.toFloat()
        val rectSize = Size(
            width = (size.width - inset * 2f).coerceAtLeast(0f),
            height = (size.height - inset * 2f).coerceAtLeast(0f)
        )
        val radius = (baseRadius - inset).coerceAtLeast(0f)

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    shadowColor.copy(alpha = 0.020f * layerProgress),
                    shadowColor.copy(alpha = 0.10f * layerProgress)
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            topLeft = Offset(inset, inset),
            size = rectSize,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 0.9.dp.toPx())
        )

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.34f * layerProgress),
                    Color.White.copy(alpha = 0.06f * layerProgress),
                    Color.Transparent
                ),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            topLeft = Offset(inset, inset),
            size = rectSize,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = 1.0.dp.toPx())
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCssClayOuterShadows(
    backgroundColor: Color,
    cornerRadius: Dp
) {
    drawIntoCanvas { canvas ->
        val radius = cornerRadius.toPx()
        val rect = RectF(0f, 0f, size.width, size.height)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor.toArgb()
        }

        paint.setShadowLayer(
            12.dp.toPx(),
            7.dp.toPx(),
            7.dp.toPx(),
            Color(0xFFA3B1C6).copy(alpha = 0.42f).toArgb()
        )
        canvas.nativeCanvas.drawRoundRect(rect, radius, radius, paint)

        paint.setShadowLayer(
            12.dp.toPx(),
            (-7).dp.toPx(),
            (-7).dp.toPx(),
            Color.White.copy(alpha = 0.45f).toArgb()
        )
        canvas.nativeCanvas.drawRoundRect(rect, radius, radius, paint)

        paint.clearShadowLayer()
    }
}
