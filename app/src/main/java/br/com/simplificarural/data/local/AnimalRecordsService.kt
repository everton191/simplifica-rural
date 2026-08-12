package br.com.simplificarural.data.local

import android.content.Context
import br.com.simplificarural.domain.agenda.AgendaStatus
import br.com.simplificarural.domain.agenda.AgendaType
import br.com.simplificarural.domain.agenda.RuralTask
import br.com.simplificarural.domain.animals.Animal
import br.com.simplificarural.domain.animals.AnimalBatch
import br.com.simplificarural.domain.animals.AnimalSex
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.animals.AnimalStatus
import br.com.simplificarural.domain.health.HealthEvent
import br.com.simplificarural.domain.health.HealthEventType
import br.com.simplificarural.domain.property.FarmScope
import br.com.simplificarural.domain.reproduction.ReproductionEvent
import br.com.simplificarural.domain.reproduction.ReproductionStage
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** Local, durable record book for identified animals, batches, health history and appointments. */
class AnimalRecordsService(context: Context) {
    private val prefs = context.getSharedPreferences("animal_records", Context.MODE_PRIVATE)

    fun animals(scope: FarmScope, species: AnimalSpecies? = null): List<Animal> = array("animals").map(::animal)
        .filter { it.scope == scope && (species == null || it.species == species) }
    fun batches(scope: FarmScope, species: AnimalSpecies? = null): List<AnimalBatch> = array("batches").map(::batch)
        .filter { it.scope == scope && (species == null || it.species == species) }
    fun healthHistory(targetId: String): List<HealthEvent> = array("health").map(::health).filter { it.animalOrBatchId == targetId }.sortedByDescending { it.date }
    fun healthHistory(scope: FarmScope): List<HealthEvent> = array("health").map(::health).filter { it.scope == scope }.sortedByDescending { it.date }
    fun reproductionHistory(targetId: String): List<ReproductionEvent> = array("reproduction").map(::reproduction).filter { it.animalId == targetId }.sortedByDescending { it.date }
    fun tasks(scope: FarmScope): List<RuralTask> = array("tasks").map(::task).filter { it.scope == scope }.sortedBy { it.dueDate }

    fun registerAnimal(scope: FarmScope, species: AnimalSpecies, identification: String, sex: AnimalSex = AnimalSex.NAO_INFORMADO, notes: String? = null): Animal {
        require(identification.isNotBlank()) { "Informe nome, brinco ou identificação." }
        require(animals(scope, species).none { it.identification.equals(identification.trim(), true) }) { "Já existe um animal com essa identificação." }
        return Animal(UUID.randomUUID().toString(), scope, species, identification.trim(), sex, notes = notes?.trim()?.ifBlank { null }).also { append("animals", it.json()) }
    }

    fun registerBatch(scope: FarmScope, species: AnimalSpecies, name: String, quantity: Int): AnimalBatch {
        require(name.isNotBlank() && quantity > 0) { "Informe lote e quantidade válida." }
        return AnimalBatch(UUID.randomUUID().toString(), scope, species, name.trim(), LocalDate.now(), quantity, quantity).also { append("batches", it.json()) }
    }
    fun updateBatch(batch: AnimalBatch) {
        val values = JSONArray(prefs.getString("batches", "[]")); for (index in 0 until values.length()) if (values.getJSONObject(index).optString("id") == batch.id) { values.put(index, batch.json()); prefs.edit().putString("batches", values.toString()).apply(); return }; error("Lote não encontrado.")
    }
    fun registerMortality(scope: FarmScope, batchId: String, quantity: Int, cause: String) {
        val batch = batches(scope).firstOrNull { it.id == batchId } ?: error("Lote não encontrado."); require(quantity > 0 && quantity <= batch.currentQuantity) { "Quantidade de mortalidade inválida." }; updateBatch(batch.copy(currentQuantity = batch.currentQuantity - quantity)); schedule(scope, "Mortalidade: ${batch.name} ($quantity)", LocalDate.now(), AgendaType.SAUDE, batchId, cause)
    }

    fun updateAnimal(animal: Animal) {
        val values = JSONArray(prefs.getString("animals", "[]"))
        for (index in 0 until values.length()) if (values.getJSONObject(index).optString("id") == animal.id) { values.put(index, animal.json()); prefs.edit().putString("animals", values.toString()).apply(); return }
        error("Animal não encontrado.")
    }

    /** Keeps the record and its history; the animal only leaves the active list. */
    fun changeAnimalStatus(scope: FarmScope, id: String, status: AnimalStatus) {
        val animal = animals(scope).firstOrNull { it.id == id } ?: error("Animal não encontrado.")
        updateAnimal(animal.copy(status = status))
    }

    /** Keeps the batch traceable instead of deleting its sanitary and mortality history. */
    fun changeBatchStatus(scope: FarmScope, id: String, status: AnimalStatus) {
        val batch = batches(scope).firstOrNull { it.id == id } ?: error("Lote não encontrado.")
        updateBatch(batch.copy(status = status))
    }

    fun removeAnimal(id: String) {
        val values = JSONArray(prefs.getString("animals", "[]"))
        for (index in 0 until values.length()) if (values.getJSONObject(index).optString("id") == id) { values.remove(index); prefs.edit().putString("animals", values.toString()).apply(); return }
    }

    fun findTarget(scope: FarmScope, species: AnimalSpecies, identification: String): String? =
        animals(scope, species).firstOrNull { it.identification.equals(identification.trim(), true) }?.id
            ?: batches(scope, species).firstOrNull { it.name.equals(identification.trim(), true) }?.id

    fun registerHealth(scope: FarmScope, targetId: String, product: String, type: HealthEventType = HealthEventType.VACINA, date: LocalDate = LocalDate.now(), nextDueDate: LocalDate? = null): HealthEvent {
        require((animals(scope) + batches(scope).map { Animal(it.id, it.scope, it.species, it.name, AnimalSex.NAO_INFORMADO) }).any { it.id == targetId }) { "Animal ou lote não encontrado." }
        val event = HealthEvent(UUID.randomUUID().toString(), scope, targetId, type, date, product.trim(), nextDueDate = nextDueDate)
        append("health", event.json())
        nextDueDate?.let { schedule(scope, "Retorno: ${event.productOrCondition}", it, AgendaType.SAUDE, targetId) }
        return event
    }

    fun registerReproduction(scope: FarmScope, targetId: String, stage: ReproductionStage, date: LocalDate = LocalDate.now(), expectedBirthDate: LocalDate? = null, bornAlive: Int? = null, bornDead: Int? = null, notes: String? = null): ReproductionEvent {
        val animal = animals(scope).firstOrNull { it.id == targetId } ?: error("Animal não encontrado.")
        require(bornAlive == null || bornAlive >= 0); require(bornDead == null || bornDead >= 0)
        return ReproductionEvent(UUID.randomUUID().toString(), scope, animal.id, animal.species, stage, date, expectedBirthDate, bornAlive, bornDead, weaned = null, notes = notes?.trim()?.ifBlank { null }).also { append("reproduction", it.json()) }
    }

    fun schedule(scope: FarmScope, title: String, dueDate: LocalDate, type: AgendaType = AgendaType.OUTRO, sourceRecordId: String? = null, notes: String? = null): RuralTask {
        require(title.isNotBlank())
        return RuralTask(UUID.randomUUID().toString(), scope, type, title.trim(), dueDate, sourceRecordId = sourceRecordId, notes = notes?.trim()?.ifBlank { null }).also { append("tasks", it.json()) }
    }

    private fun array(key: String): List<JSONObject> = JSONArray(prefs.getString(key, "[]")).let { json -> (0 until json.length()).map(json::getJSONObject) }
    private fun append(key: String, value: JSONObject) { val values = JSONArray(prefs.getString(key, "[]")); values.put(value); prefs.edit().putString(key, values.toString()).apply() }
    private fun scope(json: JSONObject) = FarmScope(json.getString("organizationId"), json.getString("farmId"), json.optString("unitId").ifBlank { null })
    private fun Animal.json() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("species", species.name); put("identification", identification); put("sex", sex.name); put("notes", notes); put("status", status.name) }
    private fun AnimalBatch.json() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("species", species.name); put("name", name); put("startedAt", startedAt.toString()); put("initialQuantity", initialQuantity); put("currentQuantity", currentQuantity); put("status", status.name) }
    private fun HealthEvent.json() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("targetId", animalOrBatchId); put("type", type.name); put("date", date.toString()); put("product", productOrCondition); put("nextDueDate", nextDueDate?.toString()) }
    private fun ReproductionEvent.json() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("animalId", animalId); put("species", species.name); put("stage", stage.name); put("date", date.toString()); put("expectedBirthDate", expectedBirthDate?.toString()); put("bornAlive", bornAlive); put("bornDead", bornDead); put("weaned", weaned); put("notes", notes) }
    private fun RuralTask.json() = JSONObject().apply { put("id", id); put("organizationId", scope.organizationId); put("farmId", scope.farmId); put("unitId", scope.unitId); put("type", type.name); put("title", title); put("dueDate", dueDate.toString()); put("status", status.name); put("sourceRecordId", sourceRecordId); put("notes", notes) }
    private fun animal(json: JSONObject) = Animal(json.getString("id"), scope(json), AnimalSpecies.valueOf(json.getString("species")), json.getString("identification"), AnimalSex.valueOf(json.optString("sex", AnimalSex.NAO_INFORMADO.name)), status = AnimalStatus.valueOf(json.optString("status", AnimalStatus.ATIVO.name)), notes = json.optString("notes").ifBlank { null })
    private fun batch(json: JSONObject) = AnimalBatch(json.getString("id"), scope(json), AnimalSpecies.valueOf(json.getString("species")), json.getString("name"), LocalDate.parse(json.getString("startedAt")), json.getInt("initialQuantity"), json.getInt("currentQuantity"), AnimalStatus.valueOf(json.optString("status", AnimalStatus.ATIVO.name)))
    private fun health(json: JSONObject) = HealthEvent(json.getString("id"), scope(json), json.getString("targetId"), HealthEventType.valueOf(json.getString("type")), LocalDate.parse(json.getString("date")), json.getString("product"), nextDueDate = json.optString("nextDueDate").ifBlank { null }?.let(LocalDate::parse))
    private fun reproduction(json: JSONObject) = ReproductionEvent(json.getString("id"), scope(json), json.getString("animalId"), AnimalSpecies.valueOf(json.getString("species")), ReproductionStage.valueOf(json.getString("stage")), LocalDate.parse(json.getString("date")), json.optString("expectedBirthDate").ifBlank { null }?.let(LocalDate::parse), if (json.isNull("bornAlive")) null else json.optInt("bornAlive"), if (json.isNull("bornDead")) null else json.optInt("bornDead"), if (json.isNull("weaned")) null else json.optInt("weaned"), json.optString("notes").ifBlank { null })
    private fun task(json: JSONObject) = RuralTask(json.getString("id"), scope(json), AgendaType.valueOf(json.getString("type")), json.getString("title"), LocalDate.parse(json.getString("dueDate")), AgendaStatus.valueOf(json.optString("status", AgendaStatus.PENDENTE.name)), json.optString("sourceRecordId").ifBlank { null }, json.optString("notes").ifBlank { null })
}
