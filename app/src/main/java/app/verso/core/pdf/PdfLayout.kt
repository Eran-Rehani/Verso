package app.verso.core.pdf

import kotlin.math.ceil
import kotlin.math.max

object PdfLayout {
    const val LINE_TOLERANCE_EM = 0.35f
    const val SPACE_GAP_EM = 0.15f
    const val HEADER_FOOTER_BAND = 0.08f
    const val SPANNING_FRACTION = 0.6f
    const val MIN_GUTTER_PT = 8f
    const val MIN_SIDE_FRACTION = 0.25f

    private val PAGE_NUMBER_REGEX = Regex(
        "^(page\\s+)?([0-9]{1,4}|[ivxlcdm]{1,6})(\\s+of\\s+[0-9]+)?$",
        RegexOption.IGNORE_CASE
    )

    fun orderedLines(pages: List<PdfPage>): List<PdfLine> {
        if (pages.isEmpty()) return emptyList()

        // 1. Extract raw ordered lines per page
        val allRawLines = mutableListOf<PdfLine>()
        val pageCount = pages.size

        for (page in pages) {
            val pageLines = processPage(page)
            allRawLines.addAll(pageLines)
        }

        // 2. Identify candidate header/footer keys across pages
        val keyPageMap = mutableMapOf<String, MutableSet<Int>>()
        for (line in allRawLines) {
            val page = pages.getOrNull(line.page) ?: continue
            val isTop = line.baseline <= HEADER_FOOTER_BAND * page.height
            val isBottom = line.baseline >= (1f - HEADER_FOOTER_BAND) * page.height
            if (isTop || isBottom) {
                val key = line.text.lowercase().replace(Regex("\\d+"), "#").trim()
                if (key.isNotEmpty()) {
                    keyPageMap.getOrPut(key) { mutableSetOf() }.add(line.page)
                }
            }
        }

        val removalThreshold = max(2, ceil(0.5f * pageCount).toInt())
        val keysToRemove = keyPageMap.filter { it.value.size >= removalThreshold }.keys

        // 3. Filter lines
        val filteredLines = allRawLines.filterNot { line ->
            val page = pages.getOrNull(line.page) ?: return@filterNot false
            val isTop = line.baseline <= HEADER_FOOTER_BAND * page.height
            val isBottom = line.baseline >= (1f - HEADER_FOOTER_BAND) * page.height
            if (isTop || isBottom) {
                val key = line.text.lowercase().replace(Regex("\\d+"), "#").trim()
                if (key in keysToRemove || PAGE_NUMBER_REGEX.matches(line.text.trim())) {
                    return@filterNot true
                }
            }
            false
        }

        return filteredLines
    }

    private fun processPage(page: PdfPage): List<PdfLine> {
        val runs = page.runs.filter { it.text.isNotBlank() }
        if (runs.isEmpty()) return emptyList()

        val pageWidth = page.width
        val spanningThreshold = SPANNING_FRACTION * pageWidth

        val spanningRuns = runs.filter { it.width >= spanningThreshold }
        val nonSpanningRuns = runs.filter { it.width < spanningThreshold }

        // Gutter detection on non-spanning runs
        var gutterLeft = 0f
        var gutterRight = 0f
        var hasGutter = false

        if (nonSpanningRuns.isNotEmpty()) {
            val sortedIntervals = nonSpanningRuns
                .map { it.x to (it.x + it.width) }
                .sortedBy { it.first }

            // Merge overlapping intervals
            val merged = mutableListOf<Pair<Float, Float>>()
            for (interval in sortedIntervals) {
                if (merged.isEmpty()) {
                    merged.add(interval)
                } else {
                    val last = merged.last()
                    if (interval.first <= last.second) {
                        merged[merged.size - 1] = last.first to maxOf(last.second, interval.second)
                    } else {
                        merged.add(interval)
                    }
                }
            }

            // Find gaps
            var bestGapWidth = 0f
            var bestGapLeft = 0f
            var bestGapRight = 0f

            for (i in 0 until merged.size - 1) {
                val gapStart = merged[i].second
                val gapEnd = merged[i + 1].first
                val gapWidth = gapEnd - gapStart
                val mid = (gapStart + gapEnd) / 2f
                val midFraction = mid / pageWidth

                if (gapWidth >= MIN_GUTTER_PT && midFraction in 0.3f..0.7f) {
                    if (gapWidth > bestGapWidth) {
                        bestGapWidth = gapWidth
                        bestGapLeft = gapStart
                        bestGapRight = gapEnd
                    }
                }
            }

            if (bestGapWidth >= MIN_GUTTER_PT) {
                val leftCount = nonSpanningRuns.count { (it.x + it.width) <= bestGapLeft }
                val rightCount = nonSpanningRuns.count { it.x >= bestGapRight }
                val total = nonSpanningRuns.size
                if (leftCount.toFloat() / total >= MIN_SIDE_FRACTION &&
                    rightCount.toFloat() / total >= MIN_SIDE_FRACTION
                ) {
                    hasGutter = true
                    gutterLeft = bestGapLeft
                    gutterRight = bestGapRight
                }
            }
        }

        // If runs straddle the gutter, treat them as spanning
        val effectiveSpanning = mutableListOf<TextRun>()
        val column0Runs = mutableListOf<TextRun>()
        val column1Runs = mutableListOf<TextRun>()

        effectiveSpanning.addAll(spanningRuns)

        for (run in nonSpanningRuns) {
            if (hasGutter) {
                if (run.x < gutterRight && (run.x + run.width) > gutterLeft) {
                    // Straddles gutter
                    effectiveSpanning.add(run)
                } else if ((run.x + run.width) <= gutterLeft) {
                    column0Runs.add(run)
                } else {
                    column1Runs.add(run)
                }
            } else {
                column0Runs.add(run)
            }
        }

        // Sort spanning runs by baseline to define vertical bands
        val sortedSpanning = effectiveSpanning.sortedBy { it.baseline }

        // Define bands: each spanning run is its own separator or line
        val result = mutableListOf<PdfLine>()

        if (sortedSpanning.isEmpty()) {
            // Whole page is one band
            emitBandLines(page.index, column0Runs, column1Runs, hasGutter, result)
        } else {
            var currentY = 0f
            for (spanRun in sortedSpanning) {
                val bandEndY = spanRun.baseline
                // Collect runs with baseline in [currentY, bandEndY)
                val bandCol0 = column0Runs.filter { it.baseline >= currentY && it.baseline < bandEndY }
                val bandCol1 = column1Runs.filter { it.baseline >= currentY && it.baseline < bandEndY }
                emitBandLines(page.index, bandCol0, bandCol1, hasGutter, result)

                // Emit spanning run line
                val spanLine = makeLine(page.index, column = 0, listOf(spanRun))
                result.add(spanLine)
                currentY = bandEndY + 0.01f
            }
            // Runs below the last spanning run
            val bandCol0 = column0Runs.filter { it.baseline >= currentY }
            val bandCol1 = column1Runs.filter { it.baseline >= currentY }
            emitBandLines(page.index, bandCol0, bandCol1, hasGutter, result)
        }

        return result
    }

    private fun emitBandLines(
        pageIndex: Int,
        col0Runs: List<TextRun>,
        col1Runs: List<TextRun>,
        hasGutter: Boolean,
        out: MutableList<PdfLine>
    ) {
        val col0Lines = groupIntoLines(pageIndex, column = 0, col0Runs)
        out.addAll(col0Lines)

        if (hasGutter && col1Runs.isNotEmpty()) {
            val col1Lines = groupIntoLines(pageIndex, column = 1, col1Runs)
            out.addAll(col1Lines)
        }
    }

    private fun groupIntoLines(
        pageIndex: Int,
        column: Int,
        runs: List<TextRun>
    ): List<PdfLine> {
        if (runs.isEmpty()) return emptyList()

        // Sort runs by baseline primarily, then x
        val sorted = runs.sortedWith(compareBy({ it.baseline }, { it.x }))
        val lineGroups = mutableListOf<MutableList<TextRun>>()

        for (run in sorted) {
            var placed = false
            for (group in lineGroups) {
                val representative = group.first()
                val largerSize = maxOf(representative.fontSize, run.fontSize)
                if (kotlin.math.abs(representative.baseline - run.baseline) <= LINE_TOLERANCE_EM * largerSize) {
                    group.add(run)
                    placed = true
                    break
                }
            }
            if (!placed) {
                lineGroups.add(mutableListOf(run))
            }
        }

        // Sort groups by average baseline
        lineGroups.sortBy { group -> group.map { it.baseline }.average().toFloat() }

        val lines = mutableListOf<PdfLine>()
        for (group in lineGroups) {
            lines.add(makeLine(pageIndex, column, group))
        }
        return lines
    }

    private fun makeLine(
        pageIndex: Int,
        column: Int,
        group: List<TextRun>
    ): PdfLine {
        val sorted = group.sortedBy { it.x }
        val sb = StringBuilder()
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxFontSize = 0f
        var allBold = true
        var avgBaselineSum = 0f

        for (i in sorted.indices) {
            val run = sorted[i]
            minX = minOf(minX, run.x)
            maxX = maxOf(maxX, run.x + run.width)
            maxFontSize = maxOf(maxFontSize, run.fontSize)
            if (!run.bold) allBold = false
            avgBaselineSum += run.baseline

            if (i > 0) {
                val prev = sorted[i - 1]
                val gap = run.x - (prev.x + prev.width)
                val fontSize = maxOf(prev.fontSize, run.fontSize)
                val needSpace = gap > (SPACE_GAP_EM * fontSize) &&
                        !prev.text.endsWith(" ") &&
                        !run.text.startsWith(" ")
                if (needSpace) {
                    sb.append(" ")
                }
            }
            sb.append(run.text)
        }

        val avgBaseline = avgBaselineSum / sorted.size
        return PdfLine(
            page = pageIndex,
            column = column,
            text = sb.toString(),
            x0 = minX,
            x1 = maxX,
            baseline = avgBaseline,
            fontSize = maxFontSize,
            bold = allBold
        )
    }
}
