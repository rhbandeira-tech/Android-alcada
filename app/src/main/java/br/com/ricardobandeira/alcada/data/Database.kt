package br.com.ricardobandeira.alcada.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "research_runs")
data class ResearchRunEntity(@PrimaryKey val id: String, val startedAt: Long, val finishedAt: Long?, val status: String, val progress: Int, val seed: Long, val configurationJson: String)

@Entity(tableName = "strategies", indices = [Index("researchRunId"), Index("createdAt")])
data class StrategyEntity(@PrimaryKey val id: String, val researchRunId: String?, val name: String, val market: String, val symbol: String, val profile: String, val definitionJson: String, val createdAt: Long)

@Entity(tableName = "backtests", indices = [Index("strategyId"), Index("datasetId"), Index("createdAt")])
data class BacktestEntity(@PrimaryKey val id: String, val strategyId: String, val datasetId: String, val market: String, val metricsJson: String, val tradesJson: String, val equityJson: String, val createdAt: Long)

@Entity(tableName = "validation_results", indices = [Index("strategyId")])
data class ValidationResultEntity(@PrimaryKey val id: String, val strategyId: String, val method: String, val inSampleJson: String, val outOfSampleJson: String, val robustness: Double, val status: String)

@Entity(tableName = "datasets")
data class DatasetEntity(@PrimaryKey val id: String, val name: String, val symbol: String, val market: String, val timeframeMinutes: Int, val startAt: Long, val endAt: Long, val rawPath: String?, val lastUsedAt: Long, val rowCount: Long)

@Entity(tableName = "economic_events")
data class EconomicEventEntity(@PrimaryKey val id: String, val name: String, val currency: String, val impact: String, val epochMillis: Long, val actual: Double?, val forecast: Double?, val previous: Double?)

@Entity(tableName = "settings") data class SettingEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface AlcadaDao {
    @Query("SELECT * FROM datasets ORDER BY lastUsedAt DESC") fun datasets(): Flow<List<DatasetEntity>>
    @Query("SELECT * FROM datasets WHERE id = :id") suspend fun dataset(id: String): DatasetEntity?
    @Query("SELECT * FROM strategies ORDER BY createdAt DESC") fun strategies(): Flow<List<StrategyEntity>>
    @Query("SELECT * FROM research_runs ORDER BY startedAt DESC") fun researchRuns(): Flow<List<ResearchRunEntity>>
    @Query("SELECT * FROM backtests ORDER BY createdAt DESC") fun backtests(): Flow<List<BacktestEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveStrategy(value: StrategyEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRun(value: ResearchRunEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDataset(value: DatasetEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBacktest(value: BacktestEntity)
    @Query("UPDATE datasets SET lastUsedAt = :now WHERE id = :id") suspend fun touchDataset(id: String, now: Long)
    @Query("SELECT * FROM datasets WHERE rawPath IS NOT NULL AND lastUsedAt < :cutoff") suspend fun expiredDatasets(cutoff: Long): List<DatasetEntity>
    @Query("UPDATE datasets SET rawPath = NULL WHERE id = :id") suspend fun rawDataDeleted(id: String)
    @Query("DELETE FROM datasets WHERE id = :id") suspend fun deleteDataset(id: String)
}

@Database(entities = [ResearchRunEntity::class, StrategyEntity::class, BacktestEntity::class, ValidationResultEntity::class, DatasetEntity::class, EconomicEventEntity::class, SettingEntity::class], version = 2, exportSchema = false)
abstract class AlcadaDatabase : RoomDatabase() { abstract fun dao(): AlcadaDao }
