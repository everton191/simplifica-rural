package br.com.simplificarural.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import br.com.simplificarural.domain.actions.QuickCommand

class Lfm3ToolHintsTest {
    @Test fun correctsStockDirectionAndKeepsCollectionSeparate() {
        val eggs = QuickCommand.Stock(true, 150.toBigDecimal(), "unidades", "ovo")
        val feed = QuickCommand.Stock(true, "2.5".toBigDecimal(), "kg", "ração")
        assertEquals(listOf(QuickCommand.Eggs(150), feed.copy(incoming = false)), Lfm3ToolHints.validate(
            "Colhi 150 ovos e dei saída de 2,5 quilos de ração", listOf(eggs, feed)
        ))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesToTurnFinancialIntentIntoCollection() {
        Lfm3ToolHints.validate("Vendi 300 ovos", listOf(QuickCommand.Eggs(300)))
    }
    @Test fun retainsTheTwoToolsForACompoundRequest() {
        val input = "hoje apanhei 300 ovos e tambem tirei 12,5 litros de leite"
        val tools = Lfm3ToolHints.tools(input)
        assertEquals(setOf("registrar_ovos", "registrar_leite"), tools)
        assertNull(Lfm3ToolHints.requestMissingData(input, tools))
    }

    @Test fun asksForMissingUnitAndAnimalBeforeInference() {
        listOf("da baixa de 5 de racao no estoque", "vacinei a vaca contra aftosa").forEach { input ->
            assertNotNull(Lfm3ToolHints.requestMissingData(input, Lfm3ToolHints.tools(input)))
        }
        val complete = "da baixa de 5 kg de racao no estoque"
        assertNull(Lfm3ToolHints.requestMissingData(complete, Lfm3ToolHints.tools(complete)))
    }
}
