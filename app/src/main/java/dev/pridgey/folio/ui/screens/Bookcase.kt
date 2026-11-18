package dev.pridgey.folio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.pridgey.folio.data.LibraryItem
import dev.pridgey.folio.image.CoverModel
import dev.pridgey.folio.ui.components.coverCacheKeys
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.ui.theme.rememberCoverColor
import kotlin.math.ln
import kotlin.math.min

// The library as spines standing on a bookcase. Sources only give covers, so each spine is made from
// a strip of its cover, washed in the cover's colour, with the title running down it.

internal object Spines {
    const val GAP = 2
    private const val MIN_WIDTH = 22f
    private const val MAX_WIDTH = 52f
    const val TALLEST = 188

    /** Longer series make thicker books: grows quickly at first, and stops at about 300 chapters. */
    fun width(chapters: Int): Float {
        val t = min(1.0, ln(1.0 + chapters.coerceAtLeast(0)) / ln(301.0)).toFloat()
        return MIN_WIDTH + (MAX_WIDTH - MIN_WIDTH) * t
    }

    /** Books aren't all the same height. Steady per title, so the shelf doesn't reshuffle. */
    fun height(id: Long): Int = TALLEST - ((id * 2654435761L ushr 7) % 4).toInt() * 6

    /** Fills shelves left to right, starting a new one when the next book wouldn't fit. */
    fun rows(items: List<LibraryItem>, available: Float): List<List<LibraryItem>> {
        val rows = ArrayList<List<LibraryItem>>()
        var row = ArrayList<LibraryItem>()
        var used = 0f
        for (item in items) {
            val w = width(item.total) + if (row.isEmpty()) 0 else GAP
            if (row.isNotEmpty() && used + w > available) {
                rows += row
                row = ArrayList()
                used = 0f
            }
            used += if (row.isEmpty()) width(item.total) else w
            row += item
        }
        if (row.isNotEmpty()) rows += row
        return rows
    }
}

/** One shelf: books standing on a plank. */
@Composable
internal fun BookcaseShelf(row: List<LibraryItem>, modifier: Modifier = Modifier, onLongClick: (LibraryItem) -> Unit, onOpen: (LibraryItem) -> Unit) {
    val dark = Folio.colors.isDark
    Column(modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spines.GAP.dp),
            verticalAlignment = Alignment.Bottom,
            // Room above the tallest book for the new-chapter tabs.
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        ) {
            row.forEach { item -> Spine(item, onLongClick = { onLongClick(item) }) { onOpen(item) } }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .shadow(6.dp, RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        if (dark) listOf(Color(0xFF5A4431), Color(0xFF33261B)) else listOf(Color(0xFFC79A6B), Color(0xFF94683F)),
                    ),
                ),
        )
    }
}

@Composable
private fun Spine(item: LibraryItem, onLongClick: () -> Unit, onClick: () -> Unit) {
    val c = Folio.colors
    val tint = rememberCoverColor(item.sourceId, item.thumbnailUrl) ?: c.fill
    val ink = if (tint.luminance() < 0.45f) Color.White else Color(0xFF1A1A1A)
    val shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
    Box(Modifier.pressable(onClick = onClick, onLongClick = onLongClick, pressedScale = 0.97f)) {
        Box(
            Modifier
                .width(Spines.width(item.total).dp)
                .height(Spines.height(item.id).dp)
                .shadow(3.dp, shape)
                .clip(shape)
                .background(tint),
        ) {
            item.thumbnailUrl?.let { url ->
                val context = LocalContext.current
                val request = remember(item.sourceId, url) {
                    ImageRequest.Builder(context).data(CoverModel(item.sourceId, url)).coverCacheKeys(item.sourceId, url).build()
                }
                // The edge of the cover nearest the binding, under a wash of its colour so the title reads.
                AsyncImage(request, null, contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(tint.copy(alpha = 0.72f)))
            }
            // A rounded spine: lit a little left of centre, falling into shadow at both edges.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.32f),
                        0.18f to Color.White.copy(alpha = 0.12f),
                        0.45f to Color.Transparent,
                        0.85f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.38f),
                    ),
                ),
            )
            // Printed bands near the head and foot, as on a real spine.
            SpineBand(ink, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
            SpineBand(ink, Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
            Text(
                item.title,
                style = Folio.type.caption2,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(vertical = 20.dp).downward(),
            )
        }
        if (item.unread > 0) {
            // A tab poking out of the top: new chapters.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-7).dp)
                    .size(width = 8.dp, height = 14.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(c.accent),
            )
        }
    }
}

@Composable
private fun SpineBand(ink: Color, modifier: Modifier) =
    Box(modifier.fillMaxWidth().padding(horizontal = 4.dp).height(1.dp).background(ink.copy(alpha = 0.35f)))

/** Turns content to run top to bottom, like the title on an English-language spine. */
private fun Modifier.downward() = layout { measurable, constraints ->
    val p = measurable.measure(Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth))
    layout(p.height, p.width) {
        p.placeWithLayer((p.height - p.width) / 2, (p.width - p.height) / 2) { rotationZ = 90f }
    }
}
