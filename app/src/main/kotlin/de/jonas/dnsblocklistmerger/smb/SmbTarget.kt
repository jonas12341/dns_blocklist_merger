package de.jonas.dnsblocklistmerger.smb

/**
 * A file on an SMB share, parsed from `smb://host/share/dir/file.txt`.
 * [directory] and [file] use `\` as separator, relative to the share root, as SMB expects.
 */
data class SmbTarget(val host: String, val share: String, val directory: String, val fileName: String) {
    val file: String get() = if (directory.isEmpty()) fileName else "$directory\\$fileName"

    fun sibling(name: String): String = if (directory.isEmpty()) name else "$directory\\$name"

    /** The target when [fileName] turns out to be a folder: [DEFAULT_FILE_NAME] inside it (#27). */
    fun intoFolder(): SmbTarget = copy(directory = file, fileName = DEFAULT_FILE_NAME)

    companion object {
        const val DEFAULT_FILE_NAME = "blocklist.txt"

        /** `smb://host/...`, also `//host/...` and `\\host\...` as Windows and file managers write it (#27). */
        fun hasKnownPrefix(raw: String): Boolean = rest(raw) != null

        private fun rest(raw: String): String? {
            val text = raw.trim()
            return when {
                text.startsWith("smb://", ignoreCase = true) -> text.substring(6)
                text.startsWith("//") || text.startsWith("\\\\") -> text.substring(2)
                else -> null
            }
        }

        /** Throws [IllegalArgumentException] with a German message for a path that cannot be used. */
        fun parse(raw: String): SmbTarget {
            val rest = requireNotNull(rest(raw)) { "Der Pfad muss mit smb:// beginnen." }
            val parts = rest.replace('\\', '/').split('/').filter { it.isNotEmpty() }
            require(parts.size >= 3) {
                "Der Pfad braucht Server, Freigabe und Dateiname, z. B. smb://fritz.box/FRITZ.NAS/dnsfilter/blocklist.txt"
            }
            val name = parts.last()
            require(name != "." && name != ".." && parts.none { it == ".." }) { "Der Pfad darf kein „..“ enthalten." }
            return SmbTarget(
                host = parts[0],
                share = parts[1],
                directory = parts.subList(2, parts.size - 1).joinToString("\\"),
                fileName = name,
            )
        }
    }
}
