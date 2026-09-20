package br.com.ricardobandeira.alcada.domain

import java.util.Random
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.ceil

data class MonteCarloSummary(
    val simulations: Int,
    val medianNetProfit: Double,
    val p05NetProfit: Double,
    val p95MaxDrawdown: Double,
    val profitableShare: Double,
    val blockSize: Int = 1
)

object MonteCarlo {
    internal fun nearestRank(values: DoubleArray, p: Double): Double {
        require(values.isNotEmpty()) { "A amostra de percentil não pode estar vazia." }
        require(p.isFinite() && p in 0.0..1.0) { "O percentil precisa ficar entre 0 e 1." }
        val sorted = values.sortedArray()
        return sorted[(ceil(sorted.size * p).toInt() - 1).coerceIn(sorted.indices)]
    }
    /**
     * Moving-block bootstrap. Sampling short contiguous runs preserves part of the
     * win/loss clustering that an independent trade bootstrap destroys.
     */
    fun analyze(trades: List<Trade>, simulations: Int = 500, seed: Long = 42, blockSize: Int? = null): MonteCarloSummary {
        require(simulations in 1..100_000) { "As simulações devem ficar entre 1 e 100.000." }
        require(blockSize == null || blockSize > 0) { "O tamanho do bloco precisa ser positivo." }
        require(trades.zipWithNext().all { (a, b) -> a.entryTime <= b.entryTime }) { "As operações precisam estar em ordem cronológica." }
        require(trades.all { it.pnl.isFinite() }) { "As operações precisam ter resultados numéricos válidos." }
        if (trades.isEmpty()) return MonteCarloSummary(simulations, 0.0, 0.0, 0.0, 0.0, 1)
        val block = (blockSize ?: sqrt(trades.size.toDouble()).toInt().coerceIn(2, 12)).coerceIn(1, trades.size)
        val random = Random(seed)
        val profits = DoubleArray(simulations)
        val drawdowns = DoubleArray(simulations)
        repeat(simulations) { s ->
            var equity = 0.0
            var peak = 0.0
            var maxDrawdown = 0.0
            var sampled = 0
            while (sampled < trades.size) {
                val start = random.nextInt(trades.size)
                val take = minOf(block, trades.size - sampled)
                repeat(take) { offset ->
                    val trade = trades[(start + offset) % trades.size]
                    equity += trade.pnl
                    peak = max(peak, equity)
                    maxDrawdown = max(maxDrawdown, peak - equity)
                }
                sampled += take
            }
            profits[s] = equity
            drawdowns[s] = maxDrawdown
        }
        profits.sort()
        drawdowns.sort()
        fun percentile(values: DoubleArray, p: Double) = nearestRank(values, p)
        return MonteCarloSummary(
            simulations,
            percentile(profits, .50),
            percentile(profits, .05),
            percentile(drawdowns, .95),
            profits.count { it > 0.0 }.toDouble() / simulations,
            block
        )
    }
}
