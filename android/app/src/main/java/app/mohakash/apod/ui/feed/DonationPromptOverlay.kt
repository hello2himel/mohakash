package app.mohakash.apod.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mohakash.apod.R
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import app.mohakash.apod.ui.theme.MohakashColors
import app.mohakash.apod.ui.theme.MohakashTextStyles

/** A rare, dismissible support card over the feed. It never opens a page automatically. */
@Composable
internal fun DonationPromptOverlay(
    onSupport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(enabled = true, onClick = onDismiss)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MohakashColors.Surface)
                .border(1.dp, MohakashColors.Accent.copy(alpha = 0.28f), shape)
                .clickable(onClick = {})
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_heart_24),
                contentDescription = null,
                tint = MohakashColors.Accent,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = stringResource(R.string.donation_feed_title),
                style = MohakashTextStyles.OnboardingTitle,
                color = MohakashColors.Text,
            )
            Text(
                text = stringResource(R.string.donation_feed_body),
                style = MohakashTextStyles.BodyBase,
                color = MohakashColors.TextMuted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DonationButton(
                    text = stringResource(R.string.settings_support_action),
                    icon = R.drawable.ic_heart_24,
                    primary = true,
                    onClick = onSupport,
                    modifier = Modifier.weight(1f),
                )
                DonationButton(
                    text = stringResource(R.string.donation_feed_not_now),
                    icon = R.drawable.ic_close_24,
                    primary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
}


/** A compact icon+label action used by the support card. */
@Composable
private fun DonationButton(
    text: String,
    icon: Int,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val contentColor = if (primary) MohakashColors.OnAccent else MohakashColors.TextMuted.copy(alpha = 0.62f)
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (primary) MohakashColors.Accent else Color.Transparent)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = text,
            style = MohakashTextStyles.UiSmallSemiBold.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}
