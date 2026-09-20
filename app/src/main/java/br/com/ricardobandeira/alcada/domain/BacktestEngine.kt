package br.com.ricardobandeira.alcada.domain

import kotlin.math.max

object BacktestEngine {
    fun binary(candles: List<Candle>, signals: List<Pair<Int, Direction>>, expirationBars: Int, payout: Double): BacktestMetrics {
        return binaryResult(candles, signals, expirationBars, payout).metrics
    }

    fun binaryResult(candles: List<Candle>, signals: List<Pair<Int, Direction>>, expirationBars: Int, payout: Double): BacktestResult {
        require(expirationBars > 0) { "O vencimento precisa ser positivo." }
        require(payout.isFinite() && payout >= .01 && payout <= 2.0) { "O retorno precisa ficar entre 1% e 200%." }
        require(signals.all { it.second == Direction.CALL || it.second == Direction.PUT }) { "Opções binárias aceitam apenas CALL ou PUT." }
        require(candles.zipWithNext().all { (a, b) -> a.epochMillis <= b.epochMillis }) { "As velas precisam estar em ordem cronológica." }
        val trades = signals.mapNotNull { (i, direction) ->
            if (i < 0 || i + expirationBars >= candles.size) null else {
                val delta = candles[i + expirationBars].close - candles[i].close
                val won = (direction == Direction.CALL && delta > 0) || (direction == Direction.PUT && delta < 0)
                Trade(candles[i].epochMillis, candles[i + expirationBars].epochMillis, if (won) payout else -1.0, won)
            }
        }
        return result(trades, 1.0 / (1.0 + payout))
    }

    fun forex(candles: List<Candle>, entries: List<Pair<Int, Direction>>, exit: ExitRule, cost: Double): BacktestMetrics {
        return forexResult(candles, entries, exit, cost).metrics
    }

    fun forexResult(candles: List<Candle>, entries: List<Pair<Int, Direction>>, exit: ExitRule, cost: Double): BacktestResult {
        require(cost.isFinite() && cost >= 0.0) { "O custo por operação precisa ser um número válido e não negativo." }
        require(exit.bars == null || exit.bars > 0) { "O limite de velas da saída precisa ser positivo." }
        require(exit.stopLoss == null || (exit.stopLoss.isFinite() && exit.stopLoss > 0.0)) { "O stop loss precisa ser positivo." }
        require(exit.takeProfit == null || (exit.takeProfit.isFinite() && exit.takeProfit > 0.0)) { "O take profit precisa ser positivo." }
        require(exit.trailingStop == null || (exit.trailingStop.isFinite() && exit.trailingStop > 0.0)) { "O trailing stop precisa ser positivo." }
        require(candles.zipWithNext().all { (a, b) -> a.epochMillis <= b.epochMillis }) { "As velas precisam estar em ordem cronológica." }
        val trades = entries.mapNotNull { (index, direction) ->
            if (index !in 0 until candles.lastIndex || direction !in listOf(Direction.LONG, Direction.SHORT)) return@mapNotNull null
            val entry = candles[index].close
            var best = entry; var result: Pair<Int, Double>? = null
            val last = minOf(candles.lastIndex, index + (exit.bars ?: 100))
            for (i in index + 1..last) {
                val c = candles[i]; val sign = if (direction == Direction.LONG) 1 else -1
                best = if (sign > 0) max(best, c.high) else minOf(best, c.low)
                val favorable = if (sign > 0) c.high - entry else entry - c.low
                val adverse = if (sign > 0) entry - c.low else c.high - entry
                val trailing = exit.trailingStop?.let { if (sign > 0) best - c.low >= it else c.high - best >= it } ?: false
                if (exit.stopLoss?.let { adverse >= it } == true) { result = i to -exit.stopLoss; break }
                if (exit.takeProfit?.let { favorable >= it } == true) { result = i to exit.takeProfit; break }
                if (trailing) { result = i to ((if (sign > 0) best - entry else entry - best) - exit.trailingStop!!); break }
                if (exit.condition?.let { matches(it, c, candles.getOrNull(i - 1)) } == true) {
                    result = i to ((c.close - entry) * sign); break
                }
            }
            val (out, gross) = result ?: (last to ((candles[last].close - entry) * if (direction == Direction.LONG) 1 else -1))
            val pnl = gross - cost - candles[index].spread
            Trade(candles[index].epochMillis, candles[out].epochMillis, pnl, pnl > 0)
        }
        return result(trades)
    }

    fun metrics(trades: List<Trade>, breakEven: Double? = null): BacktestMetrics {
        require(breakEven == null || (breakEven.isFinite() && breakEven in 0.0..1.0)) { "A taxa de equilíbrio precisa ficar entre 0% e 100%." }
        val grossProfit = trades.filter { it.pnl > 0 }.sumOf { it.pnl }
        val grossLoss = -trades.filter { it.pnl < 0 }.sumOf { it.pnl }
        var equity = 0.0; var peak = 0.0; var drawdown = 0.0
        trades.forEach { equity += it.pnl; peak = max(peak, equity); drawdown = max(drawdown, peak - equity) }
        val wins = trades.count { it.won }
        return BacktestMetrics(trades.size, wins, if (trades.isEmpty()) 0.0 else wins.toDouble() / trades.size,
            equity, if (grossLoss == 0.0) if (grossProfit > 0) Double.POSITIVE_INFINITY else 0.0 else grossProfit / grossLoss,
            if (trades.isEmpty()) 0.0 else equity / trades.size, drawdown, breakEven)
    }

    fun result(trades: List<Trade>, breakEven: Double? = null): BacktestResult {
        var current = 0.0
        var peak = 0.0
        val equity = trades.map { current += it.pnl; current }
        val drawdown = equity.map { value -> peak = max(peak, value); peak - value }
        val rolling = trades.indices.map { index ->
            val start = maxOf(0, index - 19)
            trades.subList(start, index + 1).count(Trade::won).toDouble() / (index - start + 1)
        }
        return BacktestResult(metrics(trades, breakEven), trades, equity, drawdown, rolling)
    }

    private fun matches(rule: EntryRule, candle: Candle, previous: Candle?): Boolean {
        require(rule.threshold.isFinite()) { "O limite da regra precisa ser um número válido." }
        val feature = FeatureEngine.candle(candle, previous)
        val value = when (rule.feature) {
            "wickBodyRatio" -> feature.wickBodyRatio
            "upperWick" -> feature.upperWick
            "lowerWick" -> feature.lowerWick
            "momentum" -> feature.momentum
            "gap" -> feature.gap
            "acceleration" -> feature.acceleration
            "range" -> feature.range
            "bodyRangeRatio" -> feature.bodyRangeRatio
            "closeLocation" -> feature.closeLocation
            else -> return false
        }
        return when (rule.operator) { ">" -> value > rule.threshold; ">=" -> value >= rule.threshold; "<" -> value < rule.threshold; "<=" -> value <= rule.threshold; else -> false }
    }
}
