package app.verso.core.html

import app.verso.core.markdown.MathProtector
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
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object HtmlExtractor {
    private val IGNORED_TAGS = setOf(
        "script", "style", "nav", "head", "noscript", "svg", "template", "iframe",
        "binary", "stylesheet", "document-info", "publish-info"
    )
    private val WHITESPACE_SPLIT_REGEX = Regex("\\s+")

    fun extract(html: String): Document {
        if (html.isBlank()) return Document("", emptyList())
        val cleaned = TextCleaner.clean(html)
        val isFb2 = cleaned.contains("<FictionBook", ignoreCase = true)
        val soup = if (isFb2) {
            Jsoup.parse(cleaned, "", org.jsoup.parser.Parser.xmlParser())
        } else {
            Jsoup.parse(cleaned)
        }
        val fb2Title = soup.selectFirst("book-title")?.text()?.trim().orEmpty()
        val htmlTitle = if (isFb2) "" else soup.title().trim()
        val title = fb2Title.ifBlank { htmlTitle }
        val root = if (isFb2) (soup.selectFirst("body") ?: soup) else soup.body()
        val blocks = blocksOf(root)
        return Document(title, blocks)
    }

    fun blocksOf(root: Element): List<Block> {
        val blocks = mutableListOf<Block>()
        processElement(root, blocks, listDepth = 0, quoteDepth = 0)
        return blocks
    }

    private fun processElement(
        element: Element,
        out: MutableList<Block>,
        listDepth: Int,
        quoteDepth: Int
    ) {
        val tagName = element.tagName().lowercase()
        if (tagName in IGNORED_TAGS) return

        when (tagName) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = tagName[1].digitToInt()
                val inline = buildInline(element)
                if (inline.text.isNotBlank()) {
                    out.add(Heading(level, inline))
                }
            }
            "book-title", "title" -> {
                val inline = buildInline(element)
                if (inline.text.isNotBlank()) {
                    out.add(Heading(1, inline))
                }
            }
            "subtitle" -> {
                val inline = buildInline(element)
                if (inline.text.isNotBlank()) {
                    out.add(Heading(2, inline))
                }
            }
            "p", "v" -> {
                val rawDisplayMath = extractSingleDisplayMath(element.wholeText())
                if (rawDisplayMath != null) {
                    out.add(rawDisplayMath)
                } else {
                    val inline = buildInline(element)
                    if (inline.text.isNotBlank()) {
                        if (quoteDepth > 0 || tagName == "v") {
                            out.add(Quote(quoteDepth.coerceAtLeast(0), inline))
                        } else {
                            out.add(Paragraph(inline))
                        }
                    }
                }
            }
            "blockquote", "epigraph", "cite", "poem", "stanza" -> {
                val hasBlockChildren = element.children().any { isBlockTag(it.tagName()) }
                if (!hasBlockChildren) {
                    val inline = buildInline(element)
                    if (inline.text.isNotBlank()) {
                        out.add(Quote(quoteDepth, inline))
                    }
                } else {
                    val nextDepth = if (tagName == "stanza") quoteDepth else quoteDepth + 1
                    for (child in element.children()) {
                        processElement(child, out, listDepth, nextDepth)
                    }
                }
            }
            "pre" -> {
                val codeChild = element.selectFirst("code")
                val langClass = (codeChild?.className().orEmpty() + " " + element.className())
                    .split(WHITESPACE_SPLIT_REGEX)
                    .firstOrNull { it.startsWith("language-") }
                val language = langClass?.removePrefix("language-")
                val rawCode = element.wholeText()
                val codeText = if (rawCode.endsWith("\n")) rawCode.dropLast(1) else rawCode
                out.add(CodeBlock(language, codeText))
            }
            "ul", "ol" -> {
                val isOrdered = tagName == "ol"
                val startAttr = element.attr("start").toIntOrNull() ?: 1
                var counter = startAttr

                for (child in element.children()) {
                    if (child.tagName().equals("li", ignoreCase = true)) {
                        val marker = if (isOrdered) "${counter++}." else "•"
                        val inline = buildLiInline(child)
                        if (inline.text.isNotBlank()) {
                            out.add(ListItem(listDepth, marker, inline))
                        }
                        for (nested in child.children()) {
                            if (nested.tagName().equals("ul", ignoreCase = true) ||
                                nested.tagName().equals("ol", ignoreCase = true)
                            ) {
                                processElement(nested, out, listDepth + 1, quoteDepth)
                            }
                        }
                    } else {
                        processElement(child, out, listDepth, quoteDepth)
                    }
                }
            }
            "table" -> {
                val tableBlock = parseTable(element)
                if (tableBlock != null) {
                    out.add(tableBlock)
                }
            }
            "img", "image" -> {
                val alt = element.attr("alt")
                out.add(ImageBlock(alt))
            }
            "hr", "empty-line" -> {
                if (tagName == "hr") out.add(Divider)
            }
            else -> {
                val childNodes = element.childNodes()
                var currentInlineNodes = mutableListOf<Node>()

                fun flushInline() {
                    if (currentInlineNodes.isNotEmpty()) {
                        val inline = buildInlineFromNodes(currentInlineNodes)
                        if (inline.text.isNotBlank()) {
                            if (quoteDepth > 0) {
                                out.add(Quote(quoteDepth - 1, inline))
                            } else {
                                out.add(Paragraph(inline))
                            }
                        }
                        currentInlineNodes = mutableListOf()
                    }
                }

                for (node in childNodes) {
                    if (node is Element && isBlockTag(node.tagName())) {
                        flushInline()
                        processElement(node, out, listDepth, quoteDepth)
                    } else {
                        currentInlineNodes.add(node)
                    }
                }
                flushInline()
            }
        }
    }

    private fun extractSingleDisplayMath(rawText: String): MathBlock? {
        val trimmed = rawText.trim()
        if ((trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 4) ||
            (trimmed.startsWith("\\[") && trimmed.endsWith("\\]") && trimmed.length > 4)
        ) {
            val inner = trimmed.substring(2, trimmed.length - 2).trim()
            if (inner.isNotEmpty() && !inner.contains("$$") && !inner.contains("\\]")) {
                return MathBlock(inner)
            }
        }
        return null
    }

    private fun isBlockTag(tagName: String): Boolean = when (tagName.lowercase()) {
        "p", "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote",
        "pre", "table", "hr", "div", "section", "article", "main", "figure", "figcaption", "header", "footer", "aside",
        "book-title", "title", "subtitle", "epigraph", "poem", "stanza", "v", "empty-line", "body", "description", "title-info" -> true
        else -> false
    }

    private fun buildLiInline(li: Element): InlineText {
        val inlineNodes = li.childNodes().filterNot {
            it is Element && (it.tagName().equals("ul", ignoreCase = true) || it.tagName().equals("ol", ignoreCase = true))
        }
        return buildInlineFromNodes(inlineNodes)
    }

    private fun buildInline(element: Element): InlineText {
        return buildInlineFromNodes(element.childNodes())
    }

    private fun buildInlineFromNodes(nodes: List<Node>): InlineText {
        val sb = StringBuilder()
        val spans = mutableListOf<Span>()
        collectInline(nodes, sb, spans)

        val rawText = sb.toString()
        val withoutSoftHyphens = rawText.replace("\u00AD", "")
        val collapsed = collapseWhitespaceAndTrim(withoutSoftHyphens, spans)

        // If the HTML/EPUB text contains inline LaTeX delimiters (\(...\) or $...$), protect and expand math spans
        if (collapsed.text.contains('\\') || collapsed.text.contains('$')) {
            val protected = MathProtector.protect(collapsed.text)
            if (protected.math.isNotEmpty()) {
                return MathProtector.expandPlaceholders(protected.text, collapsed.spans, protected.math)
            }
        }
        return collapsed
    }

    private fun collectInline(
        nodes: List<Node>,
        sb: StringBuilder,
        spans: MutableList<Span>
    ) {
        for (node in nodes) {
            when (node) {
                is TextNode -> {
                    sb.append(node.wholeText)
                }
                is Element -> {
                    val tag = node.tagName().lowercase()
                    if (tag in IGNORED_TAGS) continue
                    if (tag == "br") {
                        sb.append("\n")
                        continue
                    }
                    val start = sb.length
                    collectInline(node.childNodes(), sb, spans)
                    val end = sb.length
                    if (end > start) {
                        when (tag) {
                            "b", "strong" -> spans.add(Span(start, end, SpanKind.Bold))
                            "i", "em", "emphasis", "cite", "dfn" -> spans.add(Span(start, end, SpanKind.Italic))
                            "code", "kbd", "samp", "tt" -> spans.add(Span(start, end, SpanKind.Code))
                            "s", "del", "strike", "strikethrough" -> spans.add(Span(start, end, SpanKind.Strike))
                            "a" -> {
                                val href = node.attr("href").ifBlank { node.attr("l:href") }.ifBlank { node.attr("xlink:href") }
                                spans.add(Span(start, end, SpanKind.Link, href.ifBlank { null }))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun collapseWhitespaceAndTrim(
        raw: String,
        originalSpans: List<Span>
    ): InlineText {
        val out = StringBuilder()
        val indexMap = IntArray(raw.length + 1)
        var lastWasWs = false
        var startOfLine = true

        for (i in raw.indices) {
            indexMap[i] = out.length
            val c = raw[i]
            if (c == '\n') {
                out.append('\n')
                lastWasWs = false
                startOfLine = true
            } else if (c == ' ' || c == '\t' || c == '\r' || c == '\u00A0' || c == '\u000C') {
                if (!lastWasWs && !startOfLine) {
                    out.append(' ')
                    lastWasWs = true
                }
            } else {
                out.append(c)
                lastWasWs = false
                startOfLine = false
            }
        }
        indexMap[raw.length] = out.length

        var trimmed = out.toString()
        val leadingWs = trimmed.takeWhile { it == ' ' || it == '\n' }.length
        val trailingWs = trimmed.takeLastWhile { it == ' ' || it == '\n' }.length
        val newEnd = (trimmed.length - trailingWs).coerceAtLeast(leadingWs)
        trimmed = trimmed.substring(leadingWs, newEnd)

        val remappedSpans = mutableListOf<Span>()
        for (span in originalSpans) {
            val s = (indexMap[span.start.coerceIn(0, raw.length)] - leadingWs).coerceIn(0, trimmed.length)
            val e = (indexMap[span.end.coerceIn(0, raw.length)] - leadingWs).coerceIn(0, trimmed.length)
            if (e > s) {
                remappedSpans.add(Span(s, e, span.kind, span.href))
            }
        }

        return InlineText(trimmed, remappedSpans)
    }

    private fun parseTable(table: Element): TableBlock? {
        val rows = table.select("tr")
        if (rows.isEmpty()) return null

        val firstRow = rows.first()!!
        val isThHeader = firstRow.select("th").isNotEmpty()
        val headerCells = if (isThHeader) {
            firstRow.select("th").map { buildInline(it) }
        } else {
            firstRow.select("td").map { buildInline(it) }
        }

        val dataRows = rows.drop(1).map { row ->
            row.select("td, th").map { buildInline(it) }
        }

        return TableBlock(headerCells, dataRows)
    }
}
