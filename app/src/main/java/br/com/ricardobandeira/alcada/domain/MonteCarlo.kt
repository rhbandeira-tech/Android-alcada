package br.com.ricardobandeira.alcada.domain

import java.util.Random
import kotlin.math.max

data class MonteCarloSummary(val simulations: Int, val medianNetProfit: Double, val p05NetProfit: Double, val p95MaxDrawdown: Double, val profitableShare: Double)

object MonteCarlo {
    fun analyze(trades: List<Trade>, simulations: Int = 500, seed: Long = 42): MonteCarloSummary {
        require(simulations > 0)
        if (trades.isEmpty()) return MonteCarloSummary(simulations, 0.0, 0.0, 0.0, 0.0)
        val random = Random(seed)
        val profits = DoubleArray(simulations)
        val drawdowns = DoubleArray(simulations)
        repeat(simulations) { s ->
            var equity = 0.0; var peak = 0.0; var maxDrawdown = 0.0
            repeat(trades.size) {
                equity += trades[random.nextInt(trades.size)].pnl
                peak = max(peak, equity)
                maxDrawdown = max(maxDrawdown, peak - equity)
            }
            profits[s] = equity; drawdowns[s] = maxDrawdown
        }
        profits.sort(); drawdowns.sort()
        fun percentile(values: DoubleArray, p: Double) = values[(values.lastIndex * p).toInt().coerceIn(values.indices)]
        return MonteCarloSummary(simulations, percentile(profits, .50), percentile(profits, .05), percentile(drawdowns, .95), profits.count { it > 0.0 }.toDouble() / simulations)
    }
}
