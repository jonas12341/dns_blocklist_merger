package de.jonas.dnsblocklistmerger.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import de.jonas.dnsblocklistmerger.OutputPublisher
import de.jonas.dnsblocklistmerger.data.SettingsRepository
import de.jonas.dnsblocklistmerger.download.Refresher
import de.jonas.dnsblocklistmerger.smb.SmbWriteException
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

enum class UpdateOutcome { DONE, WRITE_FAILED }

/** The whole update: load lists, recompute, write to the FRITZ!Box if a path is set. Used by the worker and by "Aktualisieren". */
class UpdateFlow(
    private val refresher: Refresher,
    private val settings: SettingsRepository,
    private val publisher: OutputPublisher,
) {
    data class Report(val failedLists: Int, val entries: Int, val written: Boolean, val writeError: String?)

    suspend fun run(): Report {
        val summary = refresher.refreshAll()
        if (settings.current().smbPath.isBlank()) {
            return Report(summary.failed, summary.result.entries.size, written = false, writeError = null)
        }
        return try {
            publisher.publish()
            Report(summary.failed, summary.result.entries.size, written = true, writeError = null)
        } catch (e: SmbWriteException) {
            Report(summary.failed, summary.result.entries.size, written = false, writeError = e.message)
        }
    }
}

class DailyUpdateWorker(
    context: Context,
    params: WorkerParameters,
    private val flow: suspend () -> UpdateFlow.Report,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val report = flow()
        // The FRITZ!Box may be briefly unreachable at night; try again a few times with backoff.
        return if (report.writeError != null && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object {
        const val MAX_RETRIES = 3
    }
}

class AppWorkerFactory(private val flow: suspend () -> UpdateFlow.Report) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == DailyUpdateWorker::class.java.name) DailyUpdateWorker(appContext, workerParameters, flow) else null
}

object DailyScheduler {
    const val WORK_NAME = "daily-update"

    /** Time until the next [minutesOfDay] after [now]; a time equal to now counts as tomorrow. */
    fun delayUntil(minutesOfDay: Int, now: ZonedDateTime): Duration {
        val today = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(minutesOfDay.toLong())
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        return Duration.between(now, next)
    }

    /** [replace] true after the user changed the time; false at app start keeps an existing schedule. */
    fun schedule(workManager: WorkManager, minutesOfDay: Int, replace: Boolean, now: ZonedDateTime = ZonedDateTime.now()) {
        val request = PeriodicWorkRequestBuilder<DailyUpdateWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayUntil(minutesOfDay, now))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, Duration.ofMinutes(10))
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
