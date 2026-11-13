package app.verso.reader.ui.tts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.core.bookmark.BookmarkHelper
import app.verso.reader.tts.TtsState
import app.verso.reader.ui.reader.ReaderPalette
import java.util.Locale

@Composable
fun TtsPlayerBar(
    ttsState: TtsState,
    totalDocumentBlocks: Int,
    palette: ReaderPalette,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleAutoScroll: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentItem = ttsState.currentItem
    val totalQueue = ttsState.queue.size.coerceAtLeast(1)
    val queuePosition = (ttsState.currentQueueIndex + 1).coerceIn(1, totalQueue)
    val pageNum = BookmarkHelper.pageForBlock(currentItem?.blockIndex ?: 0)
    val totalPages = BookmarkHelper.totalPages(totalDocumentBlocks)
    val queueProgress = if (ttsState.queue.size <= 1) {
        1f
    } else {
        (ttsState.currentQueueIndex.toFloat() / (ttsState.queue.size - 1)).coerceIn(0f, 1f)
    }

    val speedLabel = if (ttsState.speechRate % 1f == 0f) {
        String.format(Locale.US, "%.1fx", ttsState.speechRate)
    } else {
        String.format(Locale.US, "%.2fx", ttsState.speechRate).trimEnd('0').trimEnd('.') + "x"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.background.copy(alpha = 0.98f))
            .border(1.dp, palette.link.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("tts_player_bar")
    ) {
        // Header row: Read Aloud badge, block/page counter, Auto-scroll chip, Speed chip, Close button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(palette.link.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = if (ttsState.isPlaying) Icons.Filled.GraphicEq else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = palette.link,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (ttsState.isPlaying) "Reading Aloud" else "Read Aloud Paused",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Block $queuePosition/$totalQueue · Page $pageNum/$totalPages",
                    fontSize = 11.sp,
                    color = palette.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("tts_progress_label")
                )
            }

            // Auto-scroll toggle pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (ttsState.autoScroll) palette.link.copy(alpha = 0.16f)
                        else palette.codeBackground
                    )
                    .clickable(onClick = onToggleAutoScroll)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("tts_autoscroll_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SyncAlt,
                    contentDescription = "Toggle auto-scroll",
                    tint = if (ttsState.autoScroll) palette.link else palette.muted,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (ttsState.autoScroll) "Follow" else "Manual",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ttsState.autoScroll) palette.link else palette.muted
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Speed cycle pill
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.codeBackground)
                    .border(1.dp, palette.rule.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onCycleSpeed)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("tts_speed_button")
            ) {
                Text(
                    text = speedLabel,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = palette.link
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onStop,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("tts_stop_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Stop Read Aloud",
                    tint = palette.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Spoken text live caption box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.codeBackground.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Text(
                text = currentItem?.spokenText ?: "No speakable text in document.",
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                color = palette.text.copy(alpha = 0.92f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("tts_spoken_caption")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { queueProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = palette.link,
            trackColor = palette.rule.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Transport controls row: Previous, Play/Pause, Next
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = ttsState.hasPrevious,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("tts_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous paragraph",
                    tint = if (ttsState.hasPrevious) palette.text else palette.muted.copy(alpha = 0.35f),
                    modifier = Modifier.size(26.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(palette.link)
                    .clickable(onClick = onPlayPause)
                    .padding(horizontal = 24.dp)
                    .testTag("tts_play_pause_button")
            ) {
                Icon(
                    imageVector = if (ttsState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (ttsState.isPlaying) "Pause Read Aloud" else "Play Read Aloud",
                    tint = palette.background,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (ttsState.isPlaying) "Pause" else "Listen",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.background
                )
            }

            IconButton(
                onClick = onNext,
                enabled = ttsState.hasNext,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("tts_next_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next paragraph",
                    tint = if (ttsState.hasNext) palette.text else palette.muted.copy(alpha = 0.35f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
