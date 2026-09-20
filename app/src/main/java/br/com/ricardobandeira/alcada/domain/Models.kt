package br.com.ricardobandeira.alcada.domain

import kotlin.math.abs

data class Candle(val epochMillis: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double = 0.0, val spread: Double = 0.0) {
    init {
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

data class EntryRule(val feature: String, val operator: String, val threshold: Double)
data class ExitRule(val stopLoss: Double? = null, val takeProfit: Double? = null, val trailingStop: Double? = null, val bars: Int? = null, val condition: EntryRule? = null)
data class StrategyDefinition(
    val id: String, val name: String, val market: Market, val symbol: String, val timeframeMinutes: Int,
    val direction: Direction, val entries: List<EntryRule>, val exit: ExitRule, val seed: Long
)
data class Trade(val entryTime: Long, val exitTime: Long, val pnl: Double, val won: Boolean)
data class BacktestResult(
    val metrics: BacktestMetrics,
    val trades: List<Trade>,
    val equity: List<Double>,
    val drawdown: List<Double>,
    val rollingWinRate: List<Double>
)
data class BacktestMetrics(
    val trades: Int, val wins: Int, val winRate: Double, val netProfit: Double, val profitFactor: Double,
    val expectancy: Double, val maxDrawdown: Double, val breakEvenWinRate: Double? = null
)
data class EvaluatedStrategy(val strategy: StrategyDefinition, val metrics: BacktestMetrics, val oosWinRate: Double, val robustness: Double, val status: ValidationStatus, val overfitWarning: Boolean)

data class ResearchBudget(val maxCandidates: Int = 10_000, val threads: Int = 2, val memoryMb: Int = 256, val seed: Long = 42, val minimumTrades: Int = 30) {
    init {
        require(maxCandidates > 0) { "A pesquisa precisa avaliar pelo menos um candidato." }
        require(threads > 0) { "A quantidade de processadores precisa ser positiva." }
        require(memoryMb >= 64) { "Reserve pelo menos 64 MB para a pesquisa." }
        require(minimumTrades > 0) { "O mínimo de operações precisa ser positivo." }
    }
}
