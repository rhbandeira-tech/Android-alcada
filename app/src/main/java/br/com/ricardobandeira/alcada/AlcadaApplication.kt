package br.com.ricardobandeira.alcada

import android.app.Application
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import br.com.ricardobandeira.alcada.data.AlcadaDatabase
import br.com.ricardobandeira.alcada.work.RawDataCleanupWorker
import java.util.concurrent.TimeUnit

class AlcadaApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, AlcadaDatabase::class.java, "alcada.db").fallbackToDestructiveMigration().build() }
    override fun onCreate() {
        super.onCreate()
        val cleanup = PeriodicWorkRequestBuilder<RawDataCleanupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("raw-data-cleanup", ExistingPeriodicWorkPolicy.KEEP, cleanup)
    }
}
