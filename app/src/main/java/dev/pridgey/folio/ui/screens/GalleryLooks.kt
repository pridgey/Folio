package dev.pridgey.folio.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.pridgey.folio.ui.theme.Display
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.Inter
import dev.pridgey.folio.ui.theme.InterDisplay

// The Gallery family: spines made from their covers' art, each a different take on the book itself.
// The room stays out of the way: the page's own colour and a thin ledge.

/** A slice of the cover's art, darkening to the foot, with the title in small spaced capitals there. */
internal open class GalleryLook : ShelfLook() {
    override val lift = 10f

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        SpineFrame(b, pull) {
            CoverArt(b.item, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.04f), 0.5f to Color.Black.copy(alpha = 0.2f), 1f to Color.Black.copy(alpha = 0.86f))))
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * 0.5f).padding(bottom = 12.dp), contentAlignment = Alignment.BottomCenter) {
                SpineTitle(b.item.title.uppercase(), TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.16.em), Color.White, 8.5.sp, min = 6.5.sp)
            }
        }
    }

    @Composable
    override fun Ledge(dark: Boolean) = Rule(Folio.colors.label.copy(alpha = 0.6f), 2.dp)
}

/**
 * What every book here shares: its size, the slight round of a spine, a shadow that deepens as it
 * lifts, and a touch of shade at rest that the lift takes away.
 */
@Composable
internal fun SpineFrame(
    b: Book,
    pull: Float,
    corner: Dp = 1.dp,
    rest: Float = 0.08f,
    round: Float = 1.3f,
    background: Color = tone(b.cover, Color(0xFF3A342E), 0.2f..0.6f, 0.2f..0.4f),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .width(b.width)
            .height(b.height)
            .graphicsLayer {
                shape = RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = corner / 2, bottomEnd = corner / 2)
                clip = true
                shadowElevation = (2.5f + 10f * pull).dp.toPx()
            }
            .background(background)
            .drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = rest * (1f - pull)))
                roundSpine(round)
                drawRect(Color.White.copy(alpha = 0.22f), size = Size(size.width, 1.dp.toPx()))
            },
        content = content,
    )
}

/** Maps the art's darks to [shadow] and lights to [light]: a two-colour print. */
internal fun duotone(shadow: Color, light: Color): ColorFilter {
    fun row(d: Float, l: Float) = floatArrayOf((l - d) * 0.299f, (l - d) * 0.587f, (l - d) * 0.114f, 0f, d * 255f)
    return ColorFilter.colorMatrix(ColorMatrix(row(shadow.red, light.red) + row(shadow.green, light.green) + row(shadow.blue, light.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)))
}

// ------------------------------------------------------------------ 1. Obi

/**
 * A new volume in its obi: the paper band publishers wrap around the lower part of a book. The band
 * sits on top of the art, casting a slight shadow, with the title and chapter count printed on it.
 */
internal object ObiLook : GalleryLook() {
    private val paper = Color(0xFFF5F1E8)

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val ink = tone(b.cover, Color(0xFF9A2B2B), 0.55f..0.85f, 0.3f..0.42f)
        SpineFrame(b, pull) {
            CoverArt(b.item, Modifier.fillMaxSize(), Alignment.TopCenter)
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * 0.38f)) {
                // The band's top edge, lifting off the cover.
                Box(Modifier.fillMaxWidth().height(4.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)))))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(paper)
                        .drawBehind { drawRect(Color.White, size = Size(size.width, 0.75.dp.toPx())) }
                        .padding(horizontal = 3.dp)
                        .padding(top = 7.dp, bottom = 6.dp),
                ) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            SpineTitle(b.item.title, TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold), ink, 11.sp, min = 6.5.sp)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.5f)))
                        BasicText(
                            b.item.total.toString(),
                            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, color = ink, fontSize = 9.sp),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(6.sp, 9.sp, 0.5.sp),
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 2. Close-up

/** Zoomed in on the top of the cover, where the faces usually are, with the title set from the head. */
internal object CloseUpLook : GalleryLook() {
    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val shadowPx = with(LocalDensity.current) { 6.dp.toPx() }
        SpineFrame(b, pull) {
            CoverArt(
                b.item,
                Modifier.fillMaxSize().graphicsLayer {
                    // Push in on the upper third, drifting a touch closer as it lifts.
                    val s = 1.75f + 0.1f * pull
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0.5f, 0.2f)
                },
                Alignment.TopCenter,
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.4f to Color.Transparent, 0.85f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.35f))))
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(b.height * 0.62f).padding(top = 10.dp), contentAlignment = Alignment.TopCenter) {
                SpineTitle(
                    b.item.title,
                    TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.01.em, shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = shadowPx)),
                    Color.White, 13.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 3. Tinted

/** The art printed in two tones of the cover's own colour, with a solid block for the title. */
internal object TintedLook : GalleryLook() {
    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val fallback = (Spines.pick(b.item.id, 6) * 60f + 20f) % 360f
        val deep = hue(b.cover, 0.6f, 0.17f, fallback)
        val light = hue(b.cover, 0.4f, 0.88f, fallback)
        val block = hue(b.cover, 0.55f, 0.32f, fallback)
        SpineFrame(b, pull, background = light) {
            CoverArt(b.item, Modifier.fillMaxSize(), colorFilter = duotone(deep, light))
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * 0.3f).background(block).padding(horizontal = 4.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                SpineTitle(b.item.title.uppercase(), TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.05.em), light, 11.sp, min = 6.5.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ 4. Gloss

/** A laminated paperback: crisp edges, a rounder spine, and a streak of light that moves as it lifts. */
internal object GlossLook : GalleryLook() {
    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val shadowPx = with(LocalDensity.current) { 3.dp.toPx() }
        SpineFrame(b, pull, corner = 2.dp, round = 2f) {
            CoverArt(b.item, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)))
                    // The streak: a narrow diagonal band of light, sliding as the book comes out.
                    val at = size.height * (0.18f + 0.22f * pull) + (b.index % 3) * size.height * 0.04f
                    fun streak(y: Float, reach: Float, alpha: Float) = drawRect(
                        Brush.linearGradient(
                            0f to Color.Transparent, 0.42f to Color.White.copy(alpha = alpha), 0.58f to Color.White.copy(alpha = alpha), 1f to Color.Transparent,
                            start = Offset(0f, y), end = Offset(size.width, y + reach),
                        ),
                    )
                    streak(at, size.width * 2.2f, 0.5f)
                    streak(at + size.height * 0.16f, size.width * 1.2f, 0.22f)
                    // Laminate catching light on the edges.
                    drawRect(Color.White.copy(alpha = 0.4f), size = Size(1.dp.toPx(), size.height))
                    drawRect(Color.White.copy(alpha = 0.35f), size = Size(size.width, 1.dp.toPx()))
                },
            )
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * 0.42f).padding(bottom = 10.dp), contentAlignment = Alignment.BottomCenter) {
                SpineTitle(
                    b.item.title,
                    TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), shadowPx)),
                    Color.White, 10.5.sp,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 5. Cloth

/** Clothbound in the cover's colour, the art faint in the weave, and the title pressed into it. */
internal object ClothLook : GalleryLook() {
    private val weave: ImageBitmap by lazy {
        val n = 6
        val pixels = IntArray(n * n) { i ->
            val x = i % n
            val y = i / n
            // Over-under threads: alternate light warp and dark weft.
            val warp = (x + y / 3) % 3 == 0
            if (warp) android.graphics.Color.argb(46, 255, 255, 255) else if ((y + x / 3) % 3 == 0) android.graphics.Color.argb(40, 0, 0, 0) else 0
        }
        Bitmap.createBitmap(pixels, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val cloth = tone(b.cover, Color(0xFF3D4A5C), 0.25f..0.5f, 0.22f..0.34f)
        val pressed = shade(cloth, -0.13f)
        val lit = shade(cloth, 0.3f)
        SpineFrame(b, pull, background = cloth, round = 1.6f) {
            CoverArt(
                b.item,
                Modifier.fillMaxSize().graphicsLayer { alpha = 0.5f },
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.45f) }),
            )
            Box(
                Modifier.fillMaxSize().drawWithCache {
                    val texture = ShaderBrush(ImageShader(weave, TileMode.Repeated, TileMode.Repeated))
                    onDrawBehind {
                        drawRect(cloth.copy(alpha = 0.22f))
                        drawRect(texture)
                        drawRect(Grain.brush(), alpha = 0.3f)
                    }
                },
            )
            // Debossed: a lighter edge below the pressed letters.
            Box(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                val style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.12.em)
                // A plate of cloth behind the title, pressed slightly deeper, keeps it legible over the art.
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(1.dp)).background(cloth.copy(alpha = 0.82f)))
                SpineTitle(b.item.title.uppercase(), style, lit, 11.sp, Modifier.offset(x = (-0.8).dp))
                SpineTitle(b.item.title.uppercase(), style, pressed, 11.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ 6. Poster

/** The title, huge and condensed, filling the spine over the art. */
internal object PosterLook : GalleryLook() {
    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        SpineFrame(b, pull) {
            CoverArt(b.item, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f)))))
            Box(Modifier.fillMaxSize().padding(horizontal = 2.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                BasicText(
                    b.item.title.uppercase(),
                    style = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = (-0.01).em, lineHeight = 0.86.em),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(9.sp, 26.sp, 0.5.sp),
                    modifier = Modifier.downward(),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 7. Logo

/**
 * The cover turned on its side, so the band across its top (where the series logo usually is) runs
 * down the spine, as publishers design them; a block of the cover's colour holds the chapter count.
 */
internal object LogoLook : GalleryLook() {
    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        val block = tone(b.cover, Color(0xFF2E2A26), 0.4f..0.75f, 0.28f..0.45f)
        SpineFrame(b, pull, background = block) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.fillMaxSize().downward()) {
                        CoverArt(b.item, Modifier.fillMaxSize(), Alignment.TopCenter)
                    }
                }
                Box(Modifier.fillMaxWidth().height(28.dp).background(block), contentAlignment = Alignment.Center) {
                    BasicText(
                        b.item.total.toString(),
                        style = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold, color = inkFor(block), fontSize = 11.sp),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(7.sp, 11.sp, 0.5.sp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 8. Numbered

/** A white volume plate at the foot, with how many chapters you've read (or the total, if none). */
internal object NumberedLook : GalleryLook() {
    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        SpineFrame(b, pull) {
            CoverArt(b.item, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 3.dp).padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(b.height * 0.36f).padding(bottom = 6.dp), contentAlignment = Alignment.BottomCenter) {
                    SpineTitle(b.item.title.uppercase(), TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em), Color.White, 8.sp, min = 6.sp)
                }
                Box(
                    Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFF7F5F0)),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        (if (b.started) b.read else b.item.total).toString(),
                        style = TextStyle(fontFamily = InterDisplay, fontWeight = FontWeight.ExtraBold, color = if (b.started) Color(0xFF111111) else Color(0xFF111111).copy(alpha = 0.45f), letterSpacing = (-0.03).em),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(8.sp, 14.sp, 0.5.sp),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 9. Vintage

/** Faded, warm, well-loved copies: the more you've read one, the more its spine is creased. */
internal object VintageLook : GalleryLook() {
    private val cream = Color(0xFFF1E6CF)

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        // Reading creases a spine: one crease to start with, up to five for a long read.
        val creases = if (b.started) (1 + b.read / 40).coerceAtMost(5) else 0
        SpineFrame(b, pull, corner = 2.dp, background = Color(0xFF6B5A44)) {
            CoverArt(
                b.item,
                Modifier.fillMaxSize(),
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.5f) }),
            )
            Box(
                Modifier.fillMaxSize().drawBehind {
                    // Sun-faded and yellowed.
                    drawRect(Color(0xFFB8864B).copy(alpha = 0.22f))
                    drawRect(Color(0xFFFFF3DD).copy(alpha = 0.12f))
                    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xFF2A1C0E).copy(alpha = 0.75f)))
                    // Worn edges, lighter where hands have rubbed.
                    drawRect(Brush.horizontalGradient(0f to Color(0xFFFFF0D8).copy(alpha = 0.3f), 0.12f to Color.Transparent, 0.88f to Color.Transparent, 1f to Color(0xFFFFF0D8).copy(alpha = 0.22f)))
                    drawRect(Brush.verticalGradient(0f to Color(0xFFFFF0D8).copy(alpha = 0.25f), 0.04f to Color.Transparent))
                    // Creases: pale lines across the spine.
                    val random = java.util.Random(b.item.id)
                    repeat(creases) {
                        val y = size.height * (0.12f + random.nextFloat() * 0.6f)
                        val tilt = (random.nextFloat() - 0.5f) * 4.dp.toPx()
                        drawLine(Color(0xFFFFF3DD).copy(alpha = 0.45f), Offset(0f, y), Offset(size.width, y + tilt), strokeWidth = 0.8.dp.toPx())
                        drawLine(Color.Black.copy(alpha = 0.2f), Offset(0f, y + 1.dp.toPx()), Offset(size.width, y + tilt + 1.dp.toPx()), strokeWidth = 0.6.dp.toPx())
                    }
                    drawRect(Grain.brush(), alpha = 0.55f)
                },
            )
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * 0.45f).padding(bottom = 11.dp), contentAlignment = Alignment.BottomCenter) {
                SpineTitle(b.item.title, TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em), cream, 11.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ 10. Foil

/** A holographic sheen over the art that shifts as you scrub, and a title stamped in silver foil. */
internal object FoilLook : GalleryLook() {
    private val rainbow = listOf(Color(0xFFFF7AD9), Color(0xFFFFE27A), Color(0xFF7AFFC4), Color(0xFF7AC8FF), Color(0xFFC27AFF), Color(0xFFFF7AD9))
    private val silver = Brush.linearGradient(listOf(Color(0xFFF4F6FA), Color(0xFFA9B0BB), Color(0xFFFFFFFF), Color(0xFF8E96A3)))

    @Composable
    override fun Spine(b: Book, pull: Float, dark: Boolean) {
        SpineFrame(b, pull, corner = 1.5.dp) {
            CoverArt(b.item, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().drawBehind {
                    drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)))
                    // The sheen: a band of colour, its position set by the book and moved by the lift.
                    val shift = size.height * ((b.index * 0.37f) % 1f - 0.5f + pull * 0.6f)
                    drawRect(
                        Brush.linearGradient(rainbow.map { it.copy(alpha = 0.3f + 0.15f * pull) }, start = Offset(0f, shift), end = Offset(size.width * 2.5f, shift + size.height)),
                    )
                    drawRect(Color.White.copy(alpha = 0.3f), size = Size(1.dp.toPx(), size.height))
                },
            )
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(b.height * 0.45f).padding(bottom = 11.dp), contentAlignment = Alignment.BottomCenter) {
                BasicText(
                    b.item.title.uppercase(),
                    style = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.em, lineHeight = 1.em, brush = silver),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(6.5.sp, 10.sp, 0.5.sp),
                    modifier = Modifier.downward(),
                )
            }
        }
    }
}
