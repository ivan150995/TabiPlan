package com.ivan.tabiplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ivan.tabiplan.AppDeps
import com.ivan.tabiplan.Nav
import com.ivan.tabiplan.Screen
import com.ivan.tabiplan.ai.ChatTurn
import com.ivan.tabiplan.data.Country
import kotlinx.coroutines.launch

/** La conversación se queda en memoria mientras la app esté abierta. */
private val conversations = mutableMapOf<String, MutableList<ChatTurn>>()

@Composable
fun AssistantScreen(deps: AppDeps, nav: Nav, tripId: String?, question: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val trip = tripId?.let { deps.store.trip(it) }
    val key = tripId ?: "general"
    val turns = remember { mutableStateListOf<ChatTurn>().apply { addAll(conversations[key].orEmpty()) } }
    var input by remember { mutableStateOf(question) }
    var busy by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.lastIndex) }

    fun send(text: String) {
        if (text.isBlank() || busy) return
        if (!deps.ai.hasKey) { ctx.toast("Agrega tu clave de Claude en Ajustes"); nav.go(Screen.Settings); return }
        val history = turns.toList()
        turns += ChatTurn(true, text.trim())
        input = ""
        busy = true
        scope.launch {
            val answer = runCatching { deps.ai.ask(trip, history, text.trim(), "") }.getOrElse { ChatTurn(false, "⚠️ ${it.message}") }
            turns += answer
            conversations[key] = turns.toMutableList()
            busy = false
        }
    }

    val quick = if ((trip?.country ?: Country.JP) == Country.JP) listOf(
        "Ya llegué a Narita, ¿qué hago ahora?",
        "¿Cómo compro la Suica o Welcome Suica?",
        "¿Cómo compro el boleto del Shinkansen en la máquina?",
        "¿Cómo recargo mi PASMO?",
        "¿Me conviene el JR Pass con mi itinerario?",
    ) else listOf(
        "Ya llegué al AICM, ¿cómo voy a mi hotel?",
        "¿Cómo uso la tarjeta de Movilidad Integrada?",
    )

    Scaffold(topBar = { BackBar(if (trip != null) "Asistente · ${trip.name}" else "Asistente de viaje", { nav.back() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (turns.isEmpty()) item {
                    SectionCard(color = MaterialTheme.colorScheme.primaryContainer) {
                        Title("Pregúntame lo que sea del viaje")
                        Muted("Busco en internet información actual (precios, horarios, trámites) y te explico paso a paso. Conozco tu viaje, hoteles y lugares.")
                    }
                }
                items(turns) { t ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (t.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
                        SectionCard(
                            Modifier.fillMaxWidth(if (t.fromUser) 0.85f else 1f),
                            color = if (t.fromUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            SelectionContainer { Text(t.text, style = MaterialTheme.typography.bodyMedium) }
                            t.sources.forEach { s ->
                                Text("🔗 $s", Modifier.clickable { ctx.openUrl(s) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                            }
                        }
                    }
                }
                if (busy) item {
                    Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); HSpace(); Muted("Buscando y pensando…") }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quick.forEach { q -> AssistChip(onClick = { input = q }, label = { Text(q) }) }
            }
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(input, { input = it }, Modifier.weight(1f), placeholder = { Text("Escribe tu pregunta…") }, maxLines = 4)
                IconButton(onClick = { send(input) }, enabled = !busy && input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar") }
            }
        }
    }
}
