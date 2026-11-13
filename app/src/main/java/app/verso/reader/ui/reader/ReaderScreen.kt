package app.verso.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.verso.core.bookmark.BookmarkHelper
import app.verso.core.model.Document
import app.verso.core.search.DocumentSearchMatch
import app.verso.core.search.DocumentSearchResult
import app.verso.core.search.DocumentSearcher
import app.verso.core.store.ReaderSettings
import app.verso.core.toc.TocExtractor
import app.verso.reader.data.db.BookmarkEntity
import app.verso.reader.tts.rememberReaderTtsController
import app.verso.reader.ui.bookmark.BookmarksSidebarOverlay
import app.verso.reader.ui.search.SearchResultsPanel
import app.verso.reader.ui.toc.TocPanel
import app.verso.reader.ui.tts.TtsPlayerBar
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
@Composable
fun ReaderScreen(
    document: Document,
    settings: ReaderSettings,
    palette: ReaderPalette,
    initialBlock: Int,
    initialOffsetPx: Int,
    chromeVisible: Boolean,
    onToggleChrome: () -> Unit,
    onAnchor: (block: Int, offsetPx: Int) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    tocOpen: Boolean = false,
    settingsOpen: Boolean = false,
    onToggleToc: () -> Unit = {},
    onCloseToc: () -> Unit = {},
    searchOpen: Boolean = false,
    onToggleSearch: () -> Unit = {},
    onCloseSearch: () -> Unit = {},
    bookmarks: List<BookmarkEntity> = emptyList(),
    bookmarksSidebarOpen: Boolean = false,
    onToggleBookmark: (blockIndex: Int, offsetPx: Int) -> Unit = { _, _ -> },
    onDeleteBookmark: (BookmarkEntity) -> Unit = {},
    onUpdateBookmarkLabel: (BookmarkEntity, String) -> Unit = { _, _ -> },
    onToggleBookmarksSidebar: () -> Unit = {},
    onCloseBookmarksSidebar: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialBlock.coerceIn(0, (document.blocks.size - 1).coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = initialOffsetPx.coerceAtLeast(0)
    )

    // Full-text search state
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var currentMatchIndex by rememberSaveable { mutableStateOf(0) }
    var searchResultsSheetOpen by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // Interactive LaTeX Equation Inspector modal state
    var inspectedEquationLatex by rememberSaveable { mutableStateOf<String?>(null) }

    // On-device Text-to-Speech (Read Aloud) controller & state
    val ttsController = rememberReaderTtsController(document)
    val ttsState by ttsController.state.collectAsStateWithLifecycle()

    val tocEntries = remember(document) {
        TocExtractor.extract(document)
    }

    val searchMatches = remember(document, searchQuery, tocEntries) {
        if (searchQuery.isNotBlank()) {
            DocumentSearcher.search(document, searchQuery, tocEntries)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(searchMatches.size) {
        if (currentMatchIndex >= searchMatches.size) {
            currentMatchIndex = 0
        }
    }

    // Auto-focus search text field when opened
    LaunchedEffect(searchOpen) {
        if (searchOpen) {
            delay(120)
            try {
                searchFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val activeMatch: DocumentSearchMatch? = searchMatches.getOrNull(currentMatchIndex)

    val searchResult = remember(searchQuery, searchMatches, currentMatchIndex) {
        DocumentSearchResult(
            query = searchQuery,
            matches = searchMatches,
            currentMatchIndex = currentMatchIndex
        )
    }

    fun jumpToBlock(targetBlock: Int, targetOffsetPx: Int = 0) {
        val coerced = targetBlock.coerceIn(0, (document.blocks.size - 1).coerceAtLeast(0))
        val safeOffset = targetOffsetPx.coerceAtLeast(0)
        coroutineScope.launch {
            listState.animateScrollToItem(coerced, safeOffset)
            onAnchor(coerced, safeOffset)
        }
    }

    // Auto-scroll to currently spoken block when Read Aloud is active
    LaunchedEffect(ttsState.activeBlockIndex, ttsState.autoScroll) {
        val spokenBlock = ttsState.activeBlockIndex
        if (spokenBlock != null && ttsState.autoScroll && document.blocks.isNotEmpty()) {
            val coerced = spokenBlock.coerceIn(0, document.blocks.lastIndex)
            listState.animateScrollToItem(coerced, 0)
            onAnchor(coerced, 0)
        }
    }

    fun jumpToMatch(index: Int) {
        if (searchMatches.isEmpty()) return
        val targetIndex = index.coerceIn(0, searchMatches.lastIndex)
        currentMatchIndex = targetIndex
        val match = searchMatches[targetIndex]
        coroutineScope.launch {
            listState.animateScrollToItem(match.blockIndex, 0)
            onAnchor(match.blockIndex, 0)
        }
    }

    fun goToNextMatch() {
        if (searchMatches.isEmpty()) return
        val nextIdx = DocumentSearcher.nextMatchIndex(currentMatchIndex, searchMatches.size)
        jumpToMatch(nextIdx)
    }

    fun goToPrevMatch() {
        if (searchMatches.isEmpty()) return
        val prevIdx = DocumentSearcher.prevMatchIndex(currentMatchIndex, searchMatches.size)
        jumpToMatch(prevIdx)
    }

    val currentBlockIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    val currentOffsetPx by remember {
        derivedStateOf { listState.firstVisibleItemScrollOffset }
    }

    val currentPageNumber by remember(document.blocks.size) {
        derivedStateOf { BookmarkHelper.pageForBlock(listState.firstVisibleItemIndex) }
    }

    val totalPages = remember(document.blocks.size) {
        BookmarkHelper.totalPages(document.blocks.size)
    }

    val bookmarkedBlockMap = remember(bookmarks) {
        bookmarks.associateBy { it.blockIndex }
    }

    val isCurrentBlockBookmarked by remember(bookmarks) {
        derivedStateOf { bookmarkedBlockMap.containsKey(listState.firstVisibleItemIndex) }
    }

    val activeTocIndex by remember(tocEntries) {
        derivedStateOf { TocExtractor.activeEntryIndex(tocEntries, listState.firstVisibleItemIndex) }
    }

    val activeTocEntry = tocEntries.getOrNull(activeTocIndex)

    val prevTocEntry by remember(tocEntries) {
        derivedStateOf { TocExtractor.previousEntry(tocEntries, listState.firstVisibleItemIndex) }
    }

    val nextTocEntry by remember(tocEntries) {
        derivedStateOf { TocExtractor.nextEntry(tocEntries, listState.firstVisibleItemIndex) }
    }

    // Debounced anchor tracking
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(400)
            .collect { (block, offset) ->
                onAnchor(block, offset)
            }
    }

    DisposableEffect(listState) {
        onDispose {
            onAnchor(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    val progressFraction by remember(document.blocks.size) {
        derivedStateOf {
            if (document.blocks.size <= 1) {
                0f
            } else {
                (listState.firstVisibleItemIndex.toFloat() / (document.blocks.size - 1)).coerceIn(0f, 1f)
            }
        }
    }

    val activeQuery = if (searchOpen && searchQuery.isNotBlank()) searchQuery else null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = settings.marginDp.dp,
                end = settings.marginDp.dp,
                top = 24.dp,
                bottom = if (ttsState.isActive) 210.dp else 96.dp
            ),
            modifier = Modifier
                .fillMaxSize()
                .testTag("reader_list")
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (!searchOpen && !bookmarksSidebarOpen) {
                                onToggleChrome()
                            }
                        }
                    )
                }
        ) {
            itemsIndexed(
                items = document.blocks,
                key = { index, _ -> index },
                contentType = { _, block -> block::class }
            ) { index, block ->
                val prevBlock = if (index > 0) document.blocks[index - 1] else null
                val topGapEm = gapBeforeEm(prevBlock, block, settings.paragraphSpacingEm)
                val topGapDp = (topGapEm * settings.fontSizeSp).dp

                val activeRawStart = if (searchOpen && activeMatch != null && activeMatch.blockIndex == index) {
                    activeMatch.charStart
                } else null

                val blockBookmark = bookmarkedBlockMap[index]
                val isTtsActiveBlock = ttsState.activeBlockIndex == index

                val blockHighlightModifier = if (isTtsActiveBlock) {
                    Modifier
                        .padding(top = topGapDp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.link.copy(alpha = 0.10f))
                        .border(1.5.dp, palette.link.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("tts_active_block_$index")
                } else if (ttsState.isActive) {
                    Modifier
                        .padding(top = topGapDp)
                        .clickable { ttsController.jumpToBlock(index, autoPlay = true) }
                } else {
                    Modifier.padding(top = topGapDp)
                }

                Column(modifier = blockHighlightModifier) {
                    if (isTtsActiveBlock) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.link.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("tts_active_indicator_$index")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Reading aloud",
                                tint = palette.link,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (ttsState.isPlaying) "Reading aloud" else "Paused here",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.link,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (blockBookmark != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.link.copy(alpha = 0.14f))
                                .clickable { onToggleBookmarksSidebar() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .testTag("inline_bookmark_ribbon_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bookmark,
                                contentDescription = "Bookmarked",
                                tint = palette.link,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (blockBookmark.label.isNotBlank()) {
                                    "Page ${blockBookmark.pageNumber} · ${blockBookmark.label}"
                                } else {
                                    "Bookmarked · Page ${blockBookmark.pageNumber}"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.link,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    BlockView(
                        block = block,
                        settings = settings,
                        palette = palette,
                        searchQuery = activeQuery,
                        activeMatchRawStart = activeRawStart,
                        onInspectEquation = { latex -> inspectedEquationLatex = latex },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Top 2dp reading progress bar
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .align(Alignment.TopCenter),
            color = palette.link,
            trackColor = palette.rule
        )

        // Top chrome: standard title/action bar OR search input bar
        AnimatedVisibility(
            visible = chromeVisible || searchOpen,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            if (searchOpen) {
                // Interactive Search Header Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.background.copy(alpha = 0.98f))
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                        .border(1.dp, palette.rule.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                ) {
                    IconButton(
                        onClick = {
                            onCloseSearch()
                            searchResultsSheetOpen = false
                        },
                        modifier = Modifier.testTag("search_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close search",
                            tint = palette.text
                        )
                    }

                    // Search input container
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.codeBackground)
                            .padding(horizontal = 10.dp)
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                currentMatchIndex = 0
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = palette.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(palette.link),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { goToNextMatch() }
                            ),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Find in document…",
                                        color = palette.muted,
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester)
                                .testTag("reader_search_input")
                        )

                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    currentMatchIndex = 0
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("search_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Clear search",
                                    tint = palette.muted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Match counter badge
                    Text(
                        text = if (searchQuery.isBlank()) {
                            ""
                        } else if (searchMatches.isEmpty()) {
                            "0/0"
                        } else {
                            "${currentMatchIndex + 1}/${searchMatches.size}"
                        },
                        color = if (searchMatches.isEmpty() && searchQuery.isNotBlank()) palette.muted else palette.link,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .testTag("search_match_count")
                    )

                    // Previous match button
                    IconButton(
                        onClick = ::goToPrevMatch,
                        enabled = searchMatches.isNotEmpty(),
                        modifier = Modifier.testTag("search_prev_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Previous match",
                            tint = if (searchMatches.isNotEmpty()) palette.text else palette.muted.copy(alpha = 0.35f)
                        )
                    }

                    // Next match button
                    IconButton(
                        onClick = ::goToNextMatch,
                        enabled = searchMatches.isNotEmpty(),
                        modifier = Modifier.testTag("search_next_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Next match",
                            tint = if (searchMatches.isNotEmpty()) palette.text else palette.muted.copy(alpha = 0.35f)
                        )
                    }

                    // Results drawer trigger
                    IconButton(
                        onClick = { searchResultsSheetOpen = !searchResultsSheetOpen },
                        enabled = searchMatches.isNotEmpty(),
                        modifier = Modifier.testTag("search_results_list_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "All matches list",
                            tint = if (searchResultsSheetOpen) {
                                palette.link
                            } else if (searchMatches.isNotEmpty()) {
                                palette.text
                            } else {
                                palette.muted.copy(alpha = 0.35f)
                            }
                        )
                    }
                }
            } else {
                // Standard Title & Action Chrome Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.background.copy(alpha = 0.94f))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("reader_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.text
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onToggleToc)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = document.title.ifBlank { "Verso" },
                            color = palette.text,
                            fontSize = 16.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = if (activeTocEntry != null) {
                            "Page $currentPageNumber/$totalPages · ${activeTocEntry.title}"
                        } else {
                            "Page $currentPageNumber of $totalPages"
                        }
                        Text(
                            text = subtitle,
                            color = palette.muted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Quick Bookmark Toggle for Current Page
                    IconButton(
                        onClick = { onToggleBookmark(currentBlockIndex, currentOffsetPx) },
                        modifier = Modifier.testTag("reader_bookmark_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isCurrentBlockBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (isCurrentBlockBookmarked) "Remove bookmark from current page" else "Bookmark current page",
                            tint = if (isCurrentBlockBookmarked) palette.link else palette.text
                        )
                    }

                    // Open Bookmarks Sidebar Drawer
                    Box {
                        IconButton(
                            onClick = onToggleBookmarksSidebar,
                            modifier = Modifier.testTag("reader_bookmarks_sidebar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bookmarks,
                                contentDescription = "Bookmarks sidebar",
                                tint = if (bookmarksSidebarOpen) palette.link else palette.text
                            )
                        }
                        if (bookmarks.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                                    .clip(CircleShape)
                                    .background(palette.link)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "${bookmarks.size}",
                                    color = palette.background,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Read Aloud (Text-to-Speech) Toggle Button
                    IconButton(
                        onClick = {
                            ttsController.toggleBar(currentBlockIndex)
                        },
                        modifier = Modifier.testTag("reader_tts_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (ttsState.isActive) "Stop Read Aloud" else "Read Aloud",
                            tint = if (ttsState.isActive) palette.link else palette.text
                        )
                    }

                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier.testTag("reader_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search document",
                            tint = if (searchOpen) palette.link else palette.text
                        )
                    }

                    IconButton(
                        onClick = onToggleToc,
                        modifier = Modifier.testTag("reader_toc_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = "Table of contents",
                            tint = if (tocOpen) palette.link else palette.text
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("reader_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Reading settings",
                            tint = if (settingsOpen) palette.link else palette.text
                        )
                    }
                }
            }
        }

        // Bottom quick chapter & TOC bar when chrome is visible and no sheet/sidebar/search/TTS is open
        AnimatedVisibility(
            visible = chromeVisible && !ttsState.isActive && !searchOpen && !tocOpen && !settingsOpen && !bookmarksSidebarOpen && tocEntries.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.background.copy(alpha = 0.96f))
                    .border(1.dp, palette.rule.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .testTag("reader_bottom_toc_bar")
            ) {
                IconButton(
                    onClick = { prevTocEntry?.let { jumpToBlock(it.blockIndex) } },
                    enabled = prevTocEntry != null,
                    modifier = Modifier.testTag("reader_prev_section_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous section",
                        tint = if (prevTocEntry != null) palette.text else palette.muted.copy(alpha = 0.35f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onToggleToc)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("reader_bottom_toc_trigger")
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeTocEntry?.title ?: "Table of Contents",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Page $currentPageNumber/$totalPages · Section ${(activeTocIndex + 1).coerceAtLeast(1)} of ${tocEntries.size}",
                            fontSize = 11.sp,
                            color = palette.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${(progressFraction * 100).toInt()}%",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.link
                    )
                }

                IconButton(
                    onClick = { nextTocEntry?.let { jumpToBlock(it.blockIndex) } },
                    enabled = nextTocEntry != null,
                    modifier = Modifier.testTag("reader_next_section_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next section",
                        tint = if (nextTocEntry != null) palette.text else palette.muted.copy(alpha = 0.35f)
                    )
                }
            }
        }

        // Floating Bottom Match Controller when search is active (for thumb navigation while reading)
        AnimatedVisibility(
            visible = searchOpen && !searchResultsSheetOpen && searchMatches.isNotEmpty(),
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.background.copy(alpha = 0.96f))
                    .border(1.dp, palette.rule.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("search_bottom_floating_controller")
            ) {
                IconButton(
                    onClick = ::goToPrevMatch,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("search_floating_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Previous match",
                        tint = palette.text,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "${currentMatchIndex + 1} of ${searchMatches.size}",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.link,
                    modifier = Modifier
                        .clickable { searchResultsSheetOpen = true }
                        .padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = ::goToNextMatch,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("search_floating_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Next match",
                        tint = palette.text,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(palette.rule)
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { searchResultsSheetOpen = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("search_floating_results_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Show all results",
                        tint = palette.text,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Table of Contents bottom panel
        AnimatedVisibility(
            visible = tocOpen && chromeVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TocPanel(
                entries = tocEntries,
                currentBlockIndex = currentBlockIndex,
                palette = palette,
                onSelectBlock = { targetBlock ->
                    jumpToBlock(targetBlock)
                },
                onClose = onCloseToc
            )
        }

        // Full-Text Search Results bottom panel
        AnimatedVisibility(
            visible = searchOpen && searchResultsSheetOpen,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SearchResultsPanel(
                searchResult = searchResult,
                palette = palette,
                onSelectMatch = { match ->
                    jumpToMatch(match.matchId)
                    searchResultsSheetOpen = false
                },
                onClose = { searchResultsSheetOpen = false }
            )
        }

        // Floating Read Aloud (Text-to-Speech) Controller Bar
        AnimatedVisibility(
            visible = ttsState.isActive && !searchOpen && !tocOpen && !settingsOpen && !bookmarksSidebarOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            TtsPlayerBar(
                ttsState = ttsState,
                totalDocumentBlocks = document.blocks.size,
                palette = palette,
                onPlayPause = { ttsController.togglePlayPause(currentBlockIndex) },
                onPrevious = { ttsController.previousBlock() },
                onNext = { ttsController.nextBlock() },
                onCycleSpeed = { ttsController.cycleSpeed() },
                onToggleAutoScroll = { ttsController.toggleAutoScroll() },
                onStop = { ttsController.stop() }
            )
        }

        // Slide-in Right Sidebar for Saved Bookmarks
        BookmarksSidebarOverlay(
            visible = bookmarksSidebarOpen,
            bookmarks = bookmarks,
            document = document,
            currentBlockIndex = currentBlockIndex,
            currentOffsetPx = currentOffsetPx,
            palette = palette,
            onToggleCurrentPageBookmark = {
                onToggleBookmark(currentBlockIndex, currentOffsetPx)
            },
            onSelectBookmark = { bookmark ->
                jumpToBlock(bookmark.blockIndex, bookmark.offsetPx)
                onCloseBookmarksSidebar()
            },
            onDeleteBookmark = onDeleteBookmark,
            onUpdateBookmarkLabel = onUpdateBookmarkLabel,
            onClose = onCloseBookmarksSidebar
        )

        val currentInspectedLatex = inspectedEquationLatex
        if (currentInspectedLatex != null) {
            LatexEquationInspectorDialog(
                initialLatex = currentInspectedLatex,
                settings = settings,
                palette = palette,
                onDismiss = { inspectedEquationLatex = null }
            )
        }
    }
}
