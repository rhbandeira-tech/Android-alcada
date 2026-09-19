package br.com.ricardobandeira.alcada.data

import br.com.ricardobandeira.alcada.domain.Candle
import java.io.BufferedReader
import java.io.InputStream

/** Reads lazily in bounded chunks. Expected columns: timestamp,open,high,low,close[,volume,spread]. */
class CsvCandleReader {
    fun chunks(input: InputStream, chunkSize: Int = 4_096): Sequence<List<Candle>> = sequence {
        require(chunkSize > 0)
        input.bufferedReader().use { reader ->
            val first = reader.readLine() ?: return@use
            val firstParts = parse(first)
            val hasHeader = firstParts.firstOrNull()?.toLongOrNull() == null
            var pending: List<String>? = if (hasHeader) null else firstParts
            while (true) {
                val chunk = ArrayList<Candle>(chunkSize)
                while (chunk.size < chunkSize) {
                    val parts = pending ?: reader.readLine()?.let(::parse) ?: break
                    pending = null
                    if (parts.size >= 5) chunk += Candle(parts[0].toLong(), parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble(), parts[4].toDouble(), parts.getOrNull(5)?.toDoubleOrNull() ?: 0.0, parts.getOrNull(6)?.toDoubleOrNull() ?: 0.0)
                }
                if (chunk.isEmpty()) break
                yield(chunk)
            }
        }
    }

    private fun parse(line: String): List<String> = line.trim().split(',').map(String::trim)
}

interface HistoricalDataProvider {
    suspend fun download(symbol: String, fromEpochMillis: Long, toEpochMillis: Long, timeframeMinutes: Int): Sequence<List<Candle>>
}

/** Integration boundary. A production endpoint/client must be explicitly configured; no undocumented API is assumed. */
class DukascopyDataProvider : HistoricalDataProvider {
    override suspend fun download(symbol: String, fromEpochMillis: Long, toEpochMillis: Long, timeframeMinutes: Int): Sequence<List<Candle>> =
        throw UnsupportedOperationException("Configure um cliente Dukascopy confiável para habilitar downloads")
}

