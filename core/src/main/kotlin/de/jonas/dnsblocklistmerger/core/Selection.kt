package de.jonas.dnsblocklistmerger.core

/** One loaded blocklist. Priority 1 is the highest; several lists may share a priority. */
data class SourceList(val priority: Int, val domains: Collection<String>)

data class SelectionSettings(
    val maxBytes: Long,
    val a: Int = 10,
    val b: Int = 1,
    val omitSubdomains: Boolean = true,
)

enum class DomainStatus {
    /** From a list with the highest priority: always written. */
    REQUIRED,

    /** Written because it fitted by rank. */
    INCLUDED,

    /** Left out because the size limit was reached. */
    CUT_BY_LIMIT,

    /** Left out because a parent domain is written and already blocks it. */
    SUBDOMAIN,

    /** Excluded by the user. */
    EXCEPTION,
}

data class DomainInfo(
    val domain: String,
    val frequency: Int,
    val bestPriority: Int,
    val rank: Long,
    val status: DomainStatus,
)

data class SelectionResult(
    /** Domains written to the output file, sorted ascending (0-9 before a-z). */
    val entries: List<String>,
    val bytes: Long,
    val requiredBytes: Long,
    val maxBytes: Long,
    val topPriority: Int?,
    val cutByLimit: Int,
    val exceptions: Int,
    val droppedSubdomains: Int,
    /** Every known domain with its numbers, in no particular order. */
    val domains: List<DomainInfo>,
) {
    fun render(): String = buildString(bytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) {
        for (domain in entries) append(Selection.line(domain))
    }
}

/**
 * Builds the output file from all lists (spec A5, A6).
 *
 * Rank = a × priority value + b × frequency. The priority value is largest for the
 * highest priority: lowest priority number p_max gets 1, priority p gets p_max - p + 1.
 * Domains from lists with the highest priority are always written, even beyond the
 * limit. The rest is added by rank (best first, ties by name) while it fits.
 */
object Selection {
    fun line(domain: String) = "||$domain^\n"

    /** Byte size of one output line; domains are ASCII after normalisation. */
    fun lineBytes(domain: String): Long = domain.length + 4L

    private class Agg(var frequency: Int, var bestPriority: Int)

    fun compute(lists: List<SourceList>, exceptions: Set<String>, settings: SelectionSettings): SelectionResult {
        val agg = HashMap<String, Agg>()
        for (list in lists) {
            for (domain in list.domains) {
                val a = agg[domain]
                if (a == null) {
                    agg[domain] = Agg(1, list.priority)
                } else {
                    a.frequency++
                    if (list.priority < a.bestPriority) a.bestPriority = list.priority
                }
            }
        }
        val usedPriorities = lists.filter { it.domains.isNotEmpty() }.map { it.priority }
        val top = usedPriorities.minOrNull()
        val lowest = usedPriorities.maxOrNull() ?: 0
        fun rank(a: Agg): Long = settings.a.toLong() * (lowest - a.bestPriority + 1) + settings.b.toLong() * a.frequency

        val required = ArrayList<String>()
        val rest = ArrayList<String>()
        for ((domain, a) in agg) {
            if (domain in exceptions) continue
            if (a.bestPriority == top) required.add(domain) else rest.add(domain)
        }
        // Parents before children inside the required set, so pruning needs no undo there.
        required.sortWith(compareBy<String> { it.count { c -> c == '.' } }.thenBy { it })
        rest.sortWith(compareByDescending<String> { rank(agg.getValue(it)) }.thenBy { it })

        val picker = Picker(settings.omitSubdomains)
        for (domain in required) picker.add(domain, Long.MAX_VALUE)
        val requiredBytes = picker.bytes
        var skipped = rest
        // Removing children of a newly added parent frees bytes, so retry skipped domains until stable.
        while (true) {
            val next = ArrayList<String>()
            val freedBefore = picker.freedBytes
            for (domain in skipped) {
                if (!picker.add(domain, settings.maxBytes)) next.add(domain)
            }
            if (next.size == skipped.size || picker.freedBytes == freedBefore) break
            skipped = next
        }

        val selected = picker.selected
        var cut = 0
        var subdomains = 0
        var excluded = 0
        val infos = ArrayList<DomainInfo>(agg.size)
        for ((domain, a) in agg) {
            val status = when {
                domain in exceptions -> DomainStatus.EXCEPTION.also { excluded++ }
                domain in selected -> if (a.bestPriority == top) DomainStatus.REQUIRED else DomainStatus.INCLUDED
                settings.omitSubdomains && picker.hasSelectedAncestor(domain) -> DomainStatus.SUBDOMAIN.also { subdomains++ }
                else -> DomainStatus.CUT_BY_LIMIT.also { cut++ }
            }
            infos.add(DomainInfo(domain, a.frequency, a.bestPriority, rank(a), status))
        }
        return SelectionResult(
            entries = selected.sorted(),
            bytes = picker.bytes,
            requiredBytes = requiredBytes,
            maxBytes = settings.maxBytes,
            topPriority = top,
            cutByLimit = cut,
            exceptions = excluded,
            droppedSubdomains = subdomains,
            domains = infos,
        )
    }

    private class Picker(val omitSubdomains: Boolean) {
        val selected = HashSet<String>()
        var bytes = 0L
        var freedBytes = 0L

        /** Selected domains by each of their ancestors, to drop them when the ancestor is added. */
        private val byAncestor = HashMap<String, MutableList<String>>()

        /** Adds the domain if it fits into [limit]; returns false only when it did not fit. */
        fun add(domain: String, limit: Long): Boolean {
            if (domain in selected) return true
            if (!omitSubdomains) {
                val size = lineBytes(domain)
                if (bytes + size > limit) return false
                selected.add(domain)
                bytes += size
                return true
            }
            if (hasSelectedAncestor(domain)) return true
            val children = byAncestor[domain].orEmpty().filter { it in selected }
            val size = lineBytes(domain) - children.sumOf { lineBytes(it) }
            if (bytes + size > limit) return false
            for (child in children) selected.remove(child)
            freedBytes += children.sumOf { lineBytes(it) }
            byAncestor.remove(domain)
            selected.add(domain)
            bytes += size
            forEachAncestor(domain) { byAncestor.getOrPut(it) { ArrayList(1) }.add(domain) }
            return true
        }

        fun hasSelectedAncestor(domain: String): Boolean {
            var found = false
            forEachAncestor(domain) { if (it in selected) found = true }
            return found
        }

        /** Calls [action] for every parent domain with at least two labels: a.b.example.com -> b.example.com, example.com. */
        private inline fun forEachAncestor(domain: String, action: (String) -> Unit) {
            var dot = domain.indexOf('.')
            while (dot >= 0) {
                val parent = domain.substring(dot + 1)
                if (parent.indexOf('.') < 0) return
                action(parent)
                dot = domain.indexOf('.', dot + 1)
            }
        }
    }
}
