package com.lifttracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dedicated Canvas vector icons tailored for Lift Tracker, styled to match
 * the HealthAnalyzer athletic icon set.
 */
object AppIcons {

    @Composable
    fun Barbell(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            // Center bar + sleeve ends
            drawLine(tint, Offset(w * 0.10f, h * 0.50f), Offset(w * 0.90f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
            // Inner large plates
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.22f, h * 0.24f),
                size = Size(w * 0.11f, h * 0.52f),
                cornerRadius = CornerRadius(w * 0.04f),
                style = Stroke(width = sw),
            )
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.67f, h * 0.24f),
                size = Size(w * 0.11f, h * 0.52f),
                cornerRadius = CornerRadius(w * 0.04f),
                style = Stroke(width = sw),
            )
            // Outer plates
            drawLine(tint, Offset(w * 0.14f, h * 0.34f), Offset(w * 0.14f, h * 0.66f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.86f, h * 0.34f), Offset(w * 0.86f, h * 0.66f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Analytics(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            drawLine(tint, Offset(w * 0.22f, h * 0.82f), Offset(w * 0.22f, h * 0.54f), strokeWidth = sw * 1.15f, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.48f, h * 0.82f), Offset(w * 0.48f, h * 0.38f), strokeWidth = sw * 1.15f, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.74f, h * 0.82f), Offset(w * 0.74f, h * 0.22f), strokeWidth = sw * 1.15f, cap = StrokeCap.Round)

            // Progression spark line above bars
            val trend = Path().apply {
                moveTo(w * 0.18f, h * 0.42f)
                lineTo(w * 0.44f, h * 0.26f)
                lineTo(w * 0.60f, h * 0.30f)
                lineTo(w * 0.82f, h * 0.14f)
            }
            drawPath(trend, color = tint, style = Stroke(width = sw * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Clipboard(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.20f, h * 0.20f),
                size = Size(w * 0.60f, h * 0.68f),
                cornerRadius = CornerRadius(w * 0.10f),
                style = Stroke(width = sw),
            )
            // Top clip
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.36f, h * 0.12f),
                size = Size(w * 0.28f, h * 0.14f),
                cornerRadius = CornerRadius(w * 0.05f),
                style = Stroke(width = sw * 0.9f),
            )
            // Routine checklist lines
            drawLine(tint, Offset(w * 0.34f, h * 0.44f), Offset(w * 0.66f, h * 0.44f), strokeWidth = sw * 0.9f, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.34f, h * 0.58f), Offset(w * 0.66f, h * 0.58f), strokeWidth = sw * 0.9f, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.34f, h * 0.72f), Offset(w * 0.54f, h * 0.72f), strokeWidth = sw * 0.9f, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Tune(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            drawLine(tint, Offset(w * 0.15f, h * 0.28f), Offset(w * 0.85f, h * 0.28f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.38f, h * 0.28f))

            drawLine(tint, Offset(w * 0.15f, h * 0.52f), Offset(w * 0.85f, h * 0.52f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.65f, h * 0.52f))

            drawLine(tint, Offset(w * 0.15f, h * 0.76f), Offset(w * 0.85f, h * 0.76f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(tint, radius = w * 0.10f, center = Offset(w * 0.32f, h * 0.76f))
        }
    }

    @Composable
    fun Palette(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 1.8.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            drawCircle(
                color = tint,
                radius = w * 0.38f,
                center = Offset(w * 0.50f, h * 0.50f),
                style = Stroke(width = sw),
            )
            drawCircle(color = tint, radius = w * 0.065f, center = Offset(w * 0.35f, h * 0.38f))
            drawCircle(color = tint, radius = w * 0.065f, center = Offset(w * 0.50f, h * 0.30f))
            drawCircle(color = tint, radius = w * 0.065f, center = Offset(w * 0.65f, h * 0.38f))
            drawCircle(color = tint, radius = w * 0.075f, center = Offset(w * 0.62f, h * 0.62f), style = Stroke(width = sw * 0.85f))
        }
    }

    @Composable
    fun HeartPulse(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            val p = Path().apply {
                moveTo(w * 0.08f, h * 0.50f)
                lineTo(w * 0.28f, h * 0.50f)
                lineTo(w * 0.40f, h * 0.18f)
                lineTo(w * 0.54f, h * 0.82f)
                lineTo(w * 0.66f, h * 0.38f)
                lineTo(w * 0.74f, h * 0.50f)
                lineTo(w * 0.92f, h * 0.50f)
            }
            drawPath(p, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Watch(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 1.8.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            // Straps
            drawLine(tint, Offset(w * 0.38f, h * 0.12f), Offset(w * 0.62f, h * 0.12f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.38f, h * 0.88f), Offset(w * 0.62f, h * 0.88f), strokeWidth = sw, cap = StrokeCap.Round)
            // Case
            drawCircle(
                color = tint,
                radius = w * 0.28f,
                center = Offset(w * 0.50f, h * 0.50f),
                style = Stroke(width = sw),
            )
            // Hands
            drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.36f), strokeWidth = sw * 0.85f, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.60f, h * 0.56f), strokeWidth = sw * 0.85f, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Database(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 1.8.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            drawOval(
                color = tint,
                topLeft = Offset(w * 0.20f, h * 0.14f),
                size = Size(w * 0.60f, h * 0.20f),
                style = Stroke(width = sw),
            )
            val midPath = Path().apply {
                moveTo(w * 0.20f, h * 0.46f)
                cubicTo(w * 0.20f, h * 0.58f, w * 0.80f, h * 0.58f, w * 0.80f, h * 0.46f)
            }
            drawPath(midPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round))

            val bodyPath = Path().apply {
                moveTo(w * 0.20f, h * 0.24f)
                lineTo(w * 0.20f, h * 0.72f)
                cubicTo(w * 0.20f, h * 0.84f, w * 0.80f, h * 0.84f, w * 0.80f, h * 0.72f)
                lineTo(w * 0.80f, h * 0.24f)
            }
            drawPath(bodyPath, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Cloud(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 1.8.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            val path = Path().apply {
                moveTo(w * 0.24f, h * 0.76f)
                lineTo(w * 0.76f, h * 0.76f)
                cubicTo(w * 0.90f, h * 0.76f, w * 0.94f, h * 0.62f, w * 0.84f, h * 0.52f)
                cubicTo(w * 0.86f, h * 0.36f, w * 0.72f, h * 0.26f, w * 0.58f, h * 0.32f)
                cubicTo(w * 0.50f, h * 0.20f, w * 0.34f, h * 0.24f, w * 0.28f, h * 0.38f)
                cubicTo(w * 0.12f, h * 0.42f, w * 0.10f, h * 0.64f, w * 0.24f, h * 0.76f)
                close()
            }
            drawPath(path, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Folder(
        modifier: Modifier = Modifier.size(18.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 1.8.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            val path = Path().apply {
                moveTo(w * 0.14f, h * 0.26f)
                lineTo(w * 0.40f, h * 0.26f)
                lineTo(w * 0.48f, h * 0.34f)
                lineTo(w * 0.86f, h * 0.34f)
                lineTo(w * 0.86f, h * 0.76f)
                lineTo(w * 0.14f, h * 0.76f)
                close()
            }
            drawPath(path, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ChevronRight(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            val p = Path().apply {
                moveTo(w * 0.35f, h * 0.20f)
                lineTo(w * 0.65f, h * 0.50f)
                lineTo(w * 0.35f, h * 0.80f)
            }
            drawPath(p, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ChevronDown(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            val p = Path().apply {
                moveTo(w * 0.20f, h * 0.35f)
                lineTo(w * 0.50f, h * 0.65f)
                lineTo(w * 0.80f, h * 0.35f)
            }
            drawPath(p, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun ArrowLeft(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height

            drawLine(tint, Offset(w * 0.22f, h * 0.50f), Offset(w * 0.85f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
            val p = Path().apply {
                moveTo(w * 0.45f, h * 0.25f)
                lineTo(w * 0.20f, h * 0.50f)
                lineTo(w * 0.45f, h * 0.75f)
            }
            drawPath(p, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    @Composable
    fun Sparkles(
        modifier: Modifier = Modifier.size(20.dp),
        tint: Color = LocalContentColor.current,
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height

            fun drawSparkle(cx: Float, cy: Float, radius: Float) {
                val p = Path().apply {
                    moveTo(cx, cy - radius)
                    cubicTo(cx, cy - radius * 0.2f, cx + radius * 0.2f, cy, cx + radius, cy)
                    cubicTo(cx + radius * 0.2f, cy, cx, cy + radius * 0.2f, cx, cy + radius)
                    cubicTo(cx, cy + radius * 0.2f, cx - radius * 0.2f, cy, cx - radius, cy)
                    cubicTo(cx - radius * 0.2f, cy, cx, cy - radius * 0.2f, cx, cy - radius)
                    close()
                }
                drawPath(p, color = tint)
            }

            drawSparkle(w * 0.45f, h * 0.45f, w * 0.38f)
            drawSparkle(w * 0.80f, h * 0.22f, w * 0.18f)
            drawSparkle(w * 0.20f, h * 0.78f, w * 0.14f)
        }
    }

    @Composable
    fun Play(
        modifier: Modifier = Modifier.size(16.dp),
        tint: Color = LocalContentColor.current,
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val p = Path().apply {
                moveTo(w * 0.28f, h * 0.20f)
                lineTo(w * 0.82f, h * 0.50f)
                lineTo(w * 0.28f, h * 0.80f)
                close()
            }
            drawPath(p, color = tint)
        }
    }

    @Composable
    fun Plus(
        modifier: Modifier = Modifier.size(18.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height
            drawLine(tint, Offset(w * 0.50f, h * 0.20f), Offset(w * 0.50f, h * 0.80f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.20f, h * 0.50f), Offset(w * 0.80f, h * 0.50f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }

    @Composable
    fun Person(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height
            drawCircle(tint, radius = w * 0.17f, center = Offset(w * 0.50f, h * 0.30f), style = Stroke(width = sw))
            val shoulders = Path().apply {
                moveTo(w * 0.16f, h * 0.86f)
                cubicTo(w * 0.16f, h * 0.52f, w * 0.84f, h * 0.52f, w * 0.84f, h * 0.86f)
            }
            drawPath(shoulders, tint, style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }

    @Composable
    fun Calendar(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current,
        strokeWidth: Dp = 2.dp,
    ) {
        Canvas(modifier = modifier) {
            val sw = strokeWidth.toPx()
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.14f, h * 0.20f),
                size = Size(w * 0.72f, h * 0.66f),
                cornerRadius = CornerRadius(w * 0.10f),
                style = Stroke(width = sw),
            )
            drawLine(tint, Offset(w * 0.14f, h * 0.40f), Offset(w * 0.86f, h * 0.40f), strokeWidth = sw)
            drawLine(tint, Offset(w * 0.33f, h * 0.12f), Offset(w * 0.33f, h * 0.27f), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(tint, Offset(w * 0.67f, h * 0.12f), Offset(w * 0.67f, h * 0.27f), strokeWidth = sw, cap = StrokeCap.Round)
            drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.36f, h * 0.60f))
            drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.64f, h * 0.60f))
        }
    }

    /** Filled flame, for the workout streak. */
    @Composable
    fun Flame(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current,
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val flame = Path().apply {
                moveTo(w * 0.52f, h * 0.04f)
                cubicTo(w * 0.58f, h * 0.30f, w * 0.86f, h * 0.42f, w * 0.84f, h * 0.66f)
                cubicTo(w * 0.82f, h * 0.86f, w * 0.66f, h * 0.96f, w * 0.50f, h * 0.96f)
                cubicTo(w * 0.32f, h * 0.96f, w * 0.16f, h * 0.84f, w * 0.16f, h * 0.64f)
                cubicTo(w * 0.16f, h * 0.48f, w * 0.26f, h * 0.38f, w * 0.32f, h * 0.28f)
                cubicTo(w * 0.36f, h * 0.38f, w * 0.40f, h * 0.42f, w * 0.44f, h * 0.44f)
                cubicTo(w * 0.42f, h * 0.28f, w * 0.44f, h * 0.14f, w * 0.52f, h * 0.04f)
                close()
            }
            drawPath(flame, tint)
        }
    }

    /** Filled crescent moon, for rest days. */
    @Composable
    fun Moon(
        modifier: Modifier = Modifier.size(24.dp),
        tint: Color = LocalContentColor.current,
    ) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val moon = Path().apply {
                moveTo(w * 0.62f, h * 0.08f)
                cubicTo(w * 0.20f, h * 0.10f, w * 0.02f, h * 0.56f, w * 0.26f, h * 0.80f)
                cubicTo(w * 0.48f, h * 1.02f, w * 0.86f, h * 0.92f, w * 0.94f, h * 0.62f)
                cubicTo(w * 0.60f, h * 0.76f, w * 0.34f, h * 0.44f, w * 0.62f, h * 0.08f)
                close()
            }
            drawPath(moon, tint)
        }
    }
}
