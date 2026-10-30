package com.mindunlock.app

import android.app.Application
import com.mindunlock.app.data.repository.SyncRepository

class MindUnlockApp : Application() {
    lateinit var repository: SyncRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SyncRepository(this)
    }
}
