package app.verso.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ReaderChrome(
    visible: Boolean, title: String, subtitle: String, bookmarked: Boolean,
    readingAloud: Boolean, palette: ReaderPalette,
    onBack: () -> Unit, onBookmark: () -> Unit, onSearch: () -> Unit,
    onToc: () -> Unit, onBookmarks: () -> Unit, onReadAloud: () -> Unit,
    onSettings: () -> Unit, onToggleChrome: () -> Unit, modifier: Modifier = Modifier
) {
    if (!visible) {
        IconButton(onClick = onToggleChrome, modifier = modifier.statusBarsPadding().size(48.dp).background(palette.background).testTag("reader_show_controls")) {
            Icon(Icons.Filled.Menu, "Show reading controls", tint = palette.text)
        }
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()
        .background(palette.background.copy(alpha = 0.94f)).statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = palette.text)
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(title, color = palette.text, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = palette.muted, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onBookmark, modifier = Modifier.testTag("reader_bookmark_toggle_button")) {
            Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                if (bookmarked) "Remove bookmark from current page" else "Bookmark current page",
                tint = if (bookmarked) palette.link else palette.text)
        }
        IconButton(onClick = onSearch, modifier = Modifier.testTag("reader_search_button")) {
            Icon(Icons.Filled.Search, "Search document", tint = palette.text)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More", tint = palette.text) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                listOf("Table of contents" to onToc, "Bookmarks" to onBookmarks,
                    (if (readingAloud) "Stop read aloud" else "Read aloud") to onReadAloud,
                    "Reading settings" to onSettings, "Hide reading controls" to onToggleChrome).forEach { (label, action) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menuOpen = false; action() })
                }
            }
        }
    }
}
