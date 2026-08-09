package br.com.simplificarural.domain.financial

import br.com.simplificarural.domain.property.FarmScope
import java.math.BigDecimal
import java.time.LocalDate

enum class CashEntryKind { ENTRADA, SAIDA }

data class CashEntry(
    val id: String,
    val organizationId: String,
    val farmId: String,
    val unitId: String?,
    val kind: CashEntryKind,
    val amount: BigDecimal,
    val date: LocalDate,
    val description: String
)

/** Controls whether the user is seeing a single point, a farm, or all farms together. */
sealed interface CashViewScope {
    data class SelectedUnit(val scope: FarmScope) : CashViewScope
    data class SelectedFarm(val organizationId: String, val farmId: String) : CashViewScope
    data class General(val organizationId: String) : CashViewScope
}

data class CashSummary(
    val entries: BigDecimal,
    val exits: BigDecimal
) {
    val balance: BigDecimal get() = entries - exits
}

object CashAggregator {
    fun summarize(entries: Iterable<CashEntry>, scope: CashViewScope): CashSummary {
        val included = entries.filter { entry ->
            when (scope) {
                is CashViewScope.SelectedUnit ->
                    entry.organizationId == scope.scope.organizationId &&
                        entry.farmId == scope.scope.farmId &&
                        entry.unitId == scope.scope.unitId
                is CashViewScope.SelectedFarm ->
                    entry.organizationId == scope.organizationId && entry.farmId == scope.farmId
                is CashViewScope.General -> entry.organizationId == scope.organizationId
            }
        }
        return CashSummary(
            entries = included.filter { it.kind == CashEntryKind.ENTRADA }.fold(BigDecimal.ZERO) { total, entry -> total + entry.amount },
            exits = included.filter { it.kind == CashEntryKind.SAIDA }.fold(BigDecimal.ZERO) { total, entry -> total + entry.amount }
        )
    }
}
