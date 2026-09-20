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

    suspend fun importBatch(items: List<Pair<Uri, String>>, onProgress: (Int, Int, String) -> Unit): Pair<DatasetEntity, ImportSummary> = withContext(Dispatchers.IO) {
        require(items.isNotEmpty()) { "Selecione pelo menos um arquivo para importar." }
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "datasets").apply { mkdirs() }
        val target = File(directory, "$id.csv")
        val sources = items.map { (uri, name) ->
            ImportSource(name) { requireNotNull(context.contentResolver.openInputStream(uri)) { "Não foi possível abrir $name" } }
        }
        val summary = try {
            BatchDataImporter(File(context.cacheDir, "importacao")).import(sources, target, onProgress)
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
        if (summary.validCandles <= 1) {
            target.delete()
            throw IllegalArgumentException("São necessárias pelo menos duas velas válidas.")
        }
        val label = if (items.size == 1) items.first().second else "Importação de ${items.size} arquivos"
        val entity = DatasetEntity(id, label, label.substringBeforeLast('.').uppercase(), "LOCAL", 1,
            requireNotNull(summary.firstEpochMillis), requireNotNull(summary.lastEpochMillis), target.absolutePath,
            System.currentTimeMillis(), summary.validCandles)
        dao.saveDataset(entity)
        entity to summary
    }

    suspend fun importCsv(uri: Uri, displayName: String): DatasetEntity = withContext(Dispatchers.IO) {
        require(displayName.isNotBlank()) { "O arquivo precisa ter um nome válido." }
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
        require(maxRows in 2..2_000_000) { "O limite de velas deve ficar entre 2 e 2.000.000." }
        val dataset = requireNotNull(dao.dataset(id)) { "Dataset não encontrado" }
        val path = requireNotNull(dataset.rawPath) { "Dados brutos expiraram; importe ou reproduza a pesquisa" }
        val rawFile = File(path)
        require(rawFile.isFile && rawFile.canRead()) { "O arquivo de dados não está mais disponível." }
        val candles = ArrayList<Candle>(minOf(dataset.rowCount, maxRows.toLong()).toInt())
        rawFile.inputStream().use { stream ->
            for (chunk in CsvCandleReader().chunks(stream)) {
                val remaining = maxRows - candles.size
                if (remaining <= 0) break
                candles.addAll(chunk.take(remaining))
            }
        }
        require(candles.size >= 2) { "O arquivo precisa conter pelo menos duas velas válidas." }
        if (dataset.rowCount <= maxRows) require(candles.size.toLong() == dataset.rowCount) { "A leitura do conjunto de dados foi interrompida antes do esperado." }
        else require(candles.size == maxRows) { "A leitura do conjunto de dados foi interrompida antes do limite esperado." }
        require(candles.zipWithNext().all { (a, b) -> a.epochMillis <= b.epochMillis }) { "Os dados precisam estar em ordem cronológica." }
        dao.touchDataset(id, System.currentTimeMillis())
        candles
    }

    suspend fun deleteDataset(id: String) = withContext(Dispatchers.IO) {
        val dataset = dao.dataset(id) ?: return@withContext
        dataset.rawPath?.let { path ->
            val file = File(path)
            if (file.exists() && !file.delete()) throw IllegalStateException("Não foi possível excluir o arquivo bruto.")
        }
        dao.deleteDataset(id)
    }

    suspend fun saveBacktest(datasetId: String, market: Market, result: BacktestResult): BacktestEntity {
        requireNotNull(dao.dataset(datasetId)) { "Conjunto de dados não encontrado." }
        val m = result.metrics
        val metrics = listOf(m.trades, m.wins, m.winRate, m.netProfit, m.profitFactor, m.expectancy, m.maxDrawdown, m.breakEvenWinRate ?: Double.NaN).joinToString("|")
        val trades = result.trades.joinToString(";") { "${it.entryTime},${it.exitTime},${it.pnl},${it.won}" }
        val entity = BacktestEntity(UUID.randomUUID().toString(), "manual", datasetId, market.name, metrics, trades, result.equity.joinToString(","), System.currentTimeMillis())
        dao.saveBacktest(entity); return entity
    }

    suspend fun saveResearch(runId: String, datasetId: String, result: EvaluatedStrategy) {
        val symbol = requireNotNull(dao.dataset(datasetId)) { "Conjunto de dados não encontrado." }.symbol
        dao.saveStrategy(StrategyEntity("$runId:${result.strategy.id}", runId, result.strategy.name, result.strategy.market.name, symbol,
            profile(result).name, encodeStrategy(result), System.currentTimeMillis()))
    }

    suspend fun saveResearchLeaders(runId: String, datasetId: String, results: List<EvaluatedStrategy>) {
        val unique = results.distinctBy { it.strategy.id }
        unique.groupBy(::profile).forEach { (_, candidates) ->
            candidates.sortedByDescending(::researchScore).take(3).forEach { saveResearch(runId, datasetId, it) }
        }
    }

    private fun researchScore(value: EvaluatedStrategy): Double =
        value.robustness * .55 + value.metrics.expectancy.coerceIn(-1.0, 1.0) * .30 -
            value.metrics.maxDrawdown * .02 - if (value.overfitWarning) .15 else 0.0

    private fun profile(value: EvaluatedStrategy): Profile {
        val breakEven = value.metrics.breakEvenWinRate ?: .5
        val edge = value.oosWinRate - breakEven
        return when {
            value.status == ValidationStatus.VALIDATED && value.robustness >= .82 &&
                !value.overfitWarning && edge >= .04 -> Profile.CONSERVATIVE
            value.status == ValidationStatus.VALIDATED && value.robustness >= .65 &&
                edge > 0.0 -> Profile.MODERATE
            value.metrics.expectancy > 0.0 && value.robustness >= .45 -> Profile.AGGRESSIVE
            else -> Profile.EXPERIMENTAL
        }
    }

    private fun encodeStrategy(value: EvaluatedStrategy) = listOf(
        value.metrics.trades, value.metrics.winRate, value.oosWinRate, value.metrics.profitFactor,
        value.metrics.expectancy, value.metrics.maxDrawdown, value.robustness, value.status.name,
        value.overfitWarning, value.strategy.direction.name, value.strategy.timeframeMinutes,
        value.strategy.exit.bars ?: 0,
        value.strategy.entries.joinToString("&") { rule -> rule.feature + ":" + rule.operator + ":" + rule.threshold },
        value.metrics.breakEvenWinRate ?: Double.NaN,
        value.oosTrades, value.oosExpectancy, value.oosProfitFactor,
        value.stableForwardFolds, value.forwardFolds,
        value.monteCarloP05, value.monteCarloP95Drawdown
    ).joinToString("|")
}
