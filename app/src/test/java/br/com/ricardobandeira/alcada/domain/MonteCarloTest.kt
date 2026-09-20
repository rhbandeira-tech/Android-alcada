package br.com.ricardobandeira.alcada.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonteCarloTest {
    @Test fun `resultado e deterministico com a mesma semente`() {
        val trades = listOf(
            Trade(1, 2, .8, true), Trade(2, 3, -1.0, false),
            Trade(3, 4, .8, true), Trade(4, 5, .8, true)
        )
        val first = MonteCarlo.analyze(trades, 200, 7)
        val second = MonteCarlo.analyze(trades, 200, 7)
        assertEquals(first, second)
        assertEquals(200, first.simulations)
        assertTrue(first.profitableShare in 0.0..1.0)
        assertTrue(first.p95MaxDrawdown >= 0.0)
    }

    @Test fun `amostra vazia retorna resumo neutro`() {
        val result = MonteCarlo.analyze(emptyList(), 50, 1)
        assertEquals(0.0, result.medianNetProfit, 0.0)
        assertEquals(0.0, result.profitableShare, 0.0)
    }
}
