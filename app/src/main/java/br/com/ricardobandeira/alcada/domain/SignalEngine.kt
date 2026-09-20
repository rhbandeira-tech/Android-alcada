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

    fun filteredWickSignals(
        candles: List<Candle>,
        direction: Direction,
        minimumWickBodyRatio: Double = 1.5,
        maximumBodyRangeRatio: Double = .55,
        minimumDirectionalClose: Double = .60,
        limit: Int = 20_000
    ): List<Pair<Int, Direction>> {
        require(minimumWickBodyRatio >= 0.0)
        require(maximumBodyRangeRatio in 0.0..1.0)
        require(minimumDirectionalClose in .5..1.0)
        require(limit > 0)
        if (candles.size < 3) return emptyList()
        return (1 until candles.lastIndex).asSequence().filter { index ->
            val feature = FeatureEngine.candle(candles[index], candles[index - 1])
            val bullish = direction == Direction.CALL || direction == Direction.LONG
            val closeOk = if (bullish) feature.closeLocation >= minimumDirectionalClose
                else feature.closeLocation <= 1.0 - minimumDirectionalClose
            val wickOk = if (bullish) feature.lowerWick >= feature.upperWick
                else feature.upperWick > feature.lowerWick
            feature.wickBodyRatio >= minimumWickBodyRatio &&
                feature.bodyRangeRatio <= maximumBodyRangeRatio && closeOk && wickOk
        }.map { it to direction }.take(limit).toList()
    }
}
