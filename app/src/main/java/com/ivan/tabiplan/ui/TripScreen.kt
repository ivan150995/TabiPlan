package com.ivan.tabiplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivan.tabiplan.AppDeps
import com.ivan.tabiplan.Nav
import com.ivan.tabiplan.Screen
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.Trip
import com.ivan.tabiplan.maps.FoundPlace
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TripScreen(deps: AppDeps, nav: Nav, tripId: String) {
    val trips by deps.store.trips.collectAsState()
    val trip = trips.firstOrNull { it.id == tripId } ?: run { nav.back(); return }
    val today = LocalDate.now().toString()
    val traveling = today >= trip.startDate && today <= trip.endDate
    var tab by rememberSaveable { mutableStateOf(if (traveling) 2 else 0) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val tabs = listOf("Lugares", "Itinerario", "Hoy", "Documentos", "Checklist")
    Scaffold(
        topBar = {
            Column {
                BackBar(trip.name, { nav.back() }) {
                    IconButton(onClick = { nav.go(Screen.Guides(trip.country)) }) { Icon(Icons.Default.MenuBook, "Guías") }
                    IconButton(onClick = { nav.go(Screen.Assistant(trip.id)) }) { Icon(Icons.Default.AutoAwesome, "Asistente") }
                    IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Editar viaje") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Borrar viaje") }
                }
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> PlacesTab(deps, nav, trip)
                1 -> ItineraryTab(deps, trip) { tab = 2 }
                2 -> TodayTab(deps, nav, trip)
                3 -> DocsTab(deps, trip)
                4 -> ChecklistTab(deps, nav, trip)
            }
        }
    }
    if (editing) NewTripDialog(onDismiss = { editing = false }, initial = trip) {
        deps.store.upsert(it); editing = false
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("¿Borrar \"${trip.name}\"?") },
        text = { Text("Se borran lugares, itinerario, checklist y los archivos adjuntos de este viaje. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; deps.store.delete(trip.id); nav.back() }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
    )
}

// -------------------------------------------------------------------------------------------------
// Lugares
// -------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlacesTab(deps: AppDeps, nav: Nav, trip: Trip) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Place?>(null) }
    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun research(p: Place) {
        if (!deps.ai.hasKey) { ctx.toast("Agrega tu clave de Claude en Ajustes"); return }
        busy = busy + p.id
        scope.launch {
            runCatching { deps.ai.research(p, trip) }
                .onSuccess { info -> deps.store.update(trip.id) { t -> t.copy(places = t.places.map { if (it.id == p.id) it.copy(info = info) else it }) } }
                .onFailure { ctx.toast(it.message ?: "Error") }
            busy = busy - p.id
        }
    }

    val hotels = trip.places.filter { it.kind == PlaceKind.HOTEL }
    val others = trip.places.filter { it.kind != PlaceKind.HOTEL }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }) { Icon(Icons.Default.Add, null); HSpace(4); Text("Agregar lugar") }
                OutlinedButton(onClick = { nav.go(Screen.HotelSearch(trip.id)) }) { Icon(Icons.Default.Hotel, null); HSpace(4); Text("Buscar hotel con IA") }
                val pending = others.filter { it.minutes == null && it.info == null }
                if (pending.isNotEmpty()) OutlinedButton(onClick = { pending.forEach { research(it) } }) {
                    Icon(Icons.Default.AutoAwesome, null); HSpace(4); Text("Investigar ${pending.size} con IA")
                }
            }
        }
        if (hotels.isNotEmpty()) item { Title("🏨 Hospedaje") }
        items(hotels, key = { it.id }) { p -> PlaceCard(trip, p, p.id in busy, { research(p) }, { editing = p }, { deletePlace(deps, trip, p) }) }
        item { Title("📍 Lugares (${others.size})") }
        if (others.isEmpty()) item {
            Muted("Agrega templos, barrios, museos, restaurantes, el aeropuerto… En cada uno decides cuánto tiempo quieres estar, o dejas que la IA lo investigue.")
        }
        items(others, key = { it.id }) { p -> PlaceCard(trip, p, p.id in busy, { research(p) }, { editing = p }, { deletePlace(deps, trip, p) }) }
        item { Spacer(Modifier.height(40.dp)) }
    }
    if (adding || editing != null) PlaceEditor(deps, trip, editing, onDismiss = { adding = false; editing = null }) { place, researchNow ->
        deps.store.update(trip.id) { t ->
            if (t.places.any { it.id == place.id }) t.copy(places = t.places.map { if (it.id == place.id) place else it })
            else t.copy(places = t.places + place)
        }
        adding = false; editing = null
        if (researchNow) research(place)
    }
}

private fun deletePlace(deps: AppDeps, trip: Trip, p: Place) {
    deps.store.update(trip.id) { t ->
        t.copy(
            places = t.places.filterNot { it.id == p.id },
            itinerary = t.itinerary.copy(
                days = t.itinerary.days.map { d -> d.copy(stops = d.stops.filterNot { it.placeId == p.id }) },
                unassigned = t.itinerary.unassigned - p.id,
            ),
        )
    }
}

@Composable
fun PlaceCard(trip: Trip, p: Place, loading: Boolean, onResearch: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    SectionCard(Modifier.clickable { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (p.completed) "✅ " else "") + p.name + if (p.mustVisit) " ⭐" else "",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                )
                if (p.address.isNotBlank()) Muted(p.address, Modifier.heightIn(max = 36.dp))
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                p.kind == PlaceKind.HOTEL -> Chip2(
                    if (p.checkIn.isNotBlank()) "${prettyDate(p.checkIn)} → ${prettyDate(p.checkOut)}" else "Sin fechas",
                    MaterialTheme.colorScheme.secondaryContainer,
                )
                p.minutes != null -> Chip2("⏱ ${minutesText(p.minutes)} (tú)", MaterialTheme.colorScheme.secondaryContainer)
                p.info != null && p.info.suggestedMinutes > 0 -> Chip2("⏱ ${minutesText(p.info.suggestedMinutes)} (IA)", MaterialTheme.colorScheme.tertiaryContainer)
                else -> Chip2("⏱ Lo decide la IA", MaterialTheme.colorScheme.tertiaryContainer)
            }
            if (p.preferredDate.isNotBlank()) Chip2("📅 ${prettyDate(p.preferredDate)}", MaterialTheme.colorScheme.primaryContainer)
            if (!p.hasCoords) Chip2("Sin ubicación", MaterialTheme.colorScheme.errorContainer)
        }
        if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.padding(4.dp).height(18.dp)); Muted("Investigando con la IA…")
        }
        p.info?.let { info ->
            if (info.summary.isNotBlank()) Text(info.summary, style = MaterialTheme.typography.bodyMedium)
            if (open) {
                InfoLine("Horario", info.hours)
                InfoLine("Cierra", info.closedDays)
                InfoLine("Precio", info.price)
                InfoLine("Mejor hora", info.bestTime)
                InfoLine("Tips", info.tips)
                info.sources.forEach { s -> Text("🔗 $s", Modifier.clickable { ctx.openUrl(s) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1) }
            }
        }
        if (p.notes.isNotBlank() && open) InfoLine("Notas", p.notes)
        if (open) Row {
            if (p.kind != PlaceKind.HOTEL) TextButton(onClick = onResearch, enabled = !loading) { Text(if (p.info == null) "Investigar con IA" else "Volver a investigar") }
            TextButton(onClick = onDelete) { Text("Quitar") }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Text("$label: $value", style = MaterialTheme.typography.bodySmall)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceEditor(deps: AppDeps, trip: Trip, initial: Place?, onDismiss: () -> Unit, onSave: (Place, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var lat by remember { mutableStateOf(initial?.lat) }
    var lng by remember { mutableStateOf(initial?.lng) }
    var kind by remember { mutableStateOf(initial?.kind ?: PlaceKind.VISIT) }
    var aiDecides by remember { mutableStateOf(initial?.minutes == null) }
    var minutes by remember { mutableStateOf((initial?.minutes ?: 90).toString()) }
    var must by remember { mutableStateOf(initial?.mustVisit ?: false) }
    var preferred by remember { mutableStateOf(initial?.preferredDate ?: "") }
    var checkIn by remember { mutableStateOf(initial?.checkIn ?: trip.startDate) }
    var checkOut by remember { mutableStateOf(initial?.checkOut ?: trip.endDate) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var results by remember { mutableStateOf<List<FoundPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Agregar lugar" else "Editar lugar") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    name, { name = it }, label = { Text("Nombre (p. ej. Senso-ji, Shibuya Sky)") }, singleLine = true,
                    trailingIcon = {
                        IconButton(enabled = name.isNotBlank() && !searching, onClick = {
                            searching = true
                            scope.launch {
                                runCatching { deps.maps.search(name, trip.country) }
                                    .onSuccess { results = it; if (it.isEmpty()) ctx.toast("No encontré ese lugar; prueba con otro nombre") }
                                    .onFailure { ctx.toast(it.message ?: "Error al buscar") }
                                searching = false
                            }
                        }) { if (searching) CircularProgressIndicator(Modifier.height(20.dp)) else Icon(Icons.Default.Search, "Buscar ubicación") }
                    },
                )
                results.forEach { r ->
                    SectionCard(Modifier.clickable {
                        name = r.name; address = r.address; lat = r.lat; lng = r.lng; results = emptyList()
                    }, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(r.name, fontWeight = FontWeight.SemiBold); Muted(r.address)
                    }
                }
                if (lat != null) Muted("📍 Ubicación guardada: ${address.ifBlank { "%.4f, %.4f".format(lat, lng) }}")
                else Muted("Toca 🔍 para encontrar la ubicación (necesaria para ordenar la ruta).")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlaceKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text(k.label) }) }
                }
                if (kind == PlaceKind.HOTEL) {
                    DateField("Check-in", checkIn, { checkIn = it })
                    DateField("Check-out", checkOut, { checkOut = it })
                } else {
                    Text("¿Cuánto tiempo quieres estar?", fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { aiDecides = true }) {
                        RadioButton(aiDecides, { aiDecides = true }); Text("No sé: que la IA lo investigue")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { aiDecides = false }) {
                        RadioButton(!aiDecides, { aiDecides = false }); Text("Yo decido")
                    }
                    if (!aiDecides) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(30, 60, 90, 120, 180, 240, 360).forEach { m ->
                                FilterChip(minutes == m.toString(), { minutes = m.toString() }, label = { Text(minutesText(m)) })
                            }
                        }
                        OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit) }, label = { Text("Minutos") }, singleLine = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { must = !must }) {
                        Icon(if (must) Icons.Default.Star else Icons.Default.StarBorder, null, tint = MaterialTheme.colorScheme.primary)
                        HSpace(6); Text("Imprescindible")
                    }
                    DateField("Día preferido (opcional)", preferred, { preferred = it }, allowClear = true)
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas (reserva a las 10:00, ir de noche…)") })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val p = (initial ?: Place(name = name.trim())).copy(
                    name = name.trim(), address = address, lat = lat, lng = lng, kind = kind,
                    minutes = if (kind == PlaceKind.HOTEL || aiDecides) null else minutes.toIntOrNull()?.coerceIn(5, 900),
                    mustVisit = must, preferredDate = if (kind == PlaceKind.HOTEL) "" else preferred,
                    checkIn = if (kind == PlaceKind.HOTEL) checkIn else "", checkOut = if (kind == PlaceKind.HOTEL) checkOut else "",
                    notes = notes,
                )
                val researchNow = aiDecides && kind != PlaceKind.HOTEL && p.info == null && deps.ai.hasKey
                onSave(p, researchNow)
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
