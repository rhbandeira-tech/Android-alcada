package br.com.ricardobandeira.alcada.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvCandleReaderTest {
    @Test fun `streams bounded chunks and accepts header`() {
        val csv = "timestamp,open,high,low,close\n1,10,12,9,11\n2,11,13,10,12\n3,12,14,11,13\n"
        val chunks = CsvCandleReader().chunks(csv.byteInputStream(), 2).toList()
        assertEquals(listOf(2, 1), chunks.map { it.size })
        assertEquals(13.0, chunks.last().single().close, 0.0)
    }
}
