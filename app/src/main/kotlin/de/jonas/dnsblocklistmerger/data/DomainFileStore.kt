package de.jonas.dnsblocklistmerger.data

import java.io.File

/** Keeps the last good domain set of each list as a plain file, one domain per line. */
class DomainFileStore(private val dir: File) {

    private fun file(listId: Long) = File(dir, "$listId.txt")

    fun write(listId: Long, domains: Collection<String>) {
        dir.mkdirs()
        val tmp = File(dir, "$listId.txt.tmp")
        tmp.bufferedWriter().use { out ->
            for (domain in domains) {
                out.write(domain)
                out.write('\n'.code)
            }
        }
        if (!tmp.renameTo(file(listId))) {
            tmp.delete()
            error("Could not store domains of list $listId")
        }
    }

    fun read(listId: Long): List<String> {
        val f = file(listId)
        if (!f.exists()) return emptyList()
        return f.bufferedReader().useLines { lines -> lines.filter { it.isNotEmpty() }.toList() }
    }

    fun delete(listId: Long) {
        file(listId).delete()
    }
}
