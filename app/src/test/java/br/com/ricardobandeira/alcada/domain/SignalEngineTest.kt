package br.com.ricardobandeira.alcada.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalEngineTest {
    @Test fun `lower wick generates only configured long direction`() {
        val candles = listOf(
            Candle(0, 10.0, 10.2, 9.8, 10.1),
            Candle(1, 10.0, 10.2, 7.0, 10.1),
            Candle(2, 10.1, 10.3, 10.0, 10.2)
        )
        val signals = SignalEngine.wickSignals(candles, Direction.CALL)
        assertEquals(listOf(1), signals.map { it.first })
        assertTrue(signals.all { it.second == Direction.CALL })
    }

    @Test fun `filtro exige pavio corpo e fechamento direcionais`() {
        val candles = listOf(
            Candle(0, 10.0, 10.2, 9.8, 10.0),
            Candle(1, 10.0, 10.4, 7.0, 10.2),
            Candle(2, 10.2, 10.3, 10.0, 10.2)
        )
        val accepted = SignalEngine.filteredWickSignals(candles, Direction.CALL, 2.0, .20, .60)
        val rejected = SignalEngine.filteredWickSignals(candles, Direction.CALL, 2.0, .05, .60)
        assertEquals(listOf(1), accepted.map { it.first })
        assertTrue(rejected.isEmpty())
    }
}
