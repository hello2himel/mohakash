package app.mohakash.apod.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import app.mohakash.apod.R
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashDimens
import app.mohakash.apod.ui.theme.MohakashTextStyles

/**
 * The top bar of a screen.
 *
 * The bar is 56 dp high. It either shows a screen title or — on the Today screen — a title and a
 * trailing action.
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
            .height(MohakashDimens.TopBarHeight)
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

/** The primary button: filled with the accent colour and fully rounded. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(MohakashDimens.ButtonRadius)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (enabled) MohakashColors.Accent else MohakashColors.SurfaceStrong)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MohakashTextStyles.UiMedium,
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
) {
    val shape = RoundedCornerShape(MohakashDimens.ButtonRadius)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, MohakashColors.BorderStrong), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MohakashTextStyles.UiMedium,
            color = MohakashColors.Text,
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
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MohakashTextStyles.UiSmall,
            color = color,
        )
    }
}

/**
 * A filter chip.
 *
 * A selected chip is filled with the accent colour and uses a bold weight — exactly one accent
 * is visible at a time.
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
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) MohakashColors.Accent else Color.Transparent)
            .then(
                if (selected) {
                    Modifier
                } else {
                    Modifier.border(BorderStroke(1.dp, MohakashColors.BorderStrong), shape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (selected) MohakashTextStyles.UiSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) else MohakashTextStyles.UiSmall,
            color = if (selected) MohakashColors.OnAccent else MohakashColors.Text,
            maxLines = 1,
        )
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
                    .heightIn(min = 40.dp)
                    .background(if (selected) MohakashColors.Accent else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = if (selected) {
                        MohakashTextStyles.UiSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    } else {
                        MohakashTextStyles.UiSmall
                    },
                    color = if (selected) MohakashColors.OnAccent else MohakashColors.Text,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A picture.
 *
 * The placeholder is the plain surface colour so the layout never jumps while the picture loads.
 */
@Composable
fun PictureFrame(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: androidx.compose.ui.unit.Dp = MohakashDimens.ImageRadius,
    borderColor: Color? = MohakashColors.Border,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MohakashColors.Surface)
            .then(
                if (borderColor != null) Modifier.border(BorderStroke(1.dp, borderColor), shape) else Modifier,
            ),
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** A row that opens an external link. */
@Composable
fun LinkRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
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
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = text,
                style = MohakashTextStyles.UiSmall,
                color = MohakashColors.Text,
            )
            Icon(
                painter = painterResource(R.drawable.ic_external_24),
                contentDescription = null,
                tint = MohakashColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A settings row with a title, an optional subtitle and a switch. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDivider: Boolean = true,
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
                .heightIn(min = 64.dp)
                .clickable(role = Role.Switch) { onCheckedChange(!checked) }
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
                        style = MohakashTextStyles.Caption,
                        color = MohakashColors.TextMuted,
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MohakashColors.OnAccent,
                    checkedTrackColor = MohakashColors.Accent,
                    checkedBorderColor = MohakashColors.Accent,
                    uncheckedThumbColor = MohakashColors.TextMuted,
                    uncheckedTrackColor = MohakashColors.Surface,
                    uncheckedBorderColor = MohakashColors.BorderStrong,
                ),
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
                .heightIn(min = 56.dp)
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
            .clip(RoundedCornerShape(MohakashDimens.CardRadius))
            .background(MohakashColors.SurfaceStrong)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MohakashTextStyles.Caption.copy(
                fontFamily = MohakashTextStyles.BodyMedium.fontFamily,
                fontSize = MohakashTextStyles.BodyMedium.fontSize,
                lineHeight = MohakashTextStyles.BodyMedium.lineHeight,
            ),
            color = MohakashColors.TextBody,
        )
    }
}

/** A single line of text that is cut off with an ellipsis. */
@Composable
fun EllipsizedText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MohakashTextStyles.UiSmall,
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
