package br.com.simplificarural.domain.management

import br.com.simplificarural.domain.property.FarmScope
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

enum class ManagementRecordType {
    COMPRA, VENDA, DESPESA, CONSUMO_ESTOQUE, AJUSTE_ESTOQUE,
    PRODUCAO_OVOS, PRODUCAO_LEITE, PESAGEM_SUINOS, PARTO_SUINOS
}

enum class FinancialCategory(val isCashOperatingCost: Boolean = false, val isOpportunityCost: Boolean = false) {
    VENDA_PRODUCAO,
    OUTRA_RECEITA,
    RACAO(true),
    SANIDADE(true),
    REPRODUCAO(true),
    MAO_DE_OBRA_CONTRATADA(true),
    MAO_DE_OBRA_FAMILIAR(isOpportunityCost = true),
    ENERGIA_COMBUSTIVEL(true),
    MANUTENCAO(true),
    TRANSPORTE(true),
    IMPOSTOS_TAXAS(true),
    SEGURO(true),
    DEPRECIACAO(isOpportunityCost = true),
    JUROS_FINANCIAMENTO(isOpportunityCost = true),
    OUTRA_DESPESA(true)
}

enum class StockDirection { ENTRADA, SAIDA }

data class ManagementRecord(
    val id: String,
    val scope: FarmScope,
    val type: ManagementRecordType,
    val date: LocalDate,
    val description: String,
    val category: FinancialCategory? = null,
    val productName: String? = null,
    val stockDirection: StockDirection? = null,
    val quantity: BigDecimal? = null,
    val unit: String? = null,
    val unitPrice: BigDecimal? = null,
    val totalAmount: BigDecimal? = null,
    val lotId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    /** Hora em que o lançamento foi gravado no aparelho; a data operacional continua editável. */
    val createdAt: LocalDateTime = LocalDateTime.now()
)

data class AnimalLot(
    val id: String,
    val scope: FarmScope,
    val name: String,
    val activity: AnimalActivity,
    val startedAt: LocalDate,
    val initialAnimals: Int,
    val active: Boolean = true
)

enum class AnimalActivity { AVES_POSTURA, BOVINOS_LEITE, SUINOS_ENGORDA, SUINOS_MATRIZES }

data class FinancialResult(
    val revenue: BigDecimal,
    val cashOperatingCost: BigDecimal,
    val familyLabor: BigDecimal,
    val depreciation: BigDecimal,
    val financialCost: BigDecimal
) {
    val cashGeneration: BigDecimal get() = revenue - cashOperatingCost
    val totalCost: BigDecimal get() = cashOperatingCost + familyLabor + depreciation + financialCost
    val netProfit: BigDecimal get() = revenue - totalCost
    val grossMargin: BigDecimal get() = revenue - cashOperatingCost
    val netMarginPercent: BigDecimal? get() = if (revenue.compareTo(BigDecimal.ZERO) == 0) null else netProfit * BigDecimal(100) / revenue
}

data class StockBalance(val productName: String, val unit: String, val quantity: BigDecimal, val inventoryCategory: String = "Outros")

data class SwinePerformance(
    val averageDailyGainKg: BigDecimal?,
    val weanedPerLitter: BigDecimal?,
    val preWeaningMortalityPercent: BigDecimal?
)
