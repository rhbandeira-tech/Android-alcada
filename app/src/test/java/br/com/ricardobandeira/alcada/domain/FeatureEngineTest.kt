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

    @Test fun `volatilidade respeita fronteira exclusiva`() {
        val candles = listOf(
            Candle(0, 100.0, 101.0, 99.0, 100.0),
            Candle(1, 100.0, 102.0, 99.0, 101.0),
            Candle(2, 101.0, 103.0, 100.0, 102.0),
            Candle(3, 102.0, 200.0, 50.0, 180.0)
        )
        val beforeShock = FeatureEngine.volatility(candles, 10, 3)
        val withShock = FeatureEngine.volatility(candles, 10, 4)
        org.junit.Assert.assertTrue(withShock > beforeShock)
    }

    @Test fun `volatilidade com uma vela e neutra`() {
        assertEquals(0.0, FeatureEngine.volatility(listOf(Candle(0, 10.0, 11.0, 9.0, 10.0)), 5), 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `vela rejeita numero nao finito`() {
        Candle(0, Double.NaN, 11.0, 9.0, 10.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `vela rejeita spread negativo`() {
        Candle(0, 10.0, 11.0, 9.0, 10.0, spread = -0.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `atr rejeita limite negativo`() {
        FeatureEngine.atr(emptyList(), 14, -1)
    }

    @Test
    fun `volatilidade ignora retorno com preco anterior zero`() {
        val candles = listOf(
            Candle(1, 0.0, 0.0, 0.0, 0.0),
            Candle(2, 1.0, 1.0, 1.0, 1.0),
            Candle(3, 1.0, 1.0, 1.0, 1.0)
        )
        assertEquals(0.0, FeatureEngine.volatility(candles, 3), 1e-12)
    }

    @Test
    fun `aceleracao usa apenas velas atuais e anteriores`() {
        val a = Candle(1, 10.0, 10.5, 9.5, 10.0, 1.0, 0.0)
        val b = Candle(2, 10.0, 11.5, 9.8, 11.0, 1.0, 0.0)
        val d = Candle(3, 11.0, 13.5, 10.8, 13.0, 1.0, 0.0)
        assertEquals(1.0, FeatureEngine.candle(d, b, a).acceleration, 1e-9)
    }

    @Test
    fun `distancias de suporte e resistencia respeitam janela passada`() {
        val candles = listOf(
            Candle(1, 10.0, 12.0, 9.0, 11.0, 1.0, 0.0),
            Candle(2, 11.0, 13.0, 10.0, 12.0, 1.0, 0.0),
            Candle(3, 12.0, 14.0, 11.0, 13.0, 1.0, 0.0)
        )
        val (support, resistance) = FeatureEngine.supportResistanceDistance(candles, 3)
        assertEquals(4.0, support, 1e-9)
        assertEquals(1.0, resistance, 1e-9)
    }
}
