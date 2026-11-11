package app.verso.reader.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.core.store.RecentEntry
import app.verso.core.store.progress
import app.verso.reader.sample.SampleDocumentSpec
import app.verso.reader.sample.SampleDocuments
import app.verso.reader.ui.reader.ReaderPalette

@Composable
fun HomeScreen(
    recents: List<RecentEntry>,
    palette: ReaderPalette,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenFile: () -> Unit,
    onPaste: () -> Unit,
    onLoadSample: (() -> Unit)? = null,
    onSelectSample: ((SampleDocumentSpec) -> Unit)? = null,
    samples: List<SampleDocumentSpec> = SampleDocuments.all,
    onNavigateToLibrary: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = "Verso",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    Text(
                        text = "Focused offline reader for Markdown, LaTeX, EPUB, PDF & Text",
                        fontSize = 12.5.sp,
                        color = palette.muted
                    )
                }
            }

            // Action buttons row: Open file & Paste
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Button(
                    onClick = onOpenFile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.link,
                        contentColor = palette.background
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_file_button")
                ) {
                    Text(
                        text = "Open file",
                        fontWeight = FontWeight.Medium
                    )
                }

                FilledTonalButton(
                    onClick = onPaste,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = palette.codeBackground,
                        contentColor = palette.text
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("paste_button")
                ) {
                    Text(
                        text = "Paste",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (onLoadSample != null) {
                OutlinedButton(
                    onClick = onLoadSample,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .testTag("load_sample_button")
                ) {
                    Text(
                        text = "Open LaTeX & TOC Sample",
                        color = palette.link,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (onSelectSample != null && samples.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Explore Sample Formats",
                    color = palette.muted,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .testTag("sample_documents_row")
                ) {
                    samples.forEach { spec ->
                        SampleDocumentCard(
                            spec = spec,
                            palette = palette,
                            onClick = { onSelectSample(spec) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (recents.isEmpty()) {
                // Empty state
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Share text, a PDF or an EPUB to Verso, tap Open file, or pick a sample format above.",
                        color = palette.muted,
                        fontSize = 15.5.sp,
                        lineHeight = 23.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Recent documents",
                        color = palette.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (onNavigateToLibrary != null) {
                        Text(
                            text = "View all in Library →",
                            color = palette.link,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable(onClick = onNavigateToLibrary)
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                .testTag("home_view_library_link")
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_list")
                ) {
                    items(
                        items = recents,
                        key = { it.id }
                    ) { entry ->
                        RecentItemRow(
                            entry = entry,
                            palette = palette,
                            onClick = { onOpen(entry.id) },
                            onDelete = { onDelete(entry.id) }
                        )
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = palette.rule.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleDocumentCard(
    spec: SampleDocumentSpec,
    palette: ReaderPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(205.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.codeBackground.copy(alpha = 0.65f))
            .border(1.dp, palette.rule.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("sample_card_${spec.id}")
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(palette.link.copy(alpha = 0.14f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = spec.badge,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = palette.link
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = spec.title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = spec.subtitle,
            fontSize = 11.5.sp,
            lineHeight = 15.sp,
            color = palette.muted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentItemRow(
    entry: RecentEntry,
    palette: ReaderPalette,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressValue = entry.progress.coerceIn(0f, 1f)
    val percent = (progressValue * 100).toInt()
    val subtitle = "${entry.format} · $percent%"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag("recent_item_${entry.id}")
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = entry.title.ifBlank { "Untitled" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = palette.muted
                )
                if (progressValue > 0f) {
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier
                            .width(56.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = palette.link,
                        trackColor = palette.rule
                    )
                }
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("remove_recent_${entry.id}")
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove from recents",
                tint = palette.muted
            )
        }
    }
}
