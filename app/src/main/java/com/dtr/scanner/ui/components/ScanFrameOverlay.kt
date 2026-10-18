package com.dtr.scanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dtr.scanner.ui.theme.RoyalBlue
import com.dtr.scanner.ui.theme.SkyAccent

@Composable
fun ScanFrameOverlay(
    instruction: String,
    statusLine: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val scrim = Color.Black.copy(alpha = 0.58f)
            drawRect(scrim)

            val frameSize = size.minDimension * 0.68f
            val left = (size.width - frameSize) / 2f
            val top = (size.height - frameSize) / 2f - size.height * 0.06f
            val cutout = Rect(Offset(left, top), Size(frameSize, frameSize))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = cutout.topLeft,
                size = cutout.size,
                cornerRadius = CornerRadius(12f, 12f),
                blendMode = BlendMode.Clear,
            )

            val stroke = 5f
            val corner = frameSize * 0.14f
            val c = RoyalBlue
            fun cornerLines(x: Float, y: Float, flipX: Int, flipY: Int) {
                drawLine(c, Offset(x, y), Offset(x + flipX * corner, y), strokeWidth = stroke)
                drawLine(c, Offset(x, y), Offset(x, y + flipY * corner), strokeWidth = stroke)
            }
            cornerLines(cutout.left, cutout.top, 1, 1)
            cornerLines(cutout.right, cutout.top, -1, 1)
            cornerLines(cutout.left, cutout.bottom, 1, -1)
            cornerLines(cutout.right, cutout.bottom, -1, -1)

            if (statusLine != null) {
                drawLine(
                    color = SkyAccent.copy(alpha = 0.85f),
                    start = Offset(cutout.left + 16f, cutout.center.y),
                    end = Offset(cutout.right - 16f, cutout.center.y),
                    strokeWidth = 3f,
                )
            }
        }

        ColumnTopText(instruction = instruction, statusLine = statusLine)
    }
}

@Composable
private fun ColumnTopText(instruction: String, statusLine: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val primary = statusLine?.takeIf { it.isNotBlank() } ?: instruction
            Text(
                text = primary,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (!statusLine.isNullOrBlank() && instruction.isNotBlank()) {
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
