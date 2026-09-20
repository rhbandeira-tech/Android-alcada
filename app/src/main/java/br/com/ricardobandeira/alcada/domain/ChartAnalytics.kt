package br.com.ricardobandeira.alcada.domain

import java.time.Instant
import java.time.ZoneOffset

data class Bucket(val label: String, val value: Double, val samples: Int)
data class WickOutcome(val wickRatio: Double, val futureReturn: Double)

object ChartAnalytics {
    fun pnlDistribution(trades: List<Trade>, buckets: Int = 8): List<Bucket> {
        if (trades.isEmpty()) return emptyList()
        val min = trades.minOf(Trade::pnl); val max = trades.maxOf(Trade::pnl)
        val width = ((max - min) / buckets).takeIf { it > 0 } ?: 1.0
        return (0 until buckets).map { bucket ->
            val values = trades.filter { ((it.pnl - min) / width).toInt().coerceIn(0, buckets - 1) == bucket }
            Bucket("${"%.2f".format(min + bucket * width)}", values.size.toDouble(), values.size)
        }
    }

    fun performanceByHour(trades: List<Trade>): List<Bucket> = trades.groupBy {
        Instant.ofEpochMilli(it.entryTime).atZone(ZoneOffset.UTC).hour
    }.toSortedMap().map { (hour, values) -> Bucket("%02dh".format(hour), values.sumOf(Trade::pnl), values.size) }

    fun performanceByDay(trades: List<Trade>): List<Bucket> = trades.groupBy {
        Instant.ofEpochMilli(it.entryTime).atZone(ZoneOffset.UTC).dayOfWeek.value
    }.toSortedMap().map { (day, values) -> Bucket(listOf("", "Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")[day], values.sumOf(Trade::pnl), values.size) }

    fun wickOutcomes(candles: List<Candle>, horizon: Int): List<WickOutcome> =
        (0 until maxOf(0, candles.size - horizon)).map { index ->
            WickOutcome(FeatureEngine.candle(candles[index]).wickBodyRatio.coerceAtMost(20.0), candles[index + horizon].close / candles[index].close - 1.0)
        }

    fun wickBuckets(values: List<WickOutcome>, buckets: Int = 8): List<Bucket> = (0 until buckets).mapNotNull { bucket ->
        val from = bucket * 20.0 / buckets
        val to = (bucket + 1) * 20.0 / buckets
        val samples = values.filter { it.wickRatio >= from && (bucket == buckets - 1 || it.wickRatio < to) }
        samples.takeIf { it.isNotEmpty() }?.let { Bucket("${"%.1f".format(from)}×", it.map(WickOutcome::futureReturn).average(), it.size) }
    }

    fun dayHourHeatmap(trades: List<Trade>): List<Bucket> = trades.groupBy {
        val time = Instant.ofEpochMilli(it.entryTime).atZone(ZoneOffset.UTC)
        time.dayOfWeek.value to time.hour
    }.toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second }).map { (key, values) ->
        Bucket("${key.first}/${key.second}", values.sumOf(Trade::pnl), values.size)
    }
}
