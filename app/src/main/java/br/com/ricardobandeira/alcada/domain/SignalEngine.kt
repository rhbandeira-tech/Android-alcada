package br.com.ricardobandeira.alcada.domain

/** Produces entries from information available at, or before, each candle. */
object SignalEngine {
    fun wickSignals(candles: List<Candle>, direction: Direction, minimumRatio: Double = 1.5, limit: Int = 20_000): List<Pair<Int, Direction>> =
        (1 until candles.lastIndex).asSequence().filter { index ->
            val feature = FeatureEngine.candle(candles[index], candles[index - 1])
            feature.wickBodyRatio >= minimumRatio &&
                if (direction == Direction.CALL || direction == Direction.LONG) feature.lowerWick >= feature.upperWick
                else feature.upperWick > feature.lowerWick
        }.map { it to direction }.take(limit).toList()
}
