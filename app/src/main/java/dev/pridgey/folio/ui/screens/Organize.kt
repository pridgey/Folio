@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.pridgey.folio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pridgey.folio.Graph
import dev.pridgey.folio.data.LibraryItem
import dev.pridgey.folio.data.MangaEntity
import dev.pridgey.folio.data.ReadingStatus
import dev.pridgey.folio.data.ShelfEntity
import dev.pridgey.folio.source.SourceManager
import dev.pridgey.folio.ui.components.Chip
import dev.pridgey.folio.ui.components.Cover
import dev.pridgey.folio.ui.components.Format
import dev.pridgey.folio.ui.components.SkeletonBox
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Status and shelves for one title

/** Pick a title's reading status and the shelves it sits on. */
@Composable
fun OrganizeSheet(mangaId: Long, title: String, onDismiss: () -> Unit) {
    val c = Folio.colors
    val scope = rememberCoroutineScope()
    val manga by remember(mangaId) { Graph.library.observe(mangaId) }.collectAsStateWithLifecycle(null)
    val shelves by remember { Graph.library.shelves() }.collectAsStateWithLifecycle(emptyList())
    val on by remember(mangaId) { Graph.library.shelvesOf(mangaId) }.collectAsStateWithLifecycle(emptyList())
    var naming by remember { mutableStateOf(false) }
    val status = ReadingStatus.of(manga?.readingStatus ?: 0)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.elevated) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(title, style = Folio.type.title3, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(18.dp))
            Text("Status", style = Folio.type.footnoteStrong, color = c.secondaryLabel)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingStatus.entries.forEach { s ->
                    Chip(s.label, selected = s == status, onClick = {
                        scope.launch {
                            Graph.library.setReadingStatus(mangaId, s)
                            // A status only means something in the library, so choosing one adds the title.
                            if (manga?.favorite == false) Graph.library.setFavorite(mangaId, true)
                        }
                    })
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Shelves", style = Folio.type.footnoteStrong, color = c.secondaryLabel)
            Spacer(Modifier.height(4.dp))
            shelves.forEach { shelf ->
                val checked = shelf.id in on
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).pressable(onClick = {
                        scope.launch {
                            if (checked) Graph.library.removeFromShelf(shelf.id, mangaId)
                            else {
                                Graph.library.addToShelf(shelf.id, listOf(mangaId))
                                if (manga?.favorite == false) Graph.library.setFavorite(mangaId, true)
                            }
                        }
                    }, pressedScale = 0.99f).padding(vertical = 10.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, tint = if (checked) c.accent else c.tertiaryLabel)
                    Spacer(Modifier.width(12.dp))
                    Text(shelf.name, style = Folio.type.body, color = c.label)
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).pressable(onClick = { naming = true }, pressedScale = 0.99f).padding(vertical = 10.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, null, tint = c.accent)
                Spacer(Modifier.width(12.dp))
                Text("New shelf", style = Folio.type.body, color = c.accent)
            }
        }
    }
    if (naming) {
        ShelfNameDialog("New shelf", "", onDismiss = { naming = false }) { name ->
            naming = false
            scope.launch {
                val id = Graph.library.createShelf(name)
                Graph.library.addToShelf(id, listOf(mangaId))
                if (manga?.favorite == false) Graph.library.setFavorite(mangaId, true)
            }
        }
    }
}

@Composable
fun ShelfNameDialog(heading: String, initial: String, onDismiss: () -> Unit, onDelete: (() -> Unit)? = null, onConfirm: (String) -> Unit) {
    val c = Folio.colors
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.elevated,
        title = { Text(heading, style = Folio.type.headline) },
        text = {
            OutlinedTextField(name, { name = it.take(40) }, singleLine = true, placeholder = { Text("Shelf name") }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton({ if (name.isNotBlank()) onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onDelete) { Text("Delete shelf", color = c.danger) }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
}

// ------------------------------------------------------------------ Library filters

/** What the library grid shows. */
sealed interface LibraryFilter {
    data object All : LibraryFilter
    data class Status(val status: ReadingStatus) : LibraryFilter
    data class Shelf(val id: Long) : LibraryFilter
}

/** Statuses and shelves as one row of chips; long-press a shelf to rename or delete it. */
@Composable
fun LibraryFilterBar(
    items: List<LibraryItem>,
    shelves: List<ShelfEntity>,
    counts: Map<Long, Int>,
    selected: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
    onNewShelf: () -> Unit,
    onEditShelf: (ShelfEntity) -> Unit,
) {
    val byStatus = remember(items) { items.groupingBy { ReadingStatus.of(it.readingStatus) }.eachCount() }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
        item { Chip("All", selected = selected == LibraryFilter.All, onClick = { onSelect(LibraryFilter.All) }) }
        ReadingStatus.entries.forEach { s ->
            val n = byStatus[s] ?: 0
            // Empty statuses stay out of the way, unless one is selected.
            if (n > 0 || selected == LibraryFilter.Status(s)) {
                item { Chip("${s.label} $n", selected = selected == LibraryFilter.Status(s), onClick = { onSelect(LibraryFilter.Status(s)) }) }
            }
        }
        items(shelves, key = { "shelf-${it.id}" }) { shelf ->
            val isSelected = selected == LibraryFilter.Shelf(shelf.id)
            Box(Modifier.pressable(onClick = { onSelect(LibraryFilter.Shelf(shelf.id)) }, onLongClick = { onEditShelf(shelf) }, pressedScale = 0.97f)) {
                Chip("${shelf.name} ${counts[shelf.id] ?: 0}", selected = isSelected)
            }
        }
        item { Chip("Shelf", leading = Icons.Rounded.Add, onClick = onNewShelf) }
    }
}

/** A shelf's header: its covers fanned into a mosaic, with its name and size. */
@Composable
fun ShelfHeader(shelf: ShelfEntity, items: List<LibraryItem>, onEdit: () -> Unit) {
    val c = Folio.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).pressable(onClick = onEdit, pressedScale = 0.99f).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(132.dp).height(96.dp)) {
            val shown = items.take(4)
            shown.forEachIndexed { i, item ->
                // Each cover a little further along and lower, the first on top.
                val z = shown.size - i
                Box(
                    Modifier
                        .padding(start = (i * 26).dp, top = (i * 4).dp)
                        .width(58.dp)
                        .zIndex(z.toFloat()),
                ) {
                    Cover(item.sourceId, item.thumbnailUrl, item.title, Modifier.fillMaxWidth(), corner = 7.dp, elevation = 8.dp)
                }
            }
            if (shown.isEmpty()) SkeletonBox(Modifier.width(58.dp).aspectRatio(2f / 3f), 7.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(shelf.name, style = Folio.type.title3, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(Format.plural(items.size, "title"), style = Folio.type.footnote, color = c.secondaryLabel)
            Spacer(Modifier.height(4.dp))
            Text("Tap to rename", style = Folio.type.caption, color = c.tertiaryLabel)
        }
    }
}

// ------------------------------------------------------------------ Moving to another source

/**
 * Looks for the same series on every other source, so the reader can move a title (and its progress,
 * status and shelves) to whichever has it best.
 */
@Composable
fun MigrateSheet(manga: MangaEntity, onMoved: (newId: Long, sourceId: Long) -> Unit, onDismiss: () -> Unit) {
    val c = Folio.colors
    val scope = rememberCoroutineScope()
    val targets = remember { Graph.sources.all().filter { it.id != manga.sourceId } }
    val found = remember { mutableStateOf<Map<Long, Result<List<SManga>>>>(emptyMap()) }
    var confirm by remember { mutableStateOf<Pair<Long, SManga>?>(null) }
    var moving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(manga.id) {
        targets.forEach { src ->
            launch {
                val r = runCatching { Graph.library.findOn(src.id, manga.title).take(10) }
                found.value = found.value + (src.id to r)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = { if (!moving) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.elevated) {
        Column(Modifier.fillMaxWidth().padding(bottom = 28.dp)) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Move to another source", style = Folio.type.title3, color = c.label)
                Text(
                    "Your read chapters, bookmarks, status and shelves come along, matched by chapter number.",
                    style = Folio.type.footnote, color = c.secondaryLabel,
                )
            }
            if (targets.isEmpty()) {
                Text("There's no other source set up yet.", style = Folio.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(20.dp))
            }
            targets.forEach { src ->
                Text(src.name, style = Folio.type.headline, color = c.label, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp))
                when (val r = found.value[src.id]) {
                    null -> LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(4) { SkeletonBox(Modifier.width(96.dp).aspectRatio(2f / 3f), 10.dp) }
                    }
                    else -> r.fold(
                        { list ->
                            if (list.isEmpty()) {
                                Text("Not found here", style = Folio.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 20.dp))
                            } else {
                                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(list, key = { it.url }) { m ->
                                        Column(Modifier.width(96.dp).pressable(onClick = { confirm = src.id to m })) {
                                            Cover(src.id, m.thumbnail_url, m.title, Modifier.fillMaxWidth())
                                            Spacer(Modifier.height(6.dp))
                                            Text(m.title, style = Folio.type.caption, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        },
                        { e -> Text(SourceManager.describe(e), style = Folio.type.subhead, color = c.secondaryLabel, modifier = Modifier.padding(horizontal = 20.dp)) },
                    )
                }
            }
            error?.let { Text(it, style = Folio.type.footnote, color = c.danger, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp)) }
        }
    }

    confirm?.let { (sourceId, target) ->
        AlertDialog(
            onDismissRequest = { if (!moving) confirm = null },
            containerColor = c.elevated,
            title = { Text("Move to ${Graph.sources.nameOf(sourceId)}?", style = Folio.type.headline) },
            text = {
                Column {
                    Text("“${manga.title}” becomes “${target.title}”. The old copy leaves your library.", style = Folio.type.subhead, color = c.secondaryLabel)
                    if (moving) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Moving your progress…", style = Folio.type.footnote, color = c.secondaryLabel)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    moving = true
                    scope.launch {
                        val id = runCatching { Graph.library.migrate(manga.id, sourceId, target) }
                        moving = false
                        confirm = null
                        id.onSuccess { newId -> if (newId != null) onMoved(newId, sourceId) }
                            .onFailure { e -> error = "Couldn't move it: ${SourceManager.describe(e)}" }
                    }
                }, enabled = !moving) { Text("Move") }
            },
            dismissButton = { TextButton({ confirm = null }, enabled = !moving) { Text("Cancel") } },
        )
    }
}
