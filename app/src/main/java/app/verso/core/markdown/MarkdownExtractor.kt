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

    private val MATH_CODE_LANGUAGES = setOf("math", "latex", "tex", "katex")

    fun extract(markdown: String): Document {
        if (markdown.isBlank()) return Document("", emptyList())
        val cleaned = TextCleaner.clean(markdown)
        val verbatimConverted = convertLatexVerbatimBlocks(cleaned)
        val protected = MathProtector.protect(verbatimConverted)
        val preprocessed = preprocessLatexAndMarkupMacros(protected.text)
        val rootNode = parser.parse(preprocessed)

        val blocks = mutableListOf<Block>()
        walkBlocks(rootNode.firstChild, blocks, quoteDepth = 0, mathList = protected.math)
        return Document("", blocks)
    }

    private val LATEX_VERBATIM_REGEX = Regex(
        """\\begin\{(verbatim|lstlisting)\}(?:\[[^\]]*\])?(.*?)\\end\{\1\}""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val LATEX_COMMENT_LINE_REGEX = Regex("""(?m)^[ \t]*%.*$""")
    private val LATEX_PREAMBLE_DROP_REGEX = Regex(
        """(?m)^[ \t]*\\(?:documentclass|usepackage|usetikzlibrary|bibliographystyle|pagenumbering|pagestyle|setlength|addtolength|newcommand|renewcommand|DeclareMathOperator|geometry|hypersetup|makeatletter|makeatother|maketitle|tableofcontents|newpage|clearpage|cleardoublepage|noindent|centering|raggedright|vspace|hspace|bigskip|medskip|smallskip)\*?(?:\[[^\]]*\])?(?:\{[^{}\n]*\})*[ \t]*$"""
    )
    private val LATEX_BEGIN_END_DOC_REGEX = Regex("""(?m)^[ \t]*\\(?:begin|end)\{(?:document|center|flushleft|flushright|figure|table)\}\*?(?:\[[^\]]*\])?[ \t]*$""")
    private val LATEX_TITLE_REGEX = Regex("""(?m)^[ \t]*\\title\*?\{([^{}\n]+)\}[ \t]*$""")
    private val LATEX_AUTHOR_REGEX = Regex("""(?m)^[ \t]*\\author\*?\{([^{}\n]+)\}[ \t]*$""")
    private val LATEX_DATE_REGEX = Regex("""(?m)^[ \t]*\\date\*?\{([^{}\n]+)\}[ \t]*$""")
    private val LATEX_ABSTRACT_REGEX = Regex(
        """\\begin\{abstract\}(.*?)\\end\{abstract\}""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val LATEX_QUOTE_ENV_REGEX = Regex(
        """\\begin\{(?:quote|quotation)\}(.*?)\\end\{(?:quote|quotation)\}""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val LATEX_THEOREM_ENV_REGEX = Regex(
        """\\begin\{(theorem|lemma|proposition|corollary|definition|remark|example|proof)\}(?:\[([^\]]*)\])?(.*?)\\end\{\1\}""",
        setOf(RegexOption.DOT_MATCHES_ALL)
    )
    private val LATEX_LIST_ENV_TAG_REGEX = Regex("""(?m)^[ \t]*\\(?:begin|end)\{(?:itemize|enumerate|description)\}[ \t]*$""")
    private val LATEX_ITEM_OPT_REGEX = Regex("""(?m)^[ \t]*\\item\[([^\]]+)\][ \t]*""")
    private val LATEX_ITEM_REGEX = Regex("""(?m)^[ \t]*\\item\b[ \t]*""")
    private val LATEX_CHAPTER_REGEX = Regex("""(?m)^\s*\\(?:part|chapter)\*?\{([^{}\n]+)\}\s*$""")
    private val LATEX_SECTION_REGEX = Regex("""(?m)^\s*\\section\*?\{([^{}\n]+)\}\s*$""")
    private val LATEX_SUBSECTION_REGEX = Regex("""(?m)^\s*\\subsection\*?\{([^{}\n]+)\}\s*$""")
    private val LATEX_SUBSUBSECTION_REGEX = Regex("""(?m)^\s*\\subsubsection\*?\{([^{}\n]+)\}\s*$""")
    private val LATEX_PARAGRAPH_REGEX = Regex("""(?m)^\s*\\paragraph\*?\{([^{}\n]+)\}\s*$""")
    private val LATEX_CAPTION_REGEX = Regex("""(?m)^\s*\\caption\*?\{([^{}\n]+)\}\s*$""")
    private val LATEX_LABEL_REGEX = Regex("""\\label\{[^{}\n]+\}""")
    private val LATEX_REF_REGEX = Regex("""\\(?:eq)?ref\{([^{}\n]+)\}""")
    private val LATEX_CITE_REGEX = Regex("""\\cite(?:\[[^\]]*\])?\{([^{}\n]+)\}""")
    private val LATEX_HREF_REGEX = Regex("""\\href\{([^{}\n]+)\}\{([^{}\n]+)\}""")
    private val LATEX_URL_REGEX = Regex("""\\url\{([^{}\n]+)\}""")
    private val LATEX_FOOTNOTE_REGEX = Regex("""\\footnote\{([^{}\n]+)\}""")
    private val LATEX_TEXTBF_REGEX = Regex("""\\textbf\{([^{}\n]+)\}""")
    private val LATEX_TEXTIT_REGEX = Regex("""\\(?:textit|emph|textsc)\{([^{}\n]+)\}""")
    private val LATEX_TEXTTT_REGEX = Regex("""\\texttt\{([^{}\n]+)\}""")
    private val LATEX_QUOTES_REGEX = Regex("""``([^`\n]+?)''""")

    private val ORG_TITLE_REGEX = Regex("""(?mi)^#\+TITLE:\s*(.+)$""")
    private val ORG_HEADING_REGEX = Regex("""(?m)^(\*{1,4})\s+(\S.*)$""")
    private val ADOC_HEADING_REGEX = Regex("""(?m)^(={1,4})\s+(\S.*)$""")

    private val LATEX_NEWCOMMAND_REGEX = Regex("""(?m)^[ \t]*\\(?:newcommand|renewcommand|def)\*?\{\\([a-zA-Z]+)\}(?:\[[^\]]*\])?\{([^{}\n]+)\}[ \t]*$""")
    private val LATEX_DEF_CMD_REGEX = Regex("""(?m)^[ \t]*\\def\\([a-zA-Z]+)\{([^{}\n]+)\}[ \t]*$""")

    private fun convertLatexVerbatimBlocks(input: String): String {
        if (!input.contains("\\begin{")) return input
        return input.replace(LATEX_VERBATIM_REGEX) { match ->
            val body = match.groupValues[2].trim('\n', '\r')
            "\n```\n$body\n```\n"
        }
    }

    /**
     * Converts LaTeX structural macros (\documentclass, \title, \chapter, \section, \subsection,
     * \begin{abstract}, \begin{itemize}/\item, \begin{theorem}, \textbf, \textit, \emph, \href)
     * as well as Org-mode and AsciiDoc headings outside math placeholders into Markdown equivalents.
     */
    private fun preprocessLatexAndMarkupMacros(input: String): String {
        var text = input

        // Support Org-mode (#+TITLE:, * Heading) and AsciiDoc (= Heading) when present
        if (text.contains("#+TITLE:", ignoreCase = true) || ORG_HEADING_REGEX.containsMatchIn(text)) {
            text = text.replace(ORG_TITLE_REGEX, "# $1")
            text = text.replace(ORG_HEADING_REGEX) { m ->
                "${"#".repeat(m.groupValues[1].length)} ${m.groupValues[2]}"
            }
        }
        if (ADOC_HEADING_REGEX.containsMatchIn(text)) {
            text = text.replace(ADOC_HEADING_REGEX) { m ->
                "${"#".repeat(m.groupValues[1].length)} ${m.groupValues[2]}"
            }
        }

        if (!text.contains('\\')) return text

        val isLatexDoc = text.contains("\\documentclass") ||
            text.contains("\\begin{document}") ||
            text.contains("\\section") ||
            text.contains("\\subsection") ||
            text.contains("\\chapter") ||
            text.contains("\\title{") ||
            text.contains("\\author{") ||
            text.contains("\\begin{")

        if (isLatexDoc) {
            // Expand simple \newcommand{\foo}{replacement} and \def\foo{replacement}
            val userMacros = mutableMapOf<String, String>()
            for (m in LATEX_NEWCOMMAND_REGEX.findAll(text)) {
                val cmdName = m.groupValues[1]
                val replacement = m.groupValues[2]
                if (cmdName.isNotEmpty() && replacement.isNotEmpty()) {
                    userMacros[cmdName] = replacement
                }
            }
            for (m in LATEX_DEF_CMD_REGEX.findAll(text)) {
                val cmdName = m.groupValues[1]
                val replacement = m.groupValues[2]
                if (cmdName.isNotEmpty() && replacement.isNotEmpty()) {
                    userMacros[cmdName] = replacement
                }
            }

            text = text.replace(LATEX_COMMENT_LINE_REGEX, "")
            text = text.replace(LATEX_PREAMBLE_DROP_REGEX, "")
            text = text.replace(LATEX_BEGIN_END_DOC_REGEX, "")
            text = text.replace(LATEX_TITLE_REGEX, "# $1")
            text = text.replace(LATEX_AUTHOR_REGEX, "*$1*")
            text = text.replace(LATEX_DATE_REGEX, "*$1*")
            text = text.replace(LATEX_ABSTRACT_REGEX) { m ->
                val body = m.groupValues[1].trim().lines().joinToString("\n") { "> ${it.trim()}" }
                "\n**Abstract**\n\n$body\n"
            }
            text = text.replace(LATEX_QUOTE_ENV_REGEX) { m ->
                val body = m.groupValues[1].trim().lines().joinToString("\n") { "> ${it.trim()}" }
                "\n$body\n"
            }
            text = text.replace(LATEX_THEOREM_ENV_REGEX) { m ->
                val envKind = m.groupValues[1].replaceFirstChar { it.uppercase() }
                val optTitle = m.groupValues[2].trim()
                val body = m.groupValues[3].trim()
                val header = if (optTitle.isNotEmpty()) "**$envKind ($optTitle).**" else "**$envKind.**"
                if (envKind.equals("Proof", ignoreCase = true)) {
                    "\n*$envKind.* $body ∎\n"
                } else {
                    "\n$header *$body*\n"
                }
            }
            text = text.replace(LATEX_LIST_ENV_TAG_REGEX, "")
            text = text.replace(LATEX_ITEM_OPT_REGEX, "- **$1** ")
            text = text.replace(LATEX_ITEM_REGEX, "- ")
            text = text.replace(LATEX_CAPTION_REGEX, "*$1*")
            text = text.replace(LATEX_LABEL_REGEX, "")
            text = text.replace(LATEX_REF_REGEX, "($1)")
            text = text.replace(LATEX_CITE_REGEX, "[$1]")
            text = text.replace(LATEX_QUOTES_REGEX, "“$1”")

            for ((cmd, repl) in userMacros) {
                text = text.replace("\\$cmd\\b".toRegex(), repl)
            }
        }

        text = text.replace(LATEX_CHAPTER_REGEX, "# $1")
        text = text.replace(LATEX_SECTION_REGEX, "# $1")
        text = text.replace(LATEX_SUBSECTION_REGEX, "## $1")
        text = text.replace(LATEX_SUBSUBSECTION_REGEX, "### $1")
        text = text.replace(LATEX_PARAGRAPH_REGEX, "#### $1")
        text = text.replace(LATEX_HREF_REGEX, "[$2]($1)")
        text = text.replace(LATEX_URL_REGEX, "<$1>")
        text = text.replace(LATEX_FOOTNOTE_REGEX, " ($1)")
        text = text.replace(LATEX_TEXTBF_REGEX, "**$1**")
        text = text.replace(LATEX_TEXTIT_REGEX, "*$1*")
        text = text.replace(LATEX_TEXTTT_REGEX, "`$1`")
        return text
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
                        } else if (rawText.contains(MathProtector.OPEN) && mathList.any { it.display }) {
                            MathProtector.splitDisplayMath(
                                text = rawText,
                                existingSpans = emptyList(),
                                mathList = mathList,
                                onParagraph = { inline ->
                                    if (quoteDepth > 0) {
                                        out.add(Quote(quoteDepth - 1, inline))
                                    } else {
                                        out.add(Paragraph(inline))
                                    }
                                },
                                onMathBlock = { displayLatex ->
                                    out.add(MathBlock(displayLatex))
                                }
                            )
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
                    if (lang != null && lang.lowercase() in MATH_CODE_LANGUAGES) {
                        out.add(MathBlock(code.trim()))
                    } else {
                        out.add(CodeBlock(lang, code))
                    }
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
                    val tr = child.firstChild
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

        return MathProtector.expandPlaceholders(sb.toString(), spans, mathList)
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
}
