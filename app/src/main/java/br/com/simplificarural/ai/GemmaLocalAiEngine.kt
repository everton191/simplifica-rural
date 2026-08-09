package br.com.simplificarural.ai

import android.content.Context
import br.com.simplificarural.domain.property.FarmScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

interface LocalAiEngine {
    suspend fun interpret(message: String, scope: FarmScope): AiDraft
}

/** LiteRT-LM engine. It owns inference only; it never receives database access. */
class GemmaLocalAiEngine(private val context: Context) : LocalAiEngine {

    override suspend fun interpret(message: String, scope: FarmScope): AiDraft = withContext(Dispatchers.Default) {
        val model = ModelDownloadWorker.modelFile(context)
        check(model.exists()) { "O modelo ainda não foi baixado." }
        val engine = inference ?: Engine(
            EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU(),
                cacheDir = context.cacheDir.absolutePath
            )
        ).also {
            it.initialize()
            inference = it
        }
        val response = engine.createConversation(
            ConversationConfig(
                systemInstruction = com.google.ai.edge.litertlm.Contents.of(SYSTEM_INSTRUCTION),
                samplerConfig = SamplerConfig(temperature = 0.2, topK = 20, topP = 0.8)
            )
        ).use { conversation ->
            conversation.sendMessage(prompt(message, scope)).contents.contents
                .filterIsInstance<Content.Text>()
                .joinToString(separator = "") { it.text }
        }
        AiResponseParser.parse(response)
    }

    private fun prompt(message: String, scope: FarmScope) = """
        Contexto atual: fazenda ${scope.farmId}, unidade ${scope.unitId ?: "fazenda toda"}.
        Não altere essa seleção e não misture dados de outra fazenda ou granja.
        Responda SOMENTE JSON válido neste formato:
        {"action":"REGISTRAR_OVOS|REGISTRAR_LEITE|REGISTRAR_RACAO|REGISTRAR_DESPESA|CONSULTAR_RESUMO|DESCONHECIDA","parameters":{"chave":"valor"},"summary":"texto curto em português"}
        Mensagem: $message
    """.trimIndent()

    private companion object {
        @Volatile private var inference: Engine? = null
        const val SYSTEM_INSTRUCTION = """
            Você é o Assistente Rural. Interprete somente a mensagem do produtor.
            Nunca calcule valores, nunca invente dados e nunca confirme uma gravação.
            Você não altera dados: apenas propõe um rascunho para confirmação humana.
        """
    }
}

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
