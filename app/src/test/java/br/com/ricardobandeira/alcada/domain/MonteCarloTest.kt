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

    @Test fun `bloco configurado e respeitado`() {
        val trades = (1L..20L).map { Trade(it, it + 1, if (it % 3L == 0L) -1.0 else .8, it % 3L != 0L) }
        val result = MonteCarlo.analyze(trades, 100, 9, blockSize = 4)
        assertEquals(4, result.blockSize)
        assertEquals(100, result.simulations)
    }

    @Test fun `bloco automatico fica dentro do tamanho da amostra`() {
        val trades = (1L..3L).map { Trade(it, it + 1, .8, true) }
        val result = MonteCarlo.analyze(trades, 20, 3)
        assertTrue(result.blockSize in 1..trades.size)
    }

    @Test fun `lucros constantes produzem faixa deterministica`() {
        val trades = (1L..10L).map { Trade(it, it + 1, .5, true) }
        val result = MonteCarlo.analyze(trades, 50, 11, blockSize = 3)
        assertEquals(5.0, result.medianNetProfit, 1e-9)
        assertEquals(5.0, result.p05NetProfit, 1e-9)
        assertEquals(0.0, result.p95MaxDrawdown, 1e-9)
        assertEquals(1.0, result.profitableShare, 1e-9)
    }

    @Test fun `amostra vazia retorna resumo neutro`() {
        val result = MonteCarlo.analyze(emptyList(), 50, 1)
        assertEquals(0.0, result.medianNetProfit, 0.0)
        assertEquals(0.0, result.profitableShare, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejeita bloco invalido`() {
        MonteCarlo.analyze(listOf(Trade(1, 2, 1.0, true)), blockSize = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejeita resultado nao finito`() {
        MonteCarlo.analyze(listOf(Trade(1, 2, Double.NaN, false)))
    }

    @Test fun `percentil usa regra exata de posto mais proximo`() {
        val values = doubleArrayOf(10.0, 1.0, 8.0, 3.0, 6.0)
        assertEquals(1.0, MonteCarlo.nearestRank(values, .05), 0.0)
        assertEquals(6.0, MonteCarlo.nearestRank(values, .50), 0.0)
        assertEquals(10.0, MonteCarlo.nearestRank(values, .95), 0.0)
    }
}
