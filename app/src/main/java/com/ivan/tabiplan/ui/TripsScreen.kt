package com.ivan.tabiplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivan.tabiplan.AppDeps
import com.ivan.tabiplan.Nav
import com.ivan.tabiplan.Screen
import com.ivan.tabiplan.content.Checklists
import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.data.Trip
import com.ivan.tabiplan.plan.tripDates
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(deps: AppDeps, nav: Nav) {
    val trips by deps.store.trips.collectAsState()
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TabiPlan", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { nav.go(Screen.Guides(null)) }) { Icon(Icons.Default.MenuBook, "Guías") }
                    IconButton(onClick = { nav.go(Screen.Assistant(null)) }) { Icon(Icons.Default.AutoAwesome, "Asistente") }
                    IconButton(onClick = { nav.go(Screen.Settings) }) { Icon(Icons.Default.Settings, "Ajustes") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Nuevo viaje") })
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (trips.isEmpty()) {
                item {
                    SectionCard(color = MaterialTheme.colorScheme.primaryContainer) {
                        Title("¡Planea tu viaje!")
                        Spacer(Modifier.height(6.dp))
                        Text("1. Crea un viaje con fechas.\n2. Agrega los lugares que quieres visitar y tus hoteles.\n3. Pide el itinerario (con IA o automático).\n4. En el viaje, la pestaña \"Hoy\" te dice cómo llegar al siguiente lugar y cuánto cuesta.")
                        Spacer(Modifier.height(8.dp))
                        Muted("Para la IA agrega tu clave de Claude en Ajustes. Para rutas exactas y tarifas de metro, una clave de Google Maps.")
                    }
                }
            }
            items(trips, key = { it.id }) { t -> TripCard(t) { nav.go(Screen.Trip(t.id)) } }
            item {
                SectionCard(Modifier.clickable { nav.go(Screen.Guides(Country.JP)) }, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Title("📘 Guías de Japón")
                    Muted("Llegar a Narita/Haneda, Suica y PASMO, máquinas de boletos, Shinkansen…")
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
    if (creating) NewTripDialog(onDismiss = { creating = false }) { trip ->
        deps.store.upsert(trip)
        creating = false
        nav.go(Screen.Trip(trip.id))
    }
}

@Composable
private fun TripCard(t: Trip, onClick: () -> Unit) {
    val days = tripDates(t).size
    val visited = t.places.count { it.completed }
    val total = t.places.count { it.kind != com.ivan.tabiplan.data.PlaceKind.HOTEL }
    val today = LocalDate.now().toString()
    val status = when {
        today < t.startDate -> "Faltan ${java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(t.startDate))} días"
        today > t.endDate -> "Terminado"
        else -> "¡De viaje!"
    }
    SectionCard(Modifier.clickable { onClick() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Title("${if (t.country == Country.JP) "🇯🇵" else "🇲🇽"} ${t.name}")
            Chip2(status, MaterialTheme.colorScheme.primaryContainer)
        }
        Muted("${prettyDate(t.startDate)} – ${prettyDate(t.endDate)} · $days días · ${t.travelers} persona(s)")
        Muted("$visited de $total lugares visitados · ${t.docs.size} documentos · ${t.checklist.count { it.done }}/${t.checklist.size} pendientes listos")
    }
}

@Composable
fun NewTripDialog(onDismiss: () -> Unit, initial: Trip? = null, onSave: (Trip) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var country by remember { mutableStateOf(initial?.country ?: Country.JP) }
    var start by remember { mutableStateOf(initial?.startDate ?: LocalDate.now().plusMonths(1).toString()) }
    var end by remember { mutableStateOf(initial?.endDate ?: LocalDate.now().plusMonths(1).plusDays(9).toString()) }
    var people by remember { mutableStateOf((initial?.travelers ?: 1).toString()) }
    var dayStart by remember { mutableStateOf(initial?.dayStart ?: "09:00") }
    var dayEnd by remember { mutableStateOf(initial?.dayEnd ?: "20:00") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo viaje" else "Editar viaje") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (p. ej. Japón 2027)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Country.entries.forEach { c ->
                        FilterChip(country == c, { country = c }, label = { Text(c.label) })
                    }
                }
                DateField("Llegada", start, { start = it; if (end < it) end = it })
                DateField("Regreso", end, { end = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(dayStart, { dayStart = it }, label = { Text("Salir a") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(dayEnd, { dayEnd = it }, label = { Text("Volver a") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(people, { people = it.filter(Char::isDigit) }, label = { Text("Personas") }, singleLine = true)
                if (initial == null) Muted("Se agrega un checklist de lo que necesitas para viajar desde México.")
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val base = initial ?: Trip(name = name.trim(), startDate = start, endDate = end, checklist = Checklists.template(country))
                onSave(
                    base.copy(
                        name = name.trim(), country = country, startDate = start, endDate = end,
                        travelers = people.toIntOrNull()?.coerceIn(1, 20) ?: 1,
                        dayStart = normTime(dayStart, "09:00"), dayEnd = normTime(dayEnd, "20:00"),
                    ),
                )
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Acepta "9", "9:30", "0930" y devuelve "HH:mm". */
fun normTime(s: String, default: String): String {
    val digits = s.filter { it.isDigit() || it == ':' }
    val parts = if (':' in digits) digits.split(":") else if (digits.length >= 3) listOf(digits.dropLast(2), digits.takeLast(2)) else listOf(digits, "0")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return default
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    if (h !in 0..23 || m !in 0..59) return default
    return "%02d:%02d".format(h, m)
}
