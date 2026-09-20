package br.com.ricardobandeira.alcada.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAnalyticsTest {
    @Test fun `equity drawdown and rolling rate use real trades`() {
        val trades = listOf(Trade(0, 1, 2.0, true), Trade(2, 3, -1.0, false), Trade(4, 5, -2.0, false))
        val result = BacktestEngine.result(trades)
        assertEquals(listOf(2.0, 1.0, -1.0), result.equity)
        assertEquals(listOf(0.0, 1.0, 3.0), result.drawdown)
        assertEquals(1.0 / 3.0, result.rollingWinRate.last(), 1e-9)
    }

    @Test fun `distribution preserves all samples`() {
        val trades = listOf(Trade(0, 1, -.5, false), Trade(2, 3, .8, true), Trade(4, 5, .8, true))
        assertEquals(trades.size, ChartAnalytics.pnlDistribution(trades).sumOf { it.samples })
    }

    @Test fun `wick outcome never reads past requested horizon`() {
        val candles = (1..5).map { i -> Candle(i.toLong(), i.toDouble(), i + 1.0, i - .5, i + .5) }
        val outcomes = ChartAnalytics.wickOutcomes(candles, 2)
        assertEquals(3, outcomes.size)
        assertTrue(outcomes.all { it.futureReturn.isFinite() })
    }
}
