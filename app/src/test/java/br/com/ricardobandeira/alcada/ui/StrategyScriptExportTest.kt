package br.com.ricardobandeira.alcada.ui

import br.com.ricardobandeira.alcada.data.StrategyEntity
import org.junit.Assert.*
import org.junit.Test

class StrategyScriptExportTest {
    private val portable = listOf(
        "wickBodyRatio", "bodyRangeRatio", "closeLocation",
        "momentumRangeRatio", "gapRangeRatio"
    )
    private val advanced = listOf(
        "spreadRangeRatio", "sessionUtc", "atrRangeRatio",
        "candleSequence", "levelDistanceRatio", "accelerationRangeRatio"
    )

    @Test fun portableRulesAreConvertedInAllFormats() {
        portable.forEach { feature ->
            listOf("TradingView", "MQL5", "Lua").forEach { language ->
                assertNotNull("$feature must be portable to $language", scriptRule("$feature:>=:0.5", language, "CALL"))
            }
        }
    }

    @Test fun implementedAdvancedRulesAreConvertedInAllFormats() {
        val implemented = advanced.filter { it != "spreadRangeRatio" && it != "sessionUtc" }
        implemented.forEach { feature ->
            listOf("TradingView", "MQL5", "Lua").forEach { language ->
                assertNotNull("$feature must be converted in $language", scriptRule("$feature:>=:0.5", language, "CALL"))
            }
        }
    }

    @Test fun sessionUtcUsesCategoricalSessionId() {
        listOf("TradingView", "Lua").forEach { language ->
            assertNotNull("$language must convert UTC session id", scriptRule("sessionUtc:==:1.0", language, "CALL"))
            assertNull("$language must reject non-session threshold", scriptRule("sessionUtc:==:0.5", language, "CALL"))
        }
        assertNull("MQL5 broker time is not guaranteed UTC", scriptRule("sessionUtc:==:1.0", "MQL5", "CALL"))
    }

    @Test fun spreadRuleStaysBlockedWithoutPlatformSpreadInput() {
        listOf("TradingView", "MQL5", "Lua").forEach { language ->
            assertNull(scriptRule("spreadRangeRatio:<=:0.1", language, "CALL"))
        }
    }

    @Test fun pineSupportsImplementedAdvancedFeatures() {
        listOf("atrRangeRatio", "candleSequence", "levelDistanceRatio", "accelerationRangeRatio").forEach {
            assertNotNull(it, scriptRule("$it:>=:1.0", "TradingView", "CALL"))
        }
        assertNotNull(scriptRule("sessionUtc:==:1.0", "TradingView", "CALL"))
        assertNull(scriptRule("spreadRangeRatio:>=:0.1", "TradingView", "CALL"))
    }

    @Test fun tradingViewExportIsSignalOnly() {
        val strategy = StrategyEntity(
            id="test-pine-signal", researchRunId="run", name="Pine signal",
            market="BINARY_OPTIONS", symbol="EURUSD", profile="EXPERIMENTAL",
            definitionJson="", createdAt=0L
        )
        val data = MutableList(13) { "" }
        data[9] = "CALL"; data[12] = "wickBodyRatio:>=:2.0"
        val script = strategyScript(strategy, data, "TradingView")
        assertTrue(script.contains("indicator(\\"Alçada -"))
        assertTrue(script.contains("alertcondition(signal"))
        assertFalse(script.contains("strategy.entry"))
        assertFalse(script.contains("strategy(\\""))
    }

    @Test fun mql5ExportNeverPlacesLiveOrders() {
        val strategy = StrategyEntity(
            id="test-mql5", researchRunId="run", name="Safe", market="BINARY_OPTIONS",
            symbol="EURUSD", profile="EXPERIMENTAL", definitionJson="", createdAt=0L
        )
        val data = MutableList(13) { "" }
        data[9] = "CALL"; data[12] = "wickBodyRatio:>=:2.0"
        val script = strategyScript(strategy, data, "MQL5")
        assertFalse(script.contains("CTrade"))
        assertFalse(script.contains("trade.Buy"))
        assertFalse(script.contains("trade.Sell"))
        assertTrue(script.contains("Alert("))
    }

    @Test fun exportedWickAndAtrMathMatchesDomainDefinition() {
        val pineWick = scriptRule("wickBodyRatio:>=:2.0", "TradingView", "CALL")!!
        assertTrue(pineWick.contains("high-math.max(open,close)"))
        assertTrue(pineWick.contains("math.min(open,close)-low"))
        val pineAtr = scriptRule("atrRangeRatio:>=:1.0", "TradingView", "CALL")!!
        assertTrue(pineAtr.contains("ta.sma(ta.tr(true),14)"))
        assertFalse(pineAtr.contains("ta.atr"))
    }

    @Test fun pineCandleSequenceMatchesEightCandleEngineLimit() {
        val expression = scriptRule("candleSequence:>=:8.0", "TradingView", "CALL")!!
        assertTrue(expression.contains("close[7]>open[7]"))
        assertTrue(expression.contains("close[7]<open[7]"))
        assertTrue(expression.contains("8.0"))
        assertTrue(expression.contains("-8.0"))
    }

    @Test fun luaAdvancedInputsFailClosedInsteadOfInventingDefaults() {
        val strategy = StrategyEntity(
            id="test-lua", researchRunId="run", name="Lua parity", market="BINARY_OPTIONS",
            symbol="EURUSD", profile="EXPERIMENTAL", definitionJson="", createdAt=0L
        )
        val data = MutableList(13) { "" }
        data[9] = "CALL"; data[12] = "atrRangeRatio:>=:1.0&candleSequence:>=:3.0&levelDistanceRatio:<=:1.0"
        val script = strategyScript(strategy, data, "Lua")
        assertTrue(script.contains("atr14 and atr14/range or (0/0)"))
        assertTrue(script.contains("sequence and math.abs(sequence) or (0/0)"))
        assertTrue(script.contains("extreme20 and math.abs(c-extreme20)/range or (0/0)"))
        assertTrue(script.contains("utcHourValue or -1"))
        assertTrue(script.contains("média simples de 14 True Ranges"))
    }

    @Test fun malformedOperatorsAndNonFiniteThresholdsAreBlocked() {
        listOf("TradingView", "MQL5", "Lua").forEach { language ->
            assertNull(scriptRule("wickBodyRatio:!=:2.0", language, "CALL"))
            assertNull(scriptRule("wickBodyRatio:>=:NaN", language, "CALL"))
            assertNull(scriptRule("wickBodyRatio:>=:Infinity", language, "CALL"))
            assertNull(scriptRule("wickBodyRatio:>=:2.0:unexpected", language, "CALL"))
            assertNull(scriptRule("wickBodyRatio:>=:", language, "CALL"))
        }
    }

    @Test fun missingDirectionBlocksGeneratedSignal() {
        val strategy = StrategyEntity(
            id="test-direction", researchRunId="run", name="Direction", market="BINARY_OPTIONS",
            symbol="EURUSD", profile="EXPERIMENTAL", definitionJson="", createdAt=0L
        )
        val data = MutableList(13) { "" }
        data[12] = "wickBodyRatio:>=:2.0"
        listOf("TradingView", "MQL5", "Lua").forEach { language ->
            val script = strategyScript(strategy, data, language)
            assertTrue(script.contains("direção ausente ou inválida"))
            assertTrue(script.contains("false"))
        }
    }

    @Test fun unknownExportLanguageIsRejected() {
        val strategy = StrategyEntity(
            id="test-format", researchRunId="run", name="Format", market="BINARY_OPTIONS",
            symbol="EURUSD", profile="EXPERIMENTAL", definitionJson="", createdAt=0L
        )
        val data = MutableList(13) { "" }
        data[9] = "CALL"; data[12] = "wickBodyRatio:>=:2.0"
        assertFailsWith<IllegalArgumentException> { strategyScript(strategy, data, "Unknown") }
    }

    @Test fun strategyNameCannotInjectLinesIntoGeneratedScript() {
        val strategy = StrategyEntity(
            id="test-name", researchRunId="run", name="Safe\\nInjected\\t\\\"Name",
            market="BINARY_OPTIONS", symbol="EURUSD", profile="EXPERIMENTAL",
            definitionJson="", createdAt=0L
        )
        val data = MutableList(13) { "" }
        data[9] = "CALL"; data[12] = "wickBodyRatio:>=:2.0"
        listOf("TradingView", "MQL5", "Lua").forEach { language ->
            val script = strategyScript(strategy, data, language)
            assertFalse(script.contains("Safe\\nInjected"))
            assertFalse(script.contains("\\\"Name"))
            assertTrue(script.contains("Safe Injected Name"))
        }
    }

    @Test fun unsupportedRuleBlocksAutomaticSignal() {
        val strategy = StrategyEntity(
            id="test", researchRunId="run", name="Parity", market="BINARY_OPTIONS",
            symbol="EURUSD", profile="EXPERIMENTAL", definitionJson="", createdAt=0L
        )
        val data = MutableList(13) { "" }
        data[9] = "CALL"; data[10] = "1"; data[11] = "1"; data[12] = "spreadRangeRatio:<=:0.1"
        val lua = strategyScript(strategy, data, "Lua")
        assertTrue(lua.contains("REVISÃO OBRIGATÓRIA"))
        assertTrue(lua.contains("return (false)"))
    }
}
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
