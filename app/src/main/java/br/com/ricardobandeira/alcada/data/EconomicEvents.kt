package br.com.ricardobandeira.alcada.data

enum class EventImpact { LOW, MEDIUM, HIGH }
data class EconomicEventModel(val id: String, val name: String, val currency: String, val impact: EventImpact, val epochMillis: Long, val actual: Double?, val forecast: Double?, val previous: Double?) {
    val surprise get() = if (actual != null && forecast != null) actual - forecast else null
}
interface EconomicCalendarProvider { suspend fun events(fromEpochMillis: Long, toEpochMillis: Long): List<EconomicEventModel> }

