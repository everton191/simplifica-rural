package br.com.simplificarural.ui

import androidx.annotation.DrawableRes
import br.com.simplificarural.R
import br.com.simplificarural.navigation.RuralRoutes

@DrawableRes
internal fun ruralRouteIcon(route: String?): Int? = when (route?.substringBefore('/')) {
    RuralRoutes.HOME -> R.drawable.ic_rural_home
    RuralRoutes.ANIMALS -> R.drawable.ic_rural_animals
    RuralRoutes.MORE -> R.drawable.ic_rural_more
    RuralRoutes.BIRDS, RuralRoutes.BIRD_LOTS -> R.drawable.ic_rural_birds
    RuralRoutes.BIRD_EGGS, RuralRoutes.PACKAGING -> R.drawable.ic_rural_egg
    RuralRoutes.CATTLE, RuralRoutes.CATTLE_LIST, RuralRoutes.CATTLE_DETAIL, RuralRoutes.CATTLE_NEW -> R.drawable.ic_rural_cattle
    RuralRoutes.CATTLE_MILK, RuralRoutes.CATTLE_MILK_CLOSURE -> R.drawable.ic_rural_milk
    RuralRoutes.CATTLE_FEED -> R.drawable.ic_rural_feed
    RuralRoutes.CATTLE_REPRODUCTION, RuralRoutes.SWINE_FARROWING -> R.drawable.ic_rural_reproduction
    RuralRoutes.SWINE, RuralRoutes.SWINE_FATTENING, RuralRoutes.SWINE_LOTS,
    RuralRoutes.SWINE_BREEDING, RuralRoutes.SOW_DETAIL, RuralRoutes.SWINE_WEIGHT -> R.drawable.ic_rural_swine
    RuralRoutes.STOCK, RuralRoutes.STOCK_DETAIL -> R.drawable.ic_rural_stock
    RuralRoutes.PURCHASES, RuralRoutes.NEW_PURCHASE, RuralRoutes.ENTRY -> R.drawable.ic_rural_stock_in
    RuralRoutes.EXPENSE -> R.drawable.ic_rural_stock_out
    RuralRoutes.FINANCE -> R.drawable.ic_rural_finance
    RuralRoutes.SALES, RuralRoutes.NEW_SALE -> R.drawable.ic_rural_sale
    RuralRoutes.AGENDA -> R.drawable.ic_rural_agenda
    RuralRoutes.HEALTH -> R.drawable.ic_rural_health
    RuralRoutes.PRODUCTION -> R.drawable.ic_rural_production
    RuralRoutes.SETTINGS -> R.drawable.ic_rural_settings
    RuralRoutes.ACTIVITIES, RuralRoutes.HISTORY -> R.drawable.ic_rural_history
    RuralRoutes.DAY_SUMMARY -> R.drawable.ic_rural_daily
    RuralRoutes.BACKUP -> R.drawable.ic_rural_backup
    RuralRoutes.ABOUT -> R.drawable.ic_rural_about
    RuralRoutes.ASSISTANT, RuralRoutes.OPERATIONAL_TEST -> R.drawable.ic_rural_assistant
    RuralRoutes.ORDERS -> R.drawable.ic_rural_orders
    RuralRoutes.STOCK_ADD -> when (route?.substringAfter('/', "")?.lowercase()) {
        "alimentação" -> R.drawable.ic_rural_feed
        "saúde" -> R.drawable.ic_rural_health
        "ferramentas" -> R.drawable.ic_rural_tools
        else -> R.drawable.ic_rural_stock_in
    }
    else -> null
}
