package br.com.simplificarural.domain.actions

import br.com.simplificarural.ai.RuralLanguageNormalizer
import br.com.simplificarural.domain.animals.AnimalSpecies
import br.com.simplificarural.domain.management.EggTraySaleLine
import java.math.BigDecimal

internal sealed interface QuickCommand {
    data class Eggs(val count: Int) : QuickCommand
    data class Milk(val liters: BigDecimal) : QuickCommand
    data class Stock(val incoming: Boolean, val quantity: BigDecimal, val unit: String, val product: String) : QuickCommand
    data class Vaccine(val species: AnimalSpecies, val target: String, val vaccine: String) : QuickCommand
    data class SwineBirth(val alive: Int, val dead: Int) : QuickCommand
    data class EggTraySale(val product: String, val lines: List<EggTraySaleLine>) : QuickCommand
    data class LooseEggSale(val count: Int, val total: BigDecimal? = null, val unitPrice: BigDecimal? = null) : QuickCommand
    data class MilkSale(val liters: BigDecimal, val hasProductName: Boolean, val total: BigDecimal? = null, val unitPrice: BigDecimal? = null) : QuickCommand
    data class Trade(val buying: Boolean, val product: String, val quantity: BigDecimal, val unit: String, val total: BigDecimal, val unitPrice: BigDecimal?) : QuickCommand
    data class Expense(val description: String, val amount: BigDecimal) : QuickCommand
}

private val eggs = Regex("""(?:(?:eu )?(?:apanhei|coletei|recolhi|produzi|registrei) )?(\d+) ovos?""")
private val milk = Regex("""(?:ordenhei|tirei|produzi|registrei) (\d+(?:[,.]\d+)?)\s*(?:l|litros?)(?: de leite)?""")
private val stock = Regex("""(registrei entrada(?: de)?|adicionei|registrei saida(?: de)?|retirei|usei|consumi|baixei) (\d+(?:[,.]\d+)?) (kg|l|litros|unidades?) de ([a-z0-9 -]+)""")
private val vaccine = Regex("""vacinei (?:a |o )?(vaca|boi|novilha|bovino|porco|suino|matriz suina|ave|galinha|lote de aves) ([a-z0-9-]+) contra ([a-z0-9 -]+)""")
private val swineBirth = Regex("""(?:a )?(?:porca|matriz suina) pariu (\d+) leitoes vivos(?: e (\d+) mortos?)?""")
private val looseEggSale = Regex("""(?:(?:eu )?vendi|vendemos|fiz (?:1 )?venda de) (\d+) ovos?(?: (a|por) (?:r )?(\d+(?:[,.]\d{1,2})?)(?: reais| real)?(?: (cada|no total))?)?""")
private val milkSale = Regex("""(?:(?:eu )?vendi|vendemos|fiz (?:1 )?venda de) (\d+(?:[,.]\d+)?)\s*(?:l|litros?)( de leite)?(?: (a|por) (?:r )?(\d+(?:[,.]\d{1,2})?)(?: reais| real)?(?: (cada|por litros|no total))?)?""")
private val saleWords = Regex("""\b(?:vendi|vendeu|vendemos|vender|venda de)\b""")
private val compoundCommand = Regex("""\b(?:e|depois|tambem|mas|nao|hoje|ontem|amanha)\b""")

internal fun parseQuickCommand(message: String): QuickCommand? {
    if (message.length > 200 || '?' in message || Regex("""[-−]\s*\d""").containsMatchIn(message)) return null
    val input = RuralLanguageNormalizer.normalize(message)
        .trimEnd('.')
    parseEggTraySale(input)?.let { return it }
    looseEggSale.matchEntire(input)?.let {
        val (quantity, preposition, price, basis) = it.destructured
        val count = quantity.toIntOrNull()?.takeIf { count -> count > 0 } ?: return null
        if (price.isEmpty()) return QuickCommand.LooseEggSale(count)
        val value = price.positiveDecimal() ?: return null
        val unitPrice = value.takeIf { basis == "cada" || (basis.isEmpty() && preposition == "a") }
        return QuickCommand.LooseEggSale(count, unitPrice?.multiply(count.toBigDecimal()) ?: value, unitPrice)
    }
    milkSale.matchEntire(input)?.let {
        val (quantity, product, preposition, price, basis) = it.destructured
        val liters = quantity.positiveDecimal() ?: return null
        val hasProductName = product.isNotEmpty()
        if (price.isEmpty()) return QuickCommand.MilkSale(liters, hasProductName)
        val value = price.positiveDecimal() ?: return null
        val unitPrice = value.takeIf { basis == "cada" || basis == "por litros" || (basis.isEmpty() && preposition == "a") }
        return QuickCommand.MilkSale(liters, hasProductName, unitPrice?.multiply(liters) ?: value, unitPrice)
    }
    financePhrase(input)?.let { return it }

    eggs.matchEntire(input)?.let {
        return it.groupValues[1].toIntOrNull()?.takeIf { count -> count > 0 }?.let { count -> QuickCommand.Eggs(count) }
    }
    milk.matchEntire(input)?.let {
        if (!input.startsWith("ordenhei ") && !input.endsWith(" de leite")) return null
        return it.groupValues[1].positiveDecimal()?.let { liters -> QuickCommand.Milk(liters) }
    }
    stock.matchEntire(input)?.let {
        val (verb, amount, unit, product) = it.destructured
        if (compoundCommand.containsMatchIn(product)) return null
        val quantity = amount.positiveDecimal() ?: return null
        val stockUnit = when {
            unit == "kg" -> "kg"
            unit == "l" || unit == "litros" -> "litros"
            else -> "unidades"
        }
        if (stockUnit == "unidades" && quantity.stripTrailingZeros().scale() > 0) return null
        return QuickCommand.Stock(verb.startsWith("registrei entrada") || verb == "adicionei", quantity, stockUnit, product)
    }
    vaccine.matchEntire(input)?.let {
        val (animal, target, name) = it.destructured
        if (compoundCommand.containsMatchIn(name)) return null
        val species = when (animal) {
            "vaca", "boi", "novilha", "bovino" -> AnimalSpecies.BOVINO
            "porco", "suino", "matriz suina" -> AnimalSpecies.SUINO
            else -> AnimalSpecies.AVE
        }
        return QuickCommand.Vaccine(species, target, name)
    }
    swineBirth.matchEntire(input)?.let {
        val alive = it.groupValues[1].toIntOrNull() ?: return null
        val dead = it.groups[2]?.value?.let { count -> count.toIntOrNull() ?: return null } ?: 0
        return QuickCommand.SwineBirth(alive, dead).takeIf { alive > 0 || dead > 0 }
    }
    return null
}

private fun String.positiveDecimal(): BigDecimal? = takeUnless { Regex("""\d+\.\d{3}""").matches(it) }
    ?.replace(',', '.')?.toBigDecimalOrNull()
    ?.takeIf { it.signum() > 0 && it.precision() <= 12 }

internal fun expressesSale(input: String): Boolean = saleWords.containsMatchIn(RuralLanguageNormalizer.normalize(input))

private val expensePhrase = Regex("""(?:eu )?(?:gastei|paguei) (?:r )?(\d+(?:[,.]\d{1,2})?)(?: reais| real)? (?:com|de|em) ([a-z0-9 ]+)""")
private val tradePhrase = Regex("""(?:eu )?(comprei|vendi) (\d+(?:[,.]\d+)?)\s*(kg|l|litros|unidades?) de ([a-z0-9 ]+?) (a|por) (?:r )?(\d+(?:[,.]\d{1,2})?)(?: reais| real)?(?: (cada|por kg|por litros|por unidade|no total))?""")

private fun financePhrase(input: String): QuickCommand? {
    expensePhrase.matchEntire(input)?.let {
        val (amount, subject) = it.destructured
        if (compoundCommand.containsMatchIn(subject)) return null
        return QuickCommand.Expense(subject, amount.positiveDecimal() ?: return null)
    }
    val trade = tradePhrase.matchEntire(input) ?: return null
    val (verb, count, rawUnit, product, preposition, price, basis) = trade.destructured
    if (compoundCommand.containsMatchIn(product)) return null
    val quantity = count.positiveDecimal() ?: return null
    val value = price.positiveDecimal() ?: return null
    val unit = when (rawUnit) { "kg" -> "kg"; "l", "litros" -> "litros"; else -> "unidades" }
    if (unit == "unidades" && quantity.stripTrailingZeros().scale() > 0) return null
    if (basis.startsWith("por ") && basis != when (unit) { "kg" -> "por kg"; "litros" -> "por litros"; else -> "por unidade" }) return null
    val unitPrice = value.takeIf { basis == "cada" || basis.startsWith("por ") || (basis.isEmpty() && preposition == "a") }
    return QuickCommand.Trade(verb == "comprei", product, quantity, unit, unitPrice?.multiply(quantity) ?: value, unitPrice)
}

internal fun expressesFinance(input: String): Boolean = expressesSale(input) ||
    Regex("""\b(?:comprei|compramos|gastei|gastamos|paguei|pagamos)\b""").containsMatchIn(RuralLanguageNormalizer.normalize(input))
