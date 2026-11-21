package dev.pridgey.folio.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.pridgey.folio.Graph
import dev.pridgey.folio.image.CoverModel
import dev.pridgey.folio.ui.components.CircleButton
import dev.pridgey.folio.ui.components.Notice
import dev.pridgey.folio.ui.components.PrimaryButton
import dev.pridgey.folio.ui.components.coverCacheKeys
import dev.pridgey.folio.ui.components.pressable
import dev.pridgey.folio.ui.theme.Folio
import dev.pridgey.folio.work.Notifications
import dev.pridgey.folio.work.TitleSync

/** Stands in for a title's page until its first sync has finished. */
@Composable
fun TitleSyncScreen(
    mangaId: Long,
    sourceId: Long,
    cover: String?,
    title: String,
    state: TitleSync.State,
    error: String?,
    /** Back to the title's page, having decided not to sync. */
    onDismiss: () -> Unit,
    /** Off the title altogether, leaving the sync running. */
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    val entry = TitleSync.Entry(mangaId, sourceId, title)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val add: (Boolean) -> Unit = { next ->
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canNotify(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        TitleSync.enqueue(context, entry, next)
    }
    if (state.contains(mangaId)) {
        SyncConfirmation(entry, state, onMakeNext = { add(true) }, onKeepBrowsing = onLeave, onCancel = { TitleSync.cancel(mangaId) })
    } else {
        BackHandler(onBack = onDismiss)
        SyncPrompt(entry, cover, state, error, onStart = { add(false) }, onMakeNext = { add(true) }, onBack = onDismiss)
    }
}

@Composable
private fun SyncPrompt(
    entry: TitleSync.Entry,
    cover: String?,
    state: TitleSync.State,
    error: String?,
    onStart: () -> Unit,
    onMakeNext: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Folio.colors
    val source = Graph.sources.nameOf(entry.sourceId)
    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SyncHero(entry, cover, source, onBack)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
                Text("This title needs a first sync", style = Folio.type.title2, color = c.label)
                Spacer(Modifier.height(7.dp))
                Text(
                    "$source titles need to sync before you can read them, which can take some time. You can leave while it runs.",
                    style = Folio.type.subhead, color = c.secondaryLabel,
                )
                error?.let {
                    Spacer(Modifier.height(14.dp))
                    Notice("Last sync didn't finish", it, Icons.Rounded.CloudOff, c.danger)
                }
                state.current?.let { current ->
                    SectionLabel("Syncing now", top = 16)
                    QueueCard { SyncingRow(current, highlighted = false) }
                    FinishFirstNote(current)
                }
                if (state.waiting.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(c.surface).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("In queue", style = Folio.type.subhead.copy(fontWeight = FontWeight.Medium), color = c.label)
                        Text(if (state.waiting.size == 1) "1 title" else "${state.waiting.size} titles", style = Folio.type.subhead, color = c.secondaryLabel)
                    }
                }
            }
        }
        SyncActions {
            PrimaryButton(if (error != null) "Try again" else "Start sync", onStart, Modifier.fillMaxWidth(), icon = Icons.Rounded.Sync)
            // Nothing to go ahead of unless other titles are already waiting.
            if (state.current != null && state.waiting.isNotEmpty()) MakeNextButton(onMakeNext)
            QuietButton("Cancel", c.secondaryLabel, onBack)
        }
    }
}

@Composable
private fun SyncConfirmation(
    entry: TitleSync.Entry,
    state: TitleSync.State,
    onMakeNext: () -> Unit,
    onKeepBrowsing: () -> Unit,
    onCancel: () -> Unit,
) {
    val c = Folio.colors
    val isCurrent = state.current?.mangaId == entry.mangaId
    val position = state.waiting.indexOfFirst { it.mangaId == entry.mangaId }
    val isNext = position == 0 && state.current != null
    val heading = when {
        isCurrent -> "Syncing now"
        isNext -> "You're up next"
        else -> "You're in line"
    }
    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars)) {
            CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onKeepBrowsing, Modifier.padding(start = 16.dp, top = 12.dp), size = 44.dp)
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(c.success.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Icon(if (isNext) Icons.Rounded.KeyboardDoubleArrowUp else Icons.Rounded.Check, null, tint = c.success, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(heading, style = Folio.type.title1.copy(fontSize = Folio.type.largeTitle.fontSize * 0.9f), color = c.label)
                Spacer(Modifier.height(10.dp))
                Text(
                    buildAnnotatedString {
                        append("We'll send a notification when ")
                        withStyle(SpanStyle(color = c.label, fontWeight = FontWeight.SemiBold)) { append(entry.title) }
                        append(" is ready. Feel free to leave or close Folio.")
                    },
                    style = Folio.type.callout, color = c.secondaryLabel,
                )
                state.current?.let { current ->
                    SectionLabel("Syncing now", top = 24)
                    QueueCard { SyncingRow(current, highlighted = isCurrent) }
                    if (!isCurrent) FinishFirstNote(current)
                }
                if (state.waiting.isNotEmpty()) {
                    SectionLabel("Queue", top = 16)
                    QueueCard {
                        state.waiting.forEachIndexed { i, item ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.separator))
                            QueueRow(i + 1, item, highlighted = item.mangaId == entry.mangaId, isNext = i == 0 && state.current != null)
                        }
                    }
                }
            }
        }
        SyncActions {
            if (position > 0) MakeNextButton(onMakeNext)
            PrimaryButton("Keep browsing", onKeepBrowsing, Modifier.fillMaxWidth())
            QuietButton("Cancel sync", c.danger, onCancel)
        }
    }
}

@Composable
private fun SyncHero(entry: TitleSync.Entry, cover: String?, source: String, onBack: () -> Unit) {
    val c = Folio.colors
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth().height(300.dp).background(c.placeholder)) {
        if (cover != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(CoverModel(entry.sourceId, cover)).coverCacheKeys(entry.sourceId, cover).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.75f to c.background.copy(alpha = 0.82f), 1f to c.background)))
        CircleButton(
            Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack,
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 16.dp, top = 12.dp),
            size = 44.dp, background = Color.Black.copy(alpha = 0.35f), tint = Color.White,
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(source.uppercase(), style = Folio.type.captionStrong, color = c.accent)
            Spacer(Modifier.height(5.dp))
            Text(entry.title, style = Folio.type.title1, color = c.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SyncActions(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun MakeNextButton(onClick: () -> Unit) {
    val c = Folio.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(50)).border(1.dp, c.label.copy(alpha = 0.24f), RoundedCornerShape(50))
            .pressable(onClick = onClick, haptic = true, pressedScale = 0.97f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.KeyboardDoubleArrowUp, null, tint = c.label, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(9.dp))
        Column {
            Text("Make this next", style = Folio.type.subheadStrong, color = c.label)
            Text("Goes right after the current sync", style = Folio.type.caption, color = c.secondaryLabel)
        }
    }
}

@Composable
private fun QuietButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(50)).pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Folio.type.subhead.copy(fontWeight = FontWeight.Medium), color = color)
    }
}

@Composable
private fun SectionLabel(text: String, top: Int) {
    Text(
        text, style = Folio.type.footnoteStrong, color = Folio.colors.secondaryLabel,
        modifier = Modifier.padding(top = top.dp, bottom = 8.dp),
    )
}

@Composable
private fun QueueCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Folio.colors.surface), content = content)
}

@Composable
private fun FinishFirstNote(current: TitleSync.Entry) {
    Text(
        "${current.title} will finish first, since stopping now would lose its progress.",
        style = Folio.type.footnote, color = Folio.colors.secondaryLabel,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp),
    )
}

@Composable
private fun SyncingRow(entry: TitleSync.Entry, highlighted: Boolean) {
    val c = Folio.colors
    Row(
        Modifier.fillMaxWidth().background(if (highlighted) c.accentSoft else Color.Transparent).heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(c.fill), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Sync, null, tint = c.accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(entry.title, style = Folio.type.subhead.copy(fontWeight = FontWeight.Medium), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QueueRow(number: Int, entry: TitleSync.Entry, highlighted: Boolean, isNext: Boolean) {
    val c = Folio.colors
    Row(
        Modifier.fillMaxWidth().background(if (highlighted) c.accentSoft else Color.Transparent).heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(if (highlighted) c.accent else c.fill), contentAlignment = Alignment.Center) {
            Text("$number", style = Folio.type.footnoteStrong, color = if (highlighted) c.onAccent else c.secondaryLabel)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(entry.title, style = Folio.type.subhead.copy(fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium), color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    highlighted && isNext -> "This title, up next"
                    highlighted -> "This title"
                    else -> "Waiting"
                },
                style = Folio.type.footnote, color = if (highlighted) c.accent else c.secondaryLabel,
            )
        }
    }
}
