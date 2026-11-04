package com.music.bitchord.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.sharedui.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The Mix editor: opened from the Mix segment beside shuffle, repeat and
 * autoplay while the queue is up.
 *
 * The switch is Automix itself — the same setting as in Settings — so the two
 * places can never disagree. Length and style apply to every transition; the
 * planner still decides anything left on Auto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MixSheet(onDismiss: () -> Unit) {
    val on by PlayerSettings.smartFadeEnabled.collectAsStateWithLifecycle()
    val bars by PlayerSettings.smartMixBars.collectAsStateWithLifecycle()
    val style by PlayerSettings.smartMixStyle.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.mix_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.mix_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = on,
                    onCheckedChange = { PlayerSettings.setSmartFadeEnabled(it) },
                )
            }

            Spacer(Modifier.height(24.dp))
            MixOptionGroup(
                title = stringResource(Res.string.mix_length),
                options = listOf(
                    0 to stringResource(Res.string.mix_length_auto),
                    8 to stringResource(Res.string.mix_length_8),
                    16 to stringResource(Res.string.mix_length_16),
                ),
                selected = bars,
                enabled = on,
                onSelect = { PlayerSettings.setSmartMixBars(it) },
            )

            Spacer(Modifier.height(20.dp))
            MixOptionGroup(
                title = stringResource(Res.string.mix_style),
                options = listOf(
                    0 to stringResource(Res.string.mix_style_auto),
                    1 to stringResource(Res.string.mix_style_crossfade),
                    2 to stringResource(Res.string.mix_style_classic),
                    3 to stringResource(Res.string.mix_style_quick_cut),
                    4 to stringResource(Res.string.mix_style_long_fade),
                ),
                selected = style,
                enabled = on,
                onSelect = { PlayerSettings.setSmartMixStyle(it) },
            )
            // What the chosen style does, in a line: the names alone do not say.
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    when (style) {
                        1 -> Res.string.mix_style_crossfade_desc
                        2 -> Res.string.mix_style_classic_desc
                        3 -> Res.string.mix_style_quick_cut_desc
                        4 -> Res.string.mix_style_long_fade_desc
                        else -> Res.string.mix_style_auto_desc
                    },
                ),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(if (on) 1f else 0.4f),
            )

            if (!on) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.mix_off_note),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A titled row of exclusive choices, dimmed and inert while Mix is off. */
@Composable
private fun MixOptionGroup(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.alpha(if (enabled) 1f else 0.4f)) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        // Three to a row: five chips in one would be too narrow to read.
        options.chunked(MIX_OPTIONS_PER_ROW).forEachIndexed { rowIndex, rowOptions ->
            if (rowIndex > 0) Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowOptions.forEach { (value, label) ->
                    val isSelected = value == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                },
                            )
                            .clickable(enabled = enabled) { onSelect(value) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
                // A short last row keeps its chips the width of the rows above it.
                repeat(MIX_OPTIONS_PER_ROW - rowOptions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val MIX_OPTIONS_PER_ROW = 3
