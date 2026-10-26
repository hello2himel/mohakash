package app.mohakash.apod.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mohakash.apod.R
import app.mohakash.apod.ui.theme.MohakashColors

/**
 * The app mark on the dock's blue key: a black brand mark on a restrained Nova-blue gradient.
 *
 * The black mark is intentional; it is the one place in the app where the logo sits on the
 * accent surface rather than on Deep Space.
 */
@Composable
internal fun BrandMarkBadge(
    badgeSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        MohakashColors.AccentLight,
                        MohakashColors.Accent,
                        MohakashColors.AccentDeep,
                    ),
                ),
                shape = CircleShape,
            )
            .border(1.dp, MohakashColors.AccentLight.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mohakash_mark_24),
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(iconSize),
        )
    }
}
