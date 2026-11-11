package app.verso.core.markdown

import app.verso.core.html.HtmlExtractor
import app.verso.core.model.Block
import app.verso.core.model.CodeBlock
import app.verso.core.model.Divider
import app.verso.core.model.Document
import app.verso.core.model.Heading
import app.verso.core.model.ImageBlock
import app.verso.core.model.InlineText
import app.verso.core.model.ListItem
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.model.Quote
import app.verso.core.model.Span
import app.verso.core.model.SpanKind
import app.verso.core.model.TableBlock
import app.verso.core.text.TextCleaner
import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.CustomBlock
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser
import org.jsoup.Jsoup
import org.commonmark.ext.gfm.tables.TableBlock as GfmTableBlock
import org.commonmark.node.Heading as MdHeading
import org.commonmark.node.ListItem as MdListItem
import org.commonmark.node.Paragraph as MdParagraph

object MarkdownExtractor {
    private val parser = Parser.builder()
        .extensions(listOf(TablesExtension.create(), StrikethroughExtension.create()))
        .build()

    fun extract(markdown: String): Document {
        if (markdown.isBlank()) return Document("", emptyList())
        val cleaned = TextCleaner.clean(markdown)
        val protected = MathProtector.protect(cleaned)
        val rootNode = parser.parse(protected.text)

        val blocks = mutableListOf<Block>()
        walkBlocks(rootNode.firstChild, blocks, quoteDepth = 0, mathList = protected.math)
        return Document("", blocks)
    }

    private fun walkBlocks(
        firstNode: Node?,
        out: MutableList<Block>,
        quoteDepth: Int,
        mathList: List<MathProtector.MathSpan>
    ) {
        var current = firstNode
        while (current != null) {
            when (current) {
                is MdHeading -> {
                    val inline = buildInline(current.firstChild, mathList)
                    if (inline.text.isNotBlank()) {
                        out.add(Heading(current.level, inline))
                    }
                }
                is MdParagraph -> {
                    // Check if it's solely an image
                    val singleChild = current.firstChild
                    if (singleChild is Image && singleChild.next == null) {
                        val alt = singleChild.title ?: extractText(singleChild.firstChild)
                        out.add(ImageBlock(alt))
                    } else {
                        // Check if it's solely a display math placeholder
                        val rawText = extractRawText(current.firstChild)
                        val mathBlock = checkDisplayMath(rawText, mathList)
                        if (mathBlock != null) {
                            out.add(mathBlock)
                        } else {
                            val inline = buildInline(current.firstChild, mathList)
                            if (inline.text.isNotBlank()) {
                                if (quoteDepth > 0) {
                                    out.add(Quote(quoteDepth - 1, inline))
                                } else {
                                    out.add(Paragraph(inline))
                                }
                            }
                        }
                    }
                }
                is FencedCodeBlock -> {
                    val lang = current.info?.trim()?.takeIf { it.isNotEmpty() }
                    val code = current.literal?.removeSuffix("\n") ?: ""
                    out.add(CodeBlock(lang, code))
                }
                is IndentedCodeBlock -> {
                    val code = current.literal?.removeSuffix("\n") ?: ""
                    out.add(CodeBlock(null, code))
                }
                is ThematicBreak -> {
                    out.add(Divider)
                }
                is BlockQuote -> {
                    walkBlocks(current.firstChild, out, quoteDepth = quoteDepth + 1, mathList = mathList)
                }
                is BulletList -> {
                    handleList(current, isOrdered = false, startNumber = 1, out = out, depth = 0, mathList = mathList)
                }
                is OrderedList -> {
                    handleList(current, isOrdered = true, startNumber = current.startNumber, out = out, depth = 0, mathList = mathList)
                }
                is GfmTableBlock -> {
                    val table = parseGfmTable(current, mathList)
                    if (table != null) out.add(table)
                }
                is HtmlBlock -> {
                    val htmlLiteral = current.literal ?: ""
                    val soup = Jsoup.parseBodyFragment(htmlLiteral)
                    val htmlBlocks = HtmlExtractor.blocksOf(soup.body())
                    out.addAll(htmlBlocks)
                }
                is Image -> {
                    val alt = current.title ?: extractText(current.firstChild)
                    out.add(ImageBlock(alt))
                }
                else -> {
                    // Traverse children if any
                    if (current.firstChild != null) {
                        walkBlocks(current.firstChild, out, quoteDepth, mathList)
                    }
                }
            }
            current = current.next
        }
    }

    private fun handleList(
        listBlock: ListBlock,
        isOrdered: Boolean,
        startNumber: Int,
        out: MutableList<Block>,
        depth: Int,
        mathList: List<MathProtector.MathSpan>
    ) {
        var num = startNumber
        var itemNode = listBlock.firstChild
        while (itemNode != null) {
            if (itemNode is MdListItem) {
                val marker = if (isOrdered) "${num++}." else "•"
                var childNode = itemNode.firstChild
                var isFirstBlockInItem = true

                while (childNode != null) {
                    when (childNode) {
                        is MdParagraph -> {
                            val inline = buildInline(childNode.firstChild, mathList)
                            if (inline.text.isNotBlank()) {
                                val m = if (isFirstBlockInItem) marker else ""
                                out.add(ListItem(depth, m, inline))
                                isFirstBlockInItem = false
                            }
                        }
                        is BulletList -> {
                            handleList(childNode, isOrdered = false, startNumber = 1, out = out, depth = depth + 1, mathList = mathList)
                        }
                        is OrderedList -> {
                            handleList(childNode, isOrdered = true, startNumber = childNode.startNumber, out = out, depth = depth + 1, mathList = mathList)
                        }
                        else -> {
                            // Any other sub-block
                            walkBlocks(childNode, out, quoteDepth = 0, mathList = mathList)
                        }
                    }
                    childNode = childNode.next
                }
            }
            itemNode = itemNode.next
        }
    }

    private fun parseGfmTable(tableNode: GfmTableBlock, mathList: List<MathProtector.MathSpan>): TableBlock? {
        var header = emptyList<InlineText>()
        val rows = mutableListOf<List<InlineText>>()

        var child = tableNode.firstChild
        while (child != null) {
            when (child) {
                is TableHead -> {
                    var tr = child.firstChild
                    if (tr is TableRow) {
                        header = parseTableRow(tr, mathList)
                    }
                }
                is TableBody -> {
                    var tr = child.firstChild
                    while (tr != null) {
                        if (tr is TableRow) {
                            rows.add(parseTableRow(tr, mathList))
                        }
                        tr = tr.next
                    }
                }
                is TableRow -> {
                    if (header.isEmpty()) {
                        header = parseTableRow(child, mathList)
                    } else {
                        rows.add(parseTableRow(child, mathList))
                    }
                }
            }
            child = child.next
        }

        if (header.isEmpty() && rows.isEmpty()) return null
        return TableBlock(header, rows)
    }

    private fun parseTableRow(row: TableRow, mathList: List<MathProtector.MathSpan>): List<InlineText> {
        val cells = mutableListOf<InlineText>()
        var cell = row.firstChild
        while (cell != null) {
            if (cell is TableCell) {
                cells.add(buildInline(cell.firstChild, mathList))
            }
            cell = cell.next
        }
        return cells
    }

    private fun checkDisplayMath(rawText: String, mathList: List<MathProtector.MathSpan>): MathBlock? {
        val trimmed = rawText.trim()
        if (trimmed.startsWith(MathProtector.OPEN) && trimmed.endsWith(MathProtector.CLOSE)) {
            val numStr = trimmed.substring(1, trimmed.length - 1)
            val idx = numStr.toIntOrNull()
            if (idx != null && idx in mathList.indices) {
                val span = mathList[idx]
                if (span.display) {
                    return MathBlock(span.latex)
                }
            }
        }
        return null
    }

    private fun extractRawText(firstNode: Node?): String {
        val sb = StringBuilder()
        var cur = firstNode
        while (cur != null) {
            when (cur) {
                is Text -> sb.append(cur.literal)
                is Code -> sb.append(cur.literal)
                else -> sb.append(extractRawText(cur.firstChild))
            }
            cur = cur.next
        }
        return sb.toString()
    }

    private fun extractText(firstNode: Node?): String {
        val sb = StringBuilder()
        var cur = firstNode
        while (cur != null) {
            when (cur) {
                is Text -> sb.append(cur.literal)
                is Code -> sb.append(cur.literal)
                else -> sb.append(extractText(cur.firstChild))
            }
            cur = cur.next
        }
        return sb.toString()
    }

    private fun buildInline(firstNode: Node?, mathList: List<MathProtector.MathSpan>): InlineText {
        val sb = StringBuilder()
        val spans = mutableListOf<Span>()
        collectInline(firstNode, sb, spans)

        // Expand math placeholders in sb and spans
        return expandMathPlaceholders(sb.toString(), spans, mathList)
    }

    private fun collectInline(
        firstNode: Node?,
        sb: StringBuilder,
        spans: MutableList<Span>
    ) {
        var cur = firstNode
        while (cur != null) {
            when (cur) {
                is Text -> {
                    sb.append(cur.literal)
                }
                is Code -> {
                    val start = sb.length
                    sb.append(cur.literal)
                    val end = sb.length
                    if (end > start) spans.add(Span(start, end, SpanKind.Code))
                }
                is Emphasis -> {
                    val start = sb.length
                    collectInline(cur.firstChild, sb, spans)
                    val end = sb.length
                    if (end > start) spans.add(Span(start, end, SpanKind.Italic))
                }
                is StrongEmphasis -> {
                    val start = sb.length
                    collectInline(cur.firstChild, sb, spans)
                    val end = sb.length
                    if (end > start) spans.add(Span(start, end, SpanKind.Bold))
                }
                is Strikethrough -> {
                    val start = sb.length
                    collectInline(cur.firstChild, sb, spans)
                    val end = sb.length
                    if (end > start) spans.add(Span(start, end, SpanKind.Strike))
                }
                is Link -> {
                    val start = sb.length
                    collectInline(cur.firstChild, sb, spans)
                    val end = sb.length
                    if (end > start) spans.add(Span(start, end, SpanKind.Link, cur.destination))
                }
                is Image -> {
                    val alt = cur.title ?: extractText(cur.firstChild)
                    sb.append("[$alt]")
                }
                is SoftLineBreak -> {
                    sb.append(" ")
                }
                is HardLineBreak -> {
                    sb.append("\n")
                }
                is HtmlInline -> {
                    // Tag stripped, text ignored
                }
                else -> {
                    collectInline(cur.firstChild, sb, spans)
                }
            }
            cur = cur.next
        }
    }

    private fun expandMathPlaceholders(
        text: String,
        existingSpans: List<Span>,
        mathList: List<MathProtector.MathSpan>
    ): InlineText {
        if (!text.contains(MathProtector.OPEN)) {
            return InlineText(text, existingSpans)
        }

        val sb = StringBuilder()
        val indexMap = IntArray(text.length + 1)
        val mathSpans = mutableListOf<Span>()
        var i = 0

        while (i < text.length) {
            indexMap[i] = sb.length
            if (text[i] == MathProtector.OPEN) {
                val closePos = text.indexOf(MathProtector.CLOSE, i + 1)
                if (closePos != -1) {
                    val numStr = text.substring(i + 1, closePos)
                    val idx = numStr.toIntOrNull()
                    if (idx != null && idx in mathList.indices) {
                        val mathContent = mathList[idx].latex
                        val start = sb.length
                        sb.append(mathContent)
                        val end = sb.length
                        mathSpans.add(Span(start, end, SpanKind.Math))
                        for (k in i until closePos + 1) {
                            indexMap[k] = start
                        }
                        i = closePos + 1
                        continue
                    }
                }
            }
            sb.append(text[i])
            i++
        }
        indexMap[text.length] = sb.length

        val newText = sb.toString()
        val finalSpans = mutableListOf<Span>()
        for (span in existingSpans) {
            val s = indexMap[span.start.coerceIn(0, text.length)]
            val e = indexMap[span.end.coerceIn(0, text.length)]
            if (e > s) {
                finalSpans.add(Span(s, e, span.kind, span.href))
            }
        }
        finalSpans.addAll(mathSpans)

        return InlineText(newText, finalSpans)
    }
}
