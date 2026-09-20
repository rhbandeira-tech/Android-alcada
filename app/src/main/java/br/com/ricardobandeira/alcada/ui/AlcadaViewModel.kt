package br.com.ricardobandeira.alcada.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.ricardobandeira.alcada.AlcadaApplication
import br.com.ricardobandeira.alcada.data.*
import br.com.ricardobandeira.alcada.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class OperationState(val running: Boolean = false, val progress: Float = 0f, val message: String? = null, val error: String? = null)
data class BacktestOptions(val market: Market = Market.BINARY_OPTIONS, val direction: Direction = Direction.CALL, val expiration: Int = 3, val payout: Double = .80, val stopLoss: Double = .002, val takeProfit: Double = .004, val trailing: Double = .0015, val bars: Int = 20, val cost: Double = 0.0)
data class QuantAnalysis(val wick: List<Bucket>, val isOos: List<Bucket>, val timeframeExpiration: List<Bucket>, val assets: List<Bucket>, val heatmap: List<Bucket>, val monteCarlo: MonteCarloSummary? = null)

class AlcadaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AlcadaRepository(application, (application as AlcadaApplication).database.dao())
    val datasets = repository.datasets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val strategies = repository.strategies.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val runs = repository.runs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val backtests = repository.backtests.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _selectedDataset = MutableStateFlow<String?>(null)
    val selectedDataset = _selectedDataset.asStateFlow()
    private val _importState = MutableStateFlow(OperationState())
    val importState = _importState.asStateFlow()
    private val _researchState = MutableStateFlow(OperationState())
    val researchState = _researchState.asStateFlow()
    private val _backtestState = MutableStateFlow(OperationState())
    val backtestState = _backtestState.asStateFlow()
    private val _result = MutableStateFlow<BacktestResult?>(null)
    val result = _result.asStateFlow()
    private val _analysis = MutableStateFlow<QuantAnalysis?>(null)
    val analysis = _analysis.asStateFlow()
    private var importJob: Job? = null
    private var researchJob: Job? = null
    private var backtestJob: Job? = null
    private fun friendlyError(error: Throwable): String = when (error) {
        is java.io.FileNotFoundException -> "O arquivo de dados não está mais disponível. Importe-o novamente."
        is java.util.zip.ZipException -> "O arquivo ZIP está corrompido ou não é compatível."
        is IllegalArgumentException -> error.message?.takeIf { it.length <= 140 } ?: "Os dados informados são inválidos."
        else -> "Não foi possível concluir a operação. Verifique os dados e tente novamente."
    }

    fun selectDataset(id: String) { _selectedDataset.value = id }

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importJob?.cancel()
        importJob = viewModelScope.launch {
            _importState.value = OperationState(true, 0f, "Preparando importação…")
            runCatching {
                val resolver = getApplication<Application>().contentResolver
                val items = uris.map { uri ->
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "arquivo"
                    uri to name
                }
                repository.importBatch(items) { done, total, name ->
                    _importState.value = OperationState(true, if (total == 0) 0f else done.toFloat() / total, "Processando: $name")
                }
            }.onSuccess { (entity, summary) ->
                _selectedDataset.value = entity.id
                val problems = if (summary.issues.isEmpty()) "" else " • ${summary.issues.size} arquivo(s) com problema"
                _importState.value = OperationState(progress = 1f, message = "${summary.validCandles} velas válidas • ${summary.duplicates} duplicadas • ${summary.csvFiles} CSV(s)$problems")
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) _importState.value = OperationState(message = "Importação cancelada")
                else _importState.value = OperationState(error = friendlyError(it))
            }
        }
    }

    fun cancelImport() { importJob?.cancel() }


    fun runBacktest(options: BacktestOptions) {
        val id = _selectedDataset.value ?: return failBacktest("Selecione um conjunto de dados")
        backtestJob?.cancel()
        backtestJob = viewModelScope.launch {
            _backtestState.value = OperationState(true, .05f, "Carregando dados em blocos…")
            runCatching {
                val candles = repository.loadCandles(id)
                _backtestState.value = OperationState(true, .35f, "Calculando sinais sem look-ahead…")
                val direction = if (options.market == Market.BINARY_OPTIONS) options.direction else if (options.direction == Direction.PUT || options.direction == Direction.SHORT) Direction.SHORT else Direction.LONG
                val signals = SignalEngine.wickSignals(candles, direction)
                val result = if (options.market == Market.BINARY_OPTIONS) BacktestEngine.binaryResult(candles, signals, options.expiration, options.payout)
                else BacktestEngine.forexResult(candles, signals, ExitRule(options.stopLoss, options.takeProfit, options.trailing, options.bars), options.cost)
                val split = (candles.size * .7).toInt()
                fun evaluate(entries: List<Pair<Int, Direction>>, expiration: Int = options.expiration) =
                    if (options.market == Market.BINARY_OPTIONS) BacktestEngine.binaryResult(candles, entries, expiration, options.payout)
                    else BacktestEngine.forexResult(candles, entries, ExitRule(options.stopLoss, options.takeProfit, options.trailing, options.bars), options.cost)
                val validationHorizon = if (options.market == Market.BINARY_OPTIONS) options.expiration else options.bars
                val ins = evaluate(signals.filter { it.first + validationHorizon < split }).metrics.netProfit
                val oos = evaluate(signals.filter { it.first >= split }).metrics.netProfit
                val selectedMetadata = datasets.value.firstOrNull { it.id == id }
                val expiry = if (options.market == Market.BINARY_OPTIONS) (1..5).map { value -> Bucket("${selectedMetadata?.timeframeMinutes ?: 1}m×$value", evaluate(signals, value).metrics.netProfit, signals.size) } else emptyList()
                val name = selectedMetadata?.symbol ?: "LOCAL"
                _analysis.value = QuantAnalysis(ChartAnalytics.wickBuckets(ChartAnalytics.wickOutcomes(candles, validationHorizon)), listOf(Bucket("IS", ins, signals.count { it.first + validationHorizon < split }), Bucket("OOS", oos, signals.count { it.first >= split })), expiry, listOf(Bucket(name, result.metrics.netProfit, result.metrics.trades)), ChartAnalytics.dayHourHeatmap(result.trades), MonteCarlo.analyze(result.trades, simulations = 500, seed = 42))
                _backtestState.value = OperationState(true, .85f, "Persistindo resultado…")
                repository.saveBacktest(id, options.market, result); result
            }.onSuccess { _result.value = it; _backtestState.value = OperationState(message = "Teste histórico concluído") }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) _backtestState.value = OperationState(message = "Teste histórico cancelado") else failBacktest(friendlyError(it)) }
        }
    }

    fun runResearch(budget: Int = 2_000) {
        val datasetId = _selectedDataset.value ?: return failResearch("Selecione um conjunto de dados")
        researchJob?.cancel()
        researchJob = viewModelScope.launch {
            val runId = UUID.randomUUID().toString(); val started = System.currentTimeMillis()
            val dao = (getApplication<Application>() as AlcadaApplication).database.dao()
            dao.saveRun(ResearchRunEntity(runId, started, null, "RUNNING", 0, 42, "budget=$budget"))
            _researchState.value = OperationState(true, 0f, "Preparando pesquisa local…")
            runCatching {
                val candles = repository.loadCandles(datasetId)
                ResearchEngine().discover(candles, ResearchBudget(maxCandidates = budget, minimumTrades = minOf(30, maxOf(5, candles.size / 50)))).collect { progress ->
                    val ratio = progress.evaluated.toFloat() / budget
                    _researchState.value = OperationState(true, ratio, "${progress.evaluated} candidatos avaliados • ${progress.accepted} passaram pelo filtro inicial")
                    dao.saveRun(ResearchRunEntity(runId, started, if (progress.finished) System.currentTimeMillis() else null, if (progress.finished) "COMPLETED" else "RUNNING", (ratio * 100).toInt(), 42, "budget=$budget"))
                    if (progress.finished) repository.saveResearchLeaders(runId, datasetId, progress.leaders)
                }
            }.onSuccess { _researchState.value = OperationState(progress = 1f, message = "Pesquisa concluída") }
                .onFailure { error ->
                    val cancelled = error is kotlinx.coroutines.CancellationException
                    withContext(NonCancellable) { dao.saveRun(ResearchRunEntity(runId, started, System.currentTimeMillis(), if (cancelled) "CANCELLED" else "FAILED", (_researchState.value.progress * 100).toInt(), 42, "budget=$budget")) }
                    _researchState.value = OperationState(message = if (cancelled) "Pesquisa cancelada" else null, error = if (cancelled) null else friendlyError(error))
                }
        }
    }

    fun cancelResearch() { researchJob?.cancel() }
    fun cancelBacktest() { backtestJob?.cancel() }
    private fun failResearch(message: String) { _researchState.value = OperationState(error = message) }
    private fun failBacktest(message: String) { _backtestState.value = OperationState(error = message) }
}
