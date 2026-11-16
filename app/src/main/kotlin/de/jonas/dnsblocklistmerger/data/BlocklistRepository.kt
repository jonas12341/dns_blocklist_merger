package de.jonas.dnsblocklistmerger.data

import androidx.paging.PagingSource
import androidx.room.withTransaction
import de.jonas.dnsblocklistmerger.core.InputText
import de.jonas.dnsblocklistmerger.core.ParsedList
import de.jonas.dnsblocklistmerger.core.Selection
import de.jonas.dnsblocklistmerger.core.SelectionResult
import de.jonas.dnsblocklistmerger.core.SelectionSettings
import de.jonas.dnsblocklistmerger.core.SourceList
import kotlinx.coroutines.flow.Flow
import java.net.URI

/** Everything the UI and the workers read or change about lists, exceptions and the output. */
class BlocklistRepository(
    private val db: AppDatabase,
    private val files: DomainFileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lists = db.blocklistDao()
    private val exceptions = db.exceptionDao()
    private val output = db.outputDao()

    fun observeLists(): Flow<List<BlocklistEntity>> = lists.observeAll()

    suspend fun allLists(): List<BlocklistEntity> = lists.getAll()

    suspend fun getList(id: Long): BlocklistEntity? = lists.get(id)

    /** Adds a list with the lowest priority so far. Throws [IllegalArgumentException] for a bad or duplicate URL. */
    suspend fun addList(url: String, priority: Int? = null): Long {
        val clean = validateUrl(url)
        require(lists.getAll().none { it.url == clean }) { "Diese Liste ist schon vorhanden." }
        val prio = priority ?: ((lists.maxPriority() ?: 0) + 1)
        return lists.insert(BlocklistEntity(url = clean, name = nameFor(clean), priority = prio.coerceAtLeast(1)))
    }

    /**
     * Changes the URL of a list. A new URL is a new source: status, format and the stored
     * domains are reset, so the next refresh loads it fresh. Throws [IllegalArgumentException]
     * for a bad URL or one another list already has.
     */
    suspend fun updateUrl(id: Long, url: String) {
        val clean = validateUrl(url)
        val list = lists.get(id) ?: return
        if (list.url == clean) return
        require(lists.getAll().none { it.id != id && it.url == clean }) { "Diese Liste ist schon vorhanden." }
        files.delete(id)
        lists.update(
            list.copy(
                url = clean, name = nameFor(clean), format = null, status = ListStatus.PENDING, errorMessage = null,
                lastSuccessAt = null, lastAttemptAt = null, etag = null, lastModified = null, domainCount = 0,
            ),
        )
    }

    suspend fun removeList(id: Long) {
        lists.delete(id)
        files.delete(id)
    }

    suspend fun setPriority(id: Long, priority: Int) {
        val list = lists.get(id) ?: return
        lists.update(list.copy(priority = priority.coerceIn(1, MAX_PRIORITY)))
    }

    suspend fun recordSuccess(id: Long, parsed: ParsedList, etag: String?, lastModified: String?) {
        val list = lists.get(id) ?: return
        files.write(id, parsed.domains)
        val now = clock()
        lists.update(
            list.copy(
                format = parsed.format.name,
                status = ListStatus.OK,
                errorMessage = null,
                lastSuccessAt = now,
                lastAttemptAt = now,
                etag = etag,
                lastModified = lastModified,
                domainCount = parsed.domains.size,
            ),
        )
    }

    /** HTTP 304: the stored domains stay, the list counts as freshly checked. */
    suspend fun recordNotModified(id: Long) {
        val list = lists.get(id) ?: return
        val now = clock()
        lists.update(list.copy(status = ListStatus.OK, errorMessage = null, lastSuccessAt = now, lastAttemptAt = now))
    }

    /** Keeps the last good domains and shows [message] at the list. */
    suspend fun recordError(id: Long, message: String) {
        val list = lists.get(id) ?: return
        lists.update(list.copy(status = ListStatus.ERROR, errorMessage = message, lastAttemptAt = clock()))
    }

    fun observeExceptions(): Flow<List<ExceptionEntity>> = exceptions.observeAll()

    /** Returns false when [raw] is not a valid domain. */
    suspend fun addException(raw: String): Boolean {
        val domain = de.jonas.dnsblocklistmerger.core.DomainNormalizer.normalize(InputText.withoutWhitespace(raw)) ?: return false
        exceptions.insert(ExceptionEntity(domain, clock()))
        return true
    }

    suspend fun removeException(domain: String) = exceptions.delete(domain)

    fun observeOutputState(): Flow<OutputStateEntity?> = output.observeState()

    fun pageDomains(query: String): PagingSource<Int, OutputDomainEntity> = output.pageDomains(InputText.withoutWhitespace(query).lowercase())

    /** The output file as it would be written now. */
    suspend fun renderOutput(): String = buildString {
        for (domain in output.writtenDomains()) append(Selection.line(domain))
    }

    /** Recomputes the output from the stored lists and saves the result for the UI. */
    suspend fun recompute(settings: Settings): SelectionResult {
        val sources = lists.getAll().map { SourceList(it.priority, files.read(it.id)) }
        val result = Selection.compute(
            sources,
            exceptions.allDomains().toHashSet(),
            SelectionSettings(settings.maxBytes, settings.a, settings.b, settings.omitSubdomains),
        )
        db.withTransaction {
            output.replaceDomains(
                result.domains.map { OutputDomainEntity(it.domain, it.frequency, it.bestPriority, it.rank, it.status) },
            )
            val previous = output.getState() ?: OutputStateEntity()
            output.putState(
                previous.copy(
                    computedAt = clock(),
                    entries = result.entries.size,
                    bytes = result.bytes,
                    requiredBytes = result.requiredBytes,
                    maxBytes = result.maxBytes,
                    topPriority = result.topPriority,
                    cutByLimit = result.cutByLimit,
                    exceptions = result.exceptions,
                    droppedSubdomains = result.droppedSubdomains,
                    totalDomains = result.domains.size,
                ),
            )
        }
        return result
    }

    suspend fun recordWritten(error: String?) {
        val previous = output.getState() ?: OutputStateEntity()
        output.putState(
            if (error == null) previous.copy(writtenAt = clock(), writeError = null) else previous.copy(writeError = error),
        )
    }

    companion object {
        const val MAX_PRIORITY = 99

        fun validateUrl(raw: String): String {
            val trimmed = InputText.withoutWhitespace(raw)
            val uri = runCatching { URI(trimmed) }.getOrNull()
            require(uri != null && (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()) {
                "Bitte eine gültige http- oder https-Adresse eingeben."
            }
            return trimmed
        }

        fun nameFor(url: String): String {
            val uri = URI(url)
            val host = uri.host.removePrefix("www.")
            val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            return when {
                host == "raw.githubusercontent.com" && segments.size >= 2 ->
                    "${segments[0]}/${segments[1]} ${segments.last()}"
                segments.isEmpty() -> host
                else -> "$host ${segments.last()}"
            }
        }
    }
}
