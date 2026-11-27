package br.com.simplificarural.ui

import android.content.Context
import br.com.simplificarural.ai.Lfm3QaEngine
import br.com.simplificarural.ai.Lfm3ToolHints
import br.com.simplificarural.ai.RuralLanguageNormalizer
import br.com.simplificarural.domain.actions.QuickCommand
import br.com.simplificarural.domain.animals.AnimalSpecies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Prototype used only by the separate LFM3 QA application. */
internal class Lfm3QaClient(private val context: Context) {
    private val definition = context.assets.open("lfm3-qa-tools.json")
        .bufferedReader().use { JSONObject(it.readText()) }

    suspend fun propose(input: String): List<QuickCommand> = withContext(Dispatchers.IO) {
        val normalized = RuralLanguageNormalizer.normalize(input)
        require(!Regex("""\b(?:amanha|qual seria|como faco|se eu|nao (?:registre|lance|anote|salve))\b""")
            .containsMatchIn(normalized)) { "Informe o que já aconteceu. Nenhum lançamento foi preparado." }
        val knownTopics = Lfm3ToolHints.tools(normalized)
        Lfm3ToolHints.requestMissingData(normalized, knownTopics)?.let { error(it) }
        val available = definition.getJSONArray("tools")
        val tools = if (knownTopics.isEmpty()) available else JSONArray().apply {
            (0 until available.length()).forEach { index ->
                val tool = available.getJSONObject(index)
                val name = tool.getJSONObject("function").getString("name")
                if (name in knownTopics || name == "pedir_esclarecimento") put(tool)
            }
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", definition.getString("system")))
            .put(JSONObject().put("role", "user").put("content", input))
        val request = JSONObject().put("messages", messages).put("tools", tools)
            .put("tool_choice", "auto").put("temperature", 0.1).put("max_tokens", 256).put("seed", 41)
            .put("top_k", 50).put("repeat_penalty", 1.1)
            .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            .put("thinking_budget_tokens", 0)
        val engine = Lfm3QaEngine.endpoint(context)
        val connection = URL("${engine.base}/v1/chat/completions").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${engine.key}")
            connection.doOutput = true
            connection.connectTimeout = 3_000
            connection.readTimeout = 120_000
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.toString()) }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            val message = response.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val calls = message.optJSONArray("tool_calls")
            require(calls != null && calls.length() > 0) {
                "Faltou entender o lançamento. Diga o que aconteceu, a quantidade e a unidade, ou use o formulário do módulo."
            }
            require(calls.length() <= 4) { "Separe o pedido em até quatro lançamentos por vez." }
            val proposals = (0 until calls.length()).map { index ->
                val function = calls.getJSONObject(index).getJSONObject("function")
                val name = function.getString("name")
                require((0 until tools.length()).any { tools.getJSONObject(it).getJSONObject("function").getString("name") == name }) { "Ferramenta fora do pedido." }
                decode(name, JSONObject(function.getString("arguments")))
            }
            Lfm3ToolHints.validate(input, proposals)
        } catch (_: java.io.IOException) {
            error("Motor LFM3 indisponível ou demorou demais no aparelho.")
        } finally {
            connection.disconnect()
        }
    }

    private fun decode(name: String, args: JSONObject): QuickCommand {
        fun fields(vararg expected: String) {
            require(args.keys().asSequence().toSet() == expected.toSet()) { "O modelo devolveu campos inválidos." }
        }
        fun text(key: String): String = (args.get(key) as? String).orEmpty().trim().take(100)
            .also { require(it.isNotBlank()) { "Falta o campo $key." } }
        fun integer(key: String, minimum: Int): Int = args.get(key).toString().toIntOrNull()
            ?.takeIf { it >= minimum } ?: error("Número inválido em $key.")
        fun quantity(key: String) = args.get(key).toString().toBigDecimalOrNull()
            ?.takeIf { it.signum() > 0 && it.precision() <= 12 && it.scale() in -6..6 }
            ?: error("Quantidade inválida em $key.")

        return when (name) {
            "registrar_ovos" -> { fields("quantidade"); QuickCommand.Eggs(integer("quantidade", 1)) }
            "registrar_leite" -> { fields("litros"); QuickCommand.Milk(quantity("litros")) }
            "movimentar_estoque" -> {
                fields("direcao", "quantidade", "unidade", "produto")
                val direction = text("direcao")
                val unit = text("unidade")
                val amount = quantity("quantidade")
                require(direction in listOf("entrada", "saida") && unit in listOf("kg", "litros", "unidades"))
                require(unit != "unidades" || amount.stripTrailingZeros().scale() <= 0)
                QuickCommand.Stock(direction == "entrada", amount, unit, text("produto"))
            }
            "registrar_vacina" -> {
                fields("especie", "animal", "vacina")
                val target = text("animal").replace(Regex("^brinco\\s+", RegexOption.IGNORE_CASE), "")
                require(target.isNotBlank())
                QuickCommand.Vaccine(AnimalSpecies.valueOf(text("especie").uppercase()), target, text("vacina"))
            }
            "registrar_parto_suino" -> {
                fields("vivos", "mortos")
                val alive = integer("vivos", 0)
                val dead = integer("mortos", 0)
                require(alive > 0 || dead > 0)
                QuickCommand.SwineBirth(alive, dead)
            }
            "pedir_esclarecimento" -> { fields("pergunta"); error(text("pergunta")) }
            else -> error("Ferramenta desconhecida.")
        }
    }
}
