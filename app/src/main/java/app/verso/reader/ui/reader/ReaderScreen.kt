package app.verso.reader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.core.model.Document
import app.verso.core.store.ReaderSettings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

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
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialBlock.coerceIn(0, (document.blocks.size - 1).coerceAtLeast(0)),
        initialFirstVisibleItemScrollOffset = initialOffsetPx.coerceAtLeast(0)
    )

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
                bottom = 96.dp
            ),
            modifier = Modifier
                .fillMaxSize()
                .testTag("reader_list")
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            onToggleChrome()
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

                BlockView(
                    block = block,
                    settings = settings,
                    palette = palette,
                    modifier = Modifier.padding(top = topGapDp)
                )
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

        // Top chrome bar with back button, title, and reading settings button
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.background.copy(alpha = 0.94f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
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

                Text(
                    text = document.title.ifBlank { "Verso" },
                    color = palette.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("reader_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Reading settings",
                        tint = palette.text
                    )
                }
            }
        }
    }
}
