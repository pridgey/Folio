package dev.pridgey.folio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.pridgey.folio.ui.theme.Display
import dev.pridgey.folio.ui.theme.Heading
import dev.pridgey.folio.ui.theme.Inter
import dev.pridgey.folio.ui.theme.InterDisplay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// A shelf where every book is its own: built on the Gallery spine (a slice of the cover's art), each
// title draws a recipe for how its art is framed, how it's finished, and how its title is set. The
// recipe comes from the title alone, so a book always looks the same.

/** How a book's art is framed. */
internal enum class ArtFrame { STANDARD, CLOSE_UP, TILTED, SIDEWAYS }

/** A light treatment over the whole spine. */
internal enum class Finish { PLAIN, FOIL, VINTAGE }

/** Where and how the title sits. */
internal enum class TitleSpot { FOOT, HEAD, BAND, RIBBON, POSTER, OBI }

internal data class SpineRecipe(
    val frame: ArtFrame,
    /** How far in the art is pushed, and the point it's pushed towards (0..1 across, down). */
    val zoom: Float,
    val focusX: Float,
    val focusY: Float,
    /** Degrees the art is turned, for TILTED. */
    val tilt: Float,
    val finish: Finish,
    val spot: TitleSpot,
    /** Which of [TitleFaces] sets the title. */
    val face: Int,
    /** Reads bottom to top instead of top to bottom. */
    val up: Boolean,
)

/** Draws a book's recipe from [seed]: the same seed always gives the same book. */
internal fun recipeFor(seed: Long): SpineRecipe {
    val r = java.util.Random(seed * 0x9E3779B97F4A7C15uL.toLong() + 17)
    fun weighted(vararg weights: Int): Int {
        var roll = r.nextInt(weights.sum())
        weights.forEachIndexed { i, w -> if (roll < w) return i else roll -= w }
        return weights.lastIndex
    }
    val frame = ArtFrame.entries[weighted(35, 40, 15, 10)]
    val zoom = when (frame) {
        ArtFrame.STANDARD, ArtFrame.SIDEWAYS -> 1f
        ArtFrame.CLOSE_UP -> 1.45f + r.nextFloat() * 0.5f
        ArtFrame.TILTED -> 1f // worked out from the spine's shape when drawn
    }
    val focusX = 0.35f + r.nextFloat() * 0.3f
    val focusY = 0.12f + r.nextFloat() * 0.22f
    val tilt = (4f + r.nextFloat() * 4f) * if (r.nextBoolean()) 1f else -1f
    val finish = Finish.entries[weighted(70, 15, 15)]
    // A sideways cover keeps its logo clear: its title goes in a band at the foot.
    val spot = if (frame == ArtFrame.SIDEWAYS) TitleSpot.BAND else TitleSpot.entries[weighted(30, 20, 18, 12, 10, 10)]
    val face = r.nextInt(TitleFaces.size)
    val up = r.nextInt(100) < 15
    return SpineRecipe(frame, zoom, focusX, focusY, tilt, finish, spot, face, up)
}

/** A title face: family, weight, case, spacing and the largest size it's set at. */
internal class TitleFace(val family: FontFamily, val weight: FontWeight, val upper: Boolean, val tracking: Float, val max: TextUnit) {
    fun style() = TextStyle(fontFamily = family, fontWeight = weight, letterSpacing = tracking.em)
    fun text(title: String) = if (upper) title.uppercase() else title
}

internal val TitleFaces = listOf(
    TitleFace(Display, FontWeight.ExtraBold, upper = false, tracking = 0.01f, max = 13.sp),
    TitleFace(Inter, FontWeight.SemiBold, upper = true, tracking = 0.16f, max = 8.5.sp),
    TitleFace(Heading, FontWeight.Bold, upper = false, tracking = 0f, max = 12.sp),
    TitleFace(InterDisplay, FontWeight.ExtraBold, upper = true, tracking = -0.01f, max = 11.5.sp),
    TitleFace(Display, FontWeight.Black, upper = true, tracking = 0.05f, max = 12.5.sp),
    TitleFace(Inter, FontWeight.Medium, upper = false, tracking = 0.01f, max = 10.5.sp),
)

internal object AssortedLook : GalleryLook() {
    private val obiPaper = Color(0xFFF5F1E8)
    private val rainbow = listOf(Color(0xFFFF7AD9), Color(0xFFFFE27A), Color(0xFF7AFFC4), Color(0xFF7AC8FF), Color(0xFFC27AFF), Color(0xFFFF7AD9))
    private val silver = Brush.linearGradient(listOf(Color(0xFFF4F6FA), Color(0xFFA9B0BB), Color(0xFFFFFFFF), Color(0xFF8E96A3)))

    /** Stamped in silver foil, when the book has the foil finish. */
    private fun TextStyle.foiled(on: Boolean) = if (on) copy(brush = silver) else this

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val recipe = remember(b.seed) { recipeFor(b.seed) }
        val accent = tone(b.cover, Color(0xFF8C2F2F), 0.5f..0.8f, 0.28f..0.42f)
        SpineFrame(b, pull) {
            Art(b, recipe, pull)
            Finishing(b, recipe, pull)
            Title(b, recipe, accent)
        }
    }

    @Composable
    private fun BoxScope.Art(b: Book, recipe: SpineRecipe, pull: Float) {
        val filter = if (recipe.finish == Finish.VINTAGE) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.72f) }) else null
        when (recipe.frame) {
            ArtFrame.SIDEWAYS -> Column(Modifier.fillMaxSize()) {
                // The cover on its side: the band across its top, usually the logo, runs down the spine.
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.fillMaxSize().downward()) { CoverArt(b.item, Modifier.fillMaxSize(), Alignment.TopCenter, filter) }
                }
                Box(Modifier.fillMaxWidth().height(b.height * 0.28f))
            }
            else -> {
                val aspect = b.height.value / b.width.value
                CoverArt(
                    b.item,
                    Modifier.fillMaxSize().graphicsLayer {
                        when (recipe.frame) {
                            ArtFrame.CLOSE_UP -> {
                                val s = recipe.zoom + 0.08f * pull
                                scaleX = s
                                scaleY = s
                                transformOrigin = TransformOrigin(recipe.focusX, recipe.focusY)
                            }
                            ArtFrame.TILTED -> {
                                // Just enough zoom that the turned art still covers the spine's corners.
                                val t = recipe.tilt * PI.toFloat() / 180f
                                val s = (abs(cos(t)) + aspect * abs(sin(t))) * 1.04f + 0.06f * pull
                                rotationZ = recipe.tilt
                                scaleX = s
                                scaleY = s
                                transformOrigin = TransformOrigin(0.5f, 0.45f)
                            }
                            else -> Unit
                        }
                    },
                    if (recipe.frame == ArtFrame.STANDARD) Alignment.Center else Alignment.TopCenter,
                    filter,
                )
            }
        }
    }

    /** Foil and vintage, kept quiet: a hint of each, not a costume. */
    @Composable
    private fun BoxScope.Finishing(b: Book, recipe: SpineRecipe, pull: Float) {
        when (recipe.finish) {
            Finish.PLAIN -> Unit
            Finish.FOIL -> Box(
                Modifier.matchParentSize().drawBehind {
                    val shift = size.height * (((b.seed % 97) / 97f) - 0.5f + pull * 0.6f)
                    drawRect(Brush.linearGradient(rainbow.map { it.copy(alpha = 0.13f + 0.1f * pull) }, start = Offset(0f, shift), end = Offset(size.width * 2.5f, shift + size.height)))
                    drawRect(Color.White.copy(alpha = 0.22f), size = Size(1.dp.toPx(), size.height))
                },
            )
            Finish.VINTAGE -> Box(
                Modifier.matchParentSize().drawBehind {
                    drawRect(Color(0xFFB8864B).copy(alpha = 0.13f))
                    drawRect(Brush.horizontalGradient(0f to Color(0xFFFFF0D8).copy(alpha = 0.16f), 0.1f to Color.Transparent, 0.9f to Color.Transparent, 1f to Color(0xFFFFF0D8).copy(alpha = 0.12f)))
                    // A crease or two once you've been reading it.
                    if (b.started) {
                        val random = java.util.Random(b.seed)
                        repeat((1 + b.read / 60).coerceAtMost(3)) {
                            val y = size.height * (0.15f + random.nextFloat() * 0.55f)
                            val tilt = (random.nextFloat() - 0.5f) * 3.dp.toPx()
                            drawLine(Color(0xFFFFF3DD).copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y + tilt), strokeWidth = 0.7.dp.toPx())
                        }
                    }
                    drawRect(Grain.brush(), alpha = 0.32f)
                },
            )
        }
    }

    @Composable
    private fun BoxScope.Title(b: Book, recipe: SpineRecipe, accent: Color) {
        val face = TitleFaces[recipe.face]
        val text = face.text(b.item.title)
        val foil = recipe.finish == Finish.FOIL
        val shadowPx = with(LocalDensity.current) { 5.dp.toPx() }
        // White on the art, or silver for foil; a soft shadow keeps it clear of busy art.
        val onArt = face.style().copy(shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = shadowPx)).foiled(foil)

        // Bands and obis grow with the title, so long titles aren't squeezed into a sliver.
        val bandShare = (0.24f + text.length * 0.0085f).coerceIn(0.28f, 0.5f)
        when (recipe.spot) {
            TitleSpot.FOOT -> {
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * 0.5f).padding(bottom = 11.dp, start = 3.dp, end = 3.dp), contentAlignment = Alignment.BottomCenter) {
                    SpineTitle(text, onArt, Color.White, face.max, min = 6.5.sp, up = recipe.up)
                }
            }
            TitleSpot.HEAD -> {
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.72f), 0.42f to Color.Transparent)))
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(b.height * 0.6f).padding(top = 10.dp, start = 3.dp, end = 3.dp), contentAlignment = Alignment.TopCenter) {
                    SpineTitle(text, onArt, Color.White, face.max, min = 6.5.sp, up = recipe.up)
                }
            }
            TitleSpot.BAND -> {
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * bandShare).background(accent).padding(horizontal = 3.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SpineTitle(text, face.style().foiled(foil), inkFor(accent), face.max, min = 6.sp, up = recipe.up)
                }
            }
            TitleSpot.RIBBON -> {
                // A strip of the cover's colour down the middle, the title running along it.
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.12f)))
                Box(
                    Modifier.align(Alignment.Center).fillMaxWidth(0.66f).fillMaxHeight(0.62f).background(accent.copy(alpha = 0.94f)).padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SpineTitle(text, face.style().foiled(foil), inkFor(accent), face.max, min = 6.sp, up = recipe.up)
                }
            }
            TitleSpot.POSTER -> {
                Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.5f)))))
                Box(Modifier.matchParentSize().padding(horizontal = 2.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    BasicText(
                        b.item.title.uppercase(),
                        style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-0.01).em, lineHeight = 0.86.em).foiled(foil),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(9.sp, 24.sp, 0.5.sp),
                        modifier = if (recipe.up) Modifier.upward() else Modifier.downward(),
                    )
                }
            }
            TitleSpot.OBI -> Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * (bandShare + 0.06f))) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.26f)))))
                Box(
                    Modifier.fillMaxWidth().weight(1f).background(obiPaper)
                        .drawBehind { drawRect(Color.White, size = Size(size.width, 0.75.dp.toPx())) }
                        .padding(horizontal = 3.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SpineTitle(text, face.style(), accent, face.max, min = 6.sp, up = recipe.up)
                }
            }
        }
    }
}
