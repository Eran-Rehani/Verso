package app.verso.reader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.reader.data.db.BookEntity
import app.verso.reader.ui.reader.ReaderPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LibrarySortOption {
    LastOpened,
    Title,
    DateAdded
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    books: List<BookEntity>,
    palette: ReaderPalette,
    onOpenBook: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onImportFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFormat by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf(LibrarySortOption.LastOpened) }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Filter books based on search query (by title) and optional format
    val filteredBooks = remember(books, searchQuery, selectedFormat, sortOption) {
        val trimmedQuery = searchQuery.trim()
        var list = books.filter { book ->
            val matchesTitle = trimmedQuery.isBlank() ||
                    book.title.contains(trimmedQuery, ignoreCase = true)
            val matchesFormat = selectedFormat == null ||
                    book.format.equals(selectedFormat, ignoreCase = true)
            matchesTitle && matchesFormat
        }

        list = when (sortOption) {
            LibrarySortOption.LastOpened -> list.sortedByDescending { it.lastOpenedAt }
            LibrarySortOption.Title -> list.sortedBy { it.title.lowercase(Locale.ROOT) }
            LibrarySortOption.DateAdded -> list.sortedByDescending { it.addedAt }
        }
        list
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header: Title and Add Book button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column {
                    Text(
                        text = "Library",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    Text(
                        text = "${books.size} ${if (books.size == 1) "book" else "books"} in library",
                        fontSize = 13.sp,
                        color = palette.muted
                    )
                }

                Button(
                    onClick = onImportFile,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.link,
                        contentColor = palette.background
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("library_add_book_button")
                ) {
                    Text(
                        text = "+ Add book",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Prominent Search Bar at top of Library screen to filter books by title
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search books by title…",
                        color = palette.muted,
                        fontSize = 15.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (searchQuery.isNotEmpty()) palette.link else palette.muted
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.testTag("search_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = palette.muted
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.link,
                    unfocusedBorderColor = palette.rule,
                    focusedTextColor = palette.text,
                    unfocusedTextColor = palette.text,
                    cursorColor = palette.link,
                    focusedContainerColor = palette.codeBackground.copy(alpha = 0.6f),
                    unfocusedContainerColor = palette.codeBackground.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_search_bar")
            )

            // Search results banner when filtering
            if (searchQuery.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${filteredBooks.size} of ${books.size} books match \"${searchQuery.trim()}\"",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.link
                    )
                    Text(
                        text = "Clear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.muted,
                        modifier = Modifier
                            .clickable { searchQuery = "" }
                            .padding(horizontal = 4.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Filter Chips (Format)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val formats = listOf(
                    null to "All",
                    "Pdf" to "PDF",
                    "Epub" to "EPUB",
                    "Markdown" to "Markdown",
                    "Html" to "HTML",
                    "PlainText" to "Text"
                )
                for ((fmtKey, fmtLabel) in formats) {
                    val isSelected = selectedFormat == fmtKey
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFormat = fmtKey },
                        label = { Text(fmtLabel, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = palette.link,
                            selectedLabelColor = palette.background,
                            containerColor = palette.codeBackground,
                            labelColor = palette.text
                        ),
                        modifier = Modifier.testTag("format_filter_${fmtLabel.lowercase()}")
                    )
                }
            }

            // Sort Selector Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = "Sort:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.muted
                )
                val sortOptions = listOf(
                    LibrarySortOption.LastOpened to "Recent",
                    LibrarySortOption.Title to "Title A–Z",
                    LibrarySortOption.DateAdded to "Date added"
                )
                for ((opt, label) in sortOptions) {
                    val isSelected = sortOption == opt
                    SuggestionChip(
                        onClick = { sortOption = opt },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                color = if (isSelected) palette.link else palette.muted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Book List or Empty State
            if (filteredBooks.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Book,
                            contentDescription = null,
                            tint = palette.muted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No books found with title matching \"${searchQuery.trim()}\"."
                            } else if (selectedFormat != null) {
                                "No $selectedFormat books found in your library."
                            } else {
                                "Your library is empty. Import a PDF, EPUB, Markdown, or text file to start reading."
                            },
                            color = palette.muted,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (searchQuery.isNotBlank()) {
                            Button(
                                onClick = { searchQuery = "" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.link,
                                    contentColor = palette.background
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Clear search filter")
                            }
                        } else if (selectedFormat != null) {
                            Button(
                                onClick = { selectedFormat = null },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.link,
                                    contentColor = palette.background
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Show all formats")
                            }
                        } else {
                            Button(
                                onClick = onImportFile,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.link,
                                    contentColor = palette.background
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Import document")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library_books_list")
                ) {
                    items(
                        items = filteredBooks,
                        key = { it.id }
                    ) { book ->
                        BookCard(
                            book = book,
                            palette = palette,
                            onClick = { onOpenBook(book.id) },
                            onDelete = { onDeleteBook(book.id) }
                        )
                    }
                }
            }
        }

        // Floating Action Button allowing users to select and import a file from device storage
        ExtendedFloatingActionButton(
            onClick = onImportFile,
            icon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Import file from device storage"
                )
            },
            text = {
                Text(
                    text = "Import file",
                    fontWeight = FontWeight.SemiBold
                )
            },
            containerColor = palette.link,
            contentColor = palette.background,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
                .testTag("library_import_fab")
        )
    }
}

@Composable
fun BookCard(
    book: BookEntity,
    palette: ReaderPalette,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = (book.progress * 100).toInt()
    val formattedLastOpened = remember(book.lastOpenedAt) {
        formatRelativeTimestamp(book.lastOpenedAt)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = palette.codeBackground
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("book_card_${book.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Format badge & Section count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormatBadge(format = book.format, palette = palette)
                    if (book.blockCount > 0) {
                        Text(
                            text = "${book.blockCount} ${if (book.blockCount == 1) "section" else "sections"}",
                            fontSize = 12.sp,
                            color = palette.muted
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("delete_book_${book.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove from library",
                        tint = palette.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Book Title
            Text(
                text = book.title.ifBlank { "Untitled" },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Reading Progress Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = palette.link,
                    trackColor = palette.rule
                )
                Text(
                    text = "$percent%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.link
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata: Last opened date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Last read $formattedLastOpened",
                    fontSize = 12.sp,
                    color = palette.muted
                )
            }
        }
    }
}

@Composable
fun FormatBadge(
    format: String,
    palette: ReaderPalette,
    modifier: Modifier = Modifier
) {
    val cleanFormat = when (format.uppercase(Locale.ROOT)) {
        "PDF" -> "PDF"
        "EPUB" -> "EPUB"
        "DJVU", "DJVO" -> "DJVU"
        "MARKDOWN", "MD" -> "MD"
        "LATEX", "TEX", "LTX" -> "TEX"
        "HTML", "HTM", "FB2" -> "HTML"
        "PLAINTEXT", "TXT" -> "TXT"
        else -> format.uppercase(Locale.ROOT).take(4)
    }

    val badgeBg = when (cleanFormat) {
        "PDF" -> Color(0xFFD32F2F)
        "EPUB" -> Color(0xFF1976D2)
        "DJVU" -> Color(0xFF7B1FA2)
        "MD" -> Color(0xFF388E3C)
        "TEX" -> Color(0xFF00897B)
        "HTML" -> Color(0xFFE65100)
        else -> palette.rule
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeBg.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = cleanFormat,
            color = badgeBg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val TIME_FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
}

private val DATE_FORMATTER = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
}

private fun formatRelativeTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Never"
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp

    val oneMinute = 60 * 1000L
    val oneHour = 60 * oneMinute
    val oneDay = 24 * oneHour

    return when {
        diffMs < oneMinute -> "Just now"
        diffMs < oneHour -> "${(diffMs / oneMinute)}m ago"
        diffMs < oneDay -> {
            "Today at ${TIME_FORMATTER.get()!!.format(Date(timestamp))}"
        }
        diffMs < 2 * oneDay -> "Yesterday"
        diffMs < 7 * oneDay -> "${(diffMs / oneDay)}d ago"
        else -> {
            DATE_FORMATTER.get()!!.format(Date(timestamp))
        }
    }
}
