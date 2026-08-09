package br.com.simplificarural.data.repository

import br.com.simplificarural.domain.agenda.RuralTask
import br.com.simplificarural.domain.animals.Animal
import br.com.simplificarural.domain.animals.AnimalBatch
import br.com.simplificarural.domain.health.HealthEvent
import br.com.simplificarural.domain.property.FarmScope
import br.com.simplificarural.domain.reproduction.ReproductionEvent

/** Contracts keep the UI and business rules independent from SharedPreferences today and Room tomorrow. */
interface AnimalRepository { fun animals(scope: FarmScope): List<Animal>; fun batches(scope: FarmScope): List<AnimalBatch> }
interface HealthRepository { fun events(scope: FarmScope): List<HealthEvent> }
interface ReproductionRepository { fun events(scope: FarmScope): List<ReproductionEvent> }
interface AgendaRepository { fun tasks(scope: FarmScope): List<RuralTask> }
