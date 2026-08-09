package br.com.simplificarural.domain.inventory

import br.com.simplificarural.domain.property.FarmScope
import java.math.BigDecimal
import java.time.LocalDate

enum class InventoryCategory { ALIMENTACAO, SAUDE, INSUMO, EMBALAGEM, PECA, EQUIPAMENTO, OUTRO }
enum class InventoryMovementType { ENTRADA, SAIDA, AJUSTE_POSITIVO, AJUSTE_NEGATIVO }

data class InventoryItem(val id: String, val scope: FarmScope, val name: String, val category: InventoryCategory, val unit: String, val minimumQuantity: BigDecimal? = null)
data class InventoryMovement(val id: String, val itemId: String, val scope: FarmScope, val type: InventoryMovementType, val quantity: BigDecimal, val date: LocalDate, val source: String, val destination: String? = null, val unitCost: BigDecimal? = null)
