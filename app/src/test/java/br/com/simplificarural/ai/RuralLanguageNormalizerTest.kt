package br.com.simplificarural.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class RuralLanguageNormalizerTest {
    private val parser = DeterministicCommandParser()

    @Test fun `entende sotaque informal e bandejas com ovos avulsos`() {
        val draft = parser.parse(RuralLanguageNormalizer.normalize("panhei trinta bandejas e mais quinze ovos"))
        assertEquals(RuralActionType.REGISTRAR_OVOS, draft.action)
        assertEquals("915", draft.parameters["ovos"])
    }

    @Test fun `entende variacao nordestina de coleta`() {
        val draft = parser.parse(RuralLanguageNormalizer.normalize("juntei duzentos ovos hoje"))
        assertEquals("200", draft.parameters["ovos"])
    }

    @Test fun `ignora marcadores regionais sem perder a quantidade`() {
        val frases = listOf(
            "uai apanhei cento e cinquenta ovos",
            "oxente juntei 30 bandejas e mais 15 ovos",
            "bah peguei doze ovos hoje",
            "visse catei trinta ovos"
        )
        val esperados = listOf("150", "915", "12", "30")
        frases.zip(esperados).forEach { (frase, esperado) ->
            assertEquals(esperado, parser.parse(RuralLanguageNormalizer.normalize(frase)).parameters["ovos"])
        }
    }

    @Test fun `entende erro comum na racao`() {
        val normalized = RuralLanguageNormalizer.normalize("dei vinte quilos de rassao")
        assertEquals("registrei 20 kg de racao", normalized)
        val draft = parser.parse(normalized)
        assertEquals(RuralActionType.REGISTRAR_RACAO, draft.action)
        assertEquals("20", draft.parameters["racaoKg"])
    }

    @Test fun `calcula lotes bandejas ovos avulsos e descarte`() {
        val draft = parser.parse(RuralLanguageNormalizer.normalize("30 lotes de 20 bandejas e 15 bandejas e 10 ovos, 40 foram quebrados e descartados"))
        assertEquals(RuralActionType.REGISTRAR_OVOS, draft.action)
        assertEquals("18420", draft.parameters["ovos"])
        assertEquals("18460", draft.parameters["ovosBrutos"])
        assertEquals("40", draft.parameters["ovosDescartados"])
    }

    @Test fun `soma maneiras diferentes de informar o mesmo deposito de ovos`() {
        val draft = parser.parse(RuralLanguageNormalizer.normalize("apanhei 15 bandejas, 1 lote com 10 e mais 600 ovos"))
        assertEquals(RuralActionType.REGISTRAR_OVOS, draft.action)
        assertEquals("1350", draft.parameters["ovos"])
        assertEquals("25", draft.parameters["bandejas"])
        assertEquals("1", draft.parameters["lotes"])
    }

    @Test fun `calcula leite em tambores de cinquenta litros`() {
        val draft = parser.parse(RuralLanguageNormalizer.normalize("20 tambores de 50 litros e mais 34 litros de leite"))
        assertEquals(RuralActionType.REGISTRAR_LEITE, draft.action)
        assertEquals("1034", draft.parameters["litros"])
    }

    @Test fun `mantem contexto da compra e calcula preco por lote`() {
        val purchase = parser.parse(RuralLanguageNormalizer.normalize("comprei 3 lotes de bandeja com 100 unidades em cada"))
        assertEquals(RuralActionType.INFORMAR_PRECO_COMPRA, purchase.action)
        assertEquals("300", purchase.parameters["quantidade"])

        val confirmation = parser.continuePurchase(purchase, RuralLanguageNormalizer.normalize("foi 50 reais cada lote"))
        assertEquals(RuralActionType.REGISTRAR_COMPRA_ESTOQUE, confirmation?.action)
        assertEquals("0.5", confirmation?.parameters?.get("precoUnitario"))
        assertEquals("150", confirmation?.parameters?.get("valorTotal"))
    }

    @Test fun `prepara venda parcial com saldo a receber`() {
        val sale = parser.parse(RuralLanguageNormalizer.normalize("vendi 50 bandejas de ovos a 10 reais a fulano ele pagou metade"))
        assertEquals(RuralActionType.INFORMAR_VENCIMENTO_VENDA, sale.action)
        assertEquals("250", sale.parameters["valorRecebido"])
        assertEquals("250", sale.parameters["saldoAberto"])
        val confirmation = parser.continueSale(sale, RuralLanguageNormalizer.normalize("ele paga dia 20/08"))
        assertEquals(RuralActionType.REGISTRAR_VENDA_ESTOQUE, confirmation?.action)
        assertEquals("2026-08-20", confirmation?.parameters?.get("vencimento"))
    }
}
