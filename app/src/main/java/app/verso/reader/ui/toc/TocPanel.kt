package app.verso.reader.ui.toc

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.core.toc.TocEntry
import app.verso.core.toc.TocExtractor
import app.verso.reader.ui.reader.ReaderPalette

@Composable
fun TocPanel(
    entries: List<TocEntry>,
    currentBlockIndex: Int,
    palette: ReaderPalette,
    onSelectBlock: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.68f).dp

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val activeIndex = remember(entries, currentBlockIndex) {
        TocExtractor.activeEntryIndex(entries, currentBlockIndex)
    }
    val prevEntry = remember(entries, currentBlockIndex) {
        TocExtractor.previousEntry(entries, currentBlockIndex)
    }
    val nextEntry = remember(entries, currentBlockIndex) {
        TocExtractor.nextEntry(entries, currentBlockIndex)
    }

    val filteredEntries = remember(entries, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) entries
        else entries.filter { it.title.contains(q, ignoreCase = true) }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex, searchQuery) {
        if (searchQuery.isBlank() && activeIndex in entries.indices) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.scrollToItem(targetScroll)
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .testTag("toc_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Drag handle indicator
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.rule)
                )
            }

            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Table of Contents",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    if (entries.isNotEmpty()) {
                        val sectionWord = if (entries.size == 1) "section" else "sections"
                        Text(
                            text = "${entries.size} $sectionWord",
                            fontSize = 12.sp,
                            color = palette.muted
                        )
                    }
                }

                if (entries.size > 1) {
                    IconButton(
                        onClick = { prevEntry?.let { onSelectBlock(it.blockIndex) } },
                        enabled = prevEntry != null,
                        modifier = Modifier.testTag("toc_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Previous section",
                            tint = if (prevEntry != null) palette.text else palette.muted.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(
                        onClick = { nextEntry?.let { onSelectBlock(it.blockIndex) } },
                        enabled = nextEntry != null,
                        modifier = Modifier.testTag("toc_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Next section",
                            tint = if (nextEntry != null) palette.text else palette.muted.copy(alpha = 0.4f)
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("toc_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close table of contents",
                        tint = palette.muted
                    )
                }
            }

            // Search bar when there are 4 or more sections
            if (entries.size >= 4) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Filter sections…",
                            fontSize = 14.sp,
                            color = palette.muted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = palette.muted,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear filter",
                                    tint = palette.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.link,
                        unfocusedBorderColor = palette.rule,
                        focusedTextColor = palette.text,
                        unfocusedTextColor = palette.text,
                        cursorColor = palette.link
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .testTag("toc_search_input")
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            HorizontalDivider(thickness = 1.dp, color = palette.rule.copy(alpha = 0.6f))

            if (entries.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = "No sections found in this document.",
                        fontSize = 15.sp,
                        color = palette.muted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            onSelectBlock(0)
                            onClose()
                        },
                        modifier = Modifier.testTag("toc_jump_top_button")
                    ) {
                        Text(
                            text = "Jump to beginning",
                            color = palette.link,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else if (filteredEntries.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = "No sections match \"$searchQuery\".",
                        fontSize = 15.sp,
                        color = palette.muted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toc_list")
                ) {
                    items(
                        items = filteredEntries,
                        key = { it.index }
                    ) { entry ->
                        val isActive = entry.index == activeIndex
                        TocEntryRow(
                            entry = entry,
                            isActive = isActive,
                            palette = palette,
                            onClick = {
                                onSelectBlock(entry.blockIndex)
                                onClose()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TocEntryRow(
    entry: TocEntry,
    isActive: Boolean,
    palette: ReaderPalette,
    onClick: () -> Unit
) {
    val indentDp = ((entry.level - 1).coerceIn(0, 3) * 16).dp
    val percent = (entry.progressFraction * 100).toInt()

    val fontSize = when (entry.level) {
        1 -> 15.5.sp
        2 -> 14.5.sp
        else -> 13.5.sp
    }

    val fontWeight = when {
        isActive -> FontWeight.Bold
        entry.level == 1 -> FontWeight.SemiBold
        entry.level == 2 -> FontWeight.Medium
        else -> FontWeight.Normal
    }

    val textColor = when {
        isActive -> palette.link
        entry.level >= 3 -> palette.muted
        else -> palette.text
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) palette.codeBackground else palette.background.copy(alpha = 0f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp + indentDp, end = 12.dp, top = 11.dp, bottom = 11.dp)
            .testTag("toc_item_${entry.index}")
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.link)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Text(
            text = entry.title,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "$percent%",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) palette.link else palette.muted
        )
    }
}
