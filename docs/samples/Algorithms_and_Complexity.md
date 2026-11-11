# Modern Algorithms & Asymptotic Complexity

This guide combines **Markdown**, **GFM Tables**, **Code Blocks**, and **LaTeX Recurrences** to test multi-block layout and readability.

## 1. Master Theorem for Divide-and-Conquer

Consider a divide-and-conquer recurrence of the form:

$$T(n) = a \, T\!\left(\frac{n}{b}\right) + f(n), \quad a \ge 1, \; b > 1 \tag{1.1}$$

Let $c_{\text{crit}} = \log_b a$. Then the asymptotic behavior of $T(n)$ falls into three regimes:

$$T(n) = \begin{cases} \Theta(n^{\log_b a}) & \text{if } f(n) = O(n^{c_{\text{crit}} - \varepsilon}) \\ \Theta(n^{\log_b a} \log n) & \text{if } f(n) = \Theta(n^{c_{\text{crit}}}) \\ \Theta(f(n)) & \text{if } f(n) = \Omega(n^{c_{\text{crit}} + \varepsilon}) \end{cases}$$

## 2. Complexity Comparison Table

| Algorithm | Best Case | Average Case | Worst Case | Space |
| --- | --- | --- | --- | --- |
| **Merge Sort** | $\Theta(n \log n)$ | $\Theta(n \log n)$ | $\Theta(n \log n)$ | $O(n)$ |
| **Quick Sort** | $\Theta(n \log n)$ | $\Theta(n \log n)$ | $\Theta(n^2)$ | $O(\log n)$ |
| **Heap Sort** | $\Theta(n \log n)$ | $\Theta(n \log n)$ | $\Theta(n \log n)$ | $O(1)$ |
| **Binary Search** | $O(1)$ | $O(\log n)$ | $O(\log n)$ | $O(1)$ |
| **Dijkstra (Heap)** | $O(E + V \log V)$ | $O(E + V \log V)$ | $O(E + V \log V)$ | $O(V)$ |

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
