package br.com.ricardobandeira.alcada.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Random

data class ResearchProgress(val evaluated: Int, val accepted: Int, val best: EvaluatedStrategy?, val leaders: List<EvaluatedStrategy> = emptyList(), val finished: Boolean = false)

/** Evolutionary, bounded search. Resource budget is honored and candidate batches remain small. */
class ResearchEngine {
    fun discover(candles: List<Candle>, budget: ResearchBudget, payout: Double = .85, checkpoint: suspend () -> Unit = {}): Flow<ResearchProgress> = flow {
        require(payout.isFinite() && payout > 0.0) { "O payout precisa ser maior que zero." }
        require(candles.size >= 20) { "A pesquisa precisa de pelo menos 20 velas." }
        require(candles.zipWithNext().all { (a, b) -> a.epochMillis <= b.epochMillis }) { "As velas precisam estar em ordem cronológica." }
        val random = Random(budget.seed)
        val binaryPayout = payout
        val workerCount = budget.threads.coerceIn(1, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        // workerCount also defines the bounded batch target; candidate state remains deterministic.
        val batchSize = minOf(workerCount * 4, 64)
        val memoryBound = (budget.memoryMb.coerceAtLeast(64) * 1024L * 1024L / 64_000L).toInt().coerceAtLeast(workerCount)
        val effectiveBatch = minOf(batchSize, memoryBound).coerceAtLeast(1)
        val evaluationSlots = Semaphore(workerCount)
        suspend fun parallelPreflight(source: List<Candle>, factor: Int): Boolean = coroutineScope {
            val checks = listOf<suspend () -> Boolean>(
                { source.size >= 20 },
                { source.zipWithNext().all { (a, b) -> a.epochMillis <= b.epochMillis } },
                { factor in 1..5 }
            )
            checks.map { check -> async(Dispatchers.Default) { evaluationSlots.withPermit { check() } } }.awaitAll().all { it }
        }
        require(budget.memoryMb >= 64) { "A pesquisa precisa de pelo menos 64 MB de orçamento de memória." }
        var accepted = 0; var best: EvaluatedStrategy? = null
        val elite = mutableListOf<EvaluatedStrategy>()
        repeat(budget.maxCandidates) { n ->
            currentCoroutineContext().ensureActive()
            checkpoint()
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
            val timeframeFactor = when { n % 11 == 0 -> 5; n % 7 == 0 -> 3; else -> 1 }
            val researchCandles = if (timeframeFactor == 1) candles else FeatureEngine.aggregate(candles, timeframeFactor)
            if (!parallelPreflight(researchCandles, timeframeFactor)) return@repeat
            // SignalEngine centralizes the no-lookahead entry rules used by research and manual tests.
            currentCoroutineContext().ensureActive()
            val sessionGate = n % 6
            val maxSpreadRatio = if (n % 5 == 0) .10 + random.nextDouble() * .30 else null
            val minAtrRatio = if (n % 7 == 0) .35 + random.nextDouble() * 1.25 else null
            val sequenceGate = if (n % 9 == 0) 2 + random.nextInt(3) else null
            val supportResistanceGate = if (n % 10 == 0) .15 + random.nextDouble() * .35 else null
            val accelerationGate = if (n % 8 == 0) random.nextDouble() * .75 else null
            val rawSignals = SignalEngine.filteredWickSignals(
                candles = researchCandles,
                direction = direction,
                minimumWickBodyRatio = ratio,
                maximumBodyRangeRatio = bodyLimit,
                minimumDirectionalClose = closeLocation,
                limit = 5_000
            )
            val spreadFiltered = maxSpreadRatio?.let { maximum -> rawSignals.filter { (index, _) -> FeatureEngine.spreadRangeRatio(researchCandles[index]) <= maximum } } ?: rawSignals
            val volatilityFiltered = minAtrRatio?.let { minimum -> spreadFiltered.filter { (index, _) -> index > 14 && FeatureEngine.atrRangeRatio(researchCandles, 14, index + 1) >= minimum } } ?: spreadFiltered
            val sequenceFiltered = sequenceGate?.let { minimum -> volatilityFiltered.filter { (index, _) -> kotlin.math.abs(FeatureEngine.candleColorSequence(researchCandles, index + 1)) >= minimum } } ?: volatilityFiltered
            val levelFiltered = supportResistanceGate?.let { maximumFraction -> sequenceFiltered.filter { (index, _) ->
                if (index < 20) false else {
                    val (supportDistance, resistanceDistance) = FeatureEngine.supportResistanceDistance(researchCandles, 20, index + 1)
                    val range = researchCandles[index].range.coerceAtLeast(1e-12)
                    val directionalDistance = if (direction == Direction.CALL) resistanceDistance else supportDistance
                    directionalDistance / range <= maximumFraction
                }
            } } ?: sequenceFiltered
            val accelerationFiltered = accelerationGate?.let { minimum -> levelFiltered.filter { (index, _) ->
                if (index < 2) false else kotlin.math.abs(FeatureEngine.candle(researchCandles[index], researchCandles[index - 1], researchCandles[index - 2]).acceleration) >= researchCandles[index].range * minimum
            } } ?: levelFiltered
            val signals = if (sessionGate == 0) accelerationFiltered else accelerationFiltered.filter { (index, _) ->
                val hour = FeatureEngine.sessionHour(researchCandles[index].epochMillis)
                when (sessionGate) { 1 -> hour in 0..6; 2 -> hour in 7..12; 3 -> hour in 13..20; else -> true }
            }
            val split = (researchCandles.size * .7).toInt()
            // Purge the boundary by the full outcome horizon so no trade can leak future candles across IS/OOS.
            val insSignals = signals.filter { it.first + expiration < split }
            val oosSignals = signals.filter { it.first >= split }
            currentCoroutineContext().ensureActive()
            checkpoint()
            val ins = BacktestEngine.binary(researchCandles, insSignals, expiration, binaryPayout)
            val oosResult = BacktestEngine.binaryResult(researchCandles, oosSignals, expiration, binaryPayout)
            val oos = oosResult.metrics
            if (ins.trades >= budget.minimumTrades && oos.trades >= budget.minimumTrades && ins.profitFactor > 1.0) {
                accepted++
                val gap = kotlin.math.abs(ins.winRate - oos.winRate)
                val sampleFactor = (oos.trades.toDouble() / budget.minimumTrades.coerceAtLeast(1)).coerceAtMost(1.0)
                val foldSize = researchCandles.size / 4
                val forwardTests = (1..3).map { fold ->
                    val start = fold * foldSize
                    val end = if (fold == 3) researchCandles.size else (fold + 1) * foldSize
                    BacktestEngine.binary(
                        researchCandles,
                        signals.filter { it.first >= start && it.first + expiration < end },
                        expiration,
                        binaryPayout
                    )
                }
                val stableFolds = forwardTests.count {
                    it.trades >= maxOf(3, budget.minimumTrades / 3) && it.expectancy > 0.0 && it.profitFactor > 1.0
                }
                val stability = stableFolds / forwardTests.size.toDouble()
                val foldExpectancies = forwardTests.filter { it.trades > 0 }.map { it.expectancy }
                val regimeDispersion = if (foldExpectancies.size < 2) 1.0 else {
                    val mean = foldExpectancies.average()
                    val variance = foldExpectancies.sumOf { (it - mean) * (it - mean) } / foldExpectancies.size
                    kotlin.math.sqrt(variance) / (kotlin.math.abs(mean) + .05)
                }
                val stabilityPenalty = (1.0 / (1.0 + regimeDispersion)).coerceIn(0.0, 1.0)
                val robustness = ((1.0 - gap * 2).coerceIn(0.0, 1.0) * .45 + stability * .30 + stabilityPenalty * .15 + sampleFactor * .10).coerceIn(0.0, 1.0)
                currentCoroutineContext().ensureActive()
                checkpoint()
                val monteCarlo = MonteCarlo.analyze(oosResult.trades, simulations = 200, seed = budget.seed + n)
                val tailPenalty = if (monteCarlo.p05NetProfit > 0.0) 1.0 else .75
                val adjustedRobustness = (robustness * tailPenalty).coerceIn(0.0, 1.0)
                val strategy = StrategyDefinition("${budget.seed}-$n", "Pavio ${"%.2f".format(java.util.Locale.US, ratio)}×", Market.BINARY_OPTIONS,
                    "dataset", timeframeFactor, direction, listOf(
                        EntryRule("wickBodyRatio", ">=", ratio),
                        EntryRule("bodyRangeRatio", "<=", bodyLimit),
                        EntryRule("closeLocation", if (direction == Direction.CALL) ">=" else "<=", if (direction == Direction.CALL) closeLocation else 1.0 - closeLocation)
                    ) + listOfNotNull(maxSpreadRatio?.let { EntryRule("spreadRangeRatio", "<=", it) }) + listOfNotNull(if (sessionGate in 1..3) EntryRule("sessionUtc", "==", sessionGate.toDouble()) else null) + listOfNotNull(minAtrRatio?.let { EntryRule("atrRangeRatio", ">=", it) }) + listOfNotNull(sequenceGate?.let { EntryRule("candleSequence", ">=", it.toDouble()) }) + listOfNotNull(supportResistanceGate?.let { EntryRule("levelDistanceRatio", "<=", it) }) + listOfNotNull(accelerationGate?.let { EntryRule("accelerationRangeRatio", ">=", it) }), ExitRule(bars = expiration), budget.seed)
                val result = EvaluatedStrategy(
                    strategy = strategy,
                    metrics = ins,
                    oosWinRate = oos.winRate,
                    robustness = adjustedRobustness,
                    status = if (adjustedRobustness >= .65 && oos.winRate > (ins.breakEvenWinRate ?: 1.0)) ValidationStatus.VALIDATED else ValidationStatus.FRAGILE,
                    overfitWarning = gap > .15 || oos.trades < budget.minimumTrades || oos.profitFactor <= 1.0 || stability < .5 || regimeDispersion > 2.0 || monteCarlo.p05NetProfit <= 0.0,
                    oosTrades = oos.trades,
                    oosExpectancy = oos.expectancy,
                    oosProfitFactor = oos.profitFactor,
                    stableForwardFolds = stableFolds,
                    forwardFolds = forwardTests.size,
                    monteCarloP05 = monteCarlo.p05NetProfit,
                    monteCarloP95Drawdown = monteCarlo.p95MaxDrawdown
                )
                val score = { e: EvaluatedStrategy -> e.robustness * .5 + e.metrics.expectancy.coerceIn(-1.0, 1.0) * .3 - e.metrics.maxDrawdown * .02 }
                if (best == null || score(result) > score(best!!)) best = result
                elite += result
                elite.sortByDescending(score)
                if (elite.size > 24) elite.removeAt(elite.lastIndex)
            }
            if (n % maxOf(25, effectiveBatch) == 0) emit(ResearchProgress(n + 1, accepted, best, elite.toList()))
        }
        emit(ResearchProgress(budget.maxCandidates, accepted, best, elite.toList(), true))
    }.flowOn(Dispatchers.Default)
}
