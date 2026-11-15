package app.verso.core.math

/**
 * TeX's atom classes.
 *
 * TeX inserts space between neighbouring atoms according to *what kind* of thing each is,
 * not how much whitespace the source happened to contain. This is the single biggest reason
 * hand-rolled renderers look wrong: they pad every operator by a fixed amount, so `a+b` and
 * `a=b` get identical gaps even though TeX gives relations more room than binaries.
 */
enum class MathClass {
    /** Ordinary: variables, digits, most symbols. */
    Ord,

    /** Large operators: `\sum`, `\int`, `\prod`. */
    Op,

    /** Binary operators: `+`, `-`, `\times`, `\cdot`. */
    Bin,

    /** Relations: `=`, `<`, `\le`, `\implies`. */
    Rel,

    /** Opening fences: `(`, `[`, `\{`. */
    Open,

    /** Closing fences and punctuation-like closers: `)`, `]`, `\}`. */
    Close,

    /** Punctuation: `,`, `;`, `:`. */
    Punct,

    /** Inner sub-formulas: `\frac`, `\sqrt`, delimited groups, `\sum` with limits. */
    Inner;

    /**
     * TeX reassigns a `Bin` atom at the start of a formula (or after an operator, a
     * relation or an opening fence) to `Ord`, and a `Bin` next to nothing becomes ordinary.
     * A leading `-` is unary minus, not a binary one, and the difference is visible.
     */
    fun atFormulaStartOrAfter(spacing: MathClass?): MathClass = when {
        this == Bin && spacing in setOf(null, Op, Bin, Rel, Open, Punct) -> Ord
        else -> this
    }
}

/**
 * Which style level a formula is being set in. TeX's inter-atom spacing differs between
 * display/text, and script/scriptscript levels use no extra space at all for most pairs.
 */
enum class MathStyleLevel {
    Display,
    Text,
    Script,
    ScriptScript;

    val isScript: Boolean get() = this == Script || this == ScriptScript
}

/**
 * TeX's inter-atom spacing table, in units of 1/18 em (a "mu").
 *
 * Read from TeX's own `\thinmuskip`/`\medmuskip`/`\thickmuskip` application: the classic
 * table is
 *
 * |            | Ord | Op | Bin | Rel | Open | Close | Punct | Inner |
 * |------------|-----|----|-----|-----|------|-------|-------|-------|
 * | Ord        |  0  | 1  |  2  |  3  |  0   |   0   |   0   |  1*   |
 * | Op         |  1  | 1  |  *  |  3  |  0   |   0   |   0   |  1*   |
 * | Bin        |  2  | 2  |  *  |  *  |  2   |   *   |   *   |  2*   |
 * | Rel        |  3  | 3  |  *  |  0  |  3   |   0   |   0   |  3*   |
 * | Open       |  0  | 0  |  *  |  0  |  0   |   0   |   0   |  0    |
 * | Close      |  0  | 1  |  2  |  3  |  0   |   0   |   0   |  1*   |
 * | Punct      |  1* | 1* |  *  |  1* |  1*  |   0   |   1*  |  1*   |
 * | Inner      |  1* | 1  |  2  |  3  |  1*  |   0   |   1*  |  1*   |
 *
 * `*` means "treated as 0 in script and scriptscript styles". Columns are the *second*
 * atom in each pair.
 *
 * The table is symmetric except where TeX's rules deliberately are not, so it is written
 * out in full rather than derived.
 */
object MathSpacing {

    /** No space, in mu. */
    private const val NONE = 0

    /** `\thinmuskip` = 3mu. */
    private const val THIN = 3

    /** `\medmuskip` = 4mu. */
    private const val MEDIUM = 4

    /** `\thickmuskip` = 5mu. */
    private const val THICK = 5

    /** 1/18 em. */
    const val MU_EM = 1f / 18f

    /**
     * Rows are the left atom, columns the right atom, in enum order
     * (`Ord, Op, Bin, Rel, Open, Close, Punct, Inner`).
     *
     * Verified against KaTeX's `spacingData.ts`, which transcribes TeXbook Chapter 18.
     * Note the punct row is deliberately *not* the transpose of its column: punctuation
     * takes a thin space **after** it (`x, y`) and none before, which is the opposite of
     * what a naive symmetric reading of the table suggests.
     */
    private val table: Array<IntArray> = arrayOf(
        // Ord     Op      Bin     Rel     Open    Close   Punct   Inner
        intArrayOf(NONE, THIN, MEDIUM, THICK, NONE, NONE, NONE, THIN), // Ord
        intArrayOf(THIN, THIN, NONE, THICK, NONE, NONE, NONE, THIN), // Op
        intArrayOf(MEDIUM, MEDIUM, NONE, NONE, MEDIUM, NONE, NONE, MEDIUM), // Bin
        intArrayOf(THICK, THICK, NONE, NONE, THICK, NONE, NONE, THICK), // Rel
        intArrayOf(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE), // Open
        intArrayOf(NONE, THIN, MEDIUM, THICK, NONE, NONE, NONE, THIN), // Close
        intArrayOf(THIN, THIN, NONE, THICK, THIN, THIN, THIN, THIN), // Punct
        intArrayOf(THIN, THIN, MEDIUM, THICK, THIN, NONE, THIN, THIN), // Inner
    )

    /**
     * Tight (script/scriptscript) spacing. Only a few pairs keep any space, and only thin
     * space survives: everything else is set solid.
     */
    private val tightTable: Array<IntArray> = arrayOf(
        intArrayOf(NONE, THIN, NONE, NONE, NONE, NONE, NONE, THIN), // Ord
        intArrayOf(THIN, THIN, NONE, NONE, NONE, NONE, NONE, NONE), // Op
        intArrayOf(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE), // Bin
        intArrayOf(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE), // Rel
        intArrayOf(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE), // Open
        intArrayOf(NONE, THIN, NONE, NONE, NONE, NONE, NONE, NONE), // Close
        intArrayOf(NONE, NONE, NONE, NONE, NONE, NONE, NONE, NONE), // Punct
        intArrayOf(NONE, THIN, NONE, NONE, NONE, NONE, NONE, NONE), // Inner
    )

    /**
     * Space between a [left] atom and a [right] atom, in em.
     *
     * In script styles every pair that would get thin/medium/thick space is set solid, which
     * is why `x^{a+b}` is tighter than `a+b` on the baseline.
     */
    fun gapEm(left: MathClass, right: MathClass, level: MathStyleLevel): Float {
        val row = if (level.isScript) tightTable else table
        val mu = row[left.ordinal][right.ordinal]
        return mu * MU_EM
    }
}
