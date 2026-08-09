package br.com.simplificarural.domain.core

import br.com.simplificarural.domain.property.FarmScope

enum class PropertyActivity { AVES, BOVINOS, SUINOS, CAPRINOS, OVINOS, PISCICULTURA, EQUINOS }

enum class RuralModule {
    ANIMAIS, PRODUCAO, ESTOQUE, COMPRAS, VENDAS, FINANCEIRO, SAUDE, REPRODUCAO, AGENDA, EQUIPAMENTOS, RELATORIOS
}

data class PropertyProfile(
    val scope: FarmScope,
    val enabledActivities: Set<PropertyActivity>,
    val enabledModules: Set<RuralModule> = RuralModule.entries.toSet()
)
