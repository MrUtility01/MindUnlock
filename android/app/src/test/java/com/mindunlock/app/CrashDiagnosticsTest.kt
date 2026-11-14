package com.mindunlock.app

import android.content.Context
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class CrashDiagnosticsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("mindunlock_boot_diagnostics", Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(context.filesDir, "diagnostics/last-crash.txt").delete()
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("mindunlock_boot_diagnostics", Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(context.filesDir, "diagnostics/last-crash.txt").delete()
    }

    @Test
    fun interruptedLaunchEntersRecoveryInsteadOfRepeatingStartup() {
        assertFalse(CrashDiagnostics.beginLaunch(context))
        assertTrue(CrashDiagnostics.beginLaunch(context))
    }

    @Test
    fun successfulFirstCompositionClearsInterruptedLaunchMarker() {
        assertFalse(CrashDiagnostics.beginLaunch(context))

        CrashDiagnostics.markLaunchHealthy(context)

        assertFalse(CrashDiagnostics.beginLaunch(context))
    }

    @Test
    fun startupFailureIsSavedAndRoutesNextLaunchToRecovery() {
        assertFalse(CrashDiagnostics.beginLaunch(context))
        CrashDiagnostics.recordStartupFailure(
            context,
            "MainActivity.onCreate",
            IllegalStateException("startup probe")
        )

        assertTrue(CrashDiagnostics.beginLaunch(context))
        assertTrue(CrashDiagnostics.readLastReport(context).contains("startup probe"))

        CrashDiagnostics.clearRecoveryState(context)

        assertFalse(CrashDiagnostics.beginLaunch(context))
    }
}
