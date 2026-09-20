package br.com.ricardobandeira.alcada.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Random

data class ResearchProgress(val evaluated: Int, val accepted: Int, val best: EvaluatedStrategy?, val leaders: List<EvaluatedStrategy> = emptyList(), val finished: Boolean = false)

/** Evolutionary, bounded search: candidates are generated and evaluated one-by-one, never materialized. */
class ResearchEngine {
    fun discover(candles: List<Candle>, budget: ResearchBudget): Flow<ResearchProgress> = flow {
        require(candles.size >= 20)
        val random = Random(budget.seed)
        var accepted = 0; var best: EvaluatedStrategy? = null
        val elite = mutableListOf<EvaluatedStrategy>()
        repeat(budget.maxCandidates) { n ->
            currentCoroutineContext().ensureActive()
            // After the initial population, mutate/recombine the current elite without retaining a huge population.
            val parent = if (elite.isEmpty()) best else elite[random.nextInt(elite.size)]
            val eliteRule = parent?.strategy?.entries?.firstOrNull()
            val ratio = if (eliteRule != null && n % 3 != 0) (eliteRule.threshold + random.nextGaussian() * .35).coerceIn(.25, 8.0) else 0.5 + random.nextDouble() * 4.5
            val direction = if (parent != null && n % 4 != 0) parent.strategy.direction else if (random.nextBoolean()) Direction.CALL else Direction.PUT
            val parentBodyLimit = parent?.strategy?.entries?.firstOrNull { it.feature == "bodyRangeRatio" }?.threshold
            val bodyLimit = if (parentBodyLimit != null && n % 3 != 0)
                (parentBodyLimit + random.nextGaussian() * .05).coerceIn(.15, .80)
            else .25 + random.nextDouble() * .45
            val storedClose = parent?.strategy?.entries?.firstOrNull { it.feature == "closeLocation" }?.threshold
            val parentClose = storedClose?.let { if (parent?.strategy?.direction == Direction.PUT) 1.0 - it else it }
            val closeLocation = if (parentClose != null && n % 3 != 0)
                (parentClose + random.nextGaussian() * .04).coerceIn(.50, .90)
            else .50 + random.nextDouble() * .35
            val eliteExpiry = parent?.strategy?.exit?.bars
            val expiration = if (eliteExpiry != null && n % 5 != 0) (eliteExpiry + random.nextInt(3) - 1).coerceIn(1, 12) else 1 + random.nextInt(8)
            // Signals use only the current/past candle; the future is consulted exclusively by the backtest.
            val signals = (1 until candles.size - expiration).asSequence()
                .filter {
                    val feature = FeatureEngine.candle(candles[it], candles[it - 1])
                    val directionalClose = if (direction == Direction.CALL) feature.closeLocation >= closeLocation
                        else feature.closeLocation <= 1.0 - closeLocation
                    feature.wickBodyRatio >= ratio && feature.bodyRangeRatio <= bodyLimit && directionalClose &&
                        if (direction == Direction.CALL) feature.lowerWick >= feature.upperWick else feature.upperWick > feature.lowerWick
                }
                .map { it to direction }.take(5_000).toList()
            val split = (candles.size * .7).toInt()
            // Purge the boundary by the full outcome horizon so no trade can leak future candles across IS/OOS.
            val insSignals = signals.filter { it.first + expiration < split }
            val oosSignals = signals.filter { it.first >= split }
            val ins = BacktestEngine.binary(candles, insSignals, expiration, .8)
            val oos = BacktestEngine.binary(candles, oosSignals, expiration, .8)
            if (ins.trades >= budget.minimumTrades && oos.trades >= budget.minimumTrades && ins.profitFactor > 1.0) {
                accepted++
                val gap = kotlin.math.abs(ins.winRate - oos.winRate)
                val sampleFactor = (oos.trades.toDouble() / budget.minimumTrades.coerceAtLeast(1)).coerceAtMost(1.0)
                val foldSize = candles.size / 4
                val forwardTests = (1..3).map { fold ->
                    val start = fold * foldSize
                    val end = if (fold == 3) candles.size else (fold + 1) * foldSize
                    BacktestEngine.binary(
                        candles,
                        signals.filter { it.first >= start && it.first + expiration < end },
                        expiration,
                        .8
                    )
                }
                val stableFolds = forwardTests.count {
                    it.trades >= maxOf(3, budget.minimumTrades / 3) && it.expectancy > 0.0 && it.profitFactor > 1.0
                }
                val stability = stableFolds / forwardTests.size.toDouble()
                val robustness = ((1.0 - gap * 2).coerceIn(0.0, 1.0) * .55 + stability * .35 + sampleFactor * .10).coerceIn(0.0, 1.0)
                val strategy = StrategyDefinition("${budget.seed}-$n", "Pavio ${"%.2f".format(ratio)}×", Market.BINARY_OPTIONS,
                    "dataset", 1, direction, listOf(
                        EntryRule("wickBodyRatio", ">=", ratio),
                        EntryRule("bodyRangeRatio", "<=", bodyLimit),
                        EntryRule("closeLocation", if (direction == Direction.CALL) ">=" else "<=", if (direction == Direction.CALL) closeLocation else 1.0 - closeLocation)
                    ), ExitRule(bars = expiration), budget.seed)
                val result = EvaluatedStrategy(strategy, ins, oos.winRate, robustness,
                    if (robustness >= .65 && oos.winRate > (ins.breakEvenWinRate ?: 1.0)) ValidationStatus.VALIDATED else ValidationStatus.FRAGILE,
                    gap > .15 || oos.trades < budget.minimumTrades || oos.profitFactor <= 1.0 || stability < .5)
                val score = { e: EvaluatedStrategy -> e.robustness * .5 + e.metrics.expectancy.coerceIn(-1.0, 1.0) * .3 - e.metrics.maxDrawdown * .02 }
                if (best == null || score(result) > score(best!!)) best = result
                elite += result
                elite.sortByDescending(score)
                if (elite.size > 24) elite.removeAt(elite.lastIndex)
            }
            if (n % 25 == 0) emit(ResearchProgress(n + 1, accepted, best, elite.toList()))
        }
        emit(ResearchProgress(budget.maxCandidates, accepted, best, elite.toList(), true))
    }.flowOn(Dispatchers.Default)
}
