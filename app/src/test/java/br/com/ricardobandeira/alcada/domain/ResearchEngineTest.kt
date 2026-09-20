package br.com.ricardobandeira.alcada.domain

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchEngineTest {
    @Test fun `same seed produces reproducible bounded progress`() = runTest {
        val candles = (0 until 120).map { index ->
            val base = 100.0 + index * .01
            Candle(index * 60_000L, base, base + 1.2, base - 1.0, base + if (index % 2 == 0) .1 else -.1)
        }
        val budget = ResearchBudget(maxCandidates = 30, seed = 7, minimumTrades = 5)
        val first = ResearchEngine().discover(candles, budget).toList()
        val second = ResearchEngine().discover(candles, budget).toList()
        assertEquals(30, first.last().evaluated)
        assertEquals(first.map { it.evaluated to it.accepted }, second.map { it.evaluated to it.accepted })
        assertEquals(first.last().best?.strategy?.id, second.last().best?.strategy?.id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `orcamento rejeita processadores invalidos`() {
        ResearchBudget(threads = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `orcamento rejeita memoria insegura`() {
        ResearchBudget(memoryMb = 32)
    }

    @Test fun `pesquisa paralela preserva reproducibilidade`() = runTest {
        val candles = (0 until 120).map { index ->
            val base = 100.0 + index * .01
            Candle(index * 60_000L, base, base + 1.2, base - 1.0, base + if (index % 2 == 0) .1 else -.1)
        }
        val one = ResearchEngine().discover(candles, ResearchBudget(maxCandidates = 30, threads = 1, seed = 9, minimumTrades = 5)).toList()
        val many = ResearchEngine().discover(candles, ResearchBudget(maxCandidates = 30, threads = 4, seed = 9, minimumTrades = 5)).toList()
        assertEquals(one.last().best?.strategy?.id, many.last().best?.strategy?.id)
        assertEquals(one.last().accepted, many.last().accepted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pesquisa rejeita payout abaixo de um por cento`() = runTest {
        val candles = (0 until 20).map { Candle(it.toLong(), 1.0, 1.1, .9, 1.0) }
        ResearchEngine().discover(candles, ResearchBudget(maxCandidates = 1), .009).toList()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `orcamento rejeita candidatos excessivos`() { ResearchBudget(maxCandidates = 1_000_001) }

    @Test(expected = IllegalArgumentException::class)
    fun `orcamento rejeita memoria excessiva`() { ResearchBudget(memoryMb = 16_385) }

    @Test(expected = IllegalArgumentException::class)
    fun `orcamento rejeita minimo de operacoes excessivo`() { ResearchBudget(minimumTrades = 100_001) }

    @Test(expected = IllegalArgumentException::class)
    fun `orcamento rejeita processadores excessivos`() { ResearchBudget(threads = 257) }
}
