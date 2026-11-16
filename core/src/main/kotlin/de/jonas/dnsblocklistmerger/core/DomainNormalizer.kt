package de.jonas.dnsblocklistmerger.core

import java.net.IDN

/** Turns a raw host token into a lowercase ASCII domain, or null when it is not a blockable domain. */
object DomainNormalizer {
    private val RESERVED = setOf(
        "localhost",
        "localhost.localdomain",
        "local",
        "broadcasthost",
        "ip6-localhost",
        "ip6-loopback",
        "ip6-localnet",
        "ip6-mcastprefix",
        "ip6-allnodes",
        "ip6-allrouters",
        "ip6-allhosts",
    )

    fun normalize(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        val scheme = s.indexOf("://")
        if (scheme >= 0) s = s.substring(scheme + 3)
        val cut = s.indexOfAny(charArrayOf('/', '?', '#'))
        if (cut >= 0) s = s.substring(0, cut)
        val at = s.lastIndexOf('@')
        if (at >= 0) s = s.substring(at + 1)
        if (s.startsWith("[")) return null // bracketed IPv6 literal
        val colon = s.indexOf(':')
        if (colon >= 0) {
            if (s.indexOf(':', colon + 1) >= 0) return null // bare IPv6 literal
            s = s.substring(0, colon)
        }
        s = s.trimEnd('.').lowercase()
        if (s.isEmpty()) return null
        if (s.any { it.code > 0x7f }) {
            s = try {
                IDN.toASCII(s, IDN.ALLOW_UNASSIGNED).lowercase()
            } catch (_: IllegalArgumentException) {
                return null
            }
        }
        return if (isValid(s)) s else null
    }

    private fun isValid(domain: String): Boolean {
        if (domain.length > 253 || domain in RESERVED) return false
        val labels = domain.split('.')
        if (labels.size < 2) return false
        for (label in labels) {
            if (label.isEmpty() || label.length > 63) return false
            if (label.first() == '-' || label.last() == '-') return false
            if (!label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }) return false
        }
        // An all-numeric last label means an IPv4 address or a bogus name.
        return !labels.last().all { it in '0'..'9' }
    }
}
