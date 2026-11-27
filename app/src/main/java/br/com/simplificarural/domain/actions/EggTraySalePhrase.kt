package br.com.simplificarural.domain.actions

import br.com.simplificarural.domain.management.EggTraySaleLine

private val firstPrice = Regex("""(?:eu )?vendi (\d+) (bandejas?|cartelas?)(?: de ovos)? (?:a|por) (?:r )?(\d+(?:[,.]\d{1,2})?)(?: reais?| real)?(?: cada)?""")
private val nextPrice = Regex("""(?:\s*,\s*|\s+e\s+|\s+)(\d+)(?: (bandejas?|cartelas?)(?: de ovos)?)? (?:a|por) (?:r )?(\d+(?:[,.]\d{1,2})?)(?: reais?| real)?(?: cada)?""")

internal fun parseEggTraySale(input: String): QuickCommand.EggTraySale? {
    val first = firstPrice.matchAt(input, 0) ?: return null
    val (quantity, packaging, price) = first.destructured
    val usingCarton = packaging.startsWith("cartela")
    val product = if (usingCarton) "Cartelas de ovos" else "Bandejas de ovos"
    val lines = mutableListOf(saleLine(quantity, price) ?: return null)
    var position = first.range.last + 1
    while (position < input.length) {
        if (lines.size >= 6) return null
        val next = nextPrice.matchAt(input, position) ?: return null
        val (amount, nextPackaging, nextValue) = next.destructured
        if (nextPackaging.isNotEmpty() && usingCarton != nextPackaging.startsWith("cartela")) return null
        lines.add(saleLine(amount, nextValue) ?: return null)
        position = next.range.last + 1
    }
    return QuickCommand.EggTraySale(product, lines)
}

private fun saleLine(quantity: String, price: String): EggTraySaleLine? {
    val count = quantity.toIntOrNull()?.takeIf { it > 0 } ?: return null
    val unitPrice = price.replace(',', '.').toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 && it.precision() <= 12 } ?: return null
    return EggTraySaleLine(count.toBigDecimal(), unitPrice)
}
