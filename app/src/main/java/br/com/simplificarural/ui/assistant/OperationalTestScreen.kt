package br.com.simplificarural.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.simplificarural.ai.RuralAssistant
import br.com.simplificarural.ui.components.Page
import br.com.simplificarural.ui.components.PressCard
import br.com.simplificarural.ui.theme.RuralSecondaryText

@Composable internal fun OperationalTestScreen(back: () -> Unit) = Page("Teste da secretária", "Analise, revise e confirme", back) {
    val context = LocalContext.current
    val assistant = remember { RuralAssistant(context) }
    var situation by remember { mutableStateOf("Hoje comprei 200 kg de ração de aves por 500 reais, comprei 300 bandejas vazias por 150 reais, produzi 900 ovos, usei 30 kg de ração, vendi 50 bandejas de ovos por 10 reais cada e gastei 80 reais de combustível.") }
    var proposal by remember { mutableStateOf<br.com.simplificarural.ai.OperationalProposal?>(null) }
    var showProposal by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf<String?>(null) }
    Text("Este cenário simula uma pendência com compras, produção, ração, venda e despesa. Revise antes de confirmar.", color = RuralSecondaryText)
    OutlinedTextField(situation, { situation = it }, Modifier.fillMaxWidth(), label = { Text("Situação para a secretária resolver") }, minLines = 5, shape = RoundedCornerShape(14.dp))
    Button({ proposal = assistant.analyzeOperationalSituation(situation); showProposal = proposal != null; editing = false; if (proposal == null) applied = "Não consegui separar os seis lançamentos. Revise os valores e tente novamente." }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(8.dp)); Text("Pedir proposta à IA") }
    applied?.let { PressCard { Text(it, fontWeight = FontWeight.Medium) } }
    if (showProposal) AlertDialog(
        onDismissRequest = { showProposal = false },
        title = { Text(if (editing) "Editar situação" else "Proposta para confirmação") },
        text = { if (editing) OutlinedTextField(situation, { situation = it }, Modifier.fillMaxWidth(), minLines = 6, label = { Text("Corrija a situação") }) else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("A secretária separou a pendência em seis lançamentos:", fontWeight = FontWeight.Medium); Text(proposal?.summary.orEmpty()); Text("Confira os valores. A confirmação aplicará estoque, caixa, produção, consumo e despesa juntos.", color = RuralSecondaryText) } },
        confirmButton = { Button(onClick = { if (editing) { proposal = assistant.analyzeOperationalSituation(situation); editing = proposal == null; if (proposal == null) applied = "Não consegui interpretar a correção." } else { proposal?.let { applied = assistant.applyOperationalProposal(it) }; showProposal = false } }) { Text(if (editing) "Atualizar proposta" else "Salvar lançamentos") } },
        dismissButton = { TextButton(onClick = { if (editing) editing = false else editing = true }) { Text(if (editing) "Cancelar edição" else "Editar manualmente") } }
    )
}
