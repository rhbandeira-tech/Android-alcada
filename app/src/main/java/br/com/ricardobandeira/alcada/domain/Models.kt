package br.com.ricardobandeira.alcada.domain

import kotlin.math.abs

data class Candle(val epochMillis: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double = 0.0, val spread: Double = 0.0) {
    init {
        require(epochMillis >= 0) { "O horário da vela não pode ser negativo." }
        require(listOf(open, high, low, close, volume, spread).all { it.isFinite() }) { "OHLC contém valor não finito." }
        require(high >= maxOf(open, close, low) && low <= minOf(open, close, high)) { "OHLC inválido" }
        require(volume >= 0.0) { "O volume não pode ser negativo." }
        require(spread >= 0.0) { "O spread não pode ser negativo." }
    }
    val body get() = abs(close - open)
    val range get() = high - low
    val upperWick get() = high - maxOf(open, close)
    val lowerWick get() = minOf(open, close) - low
    val bullish get() = close > open
}

enum class Market { FOREX, BINARY_OPTIONS, CRYPTO }
enum class Direction { LONG, SHORT, CALL, PUT }
enum class ValidationStatus { PENDING, VALIDATED, FRAGILE, REJECTED }
enum class Profile { CONSERVATIVE, MODERATE, AGGRESSIVE, EXPERIMENTAL }

data class EntryRule(val feature: String, val operator: String, val threshold: Double) {
    init {
        require(feature.isNotBlank()) { "A variável da regra não pode ficar vazia." }
        require(operator in setOf(">", ">=", "<", "<=", "==")) { "Operador de regra não suportado." }
        require(threshold.isFinite()) { "O limite da regra precisa ser um número válido." }
    }
}
data class ExitRule(val stopLoss: Double? = null, val takeProfit: Double? = null, val trailingStop: Double? = null, val bars: Int? = null, val condition: EntryRule? = null) {
    init {
        require(listOfNotNull(stopLoss, takeProfit, trailingStop).all { it.isFinite() && it > 0.0 }) { "Limites de saída precisam ser positivos e válidos." }
        require(bars == null || bars > 0) { "O limite de velas precisa ser positivo." }
    }
}
data class StrategyDefinition(
    val id: String, val name: String, val market: Market, val symbol: String, val timeframeMinutes: Int,
    val direction: Direction, val entries: List<EntryRule>, val exit: ExitRule, val seed: Long
) {
    init {
        require(id.isNotBlank() && name.isNotBlank()) { "A estratégia precisa de identificação e nome." }
        require(timeframeMinutes > 0) { "O período gráfico precisa ser positivo." }
        require(entries.isNotEmpty()) { "A estratégia precisa de pelo menos uma regra de entrada." }
        require(symbol.isNotBlank()) { "O ativo da estratégia não pode ficar vazio." }
    }
}
data class Trade(val entryTime: Long, val exitTime: Long, val pnl: Double, val won: Boolean) {
    init {
        require(exitTime >= entryTime) { "A saída não pode ocorrer antes da entrada." }
        require(pnl.isFinite()) { "O resultado da operação precisa ser finito." }
    }
}
data class BacktestResult(
    val metrics: BacktestMetrics,
    val trades: List<Trade>,
    val equity: List<Double>,
    val drawdown: List<Double>,
    val rollingWinRate: List<Double>
) {
    init {
        require(metrics.trades == trades.size) { "A quantidade de operações não corresponde às métricas." }
        require(equity.size == trades.size && drawdown.size == trades.size && rollingWinRate.size == trades.size) { "As séries do teste histórico estão inconsistentes." }
        require(equity.all { it.isFinite() } && drawdown.all { it.isFinite() && it >= 0.0 } && rollingWinRate.all { it.isFinite() && it in 0.0..1.0 }) { "As séries do teste histórico contêm valores inválidos." }
    }
}
data class BacktestMetrics(
    val trades: Int, val wins: Int, val winRate: Double, val netProfit: Double, val profitFactor: Double,
    val expectancy: Double, val maxDrawdown: Double, val breakEvenWinRate: Double? = null
) {
    init {
        require(trades >= 0 && wins in 0..trades) { "Contagem de operações inválida." }
        require(winRate.isFinite() && winRate in 0.0..1.0) { "A taxa de acerto precisa ficar entre 0% e 100%." }
        require(netProfit.isFinite() && expectancy.isFinite() && maxDrawdown.isFinite() && maxDrawdown >= 0.0) { "Métricas do teste histórico inválidas." }
        require(profitFactor >= 0.0 && !profitFactor.isNaN()) { "O fator de lucro é inválido." }
        require(breakEvenWinRate == null || (breakEvenWinRate.isFinite() && breakEvenWinRate in 0.0..1.0)) { "A taxa de equilíbrio é inválida." }
    }
}
data class EvaluatedStrategy(val strategy: StrategyDefinition, val metrics: BacktestMetrics, val oosWinRate: Double, val robustness: Double, val status: ValidationStatus, val overfitWarning: Boolean, val oosTrades: Int = 0, val oosExpectancy: Double = 0.0, val oosProfitFactor: Double = 0.0, val stableForwardFolds: Int = 0, val forwardFolds: Int = 0, val monteCarloP05: Double = 0.0, val monteCarloP95Drawdown: Double = 0.0) {
    init {
        require(oosWinRate.isFinite() && oosWinRate in 0.0..1.0) { "A taxa fora da amostra é inválida." }
        require(robustness.isFinite() && robustness in 0.0..1.0) { "A robustez precisa ficar entre 0% e 100%." }
        require(oosTrades >= 0 && oosExpectancy.isFinite() && oosProfitFactor >= 0.0 && !oosProfitFactor.isNaN()) { "Métricas fora da amostra inválidas." }
        require(forwardFolds >= 0 && stableForwardFolds in 0..forwardFolds) { "Validação temporal inconsistente." }
        require(monteCarloP05.isFinite() && monteCarloP95Drawdown.isFinite() && monteCarloP95Drawdown >= 0.0) { "Métricas de Monte Carlo inválidas." }
    }
}

data class ResearchBudget(val maxCandidates: Int = 10_000, val threads: Int = 2, val memoryMb: Int = 256, val seed: Long = 42, val minimumTrades: Int = 30) {
    init {
        require(maxCandidates in 1..1_000_000) { "A pesquisa deve avaliar entre 1 e 1.000.000 de candidatos." }
        require(threads in 1..256) { "A quantidade de processadores deve ficar entre 1 e 256." }
        require(memoryMb in 64..16_384) { "A memória reservada deve ficar entre 64 MB e 16 GB." }
        require(minimumTrades in 1..100_000) { "O mínimo de operações deve ficar entre 1 e 100.000." }
    }
}
