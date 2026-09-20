package br.com.ricardobandeira.alcada.domain

import kotlin.math.abs
import kotlin.math.sqrt

data class CandleFeatures(val body: Double, val upperWick: Double, val lowerWick: Double, val wickBodyRatio: Double, val wickRangeRatio: Double, val range: Double, val momentum: Double, val gap: Double, val bodyRangeRatio: Double, val closeLocation: Double, val acceleration: Double = 0.0)

object FeatureEngine {
    fun candle(current: Candle, previous: Candle? = null, beforePrevious: Candle? = null): CandleFeatures {
        val epsilon = 1e-12
        val wick = current.upperWick + current.lowerWick
        return CandleFeatures(current.body, current.upperWick, current.lowerWick,
            wick / maxOf(current.body, epsilon), wick / maxOf(current.range, epsilon), current.range,
            previous?.let { current.close - it.close } ?: 0.0, previous?.let { current.open - it.close } ?: 0.0,
            current.body / maxOf(current.range, epsilon), (current.close - current.low) / maxOf(current.range, epsilon),
            if (previous != null && beforePrevious != null) (current.close - previous.close) - (previous.close - beforePrevious.close) else 0.0)
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

    fun aggregate(candles: List<Candle>, factor: Int): List<Candle> {
        require(factor > 0) { "O fator do período gráfico deve ser positivo." }
        if (factor == 1) return candles
        return candles.chunked(factor).filter { it.size == factor }.map { group ->
            Candle(
                epochMillis = group.first().epochMillis,
                open = group.first().open,
                high = group.maxOf { it.high },
                low = group.minOf { it.low },
                close = group.last().close,
                volume = group.sumOf { it.volume },
                spread = group.map { it.spread }.average()
            )
        }
    }

    fun sessionHour(epochMillis: Long): Int =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneOffset.UTC).hour

    fun sessionLabel(epochMillis: Long): String = when (sessionHour(epochMillis)) {
        in 0..6 -> "Ásia"
        in 7..12 -> "Europa"
        in 13..20 -> "Américas"
        else -> "Transição"
    }

    fun candleColorSequence(candles: List<Candle>, endExclusive: Int = candles.size, maxLength: Int = 8): Int {
        require(maxLength > 0 && endExclusive in 1..candles.size)
        val last = candles[endExclusive - 1]
        val direction = last.close.compareTo(last.open)
        if (direction == 0) return 0
        var length = 0
        for (index in endExclusive - 1 downTo maxOf(0, endExclusive - maxLength)) {
            val candle = candles[index]
            if (candle.close.compareTo(candle.open) != direction) break
            length++
        }
        return if (direction > 0) length else -length
    }

    fun supportResistanceDistance(candles: List<Candle>, period: Int, endExclusive: Int = candles.size): Pair<Double, Double> {
        require(period > 0 && endExclusive in 1..candles.size)
        val start = maxOf(0, endExclusive - period)
        val window = candles.subList(start, endExclusive)
        val close = candles[endExclusive - 1].close
        val support = window.minOf { it.low }
        val resistance = window.maxOf { it.high }
        return (close - support).coerceAtLeast(0.0) to (resistance - close).coerceAtLeast(0.0)
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

