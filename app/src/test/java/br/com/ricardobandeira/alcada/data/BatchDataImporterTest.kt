package br.com.ricardobandeira.alcada.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BatchDataImporterTest {
    private fun csv(vararg times: Long) = buildString {
        appendLine("timestamp,open,high,low,close")
        times.forEach { appendLine("$it,10,12,9,11") }
    }.toByteArray()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
        return out.toByteArray()
    }

    @Test fun `combina varios csv e remove timestamp duplicado`() {
        val dir = createTempDirectory("alcada_test_").toFile(); val output = File(dir, "saida.csv")
        val result = BatchDataImporter(dir).import(listOf(
            ImportSource("a.csv") { ByteArrayInputStream(csv(1, 3)) },
            ImportSource("b.csv") { ByteArrayInputStream(csv(2, 3, 4)) }
        ), output)
        assertEquals(4L, result.validCandles); assertEquals(1L, result.duplicates)
        assertEquals(listOf(1000L,2000L,3000L,4000L), CsvCandleReader().chunks(output.inputStream()).flatten().map { it.epochMillis }.toList())
    }

    @Test fun `zip com varios csv e arquivo ignorado`() {
        val dir = createTempDirectory("alcada_test_").toFile(); val output = File(dir, "saida.csv")
        val bytes = zip("um.csv" to csv(1,2), "pasta/dois.csv" to csv(3,4), "leia.txt" to "x".toByteArray())
        val result = BatchDataImporter(dir).import(listOf(ImportSource("dados.zip") { ByteArrayInputStream(bytes) }), output)
        assertEquals(2, result.csvFiles); assertEquals(1, result.ignoredFiles); assertEquals(4L, result.validCandles)
    }

    @Test fun `bloqueia caminho inseguro em zip`() {
        val dir = createTempDirectory("alcada_test_").toFile(); val output = File(dir, "saida.csv")
        val bytes = zip("../fora.csv" to csv(1,2))
        val error = runCatching { BatchDataImporter(dir).import(listOf(ImportSource("dados.zip") { ByteArrayInputStream(bytes) }), output) }.exceptionOrNull()
        assertNotNull(error); assertTrue(error!!.message!!.contains("caminho inseguro"))
    }

    @Test fun `limita expansao de zip`() {
        val dir = createTempDirectory("alcada_test_").toFile(); val output = File(dir, "saida.csv")
        val bytes = zip("grande.csv" to csv(1,2,3,4))
        val error = runCatching { BatchDataImporter(dir, maxExpandedBytes = 10).import(listOf(ImportSource("dados.zip") { ByteArrayInputStream(bytes) }), output) }.exceptionOrNull()
        assertNotNull(error); assertTrue(error!!.message!!.contains("limite seguro"))
    }

    @Test fun `arquivo ruim nao impede outro csv valido`() {
        val dir = createTempDirectory("alcada_test_").toFile(); val output = File(dir, "saida.csv")
        val result = BatchDataImporter(dir).import(listOf(
            ImportSource("ruim.csv") { ByteArrayInputStream("invalido".toByteArray()) },
            ImportSource("bom.csv") { ByteArrayInputStream(csv(1,2)) }
        ), output)
        assertEquals(2L, result.validCandles); assertEquals(1, result.issues.size)
    }
}
