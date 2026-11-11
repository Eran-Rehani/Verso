package app.verso.core.text

object TextCleaner {
    fun clean(raw: String): String {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val sb = java.lang.StringBuilder(normalized.length)
        for (c in normalized) {
            when {
                c == '\uFEFF' || c == '\u00AD' || c == '\u200B' -> {
                    // stripped
                }
                c == '\u00A0' || c == '\t' -> {
                    sb.append(' ')
                }
                c == '\u200C' || c == '\u200D' || c == '\n' -> {
                    sb.append(c)
                }
                c.isISOControl() -> {
                    // control character removed
                }
                else -> {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }
}
