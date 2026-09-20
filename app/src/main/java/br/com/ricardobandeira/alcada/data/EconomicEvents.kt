package br.com.ricardobandeira.alcada.data

enum class EventImpact { LOW, MEDIUM, HIGH }
data class EconomicEventModel(val id: String, val name: String, val currency: String, val impact: EventImpact, val epochMillis: Long, val actual: Double?, val forecast: Double?, val previous: Double?) {
    init {
        require(id.isNotBlank() && name.isNotBlank() && currency.isNotBlank()) { "O evento econômico precisa de identificação, nome e moeda." }
        require(epochMillis >= 0) { "O horário do evento econômico não pode ser negativo." }
        require(listOfNotNull(actual, forecast, previous).all { it.isFinite() }) { "O evento econômico contém valor não finito." }
    }
    val surprise get() = if (actual != null && forecast != null) actual - forecast else null
}
interface EconomicCalendarProvider {
    suspend fun events(fromEpochMillis: Long, toEpochMillis: Long): List<EconomicEventModel>
}

fun validateEconomicEventWindow(fromEpochMillis: Long, toEpochMillis: Long) {
    require(fromEpochMillis >= 0 && toEpochMillis >= fromEpochMillis) { "O intervalo da agenda econômica é inválido." }
}

