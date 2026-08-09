package br.com.simplificarural.domain.management

import android.content.Context
import br.com.simplificarural.domain.financial.CashEntry
import br.com.simplificarural.domain.financial.CashEntryKind
import br.com.simplificarural.domain.financial.CashViewScope
import br.com.simplificarural.domain.property.FarmScope
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** Local, validated registration gateway. UI and AI must call this instead of writing data directly. */
class FarmManagementService(context: Context) {
    private val store = ManagementRecordStore(context)

    fun registerPurchase(scope: FarmScope, product: String, quantity: BigDecimal, unit: String, unitPrice: BigDecimal, category: FinancialCategory, date: LocalDate = LocalDate.now(), description: String = "", inventoryCategory: String = "Outros"): ManagementRecord {
        requirePositive(quantity, "quantidade"); requirePositive(unitPrice, "preço de compra")
        return save(scope, ManagementRecordType.COMPRA, date, description.ifBlank { "Compra de $product" }, category, product, StockDirection.ENTRADA, quantity, unit, unitPrice, metadata = mapOf("inventoryCategory" to inventoryCategory))
    }

    fun registerSale(scope: FarmScope, product: String, quantity: BigDecimal, unit: String, unitPrice: BigDecimal, date: LocalDate = LocalDate.now(), description: String = "", lotId: String? = null, receivedAmount: BigDecimal = quantity * unitPrice): ManagementRecord {
        requirePositive(quantity, "quantidade"); requirePositive(unitPrice, "preço de venda")
        require(receivedAmount >= BigDecimal.ZERO && receivedAmount <= quantity * unitPrice) { "valor recebido inválido" }
        return save(scope, ManagementRecordType.VENDA, date, description.ifBlank { "Venda de $product" }, FinancialCategory.VENDA_PRODUCAO, product, StockDirection.SAIDA, quantity, unit, unitPrice, lotId, totalAmount = receivedAmount)
    }

    fun registerExpense(scope: FarmScope, category: FinancialCategory, amount: BigDecimal, date: LocalDate = LocalDate.now(), description: String): ManagementRecord {
        require(category != FinancialCategory.VENDA_PRODUCAO && category != FinancialCategory.OUTRA_RECEITA)
        requirePositive(amount, "valor da despesa")
        return save(scope, ManagementRecordType.DESPESA, date, description, category, totalAmount = amount)
    }

    fun registerOtherIncome(scope: FarmScope, amount: BigDecimal, date: LocalDate = LocalDate.now(), description: String): ManagementRecord {
        requirePositive(amount, "valor da receita")
        return save(scope, ManagementRecordType.VENDA, date, description, FinancialCategory.OUTRA_RECEITA, totalAmount = amount)
    }

    fun registerStockConsumption(scope: FarmScope, product: String, quantity: BigDecimal, unit: String, date: LocalDate = LocalDate.now(), lotId: String? = null): ManagementRecord {
        requirePositive(quantity, "quantidade")
        return save(scope, ManagementRecordType.CONSUMO_ESTOQUE, date, "Consumo de $product", FinancialCategory.RACAO, product, StockDirection.SAIDA, quantity, unit, lotId = lotId)
    }

    fun adjustInventory(scope: FarmScope, product: String, quantity: BigDecimal, unit: String, direction: StockDirection, date: LocalDate = LocalDate.now(), description: String): ManagementRecord {
        requirePositive(quantity, "quantidade")
        return save(scope, ManagementRecordType.AJUSTE_ESTOQUE, date, description, productName = product, stockDirection = direction, quantity = quantity, unit = unit)
    }

    fun registerEggProduction(scope: FarmScope, eggs: Int, date: LocalDate = LocalDate.now(), lotId: String? = null): ManagementRecord =
        production(scope, ManagementRecordType.PRODUCAO_OVOS, eggs, "unidades", date, lotId)

    fun registerMilkProduction(scope: FarmScope, liters: BigDecimal, date: LocalDate = LocalDate.now(), lotId: String? = null): ManagementRecord =
        production(scope, ManagementRecordType.PRODUCAO_LEITE, liters, "litros", date, lotId)

    fun registerSwineWeight(scope: FarmScope, animals: Int, initialWeightKg: BigDecimal, finalWeightKg: BigDecimal, days: Int, date: LocalDate = LocalDate.now(), lotId: String? = null): ManagementRecord {
        require(animals > 0 && days > 0); require(finalWeightKg >= initialWeightKg)
        return save(scope, ManagementRecordType.PESAGEM_SUINOS, date, "Pesagem de suínos", productName = "Suínos", quantity = finalWeightKg, unit = "kg", lotId = lotId,
            metadata = mapOf("animals" to animals.toString(), "initialWeightKg" to initialWeightKg.toPlainString(), "days" to days.toString()))
    }

    fun registerSwineFarrowing(scope: FarmScope, bornAlive: Int, bornDead: Int, weaned: Int? = null, date: LocalDate = LocalDate.now(), lotId: String? = null): ManagementRecord {
        require(bornAlive >= 0 && bornDead >= 0); weaned?.let { require(it in 0..bornAlive) }
        return save(scope, ManagementRecordType.PARTO_SUINOS, date, "Parto de matriz suína", productName = "Leitões", quantity = bornAlive.toBigDecimal(), unit = "leitões", lotId = lotId,
            metadata = buildMap { put("bornDead", bornDead.toString()); weaned?.let { put("weaned", it.toString()) } })
    }

    fun financialResult(scope: CashViewScope): FinancialResult = FinancialCalculator.result(store.all().filter { matches(it, scope) })
    fun stock(scope: CashViewScope): List<StockBalance> = StockCalculator.balance(store.all().filter { matches(it, scope) })
    fun swinePerformance(scope: CashViewScope): SwinePerformance = SwineCalculator.performance(store.all().filter { matches(it, scope) })
    fun cashEntries(scope: CashViewScope): List<CashEntry> = store.all().filter { matches(it, scope) }.mapNotNull { record ->
        val amount = record.totalAmount ?: return@mapNotNull null
        val kind = if (record.type == ManagementRecordType.VENDA) CashEntryKind.ENTRADA else CashEntryKind.SAIDA
        CashEntry(record.id, record.scope.organizationId, record.scope.farmId, record.scope.unitId, kind, amount, record.date, record.description)
    }

    /** Registros brutos para telas de histórico e gráficos, já isolados pela unidade selecionada. */
    fun records(scope: CashViewScope): List<ManagementRecord> = store.all().filter { matches(it, scope) }.sortedByDescending { it.createdAt }

    private fun production(scope: FarmScope, type: ManagementRecordType, amount: Number, unit: String, date: LocalDate, lotId: String?) =
        save(scope, type, date, if (type == ManagementRecordType.PRODUCAO_OVOS) "Produção de ovos" else "Produção de leite", productName = if (type == ManagementRecordType.PRODUCAO_OVOS) "Ovos" else "Leite", stockDirection = StockDirection.ENTRADA, quantity = amount.toString().toBigDecimal(), unit = unit, lotId = lotId)

    private fun save(scope: FarmScope, type: ManagementRecordType, date: LocalDate, description: String, category: FinancialCategory? = null, productName: String? = null, stockDirection: StockDirection? = null, quantity: BigDecimal? = null, unit: String? = null, unitPrice: BigDecimal? = null, lotId: String? = null, totalAmount: BigDecimal? = null, metadata: Map<String, String> = emptyMap()): ManagementRecord {
        val total = totalAmount ?: if (quantity != null && unitPrice != null) quantity * unitPrice else null
        return ManagementRecord(UUID.randomUUID().toString(), scope, type, date, description, category, productName, stockDirection, quantity, unit, unitPrice, total, lotId, metadata).also(store::append)
    }

    private fun matches(record: ManagementRecord, scope: CashViewScope) = when (scope) {
        is CashViewScope.SelectedUnit -> record.scope == scope.scope
        is CashViewScope.SelectedFarm -> record.scope.organizationId == scope.organizationId && record.scope.farmId == scope.farmId
        is CashViewScope.General -> record.scope.organizationId == scope.organizationId
    }
    private fun requirePositive(value: BigDecimal, field: String) = require(value > BigDecimal.ZERO) { "$field deve ser maior que zero." }
}

private object FinancialCalculator {
    fun result(records: List<ManagementRecord>): FinancialResult {
        val revenue = records.filter { it.type == ManagementRecordType.VENDA }.sumAmounts()
        val expenses = records.filter { it.type == ManagementRecordType.COMPRA || it.type == ManagementRecordType.DESPESA }
        return FinancialResult(revenue, expenses.filter { it.category?.isCashOperatingCost == true }.sumAmounts(), expenses.filter { it.category == FinancialCategory.MAO_DE_OBRA_FAMILIAR }.sumAmounts(), expenses.filter { it.category == FinancialCategory.DEPRECIACAO }.sumAmounts(), expenses.filter { it.category == FinancialCategory.JUROS_FINANCIAMENTO }.sumAmounts())
    }
    private fun List<ManagementRecord>.sumAmounts() = fold(BigDecimal.ZERO) { total, record -> total + (record.totalAmount ?: BigDecimal.ZERO) }
}

private object StockCalculator {
    fun balance(records: List<ManagementRecord>): List<StockBalance> = records.filter { it.productName != null && it.stockDirection != null && it.quantity != null && it.unit != null }
        .groupBy { listOf(it.productName!!, it.unit!!) }
        .map { (key, values) -> StockBalance(key[0], key[1], values.fold(BigDecimal.ZERO) { total, record -> total + if (record.stockDirection == StockDirection.ENTRADA) record.quantity!! else -record.quantity!! }, values.maxByOrNull { it.createdAt }?.metadata?.get("inventoryCategory") ?: "Outros") }
}

private object SwineCalculator {
    fun performance(records: List<ManagementRecord>): SwinePerformance {
        val weights = records.filter { it.type == ManagementRecordType.PESAGEM_SUINOS }
        val dailyGains = weights.mapNotNull { record ->
            val initial = record.metadata["initialWeightKg"]?.toBigDecimalOrNull() ?: return@mapNotNull null
            val days = record.metadata["days"]?.toBigDecimalOrNull() ?: return@mapNotNull null
            if (days <= BigDecimal.ZERO) null else (record.quantity!! - initial) / days
        }
        val farrowings = records.filter { it.type == ManagementRecordType.PARTO_SUINOS }
        val totalBornAlive = farrowings.sumOf { it.quantity ?: BigDecimal.ZERO }
        val totalWeaned = farrowings.sumOf { it.metadata["weaned"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO }
        val totalBornDead = farrowings.sumOf { it.metadata["bornDead"]?.toBigDecimalOrNull() ?: BigDecimal.ZERO }
        return SwinePerformance(
            averageDailyGainKg = dailyGains.takeIf { it.isNotEmpty() }?.reduce(BigDecimal::plus)?.divide(dailyGains.size.toBigDecimal(), 3, RoundingMode.HALF_UP),
            weanedPerLitter = farrowings.takeIf { it.isNotEmpty() }?.let { totalWeaned.divide(it.size.toBigDecimal(), 2, RoundingMode.HALF_UP) },
            preWeaningMortalityPercent = totalBornAlive.takeIf { it > BigDecimal.ZERO }?.let { totalBornDead * BigDecimal(100) / (totalBornAlive + totalBornDead) }
        )
    }
}

private class ManagementRecordStore(context: Context) {
    private val preferences = context.getSharedPreferences("rural_management", Context.MODE_PRIVATE)
    fun all(): List<ManagementRecord> = JSONArray(preferences.getString("records", "[]")).let { array -> (0 until array.length()).map { index -> array.getJSONObject(index).toRecord() } }
    fun append(record: ManagementRecord) { val records = JSONArray(preferences.getString("records", "[]")); records.put(record.toJson()); preferences.edit().putString("records", records.toString()).apply() }
    private fun ManagementRecord.toJson() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("type", type.name); put("date", date.toString()); put("description", description); put("category", category?.name); put("productName", productName); put("stockDirection", stockDirection?.name); put("quantity", quantity?.toPlainString()); put("unit", unit); put("unitPrice", unitPrice?.toPlainString()); put("totalAmount", totalAmount?.toPlainString()); put("lotId", lotId); put("metadata", JSONObject(metadata)); put("createdAt", createdAt.toString()) }
    private fun JSONObject.toRecord(): ManagementRecord {
        val metadataJson = optJSONObject("metadata") ?: JSONObject()
        val metadata = buildMap { metadataJson.keys().forEach { key -> put(key, metadataJson.optString(key)) } }
        return ManagementRecord(optString("id"), FarmScope(optString("organizationId"), optString("farmId"), optString("unitId").ifBlank { null }), ManagementRecordType.valueOf(getString("type")), LocalDate.parse(getString("date")), optString("description"), optString("category").takeIf { it.isNotBlank() }?.let(FinancialCategory::valueOf), optString("productName").ifBlank { null }, optString("stockDirection").takeIf { it.isNotBlank() }?.let(StockDirection::valueOf), optString("quantity").takeIf { it.isNotBlank() }?.toBigDecimal(), optString("unit").ifBlank { null }, optString("unitPrice").takeIf { it.isNotBlank() }?.toBigDecimal(), optString("totalAmount").takeIf { it.isNotBlank() }?.toBigDecimal(), optString("lotId").ifBlank { null }, metadata, optString("createdAt").ifBlank { null }?.let(java.time.LocalDateTime::parse) ?: LocalDate.parse(getString("date")).atStartOfDay())
    }
}
