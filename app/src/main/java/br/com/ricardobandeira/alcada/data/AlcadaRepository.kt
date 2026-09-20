package br.com.ricardobandeira.alcada.data

import android.content.Context
import android.net.Uri
import br.com.ricardobandeira.alcada.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AlcadaRepository(private val context: Context, private val dao: AlcadaDao) {
    val datasets: Flow<List<DatasetEntity>> = dao.datasets()
    val strategies: Flow<List<StrategyEntity>> = dao.strategies()
    val runs: Flow<List<ResearchRunEntity>> = dao.researchRuns()
    val backtests: Flow<List<BacktestEntity>> = dao.backtests()

    suspend fun importCsv(uri: Uri, displayName: String): DatasetEntity = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "datasets").apply { mkdirs() }
        val target = File(directory, "$id.csv")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Não foi possível abrir o arquivo" }
            target.outputStream().use { output -> input.copyTo(output) }
        }
        var count = 0L; var first = Long.MAX_VALUE; var last = Long.MIN_VALUE
        try {
            target.inputStream().use { stream -> CsvCandleReader().chunks(stream).forEach { chunk ->
                count += chunk.size; first = minOf(first, chunk.first().epochMillis); last = maxOf(last, chunk.last().epochMillis)
            } }
            require(count > 1) { "O CSV precisa conter ao menos duas velas" }
        } catch (error: Throwable) { target.delete(); throw error }
        val entity = DatasetEntity(id, displayName, displayName.substringBeforeLast('.').uppercase(), "LOCAL", 1, first, last, target.absolutePath, System.currentTimeMillis(), count)
        dao.saveDataset(entity)
        entity
    }

    suspend fun loadCandles(id: String, maxRows: Int = 500_000): List<Candle> = withContext(Dispatchers.IO) {
        val dataset = requireNotNull(dao.dataset(id)) { "Dataset não encontrado" }
        val path = requireNotNull(dataset.rawPath) { "Dados brutos expiraram; importe ou reproduza a pesquisa" }
        val candles = ArrayList<Candle>(minOf(dataset.rowCount, maxRows.toLong()).toInt())
        File(path).inputStream().use { stream ->
            for (chunk in CsvCandleReader().chunks(stream)) {
                val remaining = maxRows - candles.size
                if (remaining <= 0) break
                candles.addAll(chunk.take(remaining))
            }
        }
        dao.touchDataset(id, System.currentTimeMillis())
        candles
    }

    suspend fun saveBacktest(datasetId: String, market: Market, result: BacktestResult): BacktestEntity {
        val m = result.metrics
        val metrics = listOf(m.trades, m.wins, m.winRate, m.netProfit, m.profitFactor, m.expectancy, m.maxDrawdown, m.breakEvenWinRate ?: Double.NaN).joinToString("|")
        val trades = result.trades.joinToString(";") { "${it.entryTime},${it.exitTime},${it.pnl},${it.won}" }
        val entity = BacktestEntity(UUID.randomUUID().toString(), "manual", datasetId, market.name, metrics, trades, result.equity.joinToString(","), System.currentTimeMillis())
        dao.saveBacktest(entity); return entity
    }

    suspend fun saveResearch(runId: String, datasetId: String, result: EvaluatedStrategy) {
        val symbol = dao.dataset(datasetId)?.symbol ?: result.strategy.symbol
        dao.saveStrategy(StrategyEntity(result.strategy.id, runId, result.strategy.name, result.strategy.market.name, symbol,
            profile(result).name, encodeStrategy(result), System.currentTimeMillis()))
    }

    private fun profile(value: EvaluatedStrategy): Profile = when {
        value.status == ValidationStatus.VALIDATED && value.robustness >= .8 -> Profile.CONSERVATIVE
        value.status == ValidationStatus.VALIDATED -> Profile.MODERATE
        value.metrics.maxDrawdown < 10 -> Profile.AGGRESSIVE
        else -> Profile.EXPERIMENTAL
    }

    private fun encodeStrategy(value: EvaluatedStrategy) = listOf(value.metrics.trades, value.metrics.winRate, value.oosWinRate, value.metrics.profitFactor,
        value.metrics.expectancy, value.metrics.maxDrawdown, value.robustness, value.status.name, value.overfitWarning).joinToString("|")
}
