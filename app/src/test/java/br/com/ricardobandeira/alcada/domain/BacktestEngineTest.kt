package br.com.ricardobandeira.alcada.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BacktestEngineTest {
    private val candles = listOf(10.0, 11.0, 9.0, 12.0).mapIndexed { i, close -> Candle(i.toLong(), close, close, close, close) }

    @Test fun `binary payout and break-even are financial not accuracy`() {
        val result = BacktestEngine.binary(candles, listOf(0 to Direction.CALL, 1 to Direction.CALL, 2 to Direction.PUT), 1, .8)
        assertEquals(1, result.wins)
        assertEquals(-1.2, result.netProfit, 1e-9)
        assertEquals(1.0 / 1.8, result.breakEvenWinRate!!, 1e-9)
    }

    @Test fun `empty metrics are finite and safe`() {
        val result = BacktestEngine.metrics(emptyList())
        assertEquals(0.0, result.winRate, 0.0)
        assertEquals(0.0, result.expectancy, 0.0)
    }

    @Test fun `forex applies take profit spread and explicit cost`() {
        val data = listOf(
            Candle(0, 1.000, 1.001, .999, 1.000, spread = .0001),
            Candle(1, 1.000, 1.006, .999, 1.005)
        )
        val result = BacktestEngine.forex(data, listOf(0 to Direction.LONG), ExitRule(takeProfit = .004, bars = 1), cost = .0002)
        assertEquals(.0037, result.netProfit, 1e-9)
        assertEquals(1, result.wins)
    }

    @Test fun `forex rejects entry on final candle instead of reading beyond data`() {
        val result = BacktestEngine.forex(candles, listOf(candles.lastIndex to Direction.LONG), ExitRule(bars = 2), 0.0)
        assertEquals(0, result.trades)
    }

    @Test fun `forex conditional exit evaluates current candle only`() {
        val data = listOf(
            Candle(0, 10.0, 10.2, 9.8, 10.0),
            Candle(1, 10.0, 11.2, 9.9, 11.0),
            Candle(2, 11.0, 20.0, 10.0, 19.0)
        )
        val exit = ExitRule(bars = 2, condition = EntryRule("range", ">", 1.0))
        val result = BacktestEngine.forex(data, listOf(0 to Direction.LONG), exit, 0.0)
        assertEquals(1.0, result.netProfit, 1e-9)
    }
}
