package dev.pridgey.folio.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.MangaEntity
import dev.pridgey.folio.download.Downloads
import dev.pridgey.folio.download.Pacer
import dev.pridgey.folio.source.Health
import dev.pridgey.folio.source.SourceLog
import dev.pridgey.folio.ui.components.Cover
import dev.pridgey.folio.ui.components.Format
import dev.pridgey.folio.ui.components.GroupedDivider
import dev.pridgey.folio.ui.components.GroupedRow
import dev.pridgey.folio.ui.components.GroupedSection
import dev.pridgey.folio.ui.components.StatusDot
import dev.pridgey.folio.ui.components.TintedButton
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// ------------------------------------------------------------------ Storage

/** Where the space goes: downloads per title, the image cache, and what's left on the phone. */
@Composable
fun StorageScreen() {
    val c = Folio.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloaded by Graph.downloads.downloaded.collectAsStateWithLifecycle()
    var refresh by remember { mutableIntStateOf(0) }
    var usage by remember { mutableStateOf<List<Pair<Downloads.Usage, MangaEntity?>>?>(null) }
    var cache by remember { mutableStateOf(0L) }
    var free by remember { mutableStateOf(0L) }
    var readCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(downloaded, refresh) {
        val u = Graph.downloads.usage()
        usage = u.map { it to Graph.library.get(it.mangaId) }
        cache = SingletonImageLoader.get(context).diskCache?.size ?: 0L
        free = withContext(Dispatchers.IO) { context.filesDir.usableSpace }
        readCount = u.sumOf { x -> Graph.library.chaptersOf(x.mangaId).count { it.read && it.id in downloaded } }
    }
    val total = usage?.sumOf { it.first.bytes } ?: 0L

    GroupedScreen("Storage") {
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(Format.bytes(total + cache), style = Folio.type.title1, color = c.label)
                Text("used by Folio · ${Format.bytes(free)} free on this phone", style = Folio.type.subhead, color = c.secondaryLabel)
                Spacer(Modifier.height(14.dp))
                // One bar for the phone's space: downloads, cache, then what's free.
                val whole = (total + cache + free).coerceAtLeast(1).toFloat()
                Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(50)).background(c.fill)) {
                    if (total > 0) Box(Modifier.weight((total / whole).coerceAtLeast(0.01f)).fillMaxHeight().background(c.accent))
                    if (cache > 0) Box(Modifier.weight((cache / whole).coerceAtLeast(0.01f)).fillMaxHeight().background(c.accent.copy(alpha = 0.45f)))
                    Box(Modifier.weight((free / whole).coerceAtLeast(0.01f)).fillMaxHeight())
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(c.accent, "Downloads ${Format.bytes(total)}")
                    Legend(c.accent.copy(alpha = 0.45f), "Image cache ${Format.bytes(cache)}")
                }
            }
        }
        item {
            GroupedSection(header = "Clean up") {
                GroupedRow(
                    "Delete chapters you've read", icon = Icons.Rounded.DeleteSweep, iconBackground = Color(0xFFFF9500),
                    subtitle = if (readCount > 0) Format.plural(readCount, "chapter") else "Nothing read is downloaded",
                    onClick = if (readCount == 0) null else ({
                        scope.launch {
                            val n = Graph.downloads.deleteRead()
                            Toast.makeText(context, "Deleted ${Format.plural(n, "chapter")}", Toast.LENGTH_SHORT).show()
                            refresh++
                        }
                    }),
                )
                GroupedDivider(59.dp)
                GroupedRow(
                    "Clear the image cache", icon = Icons.Rounded.Image, iconBackground = Color(0xFF0A84FF),
                    subtitle = "Covers and pages load again as you browse",
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            SingletonImageLoader.get(context).diskCache?.clear()
                            SingletonImageLoader.get(context).memoryCache?.clear()
                            withContext(Dispatchers.Main) { refresh++ }
                        }
                    },
                )
            }
        }
        item {
            val list = usage
            GroupedSection(header = "Downloads by title", footer = if (list?.isEmpty() == true) "Nothing downloaded yet." else null) {
                list?.forEachIndexed { i, (u, m) ->
                    if (i > 0) GroupedDivider(76.dp)
                    UsageRow(u, m, share = if (total > 0) u.bytes / total.toFloat() else 0f) { refresh++ }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Folio.type.caption, color = Folio.colors.secondaryLabel)
    }
}

@Composable
private fun UsageRow(u: Downloads.Usage, manga: MangaEntity?, share: Float, onChanged: () -> Unit) {
    val c = Folio.colors
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().pressable(onClick = { menu = true }, pressedScale = 0.99f).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(manga?.sourceId ?: 0L, manga?.thumbnailUrl, manga?.title.orEmpty(), Modifier.width(44.dp), corner = 6.dp, elevation = 2.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(manga?.title ?: "Removed title", style = Folio.type.subheadStrong, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Format.plural(u.chapters, "chapter")} · ${Format.bytes(u.bytes)}", style = Folio.type.footnote, color = c.secondaryLabel)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(c.fill)) {
                    Box(Modifier.fillMaxWidth(share.coerceIn(0.02f, 1f)).fillMaxHeight().background(c.accent))
                }
            }
        }
        DropdownMenu(menu, { menu = false }, containerColor = c.elevated, shape = RoundedCornerShape(14.dp)) {
            DropdownMenuItem(text = { Text("Delete read chapters") }, onClick = {
                menu = false
                scope.launch { Graph.downloads.deleteRead(u.mangaId); onChanged() }
            })
            DropdownMenuItem(text = { Text("Delete all downloads", color = c.danger) }, onClick = {
                menu = false
                scope.launch { Graph.downloads.deleteTitle(u.mangaId); onChanged() }
            })
        }
    }
}

// ------------------------------------------------------------------ Source health

/** How each source has been doing: uptime, the last failure, download pace, and Folio's repairs. */
@Composable
fun SourceHealthScreen() {
    val c = Folio.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val log = Graph.sources.log?.state?.collectAsStateWithLifecycle()?.value ?: SourceLog.Snapshot()
    val health by Graph.sources.health.collectAsStateWithLifecycle()
    val extensions by Graph.extensions.status.collectAsStateWithLifecycle()
    val backoff by Pacer.backoff.collectAsStateWithLifecycle()
    val settings by Graph.settings.values.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(false) }
    val sources = remember(extensions) { Graph.sources.all() }

    GroupedScreen("Source Health") {
        sources.forEach { src ->
            item(key = "src-${src.id}") {
                val h = health[src.id]
                val uptime = log.uptime(src.id)
                val last = log.lastFailure(src.id)
                val version = src.packageName?.let { extensions[it]?.installed?.versionName }
                GroupedSection(header = src.name) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(
                                when (h) {
                                    is Health.Failing -> if (h.healing) c.warning else c.danger
                                    Health.Ok -> c.success
                                    else -> c.tertiaryLabel
                                },
                                size = 10.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (h) {
                                    is Health.Failing -> if (h.healing) "Repairing…" else "Having trouble"
                                    Health.Ok -> "Working"
                                    else -> "Not used yet this session"
                                },
                                style = Folio.type.headline, color = c.label, modifier = Modifier.weight(1f),
                            )
                            Text(uptime?.let { "${(it * 100).toInt()}% · 7 days" } ?: "No data yet", style = Folio.type.subheadStrong, color = c.secondaryLabel)
                        }
                        Spacer(Modifier.height(12.dp))
                        UptimeBars(log.days[src.id].orEmpty())
                        Spacer(Modifier.height(12.dp))
                        if (version != null) Detail("Extension", "v$version")
                        log.lastOk[src.id]?.let { Detail("Last worked", Format.relative(it)) }
                        Detail("Last failure", last?.let { "${Format.relative(it.at)}: ${it.detail.orEmpty()}" } ?: "None recorded")
                        val gap = settings.downloadPace.gapMs + (backoff[src.id] ?: 0)
                        Detail("Download pace", if ((backoff[src.id] ?: 0) > 0) "Slowed to ${gap} ms between pages; the site pushed back" else "${settings.downloadPace.label} (${gap} ms)")
                    }
                }
            }
        }
        item {
            val repairs = log.events.filter { it.kind in setOf(SourceLog.Kind.HEALING, SourceLog.Kind.HEALED, SourceLog.Kind.HEAL_FAILED, SourceLog.Kind.FAILED, SourceLog.Kind.RECOVERED) }.takeLast(30).reversed()
            GroupedSection(header = "History", footer = "Folio repairs MangaFire itself when the site changes: it fetches the newest extension and tries again.") {
                if (repairs.isEmpty()) GroupedRow("Nothing to report", icon = Icons.Rounded.CheckCircle, iconBackground = c.success)
                repairs.forEachIndexed { i, e ->
                    if (i > 0) GroupedDivider(59.dp)
                    val (icon, color, label) = when (e.kind) {
                        SourceLog.Kind.FAILED -> Triple(Icons.Rounded.ErrorOutline, c.danger, "Stopped working")
                        SourceLog.Kind.RECOVERED -> Triple(Icons.Rounded.CheckCircle, c.success, "Working again")
                        SourceLog.Kind.HEALING -> Triple(Icons.Rounded.AutoFixHigh, c.warning, "Started a repair")
                        SourceLog.Kind.HEALED -> Triple(Icons.Rounded.AutoFixHigh, c.success, "Repaired")
                        SourceLog.Kind.HEAL_FAILED -> Triple(Icons.Rounded.CleaningServices, Color(0xFF8E8E93), "Repair didn't help")
                    }
                    GroupedRow(
                        "$label · ${Graph.sources.nameOf(e.sourceId)}", icon = icon, iconBackground = color,
                        subtitle = listOfNotNull(Format.relative(e.at), e.detail).joinToString(" · "),
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center) {
                TintedButton(if (checking) "Checking…" else "Check for source updates", {
                    if (!checking) {
                        checking = true
                        scope.launch {
                            val r = Graph.extensions.checkForUpdates(install = true)
                            checking = false
                            Toast.makeText(context, r.fold({ n -> if (n > 0) "Updated ${Format.plural(n, "source")}" else "Everything's up to date" }, { "Couldn't check right now" }), Toast.LENGTH_SHORT).show()
                        }
                    }
                }, icon = Icons.Rounded.SystemUpdate)
            }
        }
    }
}

/** Fourteen small bars, one per day: how much of that day's traffic worked. */
@Composable
private fun UptimeBars(days: List<SourceLog.Day>) {
    val c = Folio.colors
    val today = LocalDate.now().toEpochDay()
    val byDay = days.associateBy { it.day }
    Row(Modifier.fillMaxWidth().height(28.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        (13 downTo 0).forEach { back ->
            val d = byDay[today - back]
            val total = (d?.ok ?: 0) + (d?.failed ?: 0)
            val color = when {
                total == 0 -> c.fill
                d!!.failed == 0 -> c.success
                d.ok / total.toFloat() >= 0.9f -> c.warning
                else -> c.danger
            }
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(color))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text("14 days ago", style = Folio.type.caption2, color = c.tertiaryLabel, modifier = Modifier.weight(1f))
        Text("Today", style = Folio.type.caption2, color = c.tertiaryLabel)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    val c = Folio.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = Folio.type.footnote, color = c.secondaryLabel, modifier = Modifier.width(110.dp))
        Text(value, style = Folio.type.footnote, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
