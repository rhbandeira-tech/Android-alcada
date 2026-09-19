package br.com.ricardobandeira.alcada.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Random

data class ResearchProgress(val evaluated: Int, val accepted: Int, val best: EvaluatedStrategy?, val finished: Boolean = false)

/** Evolutionary, bounded search: candidates are generated and evaluated one-by-one, never materialized. */
class ResearchEngine {
    fun discover(candles: List<Candle>, budget: ResearchBudget): Flow<ResearchProgress> = flow {
        require(candles.size >= 20)
        val random = Random(budget.seed)
        var accepted = 0; var best: EvaluatedStrategy? = null
        repeat(budget.maxCandidates) { n ->
            currentCoroutineContext().ensureActive()
            val ratio = 0.5 + random.nextDouble() * 4.5
            val direction = if (random.nextBoolean()) Direction.CALL else Direction.PUT
            val expiration = 1 + random.nextInt(8)
            // Signals use only the current/past candle; the future is consulted exclusively by the backtest.
            val signals = (1 until candles.size - expiration).asSequence()
                .filter { FeatureEngine.candle(candles[it], candles[it - 1]).wickBodyRatio >= ratio }
                .map { it to direction }.take(5_000).toList()
            val split = (candles.size * .7).toInt()
            val ins = BacktestEngine.binary(candles, signals.filter { it.first < split }, expiration, .8)
            val oos = BacktestEngine.binary(candles, signals.filter { it.first >= split }, expiration, .8)
            if (ins.trades >= budget.minimumTrades && ins.profitFactor > 1.0) {
                accepted++
                val gap = kotlin.math.abs(ins.winRate - oos.winRate)
                val robustness = (1.0 - gap * 2).coerceIn(0.0, 1.0) * (oos.trades / 30.0).coerceAtMost(1.0)
                val strategy = StrategyDefinition("${budget.seed}-$n", "Pavio ${"%.2f".format(ratio)}×", Market.BINARY_OPTIONS,
                    "dataset", 1, direction, listOf(EntryRule("wickBodyRatio", ">=", ratio)), ExitRule(bars = expiration), budget.seed)
                val result = EvaluatedStrategy(strategy, ins, oos.winRate, robustness,
                    if (robustness >= .65 && oos.winRate > (ins.breakEvenWinRate ?: 1.0)) ValidationStatus.VALIDATED else ValidationStatus.FRAGILE,
                    gap > .15 || oos.trades < budget.minimumTrades)
                val score = { e: EvaluatedStrategy -> e.robustness * .5 + e.metrics.expectancy.coerceIn(-1.0, 1.0) * .3 - e.metrics.maxDrawdown * .02 }
                if (best == null || score(result) > score(best!!)) best = result
            }
            if (n % 25 == 0) emit(ResearchProgress(n + 1, accepted, best))
        }
        emit(ResearchProgress(budget.maxCandidates, accepted, best, true))
    }.flowOn(Dispatchers.Default)
}

