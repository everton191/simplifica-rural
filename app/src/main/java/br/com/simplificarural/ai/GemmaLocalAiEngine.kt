package br.com.simplificarural.ai

import android.content.Context
import br.com.simplificarural.domain.property.FarmScope
import org.json.JSONObject

interface LocalAiEngine {
    suspend fun interpret(message: String, scope: FarmScope, history: List<RuralConversationMessage> = emptyList()): AiDraft
}

/** Uses the family-wide engine. It sends context only; the provider never receives database access. */
class GemmaLocalAiEngine(private val context: Context) : LocalAiEngine {
    private val sharedAi = SharedAiClient(context)

    override suspend fun interpret(message: String, scope: FarmScope, history: List<RuralConversationMessage>): AiDraft {
        val response = sharedAi.generate(SYSTEM_INSTRUCTION, prompt(message, scope, history))
        return AiResponseParser.parse(response)
    }

    suspend fun chat(message: String, scope: FarmScope, history: List<RuralConversationMessage>): String {
        val recent = history.takeLast(12).joinToString("\n") { "${it.role}: ${it.text.take(700)}" }
        return sharedAi.generate(
            "Você é o Assistente Rural da família Simplifica. Converse normalmente em português claro. Responda perguntas gerais e mantenha o contexto recente. Não invente dados da propriedade e não afirme que salvou algo.",
            "Contexto selecionado: fazenda ${scope.farmId}, unidade ${scope.unitId ?: "fazenda toda"}.\nConversa recente:\n${recent.ifBlank { "Sem conversa anterior." }}\nUsuário: $message"
        ).trim()
    }

    private fun prompt(message: String, scope: FarmScope, history: List<RuralConversationMessage>) = """
        Contexto atual: fazenda ${scope.farmId}, unidade ${scope.unitId ?: "fazenda toda"}.
        Não altere essa seleção e não misture dados de outra fazenda ou granja.
        Conversa recente:
        ${history.takeLast(12).joinToString("\n") { "${it.role}: ${it.text.take(700)}" }.ifBlank { "Sem conversa anterior." }}
        Responda SOMENTE JSON válido neste formato:
        {"action":"REGISTRAR_OVOS|REGISTRAR_LEITE|REGISTRAR_RACAO|REGISTRAR_DESPESA|CONSULTAR_RESUMO|DESCONHECIDA","parameters":{"chave":"valor"},"summary":"texto curto em português"}
        Para pergunta geral ou conversa comum, use DESCONHECIDA e coloque a resposta natural completa em summary.
        Mensagem atual: $message
    """.trimIndent()

    private companion object {
        const val SYSTEM_INSTRUCTION = """
            Você é o Assistente Rural. Interprete somente a mensagem do produtor.
            Nunca calcule valores, nunca invente dados e nunca confirme uma gravação.
            Você não altera dados: apenas propõe um rascunho para confirmação humana.
        """
    }
}

data class RuralConversationMessage(val role: String, val text: String)

object AiResponseParser {
    fun parse(raw: String): AiDraft {
        val jsonText = raw.substringAfter('{').substringBeforeLast('}').replace(Regex("\\]\\s*$"), "").let { "{$it}" }
        val json = JSONObject(jsonText)
        val action = runCatching { RuralActionType.valueOf(json.optString("action")) }
            .getOrDefault(RuralActionType.DESCONHECIDA)
        val paramsObject = json.optJSONObject("parameters")
        val params = buildMap {
            paramsObject?.let { parameterObject ->
                parameterObject.keys().forEach { key ->
                    put(key, parameterObject.opt(key)?.toString().orEmpty())
                }
            }
        }
        return AiDraft(action, params, json.optString("summary", "Entendi o registro informado."))
    }
}
