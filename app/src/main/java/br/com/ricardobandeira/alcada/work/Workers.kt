package br.com.ricardobandeira.alcada.work

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.ricardobandeira.alcada.data.AlcadaDatabase
import java.io.File
import java.util.concurrent.TimeUnit

/** Deletes only raw cache files; metadata, strategies, backtests and validation remain reproducible. */
class RawDataCleanupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val db = Room.databaseBuilder(applicationContext, AlcadaDatabase::class.java, "alcada.db").build()
        return try {
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            db.dao().expiredDatasets(cutoff).forEach { dataset ->
                dataset.rawPath?.let { File(it).takeIf(File::exists)?.delete() }
                db.dao().rawDataDeleted(dataset.id)
            }
            Result.success()
        } catch (_: Exception) { Result.retry() } finally { db.close() }
    }
}

