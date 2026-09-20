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

    @Test fun advancedRulesNeverSilentlyChangeMeaning() {
        advanced.forEach { feature ->
            listOf("MQL5", "Lua").forEach { language ->
                assertNull("$feature must stay blocked in $language until exact parity exists", scriptRule("$feature:>=:0.5", language, "CALL"))
            }
        }
    }

    @Test fun pineSupportsImplementedAdvancedFeatures() {
        listOf("sessionUtc", "atrRangeRatio", "candleSequence", "levelDistanceRatio", "accelerationRangeRatio").forEach {
            assertNotNull(it, scriptRule("$it:>=:1.0", "TradingView", "CALL"))
        }
        assertNull(scriptRule("spreadRangeRatio:>=:0.1", "TradingView", "CALL"))
    }

    @Test fun unsupportedRuleBlocksAutomaticSignal() {
        val strategy = StrategyEntity(
            id="test", researchRunId="run", name="Parity", market="BINARY_OPTIONS",
            symbol="EURUSD", timeframeMinutes=1, profile="EXPERIMENTAL",
            validationStatus="PENDING", definitionJson="", createdAt=0L
        )
        val data = MutableList(13) { "" }
        data[9] = "CALL"; data[10] = "1"; data[11] = "1"; data[12] = "atrRangeRatio:>=:1.2"
        val lua = strategyScript(strategy, data, "Lua")
        assertTrue(lua.contains("REVISÃO OBRIGATÓRIA"))
        assertTrue(lua.contains("return (false)"))
    }
}
