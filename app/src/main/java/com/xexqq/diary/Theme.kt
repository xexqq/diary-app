package com.xexqq.diary

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val OFF_COLOR = Color(0xFFE5484D)
val VAC_COLOR = Color(0xFF2FB872)

val LocalT = staticCompositionLocalOf { THEMES[0] }
val LocalG = compositionLocalOf { 0f }
val LocalWide = staticCompositionLocalOf { false }

/** Плавная смена цветов темы. Новый объект создаётся только когда цвет реально меняется. */
@Composable
fun animateTheme(t: AppTheme): AppTheme {
    val sp = tween<Color>(450)
    val bg by animateColorAsState(t.bg, sp, label = "bg")
    val bg2 by animateColorAsState(t.bg2, sp, label = "bg2")
    val card by animateColorAsState(t.card, sp, label = "card")
    val ink by animateColorAsState(t.ink, sp, label = "ink")
    val muted by animateColorAsState(t.muted, sp, label = "muted")
    val accent by animateColorAsState(t.accent, sp, label = "accent")
    val onAccent by animateColorAsState(t.onAccent, sp, label = "onAccent")
    val line by animateColorAsState(t.line, sp, label = "line")
    val b1 by animateColorAsState(t.blob1, sp, label = "b1")
    val b2 by animateColorAsState(t.blob2, sp, label = "b2")
    return remember(t.id, bg, bg2, card, ink, muted, accent, onAccent, line, b1, b2) {
        AppTheme(t.id, t.name, bg, bg2, card, ink, muted, accent, onAccent, line, t.dark, b1, b2, t.grid)
    }
}

@Composable
fun Tx(
    s: String, c: Color, size: TextUnit = 16.sp, w: FontWeight? = null,
    m: Modifier = Modifier, td: TextDecoration? = null
) = Text(s, m, color = c, fontSize = size, fontWeight = w, textDecoration = td)

fun hueColor(s: String) = Color.hsv((((s.hashCode() % 360) + 360) % 360).toFloat(), .55f, .62f)

/** Фон: цвет темы, тетрадная клетка и цветные пятна для режима стекла. Рисуется с кэшем. */
@Composable
fun Modifier.backdrop(): Modifier {
    val t = LocalT.current
    val g = LocalG.current
    val base = remember(t.bg, t.bg2) { Brush.verticalGradient(listOf(t.bg, t.bg2)) }
    return this
        .background(base)
        .drawWithCache {
            val w = size.width
            val h = size.height
            val grid = Path()
            if (t.grid) {
                val step = 22.dp.toPx()
                var x = 0f
                while (x < w) {
                    grid.moveTo(x, 0f); grid.lineTo(x, h); x += step
                }
                var y = 0f
                while (y < h) {
                    grid.moveTo(0f, y); grid.lineTo(w, y); y += step
                }
            }
            val c1 = Offset(w * .1f, h * .15f)
            val r1 = w * .9f
            val c2 = Offset(w * .95f, h * .5f)
            val r2 = w * .8f
            val c3 = Offset(w * .15f, h * .95f)
            val r3 = w * .7f
            val b1 = Brush.radialGradient(listOf(t.blob1.copy(alpha = .75f), Color.Transparent), center = c1, radius = r1)
            val b2 = Brush.radialGradient(listOf(t.blob2.copy(alpha = .75f), Color.Transparent), center = c2, radius = r2)
            val b3 = Brush.radialGradient(listOf(t.accent.copy(alpha = .75f), Color.Transparent), center = c3, radius = r3)
            onDrawBehind {
                if (t.grid && g < 1f) drawPath(grid, t.line, alpha = 1f - g, style = Stroke(width = 1f))
                if (g > 0f) {
                    drawCircle(b1, radius = r1, center = c1, alpha = g)
                    drawCircle(b2, radius = r2, center = c2, alpha = g)
                    drawCircle(b3, radius = r3, center = c3, alpha = g)
                }
            }
        }
}

/** Панель: плавно переходит между обычной карточкой и «жидким стеклом» (g от 0 до 1). */
@Composable
fun Modifier.panel(shape: Shape): Modifier {
    val t = LocalT.current
    val g = LocalG.current
    val fill = remember(t.card, t.dark, g) {
        val hi = if (t.dark) .16f else .42f
        val lo = if (t.dark) .05f else .16f
        Brush.verticalGradient(
            listOf(
                lerp(t.card, Color.White.copy(alpha = hi), g),
                lerp(t.card, Color.White.copy(alpha = lo), g)
            )
        )
    }
    val edge = remember(t.line, g) {
        Brush.linearGradient(
            listOf(
                lerp(t.line, Color.White.copy(alpha = .85f), g),
                lerp(t.line, Color.White.copy(alpha = .08f), g),
                lerp(t.line, Color.White.copy(alpha = .45f), g)
            )
        )
    }
    return this.clip(shape).background(fill).border(1.dp, edge, shape)
}

/** Нажатие с лёгким «пружинящим» сжатием. Ставить раньше clip/background. */
@Composable
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) .94f else 1f, spring(dampingRatio = .5f, stiffness = 500f), label = "press")
    return this
        .graphicsLayer {
            scaleX = s
            scaleY = s
        }
        .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

/** Плавное появление снизу с задержкой по номеру элемента. */
@Composable
fun Modifier.appear(index: Int, key: Any? = null): Modifier {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        a.animateTo(1f, tween(380, delayMillis = (index * 50).coerceAtMost(350), easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 28.dp.toPx()
    }
}

@Composable
fun ArrowBtn(s: String, enabled: Boolean, onClick: () -> Unit) {
    val t = LocalT.current
    Tx(
        s, if (enabled) t.ink else t.muted.copy(alpha = .35f), 30.sp, FontWeight.Bold,
        Modifier.bounceClick(enabled = enabled, onClick = onClick).clip(CircleShape)
            .padding(horizontal = 18.dp, vertical = 2.dp)
    )
}

@Composable
fun fieldColors(): androidx.compose.material3.TextFieldColors {
    val t = LocalT.current
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = t.ink,
        unfocusedTextColor = t.ink,
        focusedBorderColor = t.accent,
        unfocusedBorderColor = t.line,
        cursorColor = t.accent
    )
}

@Composable
fun checkColors(): androidx.compose.material3.CheckboxColors {
    val t = LocalT.current
    return CheckboxDefaults.colors(checkedColor = t.accent, checkmarkColor = t.onAccent, uncheckedColor = t.muted)
}

@Composable
fun switchColors(): androidx.compose.material3.SwitchColors {
    val t = LocalT.current
    return SwitchDefaults.colors(
        checkedThumbColor = t.onAccent,
        checkedTrackColor = t.accent,
        uncheckedThumbColor = t.muted,
        uncheckedTrackColor = t.line,
        uncheckedBorderColor = t.muted
    )
}
