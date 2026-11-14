package com.mindunlock.app

import android.app.Application
import com.mindunlock.app.data.repository.SyncRepository

class MindUnlockApp : Application() {
    /**
     * Install crash reporting first and defer Room/OkHttp initialization until the
     * activity has checked whether this launch needs the recovery screen.
     */
    val repository: SyncRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        SyncRepository(this)
    }

    override fun onCreate() {
        super.onCreate()
        CrashDiagnostics.install(this)
    }
}
