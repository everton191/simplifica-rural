package br.com.simplificarural.ai

import br.com.simplificarural.domain.actions.QuickCommand
import br.com.simplificarural.domain.actions.parseQuickCommand
import br.com.simplificarural.domain.animals.AnimalSpecies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class QuickCommandTest {
    @Test fun keepsPurchasesExpensesAndReceiptsSeparate() {
        assertEquals(QuickCommand.Expense("combustivel", BigDecimal("80")),
            parseQuickCommand("Gastei 80 reais com combustível"))
        assertEquals(QuickCommand.Trade(true, "racao", BigDecimal("20"), "kg", BigDecimal("100"), null),
            parseQuickCommand("Comprei 20 kg de ração por 100 reais"))
        assertEquals(QuickCommand.Trade(false, "milho", BigDecimal("10"), "kg", BigDecimal("30"), BigDecimal("3")),
            parseQuickCommand("Vendi 10 kg de milho a 3 reais por kg"))
        assertNull(parseQuickCommand("Comprei 20 kg de ração"))
    }

    @Test fun distinguishesEggSalesFromCollection() {
        assertEquals(QuickCommand.LooseEggSale(300), parseQuickCommand("Eu vendi trezentos ovos"))
        assertEquals(QuickCommand.LooseEggSale(300, BigDecimal("360"), BigDecimal("1.20")),
            (parseQuickCommand("Vendi 300 ovos a 1,20 cada") as QuickCommand.LooseEggSale).let {
                it.copy(total = it.total?.stripTrailingZeros()?.toPlainString()?.toBigDecimal())
            })
        assertEquals(QuickCommand.LooseEggSale(300, BigDecimal("250")), parseQuickCommand("Vendi 300 ovos por 250 reais no total"))
        assertNull(parseQuickCommand("Não vendi 300 ovos"))
    }

    @Test fun distinguishesMeasuredMilkSalesFromProduction() {
        assertEquals(QuickCommand.Milk(BigDecimal("50")), parseQuickCommand("Tirei 50l de leite"))
        assertEquals(QuickCommand.MilkSale(BigDecimal("30"), false), parseQuickCommand("Vendi 30 litros"))
        assertEquals(QuickCommand.MilkSale(BigDecimal("30"), true, BigDecimal("90"), BigDecimal("3")),
            parseQuickCommand("Vendi 30 litros de leite a 3 reais por litro"))
        assertNull(parseQuickCommand("Ainda vou vender 30 litros de leite"))
    }

    @Test fun saleKeepsEachPriceAndCalculatesTotal() {
        val command = parseQuickCommand("Vendi 50 bandejas a 12 reais 40 a 15") as QuickCommand.EggTraySale
        assertEquals(listOf(BigDecimal("12"), BigDecimal("15")), command.lines.map { it.unitPrice })
        assertEquals(BigDecimal("90"), command.lines.sumOf { it.trays })
        assertEquals(BigDecimal("1200"), command.lines.sumOf { it.subtotal })
        val rounded = parseQuickCommand("Vendi 10 bandejas por R$ 12,50 cada e 2 por 13") as QuickCommand.EggTraySale
        assertTrue(rounded.lines.sumOf { it.subtotal }.compareTo(BigDecimal("151")) == 0)
        assertNull(parseQuickCommand("Vendi 50 bandejas a 12 reais e 40 a"))
        assertNull(parseQuickCommand("Vendi 50 bandejas a 12 reais e 40 cartelas a 15"))
        assertNull(parseQuickCommand("Vendi 50 bandejas a -12 reais"))
    }

    @Test fun recognizesProductionAndMovement() {
        assertEquals(QuickCommand.Eggs(300), parseQuickCommand("Apanhei trezentos ovos!"))
        assertEquals(QuickCommand.Eggs(300), parseQuickCommand("300 ovos"))
        assertEquals(QuickCommand.Eggs(300), parseQuickCommand("Eu coloquei 300 ovos"))
        assertEquals(QuickCommand.Milk(BigDecimal("12.5")), parseQuickCommand("Ordenhei 12,5 litros de leite"))
        assertEquals(QuickCommand.Stock(true, BigDecimal("20"), "kg", "racao"), parseQuickCommand("Dei entrada de 20 kg de ração"))
        assertEquals(QuickCommand.Stock(false, BigDecimal("5"), "kg", "racao"), parseQuickCommand("Usei cinco quilos de ração"))
        assertEquals(QuickCommand.Vaccine(AnimalSpecies.BOVINO, "123", "aftosa"), parseQuickCommand("Vacinei a vaca 123 contra aftosa"))
        assertEquals(QuickCommand.SwineBirth(8, 1), parseQuickCommand("A matriz suína pariu 8 leitões vivos e 1 morto"))
    }

    @Test fun rejectsAmbiguousOrUnsafeMovements() {
        listOf(
            "Usei -5 kg de ração", "Usei 1.000 kg de ração", "Peguei 3 ovos?",
            "Tirei 5 litros", "Ração 10 kg", "Apanhei 300 ovos e usei 5 kg de ração",
            "Usei 5 kg de ração e apanhei 300 ovos", "Usei 0 kg de ração",
            "Adicionei 1,5 unidades de balde", "Não apanhei 300 ovos"
        ).forEach { assertNull(it, parseQuickCommand(it)) }
    }
}
