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
}
