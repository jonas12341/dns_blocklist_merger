package de.jonas.dnsblocklistmerger.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class DailyUpdateTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var runs = 0
    private var report = UpdateFlow.Report(0, 10, written = true, writeError = null)

    @Before
    fun setUp() {
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(AppWorkerFactory { runs++; report })
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun delayPointsToTheNextRunTime() {
        val now = ZonedDateTime.of(2026, 11, 16, 4, 30, 0, 0, berlin)
        assertEquals(Duration.ofMinutes(30), DailyScheduler.delayUntil(5 * 60, now))
        assertEquals(Duration.ofHours(23).plusMinutes(30), DailyScheduler.delayUntil(4 * 60, now))
        assertEquals(Duration.ofDays(1), DailyScheduler.delayUntil(4 * 60 + 30, now))
    }

    @Test
    fun delayHandlesDaylightSavingSwitch() {
        // Night of 2026-03-29: clocks jump from 02:00 to 03:00 in Berlin.
        val now = ZonedDateTime.of(2026, 3, 28, 23, 0, 0, 0, berlin)
        assertEquals(Duration.ofHours(5), DailyScheduler.delayUntil(5 * 60, now))
    }

    private fun info(wm: WorkManager): WorkInfo = wm.getWorkInfosForUniqueWork(DailyScheduler.WORK_NAME).get().single()

    @Test
    fun scheduledWorkRunsTheUpdateWhenDelayAndNetworkAreMet() {
        val wm = WorkManager.getInstance(context)
        DailyScheduler.schedule(wm, 5 * 60, replace = false)
        val first = info(wm)
        assertEquals(WorkInfo.State.ENQUEUED, first.state)

        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        driver.setAllConstraintsMet(first.id)
        driver.setInitialDelayMet(first.id)
        assertEquals(1, runs)
        // Periodic work goes back to ENQUEUED for the next day.
        assertEquals(WorkInfo.State.ENQUEUED, info(wm).state)
    }

    @Test
    fun keepAtStartupButReplaceWhenTimeChanges() {
        val wm = WorkManager.getInstance(context)
        DailyScheduler.schedule(wm, 5 * 60, replace = false)
        val original = info(wm).id
        DailyScheduler.schedule(wm, 6 * 60, replace = false)
        assertEquals(original, info(wm).id)
        DailyScheduler.schedule(wm, 6 * 60, replace = true)
        assertEquals(1, wm.getWorkInfosForUniqueWork(DailyScheduler.WORK_NAME).get().count { it.state == WorkInfo.State.ENQUEUED })
        assert(info(wm).id != original)
    }

    @Test
    fun failedWriteIsRetried() {
        report = report.copy(written = false, writeError = "FRITZ!Box nicht erreichbar")
        val wm = WorkManager.getInstance(context)
        DailyScheduler.schedule(wm, 5 * 60, replace = false)
        val id = info(wm).id
        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        driver.setAllConstraintsMet(id)
        driver.setInitialDelayMet(id)
        assertEquals(1, runs)
        assertEquals(1, info(wm).runAttemptCount)
        assertEquals(WorkInfo.State.ENQUEUED, info(wm).state)
    }
}
