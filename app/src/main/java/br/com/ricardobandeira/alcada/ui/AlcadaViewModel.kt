package br.com.ricardobandeira.alcada.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.os.BatteryManager
import android.os.PowerManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.ricardobandeira.alcada.AlcadaApplication
import br.com.ricardobandeira.alcada.data.*
import br.com.ricardobandeira.alcada.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class OperationState(val running: Boolean = false, val progress: Float = 0f, val message: String? = null, val error: String? = null, val paused: Boolean = false)
data class BacktestOptions(val market: Market = Market.BINARY_OPTIONS, val direction: Direction = Direction.CALL, val expiration: Int = 3, val payout: Double = .80, val stopLoss: Double = .002, val takeProfit: Double = .004, val trailing: Double = .0015, val bars: Int = 20, val cost: Double = 0.0)
data class ResearchOptions(val candidates: Int = 2_000, val minimumTrades: Int = 30, val intensive: Boolean = false, val threads: Int? = null, val memoryMb: Int? = null, val payout: Double = .85)
data class DeviceHealth(val batteryPercent: Int? = null, val charging: Boolean = false, val thermalStatus: Int = 0) {
    val thermalLabel: String get() = when { thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> "alto"; thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> "moderado"; else -> "normal" }
}

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
    private val _deviceHealth = MutableStateFlow(readDeviceHealth())
    val deviceHealth = _deviceHealth.asStateFlow()
    val analysis = _analysis.asStateFlow()
    private var importJob: Job? = null
    private var researchJob: Job? = null
    private var lastResearchOptions: ResearchOptions? = null
    private var researchPaused = false
    private var backtestJob: Job? = null
    private fun readDeviceHealth(): DeviceHealth {
        val app = getApplication<Application>()
        val battery = app.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val power = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
        val charging = battery.isCharging
        return DeviceHealth(percent, charging, power.currentThermalStatus)
    }
    fun refreshDeviceHealth() { _deviceHealth.value = readDeviceHealth() }
    private fun friendlyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            error is java.io.FileNotFoundException -> "O arquivo de dados não está mais disponível. Importe-o novamente."
            error is java.util.zip.ZipException -> "O arquivo ZIP está corrompido ou não é compatível."
            message.contains("pelo menos duas velas", true) || message.contains("ao menos duas velas", true) ->
                "O arquivo precisa conter pelo menos duas velas válidas."
            message.contains("ordem cronológica", true) ->
                "Os registros precisam estar em ordem cronológica."
            message.contains("arquivos demais", true) || message.contains("limite seguro", true) ->
                "O arquivo excede o limite seguro de importação."
            message.contains("caminho inseguro", true) ->
                "O ZIP contém uma estrutura de pastas não permitida."
            message.contains("Nenhum CSV válido", true) || message.contains("não contém velas válidas", true) ->
                "Nenhum dado de vela válido foi encontrado."
            error is IllegalArgumentException -> "Os dados informados são inválidos ou incompatíveis."
            else -> "Não foi possível concluir a operação. Verifique os dados e tente novamente."
        }
    }

    fun selectDataset(id: String) { _selectedDataset.value = id }
    fun deleteDataset(id: String) { viewModelScope.launch { runCatching { repository.deleteDataset(id) }.onSuccess { if (_selectedDataset.value == id) _selectedDataset.value = null }.onFailure { _importState.value = OperationState(error = friendlyError(it)) } } }

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
        val validation = validateBacktest(options)
        if (validation != null) return failBacktest(validation)
        val id = _selectedDataset.value ?: return failBacktest("Selecione um conjunto de dados")
        backtestJob?.cancel()
        backtestJob = viewModelScope.launch {
            _backtestState.value = OperationState(true, .05f, "Carregando dados em blocos…")
            runCatching {
                val candles = repository.loadCandles(id)
                _backtestState.value = OperationState(true, .35f, "Calculando sinais sem look-ahead…")
                val result = withContext(Dispatchers.Default) {
                    val direction = if (options.market == Market.BINARY_OPTIONS) options.direction
                        else if (options.direction == Direction.PUT || options.direction == Direction.SHORT) Direction.SHORT else Direction.LONG
                    val signals = SignalEngine.wickSignals(candles, direction)
                    val backtest = if (options.market == Market.BINARY_OPTIONS)
                        BacktestEngine.binaryResult(candles, signals, options.expiration, options.payout)
                    else BacktestEngine.forexResult(candles, signals, ExitRule(options.stopLoss, options.takeProfit, options.trailing, options.bars), options.cost)
                    val split = (candles.size * .7).toInt()
                    fun evaluate(entries: List<Pair<Int, Direction>>, expiration: Int = options.expiration) =
                        if (options.market == Market.BINARY_OPTIONS) BacktestEngine.binaryResult(candles, entries, expiration, options.payout)
                        else BacktestEngine.forexResult(candles, entries, ExitRule(options.stopLoss, options.takeProfit, options.trailing, options.bars), options.cost)
                    val horizon = if (options.market == Market.BINARY_OPTIONS) options.expiration else options.bars
                    val insEntries = signals.filter { it.first + horizon < split }
                    val oosEntries = signals.filter { it.first > split }
                    val ins = evaluate(insEntries).metrics.netProfit
                    val oos = evaluate(oosEntries).metrics.netProfit
                    val metadata = datasets.value.firstOrNull { it.id == id }
                    val expiry = if (options.market == Market.BINARY_OPTIONS) (1..5).map { value ->
                        Bucket("${metadata?.timeframeMinutes ?: 1}m×$value", evaluate(signals, value).metrics.netProfit, signals.size)
                    } else emptyList()
                    val name = metadata?.symbol ?: "LOCAL"
                    _analysis.value = QuantAnalysis(
                        ChartAnalytics.wickBuckets(ChartAnalytics.wickOutcomes(candles, horizon)),
                        listOf(Bucket("Dentro", ins, insEntries.size), Bucket("Fora", oos, oosEntries.size)),
                        expiry, listOf(Bucket(name, backtest.metrics.netProfit, backtest.metrics.trades)),
                        ChartAnalytics.dayHourHeatmap(backtest.trades),
                        MonteCarlo.analyze(backtest.trades, simulations = 500, seed = 42)
                    )
                    backtest
                }
                _backtestState.value = OperationState(true, .85f, "Persistindo resultado…")
                repository.saveBacktest(id, options.market, result)
                result
            }.onSuccess {
                _result.value = it
                _backtestState.value = OperationState(message = "Teste histórico concluído")
            }.onFailure {
                if (it is kotlinx.coroutines.CancellationException) _backtestState.value = OperationState(message = "Teste histórico cancelado")
                else failBacktest(friendlyError(it))
            }
        }
    }

    fun runResearch(options: ResearchOptions = ResearchOptions()) {
        refreshDeviceHealth()
        val health = _deviceHealth.value
        if (options.intensive && health.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return failResearch("O aparelho está muito quente. Aguarde a temperatura baixar antes da pesquisa intensiva.")
        if (options.intensive && !health.charging && (health.batteryPercent ?: 100) < 20) return failResearch("A bateria está abaixo de 20%. Conecte o carregador ou use o modo normal.")
        lastResearchOptions = options
        if (options.candidates !in 100..100_000) return failResearch("Escolha entre 100 e 100.000 candidatos.")
        if (options.minimumTrades !in 5..10_000) return failResearch("O mínimo de operações deve ficar entre 5 e 10.000.")
        if (!options.payout.isFinite() || options.payout <= 0.0 || options.payout > 2.0) return failResearch("O payout da pesquisa deve ficar entre 1% e 200%.")
        if (options.threads != null && options.threads !in 1..Runtime.getRuntime().availableProcessors().coerceAtLeast(1)) return failResearch("A quantidade de processadores selecionada não é válida neste aparelho.")
        if (options.memoryMb != null && options.memoryMb !in 64..2048) return failResearch("A memória reservada deve ficar entre 64 e 2.048 MB.")
        val budget = options.candidates
        val datasetId = _selectedDataset.value ?: return failResearch("Selecione um conjunto de dados")
        researchJob?.cancel()
        researchPaused = false
        researchJob = viewModelScope.launch {
            val runId = UUID.randomUUID().toString(); val started = System.currentTimeMillis()
            val dao = (getApplication<Application>() as AlcadaApplication).database.dao()
            dao.saveRun(ResearchRunEntity(runId, started, null, "RUNNING", 0, 42, "budget=$budget"))
            _researchState.value = OperationState(true, 0f, "Preparando pesquisa local…")
            runCatching {
                val candles = repository.loadCandles(datasetId)
                ResearchEngine().discover(candles, ResearchBudget(maxCandidates = budget, threads = options.threads ?: if (options.intensive) maxOf(2, Runtime.getRuntime().availableProcessors() - 1) else 2, memoryMb = options.memoryMb ?: if (options.intensive) 512 else 256, minimumTrades = minOf(options.minimumTrades, maxOf(5, candles.size / 50))), payout = options.payout) {
                    while (researchPaused) { kotlinx.coroutines.delay(150); kotlinx.coroutines.currentCoroutineContext().ensureActive() }
                }.collect { progress ->
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

    private fun validateBacktest(options: BacktestOptions): String? = when {
        options.market == Market.BINARY_OPTIONS && options.expiration <= 0 -> "A expiração precisa ser maior que zero."
        options.market == Market.BINARY_OPTIONS && (!options.payout.isFinite() || options.payout <= 0.0) -> "O payout precisa ser maior que zero."
        options.market != Market.BINARY_OPTIONS && (!options.stopLoss.isFinite() || options.stopLoss <= 0.0) -> "O stop loss precisa ser maior que zero."
        options.market != Market.BINARY_OPTIONS && (!options.takeProfit.isFinite() || options.takeProfit <= 0.0) -> "O take profit precisa ser maior que zero."
        options.market != Market.BINARY_OPTIONS && (!options.trailing.isFinite() || options.trailing <= 0.0) -> "O trailing stop precisa ser maior que zero."
        options.market != Market.BINARY_OPTIONS && options.bars <= 0 -> "O limite de velas precisa ser maior que zero."
        options.market != Market.BINARY_OPTIONS && (!options.cost.isFinite() || options.cost < 0.0) -> "O custo por operação não pode ser negativo."
        else -> null
    }

    fun pauseResearch() { if (researchJob?.isActive == true) { researchPaused = true; _researchState.value = _researchState.value.copy(paused = true, message = "Pesquisa pausada") } }
    fun resumeResearch() { if (researchJob?.isActive == true) { researchPaused = false; _researchState.value = _researchState.value.copy(paused = false, message = "Pesquisa retomada") } }
    fun cancelResearch() { researchPaused = false; researchJob?.cancel() }
    fun repeatResearch() { lastResearchOptions?.let(::runResearch) ?: failResearch("Inicie uma pesquisa antes de tentar repeti-la.") }
    fun cancelBacktest() { backtestJob?.cancel() }
    private fun failResearch(message: String) { _researchState.value = OperationState(error = message) }
    private fun failBacktest(message: String) { _backtestState.value = OperationState(error = message) }
}
