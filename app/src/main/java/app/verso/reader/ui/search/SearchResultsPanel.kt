package app.verso.reader.ui.search

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.core.search.DocumentSearchMatch
import app.verso.core.search.DocumentSearchResult
import app.verso.reader.ui.reader.ReaderPalette

@Composable
fun SearchResultsPanel(
    searchResult: DocumentSearchResult,
    palette: ReaderPalette,
    onSelectMatch: (DocumentSearchMatch) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.65f).dp
    val listState = rememberLazyListState()

    LaunchedEffect(searchResult.currentMatchIndex) {
        if (searchResult.matches.isNotEmpty() && searchResult.currentMatchIndex in searchResult.matches.indices) {
            val targetScroll = (searchResult.currentMatchIndex - 2).coerceAtLeast(0)
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
            .testTag("search_results_panel")
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
                        text = "Search Results",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    val count = searchResult.totalCount
                    val matchWord = if (count == 1) "occurrence" else "occurrences"
                    Text(
                        text = "$count $matchWord for \"${searchResult.query}\"",
                        fontSize = 12.sp,
                        color = palette.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("search_results_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close search results",
                        tint = palette.muted
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = palette.rule.copy(alpha = 0.6f))

            if (searchResult.matches.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Text(
                        text = "No matches found for \"${searchResult.query}\".",
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
                        .testTag("search_results_list")
                ) {
                    items(
                        items = searchResult.matches,
                        key = { it.matchId }
                    ) { match ->
                        val isActive = match.matchId == searchResult.currentMatchIndex
                        SearchResultItemRow(
                            match = match,
                            isActive = isActive,
                            palette = palette,
                            onClick = { onSelectMatch(match) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItemRow(
    match: DocumentSearchMatch,
    isActive: Boolean,
    palette: ReaderPalette,
    onClick: () -> Unit
) {
    val percent = (match.progressFraction * 100).toInt()

    val snippetAnnotated = buildAnnotatedString {
        append(match.snippet)
        val s = match.snippetMatchStart.coerceIn(0, match.snippet.length)
        val e = match.snippetMatchEnd.coerceIn(0, match.snippet.length)
        if (e > s) {
            val bg = if (isActive) {
                if (palette.isDark) Color(0xFFD97706) else Color(0xFFFF9800)
            } else {
                if (palette.isDark) Color(0xFF6B4F1D) else Color(0xFFFFE082)
            }
            val fg = if (isActive) Color.White else if (palette.isDark) Color(0xFFFFF8E1) else Color(0xFF1A1A1A)
            addStyle(
                SpanStyle(
                    background = bg,
                    color = fg,
                    fontWeight = FontWeight.Bold
                ),
                s,
                e
            )
        }
    }

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) palette.codeBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("search_result_item_${match.matchId}")
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.link)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Match index badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isActive) palette.link else palette.rule.copy(alpha = 0.6f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "#${match.matchId + 1}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) palette.background else palette.text
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (!match.sectionTitle.isNullOrBlank()) {
                Text(
                    text = match.sectionTitle,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text(
                text = snippetAnnotated,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "$percent%",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isActive) palette.link else palette.muted
        )
    }
}
