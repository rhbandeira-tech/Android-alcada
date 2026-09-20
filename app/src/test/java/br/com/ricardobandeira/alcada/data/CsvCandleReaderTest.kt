package br.com.ricardobandeira.alcada.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class CsvCandleReaderTest {
    @Test fun `streams bounded chunks and accepts header`() {
        val csv = "timestamp,open,high,low,close\n1,10,12,9,11\n2,11,13,10,12\n3,12,14,11,13\n"
        val chunks = CsvCandleReader().chunks(csv.byteInputStream(), 2).toList()
        assertEquals(listOf(2, 1), chunks.map { it.size })
        assertEquals(13.0, chunks.last().single().close, 0.0)
    }

    @Test fun `accepts ISO 8601 timestamp with offset`() {
        val csv = "timestamp,open,high,low,close\n2006-01-02T00:00:00+00:00,10,12,9,11\n"
        val candle = CsvCandleReader().chunks(csv.byteInputStream()).single().single()
        assertEquals(Instant.parse("2006-01-02T00:00:00Z").toEpochMilli(), candle.timestamp)
    }

    @Test fun `accepts semicolon delimiter`() {
        val csv = "timestamp;open;high;low;close\n2006-01-02T00:00:00Z;10;12;9;11\n"
        val candle = CsvCandleReader().chunks(csv.byteInputStream()).single().single()
        assertEquals(11.0, candle.close, 0.0)
    }
}
