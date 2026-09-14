package br.com.simplificarural.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import br.com.simplificarural.navigation.RuralRoutes
import br.com.simplificarural.ui.components.MoreGroup
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.theme.RuralSecondaryText

@Composable internal fun MoreScreen(open: (String) -> Unit) = Page("Mais") { MoreGroup("Gestão", listOf("Produção" to RuralRoutes.PRODUCTION, "Pedidos" to RuralRoutes.ORDERS, "Agenda" to RuralRoutes.AGENDA, "Saúde" to RuralRoutes.HEALTH, "Compras" to RuralRoutes.PURCHASES, "Vendas" to RuralRoutes.SALES), open); MoreGroup("Análise", listOf("Relatórios" to RuralRoutes.PRODUCTION, "Indicadores" to RuralRoutes.PRODUCTION, "Histórico geral" to RuralRoutes.HISTORY), open); MoreGroup("Sistema", listOf("Embalagens de ovos" to RuralRoutes.PACKAGING, "Configurações" to RuralRoutes.SETTINGS, "Backup" to RuralRoutes.BACKUP, "Assistente Rural" to RuralRoutes.ASSISTANT, "Sobre" to RuralRoutes.ABOUT), open) }

@Composable internal fun SettingsScreen(open: (String) -> Unit) = Page("Configurações") { listOf("Propriedade" to RuralRoutes.feature("Propriedade"), "Atividades" to RuralRoutes.ACTIVITIES, "Aves" to RuralRoutes.BIRDS, "Bovinos" to RuralRoutes.CATTLE, "Suínos" to RuralRoutes.SWINE, "Financeiro" to RuralRoutes.FINANCE, "Alertas" to RuralRoutes.AGENDA, "Backup" to RuralRoutes.BACKUP, "Aparência" to RuralRoutes.feature("Aparência"), "Geral" to RuralRoutes.feature("Geral")).forEach { (name, route) -> PressCard({ open(route) }) { Row(verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium); Icon(Icons.Default.ChevronRight, null, tint = RuralSecondaryText) } } } }
@Composable internal fun ActivitiesScreen(back: () -> Unit, message: (String) -> Unit) = Page("Atividades da propriedade", "Módulos habilitados nesta versão.", back) { listOf("Aves" to "Lotes, ovos, saúde e estoque", "Bovinos" to "Ficha individual, leite, alimentação e saúde", "Suínos" to "Engorda, matrizes, pesagem, partos e saúde").forEach { (name, detail) -> PressCard { Text(name, fontWeight = FontWeight.Medium); Text(detail, color = RuralSecondaryText) } }; PressCard { Text("Novas atividades", fontWeight = FontWeight.Medium); Text("Caprinos, ovinos e piscicultura serão habilitados quando houver formulários, indicadores e relatórios próprios — sem criar telas vazias.", color = RuralSecondaryText) } }
@Composable internal fun AboutScreen(back: () -> Unit) = Page("Sobre o Simplifica Rural", "Organização local da rotina da fazenda.", back) {
    PressCard { Text("Simplifica Rural", fontWeight = FontWeight.Bold); Text("Registros de animais, produção, estoque, financeiro, agenda e secretária local.", color = RuralSecondaryText) }
    PressCard { Text("Como os dados funcionam", fontWeight = FontWeight.Bold); Text("Cada confirmação grava um lançamento local ligado à fazenda selecionada. A secretária prepara a ação; você revisa antes de salvar.", color = RuralSecondaryText) }
}
