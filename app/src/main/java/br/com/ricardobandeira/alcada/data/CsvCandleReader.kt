package br.com.ricardobandeira.alcada.data

import br.com.ricardobandeira.alcada.domain.Candle
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/** Reads lazily in bounded chunks. Expected columns: timestamp,open,high,low,close[,volume,spread]. */
class CsvCandleReader {
    fun chunks(input: InputStream, chunkSize: Int = 4_096): Sequence<List<Candle>> = sequence {
        require(chunkSize > 0)
        input.bufferedReader().use { reader ->
            val first = reader.readLine() ?: return@use
            val delimiter = detectDelimiter(first)
            val firstParts = parse(first, delimiter)
            val hasHeader = parseTimestamp(firstParts.firstOrNull().orEmpty()) == null
            var pending: List<String>? = if (hasHeader) null else firstParts
            var lineNumber = 1

            while (true) {
                val chunk = ArrayList<Candle>(chunkSize)
                while (chunk.size < chunkSize) {
                    val parts = pending ?: reader.readLine()?.also { lineNumber++ }?.let { parse(it, delimiter) } ?: break
                    pending = null
                    if (parts.all { it.isBlank() }) continue
                    require(parts.size >= 5) { "CSV inválido na linha $lineNumber: são necessárias as colunas data/hora, abertura, máxima, mínima e fechamento." }

                    val timestamp = parseTimestamp(parts[0])
                        ?: throw IllegalArgumentException("Data/hora inválida na linha $lineNumber.")
                    val open = number(parts[1])
                        ?: throw IllegalArgumentException("Abertura inválida na linha $lineNumber.")
                    val high = number(parts[2])
                        ?: throw IllegalArgumentException("Máxima inválida na linha $lineNumber.")
                    val low = number(parts[3])
                        ?: throw IllegalArgumentException("Mínima inválida na linha $lineNumber.")
                    val close = number(parts[4])
                        ?: throw IllegalArgumentException("Fechamento inválido na linha $lineNumber.")
                    require(high >= maxOf(open, close, low) && low <= minOf(open, close, high)) {
                        "OHLC inconsistente na linha $lineNumber"
                    }

                    chunk += Candle(
                        epochMillis = timestamp,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = parts.getOrNull(5)?.takeIf { it.isNotBlank() }?.let { number(it) ?: throw IllegalArgumentException("Volume inválido na linha $lineNumber.") } ?: 0.0,
                        spread = parts.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { number(it) ?: throw IllegalArgumentException("Spread inválido na linha $lineNumber.") } ?: 0.0
                    )
                }
                if (chunk.isEmpty()) break
                yield(chunk)
            }
        }
    }

    private fun number(value: String): Double? =
        value.trim().removeSurrounding("\"").toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun parseTimestamp(value: String): Long? {
        val clean = value.trim().removeSurrounding("\"")
        if (clean.startsWith("@")) return clean.removePrefix("@").toLongOrNull()
        clean.toLongOrNull()?.let { numeric ->
            // Accept both Unix seconds and milliseconds.
            return if (kotlin.math.abs(numeric) < 100_000_000_000L) numeric * 1_000L else numeric
        }
        return runCatching { Instant.parse(clean).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(clean).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(clean).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun detectDelimiter(line: String): Char {
        val candidates = listOf(',', ';', '\t')
        return candidates.maxByOrNull { delimiter -> line.count { it == delimiter } } ?: ','
    }

    private fun parse(line: String, delimiter: Char): List<String> =
        line.trim().split(delimiter).map { it.trim().removeSurrounding("\"") }
}

interface HistoricalDataProvider {
    suspend fun download(symbol: String, fromEpochMillis: Long, toEpochMillis: Long, timeframeMinutes: Int): Sequence<List<Candle>>
}

/** Integration boundary. A production endpoint/client must be explicitly configured; no undocumented API is assumed. */
class DukascopyDataProvider : HistoricalDataProvider {
    override suspend fun download(symbol: String, fromEpochMillis: Long, toEpochMillis: Long, timeframeMinutes: Int): Sequence<List<Candle>> =
        throw UnsupportedOperationException("Configure um cliente Dukascopy confiável para habilitar downloads")
}
