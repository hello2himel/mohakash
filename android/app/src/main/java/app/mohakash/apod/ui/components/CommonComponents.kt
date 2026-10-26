package app.mohakash.apod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.mohakash.apod.R
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The top bar of a screen.
 *
 * The bar is at least 56 dp high — it can grow when the font scaling is turned up. It either
 * shows a screen title or — on the detail and settings screens — a back arrow followed by the
 * title.
 *
 * It applies the status-bar inset itself: the app is edge-to-edge so the feed can paint under the
 * status bar, which means every other top bar has to reserve that strip here, in one place.
 */
@Composable
fun MohakashTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = stringResource(R.string.detail_back),
    actions: @Composable RowScopeAlias.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = MohakashDimens.TopBarHeight)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(MohakashDimens.TouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back_24),
                    contentDescription = backContentDescription,
                    tint = MohakashColors.Text,
                )
            }
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = title,
            style = MohakashTextStyles.ScreenTitle,
            color = MohakashColors.Text,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Alias so the top bar actions can be declared without importing the whole layout scope. */
typealias RowScopeAlias = androidx.compose.foundation.layout.RowScope

/** A 48 dp icon button — the touch target of every icon action in the app. */
@Composable
fun IconActionButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MohakashColors.Text,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(MohakashDimens.TouchTarget),
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/**
 * The primary button: filled with the accent colour and fully rounded.
 *
 * The height is a minimum, never a fixed size, so the label can grow with the system font scale
 * instead of being clipped.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    textStyle: TextStyle = MohakashTextStyles.UiSmallSemiBold,
) {
    val shape = RoundedCornerShape(MohakashDimens.ButtonRadius)
    Box(
        modifier = modifier
            .heightIn(min = height)
            .clip(shape)
            .background(if (enabled) MohakashColors.Accent else MohakashColors.SurfaceStrong)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = textStyle,
            color = if (enabled) MohakashColors.OnAccent else MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** The secondary button: outlined with the strong border colour. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    textStyle: TextStyle = MohakashTextStyles.UiSmallSemiBold,
) {
    val shape = RoundedCornerShape(MohakashDimens.ButtonRadius)
    Box(
        modifier = modifier
            .heightIn(min = height)
            .clip(shape)
            .border(BorderStroke(1.dp, MohakashColors.BorderStrong), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = textStyle,
            color = if (enabled) MohakashColors.Text else MohakashColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A borderless text button — e.g. *Skip for now* on the onboarding screen.
 *
 * It has no background and no border; only the text and the touch area are visible.
 */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 52.dp,
    color: Color = MohakashColors.Text,
    style: TextStyle = MohakashTextStyles.UiMedium,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
) {
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(MohakashDimens.ButtonRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

/** A quiet text action, e.g. *Retry* or *Cancel*. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MohakashColors.Accent,
    minHeight: Dp = 40.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
) {
    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(MohakashDimens.ButtonRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MohakashTextStyles.UiSmallSemiBold,
            color = color,
        )
    }
}

/**
 * A filter chip.
 *
 * The visible chip is 40 dp high but the whole 48 dp touch target is clickable. A selected chip
 * is filled with the accent colour and uses a bold weight — exactly one accent is visible at a
 * time.
 */
@Composable
fun FilterChipItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .heightIn(min = MohakashDimens.TouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = MohakashDimens.ChipHeight)
                .clip(shape)
                .background(if (selected) MohakashColors.Accent else Color.Transparent)
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(BorderStroke(1.dp, MohakashColors.BorderStrong), shape)
                    },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = if (selected) MohakashTextStyles.UiSmallSemiBold else MohakashTextStyles.UiSmall,
                color = if (selected) MohakashColors.OnAccent else MohakashColors.Text,
            )
        }
    }
}

/**
 * A segmented control, e.g. *Fill / Fit* or *All / Images / Videos*.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .border(BorderStroke(1.dp, MohakashColors.BorderStrong), shape),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .heightIn(min = MohakashDimens.ChipHeight)
                    .background(if (selected) MohakashColors.Accent else Color.Transparent)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = if (selected) MohakashTextStyles.UiSmallSemiBold else MohakashTextStyles.UiSmall,
                    color = if (selected) MohakashColors.OnAccent else MohakashColors.Text,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * A picture.
 *
 * The placeholder is the plain surface colour so the layout never jumps while the picture
 * loads. When [unavailableLabel] is set and the picture cannot be loaded, the frame keeps its
 * size and shows the muted label instead.
 */
@Composable
fun PictureFrame(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = MohakashDimens.ImageRadius,
    borderColor: Color? = MohakashColors.Border,
    unavailableLabel: String? = null,
) {
    val shape = RoundedCornerShape(cornerRadius)
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) { failed = false }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MohakashColors.Surface)
            .then(
                if (borderColor != null) Modifier.border(BorderStroke(1.dp, borderColor), shape) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!url.isNullOrBlank() && !failed) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
                onError = { failed = true },
                onSuccess = { failed = false },
            )
        }
        if ((url.isNullOrBlank() || failed) && unavailableLabel != null) {
            Text(
                text = unavailableLabel,
                style = MohakashTextStyles.Helper,
                color = MohakashColors.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/**
 * A full-bleed picture frame with a 1 dp top and bottom border.
 *
 * The whole frame can be a tap target; the optional [interactionLabel] is the content description
 * of that target. Video entries get the centred play glyph and the small *Video* chip.
 */
@Composable
fun FullBleedImageFrame(
    url: String?,
    contentDescription: String?,
    height: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interactionLabel: String? = null,
    isVideo: Boolean = false,
    videoLabel: String? = null,
    playContentDescription: String? = null,
    unavailableLabel: String? = null,
) {
    val click = onClick
    val clickModifier = if (click != null) {
        Modifier.clickable(role = Role.Button, onClick = click)
    } else {
        Modifier
    }
    val interactionModifier = if (interactionLabel != null) {
        Modifier.semantics { this.contentDescription = interactionLabel }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(MohakashColors.Surface)
            .then(clickModifier)
            .then(interactionModifier),
        contentAlignment = Alignment.Center,
    ) {
        PictureFrame(
            url = url,
            contentDescription = if (click == null) contentDescription else null,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 0.dp,
            borderColor = null,
            unavailableLabel = unavailableLabel,
        )
        if (isVideo) {
            Icon(
                painter = painterResource(R.drawable.ic_play_24),
                contentDescription = playContentDescription,
                tint = MohakashColors.TextMuted,
                modifier = Modifier.size(56.dp),
            )
            if (videoLabel != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MohakashColors.Background)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = videoLabel,
                        style = MohakashTextStyles.Caption.copy(fontWeight = FontWeight.SemiBold),
                        color = MohakashColors.Text,
                    )
                }
            }
        }
        // The frame is full-bleed: the hairlines sit on the very top and bottom edge.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(MohakashColors.Border),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(MohakashColors.Border),
        )
    }
}

/** A skeleton block, shown where the content will be while it loads. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MohakashColors.SurfaceStrong),
    )
}

/**
 * Marks a whole subtree as busy while its content is loading.
 *
 * This is the Compose equivalent of `aria-busy`: TalkBack announces the description and the
 * subtree gets an explicit state description.
 */
fun Modifier.busySemantics(label: String): Modifier = semantics {
    stateDescription = label
    contentDescription = label
}

/** A row that opens an external link. */
@Composable
fun LinkRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    minHeight: Dp = 48.dp,
) {
    Column(modifier = modifier) {
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MohakashColors.Divider),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = text,
                style = MohakashTextStyles.UiSmall,
                color = MohakashColors.Text,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_external_24),
                contentDescription = null,
                tint = MohakashColors.TextMuted,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp),
            )
        }
    }
}

/** A settings row that opens another screen, with an optional subtitle and a chevron. */
@Composable
fun NavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true,
    minHeight: Dp = 64.dp,
) {
    Column(modifier = modifier) {
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MohakashColors.Divider),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MohakashTextStyles.UiMedium,
                    color = MohakashColors.Text,
                )
                if (subtitle != null) {
                    EllipsizedText(
                        text = subtitle,
                        style = MohakashTextStyles.Helper,
                        color = MohakashColors.TextMuted,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right_24),
                contentDescription = null,
                tint = MohakashColors.TextMuted,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp),
            )
        }
    }
}

/** A settings row with a title, an optional subtitle and a switch. The whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true,
    minHeight: Dp = 64.dp,
) {
    Column(modifier = modifier) {
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MohakashColors.Divider),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MohakashTextStyles.UiMedium,
                    color = MohakashColors.Text,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MohakashTextStyles.Helper,
                        color = MohakashColors.TextMuted,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 12.dp),
                colors = MohakashColors.switchColors(),
            )
        }
    }
}

/** A settings row with a title and a value on the right. */
@Composable
fun InfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    minHeight: Dp = 56.dp,
) {
    Column(modifier = modifier) {
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MohakashColors.Divider),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MohakashTextStyles.UiMedium,
                color = MohakashColors.Text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MohakashTextStyles.UiSmall,
                color = MohakashColors.TextMuted,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

/** The small grey label above a group of settings. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MohakashTextStyles.Caption,
        color = MohakashColors.TextMuted,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

/** A highlighted note, e.g. the explanation that a video cannot become a wallpaper. */
@Composable
fun NoteBox(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MohakashDimens.BannerRadius))
            .background(MohakashColors.SurfaceStrong)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MohakashTextStyles.Helper,
            color = MohakashColors.TextBody,
        )
    }
}

/** A single line of text that is cut off with an ellipsis. */
@Composable
fun EllipsizedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MohakashTextStyles.UiSmall,
    color: Color = MohakashColors.Text,
) {
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The one switch style of the app: accent when on, surface and border-strong when off. */
@Composable
fun MohakashColors.switchColors() = SwitchDefaults.colors(
    checkedThumbColor = MohakashColors.OnAccent,
    checkedTrackColor = MohakashColors.Accent,
    checkedBorderColor = MohakashColors.Accent,
    uncheckedThumbColor = MohakashColors.TextMuted,
    uncheckedTrackColor = MohakashColors.Surface,
    uncheckedBorderColor = MohakashColors.BorderStrong,
)
