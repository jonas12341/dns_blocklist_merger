package de.jonas.dnsblocklistmerger.core

/**
 * Cleans text typed on a phone. Keyboards such as Gboard insert a space after every
 * dot by default, which breaks URLs and domains.
 */
object InputText {
    /** For URLs and domains: they never contain whitespace, so all of it goes. */
    fun withoutWhitespace(raw: String): String = raw.filterNot(Char::isWhitespace)

    /** For SMB paths: folder names may contain spaces, so only spaces after a dot go. */
    fun withoutSpacesAfterDots(raw: String): String = raw.trim().replace(Regex("""\.[ \t ]+"""), ".")
}
