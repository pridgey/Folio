@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.pridgey.folio.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import dev.pridgey.folio.image.CoverModel
import dev.pridgey.folio.ui.theme.Folio

// ------------------------------------------------------------------ Shared element plumbing

val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedContentScope?> { null }

@Composable
fun ProvideNavScope(scope: AnimatedContentScope, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalNavScope provides scope, content = content)

/** How long a screen takes to push or pop. Flying covers use the same timing so both land together. */
const val NAV_MS = 340

/**
 * Lets a cover fly between screens. The cover draws above both screens while it flies, so chrome that
 * should stay on top of it uses [aboveSharedCovers].
 */
@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val shared = LocalSharedScope.current
    val nav = LocalNavScope.current
    if (key == null || shared == null || nav == null) return this
    return with(shared) {
        this@sharedCover.sharedElement(
            sharedContentState = rememberSharedContentState("cover:$key"),
            animatedVisibilityScope = nav,
            // A tween, not a spring: it ends exactly when the screens do, and tracks a predictive back swipe.
            boundsTransform = { _, _ -> tween(NAV_MS, easing = FastOutSlowInEasing) },
        )
    }
}

/**
 * Keeps chrome (bars, scrims, titles) above a flying cover. While a cover flies, this element moves into
 * the same overlay at a higher [z], fading with its screen since the screen's own fade no longer reaches it.
 */
@Composable
fun Modifier.aboveSharedCovers(z: Float = 1f): Modifier {
    val shared = LocalSharedScope.current
    val nav = LocalNavScope.current
    if (shared == null || nav == null) return this
    val alpha by nav.transition.animateFloat(transitionSpec = { tween(NAV_MS) }, label = "chrome") { if (it == EnterExitState.Visible) 1f else 0f }
    return with(shared) {
        this@aboveSharedCovers
            .renderInSharedTransitionScopeOverlay(zIndexInOverlay = z)
            .graphicsLayer { this.alpha = if (shared.isTransitionActive) alpha else 1f }
    }
}

// ------------------------------------------------------------------ Motion

/** A soft press: the element dips a little under the finger and springs back. */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    pressedScale: Float = 0.965f,
    enabled: Boolean = true,
    haptic: Boolean = false,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, spring(dampingRatio = 0.6f, stiffness = 900f), label = "press")
    val haptics = LocalHapticFeedback.current
    return this
        .scale(scale)
        .then(
            if (onLongClick != null) {
                Modifier.combinedClickableCompat(source, enabled, onClick = {
                    if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }, onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                })
            } else {
                Modifier.clickable(source, indication = null, enabled = enabled) {
                    if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            },
        )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    source: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) = this.then(
    Modifier.combinedClickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick, onLongClick = onLongClick),
)

// ------------------------------------------------------------------ Covers

/** A cover at 2:3 with a soft shadow, hairline edge and a calm placeholder while it loads. */
@Composable
fun Cover(
    sourceId: Long,
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
    sharedKey: String? = null,
    elevation: Dp = 6.dp,
) {
    val c = Folio.colors
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .sharedCover(sharedKey)
            .aspectRatio(2f / 3f)
            .shadow(elevation, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.3f))
            .clip(shape)
            .background(c.placeholder)
            .border(BorderStroke(0.75.dp, c.coverStroke), shape),
    ) {
        if (url != null) {
            val context = LocalContext.current
            val request = remember(sourceId, url) {
                ImageRequest.Builder(context).data(CoverModel(sourceId, url)).coverCacheKeys(sourceId, url).build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                // Show the copy already in memory (e.g. the big one from the title page) while this size loads.
                loading = { state ->
                    val held = state.painter
                    if (held != null) Image(held, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else CoverPlaceholder(title, shimmer = true)
                },
                error = { CoverPlaceholder(title, shimmer = false) },
            )
        } else {
            CoverPlaceholder(title, shimmer = false)
        }
    }
}

/**
 * Every view of a cover shares one memory-cache entry, and shows whatever copy is there while its own
 * size loads. Without the placeholder a flying cover is a blank box until the new size decodes.
 */
fun ImageRequest.Builder.coverCacheKeys(sourceId: Long, url: String): ImageRequest.Builder {
    val key = "cover:$sourceId:$url"
    return memoryCacheKey(key).placeholderMemoryCacheKey(key)
}

@Composable
private fun CoverPlaceholder(title: String, shimmer: Boolean) {
    val c = Folio.colors
    Box(
        Modifier
            .fillMaxSize()
            .then(if (shimmer) Modifier.shimmer() else Modifier)
            .background(Brush.verticalGradient(listOf(c.placeholder, c.fill))),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (!shimmer) {
            Text(
                title, style = Folio.type.footnoteStrong, color = c.tertiaryLabel, maxLines = 4,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(10.dp),
            )
        }
    }
}

/** A gentle highlight sweeping across a placeholder. */
@Composable
fun Modifier.shimmer(): Modifier {
    val c = Folio.colors
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400)), label = "x")
    val highlight = if (c.isDark) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.55f)
    return drawBehind {
        drawRect(c.placeholder)
        val w = size.width
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(w * x - w * 0.5f, 0f),
                end = Offset(w * x + w * 0.5f, size.height),
            ),
        )
    }
}

@Composable
fun SkeletonBox(modifier: Modifier, corner: Dp = 8.dp) =
    Box(modifier.clip(RoundedCornerShape(corner)).shimmer())

// ------------------------------------------------------------------ Buttons

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    subtitle: String? = null,
) {
    val c = Folio.colors
    val fg = if (enabled) c.onAccent else c.tertiaryLabel
    Box(
        modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) c.accent else c.fill)
            .pressable(onClick = onClick, enabled = enabled && !loading, haptic = true, pressedScale = 0.97f)
            .padding(horizontal = 22.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = c.onAccent, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(it, null, tint = fg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(text, style = Folio.type.title3.copy(fontSize = 18.sp, lineHeight = 21.sp), color = fg, maxLines = 1)
                    subtitle?.let {
                        Text(it, style = Folio.type.caption, color = fg.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun TintedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Folio.colors
    val fg = if (enabled) c.accent else c.tertiaryLabel
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) c.accentSoft else c.fill)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.96f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = Folio.type.subheadStrong, color = fg, maxLines = 1)
    }
}

/** A round icon button with a soft fill, like the ones over photos. */
@Composable
fun CircleButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    tint: Color = Folio.colors.label,
    background: Color = Folio.colors.fill,
    active: Boolean = false,
) {
    val c = Folio.colors
    val bg by animateColorAsState(if (active) c.accent else background, label = "bg")
    val fg by animateColorAsState(if (active) c.onAccent else tint, label = "fg")
    Box(
        modifier.size(size).clip(CircleShape).background(bg).pressable(onClick = onClick, pressedScale = 0.9f, haptic = true),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = fg, modifier = Modifier.size(size * 0.52f))
    }
}

/** A text button for bars ("Done", "Select"). */
@Composable
fun BarTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, strong: Boolean = false, enabled: Boolean = true) {
    val c = Folio.colors
    Text(
        text,
        style = if (strong) Folio.type.headline else Folio.type.body,
        color = if (enabled) c.accent else c.tertiaryLabel,
        modifier = modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onClick, enabled = enabled).padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

// ------------------------------------------------------------------ Pill selector

/** One-of-several choice as a row of pills; the chosen one is filled with ink. */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Folio.colors
    val haptics = LocalHapticFeedback.current
    Row(modifier.height(38.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) c.label else Color.Transparent, spring(stiffness = Spring.StiffnessMediumLow), label = "pill")
            val fg by animateColorAsState(if (isSelected) c.background else c.label, spring(stiffness = Spring.StiffnessMediumLow), label = "pillText")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .border(1.dp, if (isSelected) Color.Transparent else c.separator.copy(alpha = c.separator.alpha * 1.6f), RoundedCornerShape(50))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(option)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option), style = Folio.type.footnoteStrong, color = fg,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Lists (settings style)

/** A titled group of rows. Open, not boxed: a heading, the rows, and a note. */
@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Folio.colors
    Column(modifier) {
        header?.let {
            Text(it, style = Folio.type.sectionHeader, color = c.label, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp, top = 4.dp))
        }
        Column(content = content)
        footer?.let {
            Text(it, style = Folio.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp))
        }
    }
}

@Composable
fun GroupedDivider(inset: Dp = 16.dp) =
    HorizontalDivider(Modifier.padding(start = inset + 4.dp, end = 20.dp), thickness = 0.5.dp, color = Folio.colors.separator)

/** A row's icon: a plain glyph that leads the eye, never a coloured tile. */
@Composable
fun RowIcon(icon: ImageVector, @Suppress("UNUSED_PARAMETER") background: Color = Color.Unspecified) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Folio.colors.secondaryLabel, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconBackground: Color = Folio.colors.accent,
    value: String? = null,
    @Suppress("UNUSED_PARAMETER") chevron: Boolean = false,
    titleColor: Color = Folio.colors.label,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Folio.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .background(if (pressed) c.fill else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(source, indication = null, onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            RowIcon(it, iconBackground)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Folio.type.body, color = titleColor)
            subtitle?.let { Text(it, style = Folio.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(top = 1.dp)) }
        }
        value?.let {
            Text(it, style = Folio.type.subheadStrong, color = c.accent, maxLines = 1, modifier = Modifier.padding(start = 12.dp))
        }
        trailing?.invoke(this)
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconBackground: Color = Folio.colors.accent,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    GroupedRow(
        title = title, subtitle = subtitle, icon = icon, iconBackground = iconBackground,
        onClick = if (enabled) ({
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(!checked)
        }) else null,
    ) {
        Spacer(Modifier.width(12.dp))
        FolioSwitch(checked, { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(it) }, enabled)
    }
}

@Composable
fun FolioSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Folio.colors
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = c.onAccent, checkedTrackColor = c.accent, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = c.secondaryLabel, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = c.tertiaryLabel,
        ),
        thumbContent = null,
    )
}

// ------------------------------------------------------------------ Search field

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    focusRequester: FocusRequester? = null,
    onSubmit: () -> Unit = {},
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    icon: ImageVector = Icons.Rounded.Search,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val c = Folio.colors
    Row(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(c.fill)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = Folio.type.body, color = c.secondaryLabel, maxLines = 1)
            if (onClick == null) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    readOnly = readOnly,
                    textStyle = Folio.type.body.copy(color = c.label),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                )
            }
        }
        AnimatedVisibility(value.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
            Icon(
                Icons.Rounded.Cancel, "Clear", tint = c.tertiaryLabel,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

// ------------------------------------------------------------------ Bars

/**
 * The bar at the top of a screen. Over content it's clear; once the big title scrolls away the bar
 * fills with the page colour and the title settles into it, left-aligned.
 */
@Composable
fun TopBar(
    title: String,
    @Suppress("UNUSED_PARAMETER") glass: GlassState?,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") backLabel: String? = null,
    showDivider: Boolean = showTitle,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Folio.colors
    val titleAlpha by animateFloatAsState(if (showTitle) 1f else 0f, tween(200), label = "title")
    val fill by animateFloatAsState(if (showDivider) 1f else 0f, tween(220), label = "fill")
    Row(
        modifier
            .aboveSharedCovers(z = 2f)
            .fillMaxWidth()
            .drawBehind { if (fill > 0f) drawRect(c.background.copy(alpha = 0.97f * fill)) }
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).pressable(onClick = onBack, pressedScale = 0.9f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.label, modifier = Modifier.size(24.dp))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title, style = Folio.type.title3, color = c.label.copy(alpha = titleAlpha), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp).graphicsLayer { translationY = (1f - titleAlpha) * 10f },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = actions)
    }
}

/** The title at the top of a tab, set big in the display face. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, inset: Dp = 20.dp, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = inset, end = (inset - 4.dp).coerceAtLeast(0.dp), top = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = Folio.type.largeTitle, color = Folio.colors.label, modifier = Modifier.weight(1f))
        trailing()
    }
}

data class TabItem(val key: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val badge: Int = 0)

/** A floating dock. The current tab opens into a labelled pill; the rest are quiet icons. */
@Composable
fun TabBar(tabs: List<TabItem>, selected: String, onSelect: (String) -> Unit, glass: GlassState, modifier: Modifier = Modifier) {
    val c = Folio.colors
    val haptics = LocalHapticFeedback.current
    Box(modifier.aboveSharedCovers(z = 2f).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(50), ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(RoundedCornerShape(50))
                .glass(glass, if (c.isDark) Color(0xFF1C1C20).copy(alpha = 0.82f) else Color(0xFFFFFEFB).copy(alpha = 0.86f))
                .border(0.75.dp, c.separator, RoundedCornerShape(50))
                .height(62.dp)
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab.key == selected
                val bg by animateColorAsState(if (isSelected) c.accent else Color.Transparent, spring(stiffness = Spring.StiffnessMediumLow), label = "dock")
                val fg by animateColorAsState(if (isSelected) c.onAccent else c.secondaryLabel, label = "dockFg")
                Row(
                    Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(bg)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(tab.key)
                        }
                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        Icon(if (isSelected) tab.selectedIcon else tab.icon, tab.label, tint = fg, modifier = Modifier.size(24.dp))
                        if (tab.badge > 0 && !isSelected) {
                            Box(Modifier.align(Alignment.TopEnd).padding(start = 16.dp).offset(x = 6.dp, y = (-4).dp)) { CountBadge(tab.badge, small = true) }
                        }
                    }
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Text(tab.label, style = Folio.type.subheadStrong, color = fg, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier, small: Boolean = false, color: Color = Folio.colors.accent) {
    val text = if (count > 999) "999+" else count.toString()
    Box(
        modifier
            .heightIn(min = if (small) 16.dp else 20.dp)
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = if (small) 4.5.dp else 6.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = if (small) Folio.type.caption2 else Folio.type.captionStrong, color = Folio.colors.onAccent)
    }
}

/** A coloured dot that says how a source is doing. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

// ------------------------------------------------------------------ Empty and error states

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryAction: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val c = Folio.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(c.accentSoft, c.fill))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, style = Folio.type.title3, color = c.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = Folio.type.subhead, color = c.secondaryLabel, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            TintedButton(action, onAction)
        }
        if (secondaryAction != null && onSecondaryAction != null) {
            Spacer(Modifier.height(4.dp))
            BarTextButton(secondaryAction, onSecondaryAction)
        }
    }
}

/** A slim notice card, for things like "MangaFire is having trouble". */
@Composable
fun Notice(
    title: String,
    message: String?,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    busy: Boolean = false,
) {
    val c = Folio.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = if (c.isDark) 0.16f else 0.1f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = tint, strokeWidth = 2.dp) else Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Folio.type.subheadStrong, color = c.label)
            message?.let { Text(it, style = Folio.type.footnote, color = c.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                action, style = Folio.type.subheadStrong, color = tint,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).pressable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun Chip(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: (() -> Unit)? = null, leading: ImageVector? = null) {
    val c = Folio.colors
    val bg by animateColorAsState(if (selected) c.accent else c.fill, label = "chip")
    val fg by animateColorAsState(if (selected) c.onAccent else c.label, label = "chipfg")
    Row(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick, haptic = true) else Modifier)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            Icon(it, null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = Folio.type.footnoteStrong, color = fg, maxLines = 1)
    }
}

/** A section heading inside scrolling content, with an optional action on the right. */
@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Folio.type.title2, color = Folio.colors.label, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) BarTextButton(action, onAction)
    }
}

/** Space for the floating tab bar at the end of scrolling content. */
@Composable
fun BottomBarSpacer() = Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(96.dp))

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Folio.colors.accent, track: Color = Folio.colors.fill, height: Dp = 4.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}
