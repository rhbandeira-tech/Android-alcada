package br.com.ricardobandeira.alcada.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FeatureEngineTest {
    @Test fun `candle geometry is exact`() {
        val f = FeatureEngine.candle(Candle(0, 10.0, 15.0, 8.0, 12.0))
        assertEquals(2.0, f.body, 1e-9)
        assertEquals(3.0, f.upperWick, 1e-9)
        assertEquals(2.0, f.lowerWick, 1e-9)
        assertEquals(2.5, f.wickBodyRatio, 1e-9)
        assertEquals(5.0 / 7.0, f.wickRangeRatio, 1e-9)
    }

    @Test fun `atr uses only values before exclusive boundary`() {
        val candles = listOf(Candle(0, 10.0, 11.0, 9.0, 10.0), Candle(1, 10.0, 13.0, 8.0, 12.0), Candle(2, 12.0, 101.0, 1.0, 50.0))
        assertEquals(5.0, FeatureEngine.atr(candles, 10, 2), 1e-9)
    }
}
