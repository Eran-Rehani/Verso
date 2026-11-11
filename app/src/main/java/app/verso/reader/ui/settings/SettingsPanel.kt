package app.verso.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.core.store.FontChoice
import app.verso.core.store.ReadAlign
import app.verso.core.store.ReaderSettings
import app.verso.core.store.SettingsLimits
import app.verso.core.store.ThemeChoice
import app.verso.core.store.coerced
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPanel(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.45f).dp

    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .testTag("settings_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .navigationBarsPadding()
        ) {
            // Theme selection
            Text(
                text = "Theme",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                val themeOptions = listOf(
                    ThemeChoice.System to "System",
                    ThemeChoice.Light to "Light",
                    ThemeChoice.Sepia to "Sepia",
                    ThemeChoice.Dark to "Dark",
                    ThemeChoice.Black to "Black"
                )
                for ((choice, label) in themeOptions) {
                    FilterChip(
                        selected = settings.theme == choice,
                        onClick = { onChange(settings.copy(theme = choice).coerced()) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Font selection
            Text(
                text = "Font",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                val fontOptions = listOf(
                    FontChoice.Sans to "Sans",
                    FontChoice.Serif to "Serif",
                    FontChoice.Mono to "Mono"
                )
                for ((choice, label) in fontOptions) {
                    FilterChip(
                        selected = settings.font == choice,
                        onClick = { onChange(settings.copy(font = choice).coerced()) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Alignment
            Text(
                text = "Align",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                FilterChip(
                    selected = settings.align == ReadAlign.Start,
                    onClick = { onChange(settings.copy(align = ReadAlign.Start).coerced()) },
                    label = { Text("Left") }
                )
                FilterChip(
                    selected = settings.align == ReadAlign.Justify,
                    onClick = { onChange(settings.copy(align = ReadAlign.Justify).coerced()) },
                    label = { Text("Justified") }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Font Size slider (12..48, step 1 -> 35 steps)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Size",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${settings.fontSizeSp.toInt()}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = settings.fontSizeSp,
                onValueChange = { onChange(settings.copy(fontSizeSp = it).coerced()) },
                valueRange = SettingsLimits.FONT_SIZE,
                steps = 35,
                modifier = Modifier.semantics { contentDescription = "Size" }
            )

            // Line spacing slider (1.0..2.4, step 0.05 -> 27 steps)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Line spacing",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = String.format(Locale.US, "%.2f", settings.lineHeight),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = settings.lineHeight,
                onValueChange = { onChange(settings.copy(lineHeight = it).coerced()) },
                valueRange = SettingsLimits.LINE_HEIGHT,
                steps = 27,
                modifier = Modifier.semantics { contentDescription = "Line spacing" }
            )

            // Paragraph spacing slider (0.0..2.0, step 0.1 -> 19 steps)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Paragraph spacing",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = String.format(Locale.US, "%.1fem", settings.paragraphSpacingEm),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = settings.paragraphSpacingEm,
                onValueChange = { onChange(settings.copy(paragraphSpacingEm = it).coerced()) },
                valueRange = SettingsLimits.PARAGRAPH_SPACING,
                steps = 19,
                modifier = Modifier.semantics { contentDescription = "Paragraph spacing" }
            )

            // Margins slider (0..48, step 4 -> 11 steps)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Margins",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${settings.marginDp}dp",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Slider(
                value = settings.marginDp.toFloat(),
                onValueChange = { onChange(settings.copy(marginDp = it.toInt()).coerced()) },
                valueRange = 0f..48f,
                steps = 11,
                modifier = Modifier.semantics { contentDescription = "Margins" }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Keep screen on switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Keep screen on",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = settings.keepScreenOn,
                    onCheckedChange = { onChange(settings.copy(keepScreenOn = it).coerced()) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reset button
            TextButton(
                onClick = { onChange(ReaderSettings()) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Reset")
            }
        }
    }
}
