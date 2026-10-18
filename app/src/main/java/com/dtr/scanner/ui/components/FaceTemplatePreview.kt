package com.dtr.scanner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.dtr.scanner.face.FaceSignature
import com.dtr.scanner.ui.theme.RoyalBlue

private val FaceFill = RoyalBlue.copy(alpha = 0.12f)
private val FaceStroke = RoyalBlue.copy(alpha = 0.85f)
private val FeatureStroke = RoyalBlue.copy(alpha = 0.65f)
private val CheekDot = RoyalBlue.copy(alpha = 0.45f)

@Composable
fun FaceTemplatePreview(
    signature: FloatArray,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .size(168.dp)
                .background(Color(0xFFF3F6FB), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFD0D9E8), RoundedCornerShape(12.dp)),
        ) {
            val contours = FaceSignature.splitIntoContours(signature)
            if (contours.isEmpty()) return@Canvas

            val allPoints = contours.flatten()
            if (allPoints.isEmpty()) return@Canvas

            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            for (p in allPoints) {
                minX = minOf(minX, p.x)
                maxX = maxOf(maxX, p.x)
                minY = minOf(minY, p.y)
                maxY = maxOf(maxY, p.y)
            }
            val pad = 0.12f
            val rangeX = (maxX - minX).coerceAtLeast(0.05f)
            val rangeY = (maxY - minY).coerceAtLeast(0.05f)
            val contentSide = size.minDimension * (1f - pad * 2f)
            val scale = minOf(contentSide / rangeX, contentSide / rangeY)
            val drawnW = rangeX * scale
            val drawnH = rangeY * scale
            val left = (size.width - drawnW) / 2f
            val top = (size.height - drawnH) / 2f

            fun map(p: FaceSignature.NormPoint): Offset {
                val nx = (p.x - minX) / rangeX
                val ny = (p.y - minY) / rangeY
                return Offset(left + nx * drawnW, top + (1f - ny) * drawnH)
            }

            fun drawPolyline(points: List<FaceSignature.NormPoint>, close: Boolean) {
                if (points.isEmpty()) return
                val mapped = points.map(::map)
                if (mapped.size == 1) {
                    drawCircle(CheekDot, radius = 4f, center = mapped.first())
                    return
                }
                val path = Path().apply {
                    moveTo(mapped.first().x, mapped.first().y)
                    for (i in 1 until mapped.size) {
                        lineTo(mapped[i].x, mapped[i].y)
                    }
                    if (close) close()
                }
                drawPath(path, FeatureStroke, style = Stroke(width = 2f))
            }

            contours.forEachIndexed { index, points ->
                when (index) {
                    0 -> {
                        val mapped = points.map(::map)
                        if (mapped.size >= 3) {
                            val path = Path().apply {
                                moveTo(mapped.first().x, mapped.first().y)
                                for (i in 1 until mapped.size) {
                                    lineTo(mapped[i].x, mapped[i].y)
                                }
                                close()
                            }
                            drawPath(path, FaceFill)
                            drawPath(path, FaceStroke, style = Stroke(width = 2.5f))
                        }
                    }

                    5, 6 -> drawPolyline(points, close = true)
                    in 1..12 -> drawPolyline(points, close = false)
                    else -> drawPolyline(points, close = false)
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
