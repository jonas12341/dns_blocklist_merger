package de.jonas.dnsblocklistmerger.download

import de.jonas.dnsblocklistmerger.core.SelectionResult
import de.jonas.dnsblocklistmerger.data.BlocklistRepository
import de.jonas.dnsblocklistmerger.data.SettingsRepository

data class RefreshSummary(val updated: Int, val unchanged: Int, val failed: Int, val result: SelectionResult)

/** Loads every list, stores the outcome at the list and recomputes the output. */
class Refresher(
    private val lists: BlocklistRepository,
    private val settings: SettingsRepository,
    private val downloader: BlocklistDownloader,
) {
    suspend fun refreshAll(): RefreshSummary {
        var updated = 0
        var unchanged = 0
        var failed = 0
        for (list in lists.allLists()) {
            when (val r = downloader.download(list.url, list.etag, list.lastModified)) {
                is DownloadResult.Updated -> {
                    lists.recordSuccess(list.id, r.parsed, r.etag, r.lastModified)
                    updated++
                }
                DownloadResult.NotModified -> {
                    lists.recordNotModified(list.id)
                    unchanged++
                }
                is DownloadResult.Failed -> {
                    lists.recordError(list.id, r.message)
                    failed++
                }
            }
        }
        return RefreshSummary(updated, unchanged, failed, lists.recompute(settings.current()))
    }
}
