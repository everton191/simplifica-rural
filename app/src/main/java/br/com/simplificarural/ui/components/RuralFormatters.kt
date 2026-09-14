package br.com.simplificarural.ui.components

import java.math.BigDecimal

fun money(value: BigDecimal): String = "R$ ${value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',')}"
fun String.decimal(): BigDecimal = replace(',', '.').toBigDecimal()
fun stockCategory(name: String): String = when {
    name.contains("raç", true) || name.contains("silagem", true) || name.contains("farelo", true) -> "Alimentação"
    name.contains("vacina", true) || name.contains("remédio", true) || name.contains("medic", true) -> "Saúde"
    name.contains("martelo", true) || name.contains("ferrament", true) -> "Ferramentas"
    else -> "Outros"
}
fun itemCategory(item: br.com.simplificarural.domain.management.StockBalance): String = item.inventoryCategory.takeIf { it in setOf("Alimentação", "Saúde", "Ferramentas", "Outros") } ?: stockCategory(item.productName)
