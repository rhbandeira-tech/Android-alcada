package br.com.ricardobandeira.alcada.domain

import kotlin.math.abs
import kotlin.math.sqrt

data class CandleFeatures(val body: Double, val upperWick: Double, val lowerWick: Double, val wickBodyRatio: Double, val wickRangeRatio: Double, val range: Double, val momentum: Double, val gap: Double, val bodyRangeRatio: Double, val closeLocation: Double)

object FeatureEngine {
    fun candle(current: Candle, previous: Candle? = null): CandleFeatures {
        val epsilon = 1e-12
        val wick = current.upperWick + current.lowerWick
        return CandleFeatures(current.body, current.upperWick, current.lowerWick,
            wick / maxOf(current.body, epsilon), wick / maxOf(current.range, epsilon), current.range,
            previous?.let { current.close - it.close } ?: 0.0, previous?.let { current.open - it.close } ?: 0.0,
            current.body / maxOf(current.range, epsilon), (current.close - current.low) / maxOf(current.range, epsilon))
    }

    fun atr(candles: List<Candle>, period: Int, endExclusive: Int = candles.size): Double {
        require(period > 0 && endExclusive in 0..candles.size)
        val start = maxOf(1, endExclusive - period)
        if (endExclusive <= 1) return 0.0
        return (start until endExclusive).map { i ->
            val c = candles[i]; val p = candles[i - 1]
            maxOf(c.high - c.low, abs(c.high - p.close), abs(c.low - p.close))
        }.average()
    }

    fun volatility(candles: List<Candle>, period: Int, endExclusive: Int = candles.size): Double {
        require(period > 0 && endExclusive in 0..candles.size)
        if (endExclusive <= 1) return 0.0
        val returns = (maxOf(1, endExclusive - period) until endExclusive).mapNotNull { i ->
            val previous = candles[i - 1].close
            if (previous == 0.0) null else (candles[i].close / previous - 1.0).takeIf { it.isFinite() }
        }
        if (returns.size < 2) return 0.0
        val mean = returns.average()
        return sqrt(returns.sumOf { (it - mean) * (it - mean) } / (returns.size - 1))
    }
}

