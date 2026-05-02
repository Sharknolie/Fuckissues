package com.jadeai.solvertracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowRightAlt
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jadeai.solvertracker.domain.model.SolutionStep
import com.jadeai.solvertracker.ui.theme.ProblemColor
import com.jadeai.solvertracker.ui.theme.SolutionColor

@Composable
fun TimelineView(steps: List<SolutionStep>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            TimelineNode(
                step = step,
                index = index,
                isLast = index == steps.lastIndex
            )
        }
    }
}

@Composable
private fun TimelineNode(
    step: SolutionStep,
    index: Int,
    isLast: Boolean
) {
    val outlineColor = MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Timeline indicator
        Box(
            modifier = Modifier.width(40.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Node circle
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            // Vertical line (if not last)
            if (!isLast) {
                Canvas(
                    modifier = Modifier
                        .width(2.dp)
                        .height(200.dp)
                        .offset(y = 24.dp)
                ) {
                    drawLine(
                        color = outlineColor,
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Step card
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "第 ${index + 1} 步",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Problem card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(2.dp, ProblemColor.copy(alpha = 0.9f)),
                colors = CardDefaults.cardColors(
                    containerColor = ProblemColor.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "❓",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.problem,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProblemColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Arrow
            Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(
                    Icons.Default.ArrowRightAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = SolutionColor
                )
            }

            // Solution card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(2.dp, SolutionColor.copy(alpha = 0.9f)),
                colors = CardDefaults.cardColors(
                    containerColor = SolutionColor.copy(alpha = 0.08f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "✅",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.solution,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SolutionColor.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (!isLast) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
