package com.dtr.scanner.ui.components

import androidx.compose.foundation.Canvas
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
import com.dtr.scanner.ui.theme.RoyalBlue

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
                .size(120.dp)
                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
        ) {
            val points = signatureToPoints(signature)
            if (points.isEmpty()) return@Canvas

            var minX = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var minY = Float.MAX_VALUE
            var maxY = Float.MIN_VALUE
            for (p in points) {
                minX = minOf(minX, p.x)
                maxX = maxOf(maxX, p.x)
                minY = minOf(minY, p.y)
                maxY = maxOf(maxY, p.y)
            }
            val pad = 0.15f
            val rangeX = (maxX - minX).coerceAtLeast(0.01f)
            val rangeY = (maxY - minY).coerceAtLeast(0.01f)

            fun map(p: Offset): Offset {
                val nx = (p.x - minX) / rangeX
                val ny = (p.y - minY) / rangeY
                val side = size.minDimension * (1f - pad * 2f)
                val left = (size.width - side) / 2f
                val top = (size.height - side) / 2f
                return Offset(left + nx * side, top + ny * side)
            }

            val mapped = points.map(::map)
            val path = Path().apply {
                moveTo(mapped.first().x, mapped.first().y)
                for (i in 1 until mapped.size) {
                    lineTo(mapped[i].x, mapped[i].y)
                }
                close()
            }
            drawPath(path, RoyalBlue.copy(alpha = 0.25f))
            drawPath(path, RoyalBlue, style = Stroke(width = 2f))
            for (p in mapped) {
                drawCircle(RoyalBlue, radius = 2.5f, center = p)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun signatureToPoints(signature: FloatArray): List<Offset> {
    if (signature.size < 4) return emptyList()
    val points = ArrayList<Offset>(signature.size / 2)
    var i = 0
    while (i + 1 < signature.size) {
        points.add(Offset(signature[i], signature[i + 1]))
        i += 2
    }
    return points
}
