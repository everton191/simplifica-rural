package br.com.simplificarural.domain.management

import java.math.BigDecimal

internal data class DailyStockMovement(
    val product: String,
    val unit: String,
    val incoming: BigDecimal,
    val outgoing: BigDecimal
)

internal data class DailyManagementTotals(
    val eggs: BigDecimal,
    val milk: BigDecimal,
    val swineWeighings: Int,
    val swineBirths: Int,
    val livePiglets: BigDecimal,
    val stock: List<DailyStockMovement>
) {
    companion object {
        fun from(records: List<ManagementRecord>): DailyManagementTotals {
            val kinds = records.groupBy { it.type }
            fun quantity(type: ManagementRecordType) =
                kinds[type].orEmpty().fold(BigDecimal.ZERO) { sum, record -> sum + (record.quantity ?: BigDecimal.ZERO) }
            val stock = records.mapNotNull { record ->
                val product = record.productName ?: return@mapNotNull null
                val unit = record.unit ?: return@mapNotNull null
                if (record.stockDirection == null || record.quantity == null) return@mapNotNull null
                (product to unit) to record
            }.groupBy({ it.first }, { it.second }).map { (key, movements) ->
                fun sum(direction: StockDirection) = movements.asSequence()
                    .filter { it.stockDirection == direction }
                    .fold(BigDecimal.ZERO) { total, movement -> total + requireNotNull(movement.quantity) }
                DailyStockMovement(key.first, key.second, sum(StockDirection.ENTRADA), sum(StockDirection.SAIDA))
            }.sortedWith(compareBy({ it.product.lowercase() }, { it.unit }))
            return DailyManagementTotals(
                eggs = quantity(ManagementRecordType.PRODUCAO_OVOS),
                milk = quantity(ManagementRecordType.PRODUCAO_LEITE),
                swineWeighings = kinds[ManagementRecordType.PESAGEM_SUINOS].orEmpty().size,
                swineBirths = kinds[ManagementRecordType.PARTO_SUINOS].orEmpty().size,
                livePiglets = quantity(ManagementRecordType.PARTO_SUINOS),
                stock = stock
            )
        }
    }
}
