@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.pridgey.folio.ui.components

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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

/** Lets a cover fly between screens. */
@Composable
fun Modifier.sharedCover(key: String?): Modifier {
    val shared = LocalSharedScope.current
    val nav = LocalNavScope.current
    if (key == null || shared == null || nav == null) return this
    return with(shared) {
        this@sharedCover.sharedElement(
            sharedContentState = rememberSharedContentState("cover:$key"),
            animatedVisibilityScope = nav,
            boundsTransform = { _, _ -> spring(dampingRatio = 0.86f, stiffness = 380f) },
        )
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
                ImageRequest.Builder(context).data(CoverModel(sourceId, url)).memoryCacheKey("cover:$sourceId:$url").build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { CoverPlaceholder(title, shimmer = true) },
                error = { CoverPlaceholder(title, shimmer = false) },
            )
        } else {
            CoverPlaceholder(title, shimmer = false)
        }
    }
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
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) c.accent else c.fill)
            .pressable(onClick = onClick, enabled = enabled && !loading, haptic = true, pressedScale = 0.98f)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = c.onAccent, strokeWidth = 2.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(it, null, tint = if (enabled) c.onAccent else c.tertiaryLabel, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text, style = Folio.type.headline, color = if (enabled) c.onAccent else c.tertiaryLabel, maxLines = 1)
                    subtitle?.let {
                        Text(it, style = Folio.type.caption, color = (if (enabled) c.onAccent else c.tertiaryLabel).copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun TintedButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val c = Folio.colors
    Row(
        modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.accentSoft)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.97f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(it, null, tint = c.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Folio.type.subheadStrong, color = c.accent, maxLines = 1)
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

// ------------------------------------------------------------------ Segmented control

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
    Row(
        modifier
            .height(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(c.fill)
            .padding(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) (if (c.isDark) Color(0xFF636366) else Color.White) else Color.Transparent, spring(stiffness = Spring.StiffnessMediumLow), label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .shadow(if (isSelected) 2.dp else 0.dp, RoundedCornerShape(7.dp))
                    .clip(RoundedCornerShape(7.dp))
                    .background(bg)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(option)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = if (isSelected) Folio.type.footnoteStrong else Folio.type.footnote,
                    color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Grouped lists (settings style)

@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    header: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Folio.colors
    Column(modifier.padding(horizontal = 16.dp)) {
        header?.let {
            Text(it.uppercase(), style = Folio.type.sectionHeader, color = c.secondaryLabel, modifier = Modifier.padding(start = 16.dp, bottom = 7.dp, top = 6.dp))
        }
        Column(Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface), content = content)
        footer?.let {
            Text(it, style = Folio.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

@Composable
fun GroupedDivider(inset: Dp = 16.dp) =
    HorizontalDivider(Modifier.padding(start = inset), thickness = 0.5.dp, color = Folio.colors.separator)

/** An icon in a rounded square, like settings icons. */
@Composable
fun RowIcon(icon: ImageVector, background: Color) {
    Box(Modifier.size(29.dp).clip(RoundedCornerShape(7.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
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
    chevron: Boolean = false,
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
            .heightIn(min = 46.dp)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            RowIcon(it, iconBackground)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Folio.type.body, color = titleColor)
            subtitle?.let { Text(it, style = Folio.type.footnote, color = c.secondaryLabel, modifier = Modifier.padding(top = 1.dp)) }
        }
        value?.let {
            Text(it, style = Folio.type.body, color = c.secondaryLabel, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
        trailing?.invoke(this)
        if (chevron) {
            Icon(Icons.Rounded.ChevronRight, null, tint = c.tertiaryLabel, modifier = Modifier.padding(start = 4.dp).size(22.dp))
        }
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
        FolioSwitch(checked, { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onChange(it) }, enabled)
    }
}

@Composable
fun FolioSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Folio.colors
    Switch(
        checked = checked, onCheckedChange = onChange, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White, checkedTrackColor = c.success, checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White, uncheckedTrackColor = c.fill, uncheckedBorderColor = Color.Transparent,
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
        Icon(Icons.Rounded.Search, null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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

/** The bar at the top of a screen. Its small title fades in once the large title scrolls away. */
@Composable
fun TopBar(
    title: String,
    glass: GlassState?,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    showDivider: Boolean = showTitle,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Folio.colors
    val titleAlpha by animateFloatAsState(if (showTitle) 1f else 0f, tween(180), label = "title")
    val dividerAlpha by animateFloatAsState(if (showDivider) 1f else 0f, tween(180), label = "divider")
    val glassAlpha by animateFloatAsState(if (showDivider) 1f else 0f, tween(220), label = "glass")
    Box(
        modifier
            .fillMaxWidth()
            .then(if (glass != null && glassAlpha > 0f) Modifier.glass(glass, c.glass.copy(alpha = c.glass.alpha * glassAlpha)) else Modifier)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(48.dp)
            .drawBehind {
                drawLine(c.separator.copy(alpha = c.separator.alpha * dividerAlpha), Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
            },
    ) {
        if (onBack != null) {
            Row(
                Modifier.align(Alignment.CenterStart).padding(start = 4.dp).clip(RoundedCornerShape(10.dp))
                    .pressable(onClick = onBack).padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Back", tint = c.accent, modifier = Modifier.size(20.dp))
                backLabel?.let { Text(it, style = Folio.type.body, color = c.accent, maxLines = 1) }
            }
        }
        Text(
            title, style = Folio.type.headline, color = c.label.copy(alpha = titleAlpha), maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 96.dp),
        )
        Row(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** The big bold title at the top of a tab, in the scrolling content. */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier, inset: Dp = 16.dp, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = inset, end = (inset - 4.dp).coerceAtLeast(0.dp), top = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = Folio.type.largeTitle, color = Folio.colors.label, modifier = Modifier.weight(1f))
        trailing()
    }
}

data class TabItem(val key: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector, val badge: Int = 0)

/** The glass tab bar along the bottom. */
@Composable
fun TabBar(tabs: List<TabItem>, selected: String, onSelect: (String) -> Unit, glass: GlassState, modifier: Modifier = Modifier) {
    val c = Folio.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .glass(glass, c.glass)
            .drawBehind { drawLine(c.separator, Offset.Zero, Offset(size.width, 0f), 0.5.dp.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(56.dp),
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.key == selected
            val tint by animateColorAsState(if (isSelected) c.accent else c.secondaryLabel.copy(alpha = 0.75f), label = "tab")
            val lift by animateDpAsState(if (isSelected) 0.dp else 1.dp, label = "lift")
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(tab.key)
                    }
                    .padding(top = 6.dp + lift),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    Icon(if (isSelected) tab.selectedIcon else tab.icon, tab.label, tint = tint, modifier = Modifier.size(26.dp))
                    if (tab.badge > 0) {
                        Box(Modifier.align(Alignment.TopEnd).padding(start = 18.dp)) { CountBadge(tab.badge, small = true) }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(tab.label, style = Folio.type.caption2, color = tint)
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
    Row(modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Folio.type.title3, color = Folio.colors.label, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) BarTextButton(action, onAction)
    }
}

/** Space for the floating tab bar at the end of scrolling content. */
@Composable
fun BottomBarSpacer() = Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(72.dp))

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = Folio.colors.accent, track: Color = Folio.colors.fill, height: Dp = 4.dp) {
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
    }
}
