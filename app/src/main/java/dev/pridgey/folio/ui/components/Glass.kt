package dev.pridgey.folio.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/**
 * Frosted glass for bars that float over scrolling content. The content draws into [source]; each
 * bar draws a blurred copy of what's behind it, then a tint. Blur needs Android 12; older phones get
 * a denser tint instead.
 */
@Stable
class GlassState internal constructor(internal val source: GraphicsLayer, internal val blurred: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
    internal var size by mutableStateOf(IntSize.Zero)

    companion object {
        // Robolectric's renderer crashes on RenderEffect; screenshot tests get the tint fallback.
        val blurSupported = Build.VERSION.SDK_INT >= 31 && Build.FINGERPRINT != "robolectric"
    }
}

@Composable
fun rememberGlassState(): GlassState {
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    return remember(source, blurred) { GlassState(source, blurred) }
}

/** Marks the content that glass bars blur. */
fun Modifier.glassSource(state: GlassState): Modifier = this
    .onGloballyPositioned {
        state.origin = it.positionInRoot()
        state.size = it.size
    }
    .drawWithContent {
        state.source.record { this@drawWithContent.drawContent() }
        drawLayer(state.source)
    }

/** Paints frosted glass made from what [state]'s content shows behind this element. */
fun Modifier.glass(state: GlassState, tint: Color, radius: Dp = 28.dp): Modifier {
    var position = Offset.Zero
    return this
        .onGloballyPositioned { position = it.positionInRoot() }
        .drawBehind {
            if (GlassState.blurSupported && state.size != IntSize.Zero) {
                val r = radius.toPx()
                state.blurred.renderEffect = BlurEffect(r, r, TileMode.Clamp)
                state.blurred.record(state.size) { drawLayer(state.source) }
                clipRect {
                    translate(state.origin.x - position.x, state.origin.y - position.y) {
                        drawLayer(state.blurred)
                    }
                }
                drawRect(tint)
            } else {
                drawRect(tint.copy(alpha = (tint.alpha + 0.2f).coerceAtMost(0.97f)))
            }
        }
}

@Suppress("unused")
private fun IntSize.isEmpty() = toSize().minDimension <= 0f
