package app.verso.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SpanKind {
    Bold,
    Italic,
    Code,
    Strike,
    Link,
    Math
}

@Serializable
data class Span(
    val start: Int,
    val end: Int,
    val kind: SpanKind,
    val href: String? = null
)

@Serializable
data class InlineText(
    val text: String,
    val spans: List<Span> = emptyList()
) {
    companion object {
        fun plain(text: String) = InlineText(text)
    }
}

@Serializable
sealed interface Block

@Serializable
@SerialName("heading")
data class Heading(val level: Int, val text: InlineText) : Block

@Serializable
@SerialName("paragraph")
data class Paragraph(val text: InlineText) : Block

@Serializable
@SerialName("list_item")
data class ListItem(val depth: Int, val marker: String, val text: InlineText) : Block

@Serializable
@SerialName("quote")
data class Quote(val depth: Int, val text: InlineText) : Block

@Serializable
@SerialName("code")
data class CodeBlock(val language: String?, val code: String) : Block

@Serializable
@SerialName("math")
data class MathBlock(val latex: String) : Block

@Serializable
@SerialName("table")
data class TableBlock(
    val header: List<InlineText>,
    val rows: List<List<InlineText>>
) : Block

@Serializable
@SerialName("image")
data class ImageBlock(val alt: String) : Block

@Serializable
@SerialName("divider")
data object Divider : Block

@Serializable
data class Document(val title: String, val blocks: List<Block>)

fun Block.plainText(): String = when (this) {
    is Heading -> text.text
    is Paragraph -> text.text
    is ListItem -> text.text
    is Quote -> text.text
    is CodeBlock -> code
    is MathBlock -> latex
    is TableBlock -> {
        val parts = mutableListOf<String>()
        val headerText = header.joinToString(" ") { it.text }
        if (headerText.isNotBlank()) parts.add(headerText)
        for (row in rows) {
            val rowText = row.joinToString(" ") { it.text }
            if (rowText.isNotBlank()) parts.add(rowText)
        }
        parts.joinToString(" ")
    }
    is ImageBlock -> alt
    is Divider -> ""
}
