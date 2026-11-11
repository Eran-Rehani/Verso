package app.verso.reader.ui.reader

import app.verso.core.model.Block
import app.verso.core.model.Heading
import app.verso.core.model.ListItem

fun gapBeforeEm(prev: Block?, cur: Block, paragraphSpacingEm: Float): Float {
    if (prev == null) return 0f
    if (prev is ListItem && cur is ListItem) return 0.25f
    if (prev is Heading) return 0.4f
    if (cur is Heading) return paragraphSpacingEm + 0.6f
    return paragraphSpacingEm
}
