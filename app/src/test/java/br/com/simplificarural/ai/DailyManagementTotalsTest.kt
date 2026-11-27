package br.com.simplificarural.ai

import br.com.simplificarural.domain.management.DailyManagementTotals
import br.com.simplificarural.domain.management.ManagementRecord
import br.com.simplificarural.domain.management.ManagementRecordType
import br.com.simplificarural.domain.management.StockDirection
import br.com.simplificarural.domain.property.FarmScope
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DailyManagementTotalsTest {
    @Test fun separatesProductionAndStockByProductAndUnit() {
        val scope = FarmScope("org", "fazenda")
        val date = LocalDate.of(2026, 11, 26)
        fun movement(type: ManagementRecordType, product: String, unit: String, quantity: String, direction: StockDirection) =
            ManagementRecord("$type-$quantity", scope, type, date, product, productName = product,
                stockDirection = direction, quantity = quantity.toBigDecimal(), unit = unit)
        val totals = DailyManagementTotals.from(listOf(
            movement(ManagementRecordType.PRODUCAO_OVOS, "Ovos", "unidades", "300", StockDirection.ENTRADA),
            movement(ManagementRecordType.PRODUCAO_LEITE, "Leite", "litros", "12.5", StockDirection.ENTRADA),
            movement(ManagementRecordType.CONSUMO_ESTOQUE, "Ovos", "unidades", "90", StockDirection.SAIDA),
            movement(ManagementRecordType.AJUSTE_ESTOQUE, "Ração", "kg", "20", StockDirection.ENTRADA),
            movement(ManagementRecordType.AJUSTE_ESTOQUE, "Ração", "unidades", "2", StockDirection.ENTRADA)
        ))
        assertEquals("300".toBigDecimal(), totals.eggs)
        assertEquals("12.5".toBigDecimal(), totals.milk)
        assertEquals("90".toBigDecimal(), totals.stock.single { it.product == "Ovos" }.outgoing)
        assertEquals(setOf("kg", "unidades"), totals.stock.filter { it.product == "Ração" }.map { it.unit }.toSet())
    }
}
