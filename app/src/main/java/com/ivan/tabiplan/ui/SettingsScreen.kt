package com.ivan.tabiplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ivan.tabiplan.AppDeps
import com.ivan.tabiplan.BuildConfig
import com.ivan.tabiplan.Nav
import androidx.compose.material3.MaterialTheme
import com.ivan.tabiplan.data.AiEffort
import com.ivan.tabiplan.data.AiModel

@Composable
fun SettingsScreen(deps: AppDeps, nav: Nav) {
    val ctx = LocalContext.current
    val s = deps.settings
    var claude by remember { mutableStateOf(s.claudeKey) }
    var maps by remember { mutableStateOf(s.mapsKey) }
    var model by remember { mutableStateOf(s.model) }
    var effort by remember { mutableStateOf(s.effort) }
    var rate by remember { mutableStateOf("%.4f".format(java.util.Locale.US, s.mxnPerJpy).trimEnd('0').trimEnd('.')) }
    var showMxn by remember { mutableStateOf(s.showMxn) }
    Scaffold(topBar = { BackBar("Ajustes", { nav.back() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Title("IA (Claude)")
                Muted("Investiga lugares, arma itinerarios, busca hoteles y responde preguntas con búsqueda web. Crea la clave en platform.claude.com → API keys. Se guarda solo en este teléfono.")
                OutlinedTextField(claude, { claude = it }, label = { Text("Clave de la API de Claude") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Text("Modelo", style = MaterialTheme.typography.titleSmall)
                AiModel.entries.forEach { m ->
                    Row(Modifier.clickable { model = m.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(model == m.id, { model = m.id })
                        Column { Text(m.label); Muted(m.note) }
                    }
                }
                Text("Esfuerzo", style = MaterialTheme.typography.titleSmall)
                AiEffort.entries.forEach { e ->
                    Row(Modifier.clickable { effort = e.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(effort == e.id, { effort = e.id })
                        Column { Text(e.label); Muted(e.note) }
                    }
                }
                Muted("Las búsquedas web también cuestan (US\$10 por cada 1,000) y traen mucho texto. Para ahorrar: Sonnet con esfuerzo Automático, o Haiku con esfuerzo Bajo para dudas rápidas.")
            }
            SectionCard {
                Title("Google Maps")
                Muted("Activa en Google Cloud: Maps SDK for Android, Routes API y Places API (New). Rutas reales en auto y caminando; transporte público con líneas y tarifas solo en México (en Japón Google no lo da a las apps: se estima y el botón abre Google Maps). El mapa dentro de la app usa la clave puesta al compilar (MAPS_API_KEY en local.properties).")
                Muted(if (BuildConfig.MAPS_API_KEY.isNotBlank()) "✓ Esta versión trae clave de Maps." else "Esta versión no trae clave de Maps: el mapa del día abre Google Maps.")
                OutlinedTextField(maps, { maps = it }, label = { Text("Clave para rutas (opcional, reemplaza la de compilación)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Muted("Sin clave la app estima tiempos y precios por distancia y te manda a Google Maps para navegar.")
            }
            SectionCard {
                Title("Moneda")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mostrar precios en pesos también", Modifier.weight(1f)); Switch(showMxn, { showMxn = it })
                }
                OutlinedTextField(rate, { rate = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Pesos por 1 yen (ej. 0.12)") }, singleLine = true)
                Muted("Actualízalo antes del viaje; es solo para mostrar equivalencias.")
            }
            Button(onClick = {
                s.claudeKey = claude; s.mapsKey = maps; s.model = model; s.effort = effort; s.showMxn = showMxn
                rate.toDoubleOrNull()?.takeIf { it > 0 }?.let { s.mxnPerJpy = it }
                ctx.toast("Guardado"); nav.back()
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            Muted("TabiPlan ${BuildConfig.VERSION_NAME}")
        }
    }
}
