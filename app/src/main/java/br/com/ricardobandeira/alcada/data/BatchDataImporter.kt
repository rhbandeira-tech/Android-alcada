package br.com.ricardobandeira.alcada.data

import br.com.ricardobandeira.alcada.domain.Candle
import java.io.*
import java.util.PriorityQueue
import java.util.zip.ZipInputStream

data class ImportSource(val name: String, val open: () -> InputStream)
data class ImportIssue(val file: String, val message: String)
data class ImportSummary(val selectedFiles: Int, val csvFiles: Int, val validCandles: Long, val duplicates: Long, val ignoredFiles: Int, val firstEpochMillis: Long?, val lastEpochMillis: Long?, val issues: List<ImportIssue>)

class BatchDataImporter(private val workDir: File, private val maxExpandedBytes: Long = 1_000_000_000L, private val maxEntries: Int = 512) {
    fun import(sources: List<ImportSource>, output: File, onProgress: (Int, Int, String) -> Unit = { _,_,_ -> }): ImportSummary {
        require(sources.isNotEmpty()) { "Selecione pelo menos um arquivo." }
        workDir.mkdirs()
        val canonical = mutableListOf<File>(); val issues = mutableListOf<ImportIssue>()
        var csvFiles = 0; var ignored = 0; var expanded = 0L; var entryCount = 0
        sources.forEachIndexed { index, source ->
            onProgress(index, sources.size, source.name)
            runCatching {
                if (source.name.lowercase().endsWith(".zip")) {
                    ZipInputStream(BufferedInputStream(source.open())).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            entryCount++; require(entryCount <= maxEntries) { "ZIP com arquivos demais." }
                            val unsafe = entry.name.startsWith("/") || entry.name.startsWith("\\") || entry.name.split('/', '\\').any { it == ".." }
                            require(!unsafe) { "ZIP contém caminho inseguro: \${entry.name}" }
                            if (entry.isDirectory) continue
                            if (!entry.name.lowercase().endsWith(".csv")) { ignored++; continue }
                            val temp = File.createTempFile("alcada_", ".csv", workDir)
                            FileOutputStream(temp).use { out ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) { val n = zip.read(buffer); if (n < 0) break; expanded += n; require(expanded <= maxExpandedBytes) { "ZIP excede o limite seguro de expansão." }; out.write(buffer, 0, n) }
                            }
                            canonical += normalize(temp, entry.name); temp.delete(); csvFiles++
                        }
                    }
                } else if (source.name.lowercase().endsWith(".csv")) {
                    val temp = File.createTempFile("alcada_", ".csv", workDir)
                    source.open().use { input -> FileOutputStream(temp).use { input.copyTo(it) } }
                    canonical += normalize(temp, source.name); temp.delete(); csvFiles++
                } else ignored++
            }.onFailure { issues += ImportIssue(source.name, friendly(it)) }
        }
        require(canonical.isNotEmpty()) { issues.firstOrNull()?.message ?: "Nenhum CSV válido foi encontrado." }
        val merge = merge(canonical, output); canonical.forEach(File::delete)
        onProgress(sources.size, sources.size, "Concluído")
        return ImportSummary(sources.size, csvFiles, merge.valid, merge.duplicates, ignored, merge.first, merge.last, issues)
    }

    private fun normalize(input: File, label: String): File {
        val target = File.createTempFile("alcada_normalizado_", ".csv", workDir); var previous = Long.MIN_VALUE
        target.bufferedWriter().use { writer -> input.inputStream().use { stream ->
            CsvCandleReader().chunks(stream).forEach { chunk -> chunk.forEach { c ->
                require(c.epochMillis >= previous) { "O arquivo \${label} não está em ordem cronológica." }; previous = c.epochMillis; writer.appendLine(row(c))
            } }
        } }
        return target
    }

    private data class Cursor(val reader: BufferedReader, var candle: Candle)
    private data class MergeResult(val valid: Long, val duplicates: Long, val first: Long?, val last: Long?)
    private fun merge(files: List<File>, output: File): MergeResult {
        val queue = PriorityQueue<Cursor>(compareBy { it.candle.epochMillis }); val readers = mutableListOf<BufferedReader>()
        try {
            files.forEach { file -> val reader = file.bufferedReader(); readers += reader; readCanonical(reader)?.let { queue += Cursor(reader, it) } }
            var valid = 0L; var duplicates = 0L; var lastTime: Long? = null; var first: Long? = null; var last: Long? = null
            output.bufferedWriter().use { writer ->
                writer.appendLine("timestamp,open,high,low,close,volume,spread")
                while (queue.isNotEmpty()) {
                    val cursor = queue.poll(); val c = cursor.candle
                    if (lastTime == c.epochMillis) duplicates++ else { writer.appendLine(row(c)); valid++; first = first ?: c.epochMillis; last = c.epochMillis; lastTime = c.epochMillis }
                    readCanonical(cursor.reader)?.let { cursor.candle = it; queue += cursor }
                }
            }
            return MergeResult(valid, duplicates, first, last)
        } finally { readers.forEach { runCatching { it.close() } } }
    }
    private fun readCanonical(reader: BufferedReader): Candle? { val line = reader.readLine() ?: return null; val p = line.split(','); return Candle(p[0].toLong(),p[1].toDouble(),p[2].toDouble(),p[3].toDouble(),p[4].toDouble(),p[5].toDouble(),p[6].toDouble()) }
    private fun row(c: Candle) = listOf(c.epochMillis,c.open,c.high,c.low,c.close,c.volume,c.spread).joinToString(",")
    private fun friendly(t: Throwable): String = when { t.message?.contains("caminho inseguro", true) == true -> t.message!!; t.message?.contains("limite seguro", true) == true -> t.message!!; t is java.util.zip.ZipException -> "O arquivo ZIP está inválido ou corrompido."; else -> t.message ?: "Não foi possível processar este arquivo." }
}
