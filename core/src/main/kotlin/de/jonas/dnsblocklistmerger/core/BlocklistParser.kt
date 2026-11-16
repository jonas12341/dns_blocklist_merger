package de.jonas.dnsblocklistmerger.core

enum class ListFormat { ADBLOCK, HOSTS, DOMAINS }

data class ParsedList(val format: ListFormat, val domains: Set<String>)

/**
 * Reads adblock (`||domain^`), hosts (`0.0.0.0 domain`) and plain domain lists.
 * Lines are classified one by one, so mixed files work too; the reported format
 * is the one most lines used.
 */
object BlocklistParser {

    fun parse(text: String): ParsedList = parse(text.lineSequence())

    fun parse(lines: Sequence<String>): ParsedList {
        val domains = LinkedHashSet<String>()
        var adblock = 0
        var hosts = 0
        var plain = 0
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line[0] == '!' || line[0] == '#' || line[0] == '[') continue
            when {
                line.startsWith("||") -> {
                    adblock++
                    adblockDomain(line)?.let(domains::add)
                }
                line.startsWith("@@") || line.contains("##") || line.contains("#@#") || line.startsWith("/") -> adblock++
                else -> {
                    val tokens = stripInlineComment(line).split(' ', '\t').filter { it.isNotEmpty() }
                    if (tokens.isEmpty()) continue
                    if (looksLikeIp(tokens[0])) {
                        hosts++
                        for (i in 1 until tokens.size) DomainNormalizer.normalize(tokens[i])?.let(domains::add)
                    } else if (tokens.size == 1) {
                        plain++
                        if (!tokens[0].contains('*')) DomainNormalizer.normalize(tokens[0])?.let(domains::add)
                    }
                }
            }
        }
        val format = when {
            adblock > 0 && adblock >= hosts && adblock >= plain -> ListFormat.ADBLOCK
            hosts > 0 && hosts >= plain -> ListFormat.HOSTS
            else -> ListFormat.DOMAINS
        }
        return ParsedList(format, domains)
    }

    /** Only plain `||domain^` rules block a whole domain; rules with paths, wildcards or options are skipped. */
    private fun adblockDomain(line: String): String? {
        val end = line.indexOf('^')
        if (end < 0) return null
        val tail = line.substring(end + 1)
        if (tail.isNotEmpty() && tail != "|") return null
        val host = line.substring(2, end)
        if (host.isEmpty() || host.any { it == '/' || it == '*' || it == '$' || it == ':' }) return null
        return DomainNormalizer.normalize(host)
    }

    private fun stripInlineComment(line: String): String {
        val hash = line.indexOf('#')
        return if (hash >= 0) line.substring(0, hash) else line
    }

    private fun looksLikeIp(token: String): Boolean {
        if (token.contains(':')) return token.all { it.isLetterOrDigit() || it == ':' || it == '%' || it == '.' }
        val parts = token.split('.')
        return parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.length <= 3 && p.all { it in '0'..'9' } }
    }
}
