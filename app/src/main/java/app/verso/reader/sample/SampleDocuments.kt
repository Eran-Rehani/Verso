package app.verso.reader.sample

import app.verso.core.importer.DocumentSource

data class SampleDocumentSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val fileName: String,
    val mimeType: String,
    val content: String
) {
    fun toDocumentSource(): DocumentSource = DocumentSource(
        name = fileName,
        mimeType = mimeType,
        bytes = content.toByteArray(Charsets.UTF_8)
    )
}

object SampleDocuments {

    val all: List<SampleDocumentSpec> by lazy {
        listOf(
            MATH_PHYSICS_MARKDOWN,
            QUANTUM_LATEX_PAPER,
            ALGORITHMS_TABLES_CODE_MD,
            TYPOGRAPHY_HTML_ARTICLE,
            MEDITATIONS_PLAIN_TEXT
        )
    }

    val MATH_PHYSICS_MARKDOWN = SampleDocumentSpec(
        id = "math_physics_md",
        title = "Mathematical Analysis & Physics Primer",
        subtitle = "Inline & display LaTeX, matrices, integrals, and multi-level TOC",
        badge = "MD + MATH",
        fileName = "Mathematical_Physics_Primer.md",
        mimeType = "text/markdown",
        content = """
# Mathematical Analysis & Physics Primer

Welcome to **Verso**. Use the **Table of Contents** button in the top bar (or the bottom chapter bar) to jump between chapters, tap **Search** to find keywords, or tap the **Bookmark** icon to save pages to the sidebar. All inline and display \(\LaTeX\) equations are typeset natively.

## 1. Algebraic Foundations

For any quadratic polynomial \(ax^2 + bx + c = 0\) with \(a \neq 0\) and coefficients in \(\mathbb{R}\), the roots are given by the classical quadratic formula:

\[x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a} \tag{1.1}\]

### 1.1 Binomial Theorem & Series

For any non-negative integer \(n \in \mathbb{N}\), the binomial expansion of \((x + y)^n\) is expressed using the binomial coefficient \(\binom{n}{k}\):

\[(x + y)^n = \sum_{k=0}^{n} \binom{n}{k} x^{n-k} y^k = \sum_{k=0}^{n} \frac{n!}{k!(n-k)!} x^{n-k} y^k \tag{1.2}\]

## 2. Calculus & Gaussian Integrals

One of the most celebrated identities in analysis is the Euler–Poisson Gaussian integral over the real line \(\mathbb{R}\):

\[\int_{-\infty}^{\infty} e^{-x^2} \, dx = \sqrt{\pi} \tag{2.1}\]

### 2.1 Fundamental Limits & Derivatives

The derivative of a smooth function \(f(x)\) at a point \(x_0\) is defined by the limit:

\[f'(x_0) = \lim_{h \to 0} \frac{f(x_0 + h) - f(x_0)}{h}\]

And the Riemann zeta function \(\zeta(s)\) for \(\Re(s) > 1\) is given by both an infinite series and an Euler product over primes \(p\):

\[\zeta(s) = \sum_{n=1}^{\infty} \frac{1}{n^s} = \prod_{p} \left( \frac{1}{1 - p^{-s}} \right) \tag{2.2}\]

## 3. Linear Algebra & Matrices

Consider a rotation matrix \(R(\theta) \in \mathbb{R}^{2 \times 2}\) and the identity matrix \(I_2\):

\[R(\theta) = \begin{pmatrix} \cos\theta & -\sin\theta \\ \sin\theta & \cos\theta \end{pmatrix}, \quad \det R(\theta) = \cos^2\theta + \sin^2\theta = 1 \tag{3.1}\]

### 3.1 Piecewise Functions & Cases

The absolute value \(|x|\) and the ReLU activation function \(\sigma(x)\) can be written using piecewise cases:

\[|x| = \begin{cases} x & \text{if } x \ge 0 \\ -x & \text{if } x < 0 \end{cases}\]

## 4. Quantum Mechanics & Electrodynamics

The time-dependent Schrödinger wave equation governs the evolution of the state vector \(\psi(\vec{r}, t)\) under the Hamiltonian operator \(\hat{H}\):

\[i\hbar \frac{\partial \psi}{\partial t} = \left( -\frac{\hbar^2}{2m} \nabla^2 + V(\vec{r}, t) \right) \psi(\vec{r}, t) \tag{4.1}\]

### 4.1 Maxwell's Equations in Differential Form

In vacuum, the electric field \(\vec{E}\) and magnetic field \(\vec{B}\) satisfy the coupled system:

\[\begin{aligned} \nabla \cdot \vec{E} &= \frac{\rho}{\varepsilon_0} \\ \nabla \cdot \vec{B} &= 0 \\ \nabla \times \vec{E} &= -\frac{\partial \vec{B}}{\partial t} \\ \nabla \times \vec{B} &= \mu_0 \vec{J} + \mu_0 \varepsilon_0 \frac{\partial \vec{E}}{\partial t} \end{aligned} \tag{4.2}\]
        """.trimIndent()
    )

    val QUANTUM_LATEX_PAPER = SampleDocumentSpec(
        id = "quantum_latex_tex",
        title = "Path Integrals & Harmonic Oscillators",
        subtitle = "Standalone .tex paper with \\section macros, aligned equations, and matrices",
        badge = "LATEX .TEX",
        fileName = "Quantum_Path_Integrals.tex",
        mimeType = "application/x-tex",
        content = """
\section{Feynman Path Integral Formulation}

In non-relativistic quantum mechanics, the transition amplitude \(K(x_b, t_b; x_a, t_a)\) between initial position \(x_a\) and final position \(x_b\) is expressed as a functional integral over all trajectories \(x(t)\) with action \(S[x]\):

\begin{equation}
K(x_b, t_b; x_a, t_a) = \int \mathcal{D}x(t) \, \exp\left( \frac{i}{\hbar} S[x(t)] \right) \tag{1}
\end{equation}

Here the classical action functional \(S[x]\) for a particle of mass \(m\) moving in a potential \(V(x)\) is defined by:

\[S[x] = \int_{t_a}^{t_b} \left( \frac{1}{2} m \dot{x}^2 - V(x) \right) dt \tag{2}\]

\subsection{The Quantum Harmonic Oscillator}

For the harmonic potential \(V(x) = \frac{1}{2} m \omega^2 x^2\), the Hamiltonian operator \(\hat{H}\) can be factored in terms of ladder operators \(\hat{a}\) and \(\hat{a}^\dagger\):

\begin{aligned}
\hat{a} &= \sqrt{\frac{m\omega}{2\hbar}} \left( \hat{x} + \frac{i}{m\omega} \hat{p} \right) \\
\hat{H} &= \hbar\omega \left( \hat{a}^\dagger \hat{a} + \frac{1}{2} \right)
\end{aligned}

The discrete energy eigenvalues \(E_n\) for \(n \in \{0, 1, 2, \dots\}\) are:

\[E_n = \hbar\omega \left( n + \frac{1}{2} \right) \tag{3}\]

\subsection{Pauli Spin Matrices}

For a spin-\(\frac{1}{2}\) system in \(\mathbb{C}^2\), the three Pauli operators \(\sigma_x, \sigma_y, \sigma_z\) are represented by the \(2 \times 2\) Hermitian matrices:

\[\sigma_x = \begin{pmatrix} 0 & 1 \\ 1 & 0 \end{pmatrix}, \quad \sigma_y = \begin{pmatrix} 0 & -i \\ i & 0 \end{pmatrix}, \quad \sigma_z = \begin{pmatrix} 1 & 0 \\ 0 & -1 \end{pmatrix} \tag{4}\]

\section{Semiclassical WKB Approximation}

In the limit \(\hbar \to 0\), the stationary wave function \(\psi(x)\) is approximated by:

\[\psi(x) \approx \frac{C}{\sqrt{p(x)}} \exp\left( \pm \frac{i}{\hbar} \int^x p(x') \, dx' \right), \quad p(x) = \sqrt{2m(E - V(x))} \tag{5}\]
        """.trimIndent()
    )

    val ALGORITHMS_TABLES_CODE_MD = SampleDocumentSpec(
        id = "algorithms_md",
        title = "Modern Algorithms & Asymptotic Complexity",
        subtitle = "GFM tables, Kotlin code blocks, Master Theorem recurrences, and lists",
        badge = "MD + CODE",
        fileName = "Algorithms_and_Complexity.md",
        mimeType = "text/markdown",
        content = """
# Modern Algorithms & Asymptotic Complexity

This guide combines **Markdown**, **GFM Tables**, **Code Blocks**, and **LaTeX Recurrences** to test multi-block layout and readability.

## 1. Master Theorem for Divide-and-Conquer

Consider a divide-and-conquer recurrence of the form:

\[T(n) = a \, T\!\left(\frac{n}{b}\right) + f(n), \quad a \ge 1, \; b > 1 \tag{1.1}\]

Let \(c_{\text{crit}} = \log_b a\). Then the asymptotic behavior of \(T(n)\) falls into three regimes:

\[T(n) = \begin{cases} \Theta(n^{\log_b a}) & \text{if } f(n) = O(n^{c_{\text{crit}} - \varepsilon}) \\ \Theta(n^{\log_b a} \log n) & \text{if } f(n) = \Theta(n^{c_{\text{crit}}}) \\ \Theta(f(n)) & \text{if } f(n) = \Omega(n^{c_{\text{crit}} + \varepsilon}) \end{cases}\]

## 2. Complexity Comparison Table

| Algorithm | Best Case | Average Case | Worst Case | Space |
| --- | --- | --- | --- | --- |
| **Merge Sort** | \(\Theta(n \log n)\) | \(\Theta(n \log n)\) | \(\Theta(n \log n)\) | \(O(n)\) |
| **Quick Sort** | \(\Theta(n \log n)\) | \(\Theta(n \log n)\) | \(\Theta(n^2)\) | \(O(\log n)\) |
| **Heap Sort** | \(\Theta(n \log n)\) | \(\Theta(n \log n)\) | \(\Theta(n \log n)\) | \(O(1)\) |
| **Binary Search** | \(O(1)\) | \(O(\log n)\) | \(O(\log n)\) | \(O(1)\) |
| **Dijkstra (Heap)** | \(O(E + V \log V)\) | \(O(E + V \log V)\) | \(O(E + V \log V)\) | \(O(V)\) |

## 3. Shortest Paths Implementation

Below is a clean Kotlin implementation of Dijkstra's shortest-path relaxation step:

```kotlin
data class Edge(val to: Int, val weight: Long)

fun dijkstra(n: Int, adj: List<List<Edge>>, source: Int): LongArray {
    val dist = LongArray(n) { Long.MAX_VALUE }
    dist[source] = 0L
    val pq = java.util.PriorityQueue<Pair<Long, Int>>(compareBy { it.first })
    pq.add(0L to source)

    while (pq.isNotEmpty()) {
        val (d, u) = pq.poll()
        if (d > dist[u]) continue
        for (edge in adj[u]) {
            val nextDist = d + edge.weight
            if (nextDist < dist[edge.to]) {
                dist[edge.to] = nextDist
                pq.add(nextDist to edge.to)
            }
        }
    }
    return dist
}
```

> "Premature optimization is the root of all evil, yet asymptotic clarity is the foundation of engineering." — *Donald Knuth*
        """.trimIndent()
    )

    val TYPOGRAPHY_HTML_ARTICLE = SampleDocumentSpec(
        id = "typography_html",
        title = "The Craft of Digital Typography",
        subtitle = "Clean HTML article extraction with semantic headings, quotes, and lists",
        badge = "HTML",
        fileName = "Typography_for_Digital_Reading.html",
        mimeType = "text/html",
        content = """
<!DOCTYPE html>
<html>
<head><title>The Craft of Digital Typography</title></head>
<body>
<article>
  <h1>The Craft of Digital Typography</h1>
  <p>Typography exists to honor content. On a handheld screen, comfortable reading depends on three harmonious variables: <strong>measure</strong> (line width), <strong>leading</strong> (line height), and <strong>contrast</strong>.</p>

  <h2>1. Measure and Line Rhythm</h2>
  <p>A well-set column contains between 45 and 75 characters per line. When lines are too short, the eye darts back and forth prematurely; when lines are too wide, finding the start of the next line becomes fatiguing.</p>
  <blockquote>Typography is what language looks like when it is given room to breathe.</blockquote>

  <h2>2. Choosing Palettes for Ambient Light</h2>
  <ul>
    <li><strong>Light &amp; Sepia:</strong> Ideal for daylight and warm indoor reading, reducing harsh blue contrast.</li>
    <li><strong>Dark Slate:</strong> Balanced low-luminance contrast for evening study.</li>
    <li><strong>AMOLED Black:</strong> Zero-emission background pixels on OLED displays for maximum battery conservation in dark environments.</li>
  </ul>

  <h2>3. Mathematical &amp; Technical Typesetting</h2>
  <p>Technical prose mixes continuous narrative with symbolic expressions, code listings, and structured tables. Maintaining consistent vertical spacing before and after display blocks prevents visual crowding.</p>
</article>
</body>
</html>
        """.trimIndent()
    )

    val MEDITATIONS_PLAIN_TEXT = SampleDocumentSpec(
        id = "meditations_txt",
        title = "Meditations — Book IV (Selected Passages)",
        subtitle = "Plain text reflow, paragraph spacing, and long-form reading comfort",
        badge = "TEXT",
        fileName = "Meditations_Book_IV.txt",
        mimeType = "text/plain",
        content = """
Meditations — Book IV (Selected Passages)

People look for retreats for themselves, in the country, by the coast, or in the hills. There is nowhere that a person can find a more peaceful and trouble-free retreat than in his own mind, especially if he has within himself the kind of thoughts that let him dip into them and immediately gain complete ease of mind: and by ease of mind I mean nothing other than an ordered mind.

So constantly give yourself this retreat, and renew yourself. Let the principles be brief and fundamental, the kind that will at once shut out the world and send you back without any irritation to the life to which you must return.

Do not act as if you had ten thousand years to throw away. Death stands at your elbow. Be good for something while you live and it is in your power.

How much time he gains who does not look to see what his neighbor says or does or thinks, but only at what he does himself, to make it just and holy.

Everything that happens is as normal and expected as the spring rose or the summer fruit: this is true of sickness, death, slander, intrigue, and all the other things that delight or trouble foolish people.

Remember that to change your mind and to accept correction are free acts too. The action is yours, accomplished in accordance with your own impulse and judgment, and indeed your own intelligence.
        """.trimIndent()
    )
}
