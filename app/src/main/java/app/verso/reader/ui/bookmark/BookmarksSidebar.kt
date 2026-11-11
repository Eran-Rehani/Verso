package app.verso.reader.ui.bookmark

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.core.bookmark.BookmarkHelper
import app.verso.core.model.Document
import app.verso.reader.data.db.BookmarkEntity
import app.verso.reader.ui.reader.ReaderPalette

enum class BookmarkSortOrder {
    BY_PAGE,
    RECENT
}

@Composable
fun BookmarksSidebarOverlay(
    visible: Boolean,
    bookmarks: List<BookmarkEntity>,
    document: Document,
    currentBlockIndex: Int,
    currentOffsetPx: Int,
    palette: ReaderPalette,
    onToggleCurrentPageBookmark: () -> Unit,
    onSelectBookmark: (BookmarkEntity) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onUpdateBookmarkLabel: (BookmarkEntity, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var draftLabel by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        // Dimmed backdrop scrim
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClose
                    )
                    .testTag("bookmarks_sidebar_scrim")
            )
        }

        // Slide-in Right Sidebar Drawer
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            BookmarksSidebarContent(
                bookmarks = bookmarks,
                document = document,
                currentBlockIndex = currentBlockIndex,
                currentOffsetPx = currentOffsetPx,
                palette = palette,
                onToggleCurrentPageBookmark = onToggleCurrentPageBookmark,
                onSelectBookmark = onSelectBookmark,
                onEditBookmark = { b ->
                    editingBookmark = b
                    draftLabel = b.label
                },
                onDeleteBookmark = onDeleteBookmark,
                onClose = onClose
            )
        }
    }

    val targetEdit = editingBookmark
    if (targetEdit != null) {
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            containerColor = palette.background,
            titleContentColor = palette.text,
            textContentColor = palette.text,
            title = {
                Text(
                    text = "Bookmark Note (Page ${targetEdit.pageNumber})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = targetEdit.snippet,
                        fontSize = 13.sp,
                        color = palette.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draftLabel,
                        onValueChange = { draftLabel = it },
                        placeholder = { Text("Add a note or label…", color = palette.muted) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bookmark_label_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateBookmarkLabel(targetEdit, draftLabel)
                        editingBookmark = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.link,
                        contentColor = palette.background
                    ),
                    modifier = Modifier.testTag("bookmark_label_save_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) {
                    Text("Cancel", color = palette.muted)
                }
            }
        )
    }
}

@Composable
private fun BookmarksSidebarContent(
    bookmarks: List<BookmarkEntity>,
    document: Document,
    currentBlockIndex: Int,
    currentOffsetPx: Int,
    palette: ReaderPalette,
    onToggleCurrentPageBookmark: () -> Unit,
    onSelectBookmark: (BookmarkEntity) -> Unit,
    onEditBookmark: (BookmarkEntity) -> Unit,
    onDeleteBookmark: (BookmarkEntity) -> Unit,
    onClose: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val sidebarWidth = (configuration.screenWidthDp * 0.84f).dp.coerceIn(280.dp, 360.dp)

    var sortOrder by rememberSaveable { mutableStateOf(BookmarkSortOrder.BY_PAGE) }

    val sortedBookmarks = remember(bookmarks, sortOrder) {
        when (sortOrder) {
            BookmarkSortOrder.BY_PAGE -> bookmarks.sortedBy { it.blockIndex }
            BookmarkSortOrder.RECENT -> bookmarks.sortedByDescending { it.createdAt }
        }
    }

    val currentDescriptor = remember(document, currentBlockIndex, currentOffsetPx) {
        BookmarkHelper.describe(document, currentBlockIndex, currentOffsetPx)
    }

    val isCurrentBlockBookmarked = remember(bookmarks, currentBlockIndex) {
        bookmarks.any { it.blockIndex == currentBlockIndex }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .testTag("bookmarks_sidebar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Sidebar Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = palette.link,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Bookmarks",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.text
                        )
                        val countText = if (bookmarks.size == 1) "1 saved page" else "${bookmarks.size} saved pages"
                        Text(
                            text = countText,
                            fontSize = 12.sp,
                            color = palette.muted
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("bookmarks_sidebar_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close bookmarks sidebar",
                        tint = palette.muted
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = palette.rule.copy(alpha = 0.6f))

            // Quick Action Card for Current Page
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.codeBackground)
                    .border(1.dp, palette.rule.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "CURRENT READING POSITION",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.muted,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        text = "Page ${currentDescriptor.pageNumber} of ${currentDescriptor.totalPages}",
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.link
                    )
                }

                if (!currentDescriptor.sectionTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentDescriptor.sectionTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onToggleCurrentPageBookmark,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrentBlockBookmarked) palette.rule.copy(alpha = 0.75f) else palette.link,
                        contentColor = if (isCurrentBlockBookmarked) palette.text else palette.background
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bookmark_current_page_button")
                ) {
                    Icon(
                        imageVector = if (isCurrentBlockBookmarked) Icons.Filled.BookmarkAdded else Icons.Filled.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCurrentBlockBookmarked) {
                            "Page ${currentDescriptor.pageNumber} Bookmarked · Remove"
                        } else {
                            "Bookmark Page ${currentDescriptor.pageNumber}"
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Sort controls when multiple bookmarks exist
            if (bookmarks.size > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Sort:",
                        fontSize = 12.sp,
                        color = palette.muted
                    )
                    SortChip(
                        label = "By Page",
                        selected = sortOrder == BookmarkSortOrder.BY_PAGE,
                        palette = palette,
                        onClick = { sortOrder = BookmarkSortOrder.BY_PAGE },
                        modifier = Modifier.testTag("bookmarks_sort_by_page")
                    )
                    SortChip(
                        label = "Recent",
                        selected = sortOrder == BookmarkSortOrder.RECENT,
                        palette = palette,
                        onClick = { sortOrder = BookmarkSortOrder.RECENT },
                        modifier = Modifier.testTag("bookmarks_sort_recent")
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            HorizontalDivider(thickness = 1.dp, color = palette.rule.copy(alpha = 0.4f))

            if (sortedBookmarks.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 28.dp)
                        .testTag("bookmarks_empty_state")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BookmarkBorder,
                            contentDescription = null,
                            tint = palette.muted.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No bookmarks saved yet",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.text,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Save key pages, equations, or sections while reading to jump back to them anytime.",
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            color = palette.muted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("bookmarks_sidebar_list")
                ) {
                    items(
                        items = sortedBookmarks,
                        key = { it.id }
                    ) { bookmark ->
                        val isCurrentPage = bookmark.blockIndex == currentBlockIndex ||
                            BookmarkHelper.pageForBlock(bookmark.blockIndex) == currentDescriptor.pageNumber
                        BookmarkSidebarItemCard(
                            bookmark = bookmark,
                            isCurrentPage = isCurrentPage,
                            palette = palette,
                            onClick = { onSelectBookmark(bookmark) },
                            onEdit = { onEditBookmark(bookmark) },
                            onDelete = { onDeleteBookmark(bookmark) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    palette: ReaderPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) palette.link else palette.codeBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) palette.background else palette.text
        )
    }
}

@Composable
private fun BookmarkSidebarItemCard(
    bookmark: BookmarkEntity,
    isCurrentPage: Boolean,
    palette: ReaderPalette,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val percent = (bookmark.progressFraction * 100).toInt()

    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentPage) palette.codeBackground else palette.codeBackground.copy(alpha = 0.45f))
            .border(
                width = 1.dp,
                color = if (isCurrentPage) palette.link.copy(alpha = 0.65f) else palette.rule.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp)
            .testTag("bookmark_item_${bookmark.blockIndex}")
    ) {
        if (isCurrentPage) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.link)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            // Top badge row: Page number + Progress %
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCurrentPage) palette.link else palette.rule.copy(alpha = 0.7f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Page ${bookmark.pageNumber}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentPage) palette.background else palette.text
                    )
                }

                Text(
                    text = "$percent%",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.muted
                )
            }

            // Custom label/note if present
            if (bookmark.label.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = bookmark.label,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.link,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Section / Chapter Title
            if (bookmark.sectionTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookmark.sectionTitle,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Snippet preview
            if (bookmark.snippet.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookmark.snippet,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontStyle = FontStyle.Normal,
                    color = palette.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Edit and Delete buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("bookmark_edit_${bookmark.blockIndex}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit bookmark note",
                    tint = palette.muted,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("bookmark_delete_${bookmark.blockIndex}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Remove bookmark",
                    tint = palette.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
