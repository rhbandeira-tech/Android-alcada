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

    @Test(expected = IllegalArgumentException::class)
    fun `teste binario rejeita velas fora de ordem`() {
        val candles = listOf(
            Candle(2, 10.0, 11.0, 9.0, 10.0),
            Candle(1, 10.0, 11.0, 9.0, 10.0)
        )
        BacktestEngine.binary(candles, emptyList(), 1, .85)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `forex rejeita custo negativo`() {
        val candles = listOf(
            Candle(1, 10.0, 11.0, 9.0, 10.0),
            Candle(2, 10.0, 11.0, 9.0, 10.0)
        )
        BacktestEngine.forex(candles, emptyList(), ExitRule(bars = 1), -.01)
    }

    @Test fun `payout de 85 por cento calcula ponto de equilibrio correto`() {
        val candles = listOf(
            Candle(1, 1.0, 1.1, .9, 1.0),
            Candle(2, 1.0, 1.2, .9, 1.1)
        )
        val result = BacktestEngine.binaryResult(candles, listOf(0 to Direction.CALL), 1, .85)
        assertEquals(1.0 / 1.85, result.metrics.breakEvenWinRate ?: 0.0, 1e-12)
        assertEquals(.85, result.metrics.netProfit, 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `regra rejeita operador desconhecido`() {
        EntryRule("momentum", "!=", 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `saida rejeita limite de velas zero`() {
        ExitRule(bars = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `regra rejeita limite nao finito`() {
        EntryRule("momentum", ">=", Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `saida rejeita stop negativo`() {
        ExitRule(stopLoss = -.01, bars = 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `operacao rejeita saida anterior a entrada`() { Trade(2, 1, 1.0, true) }

    @Test(expected = IllegalArgumentException::class)
    fun `operacao rejeita resultado nao finito`() { Trade(1, 2, Double.POSITIVE_INFINITY, true) }

    @Test(expected = IllegalArgumentException::class)
    fun `binario rejeita direcao forex`() {
        BacktestEngine.binary(candles, listOf(0 to Direction.LONG), 1, .85)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `binario rejeita retorno acima de duzentos por cento`() {
        BacktestEngine.binary(candles, listOf(0 to Direction.CALL), 1, 2.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `metricas rejeitam equilibrio acima de cem por cento`() {
        BacktestEngine.metrics(emptyList(), 1.01)
    }

    @Test fun `binario ordena operacoes cronologicamente`() {
        val result = BacktestEngine.binaryResult(candles, listOf(2 to Direction.CALL, 0 to Direction.CALL), 1, .85)
        assertEquals(candles[0].epochMillis, result.trades.first().entryTime)
    }
}
