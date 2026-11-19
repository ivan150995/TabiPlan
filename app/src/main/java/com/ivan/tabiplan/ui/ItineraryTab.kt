package com.ivan.tabiplan.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.ivan.tabiplan.AppDeps
import com.ivan.tabiplan.BuildConfig
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.PlanDay
import com.ivan.tabiplan.data.Trip
import com.ivan.tabiplan.data.Meal
import com.ivan.tabiplan.plan.Advisor
import com.ivan.tabiplan.plan.Planner
import com.ivan.tabiplan.plan.TravelCalendar
import com.ivan.tabiplan.plan.fromMin
import com.ivan.tabiplan.plan.startHotelFor
import com.ivan.tabiplan.plan.toMin
import kotlinx.coroutines.launch

@Composable
fun ItineraryTab(deps: AppDeps, trip: Trip, onGoToday: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var generating by remember { mutableStateOf(false) }
    var askAi by remember { mutableStateOf(false) }
    var mapDay by remember { mutableStateOf<String?>(null) }
    var hoursFor by remember { mutableStateOf<String?>(null) }
    val itin = trip.itinerary
    val toPlan = trip.places.filter { p -> p.kind != PlaceKind.HOTEL && !p.completed }

    fun save(days: List<PlanDay>, unassigned: List<String> = itin.unassigned) =
        deps.store.update(trip.id) { t -> t.copy(itinerary = t.itinerary.copy(days = days, unassigned = unassigned)) }

    fun moveStop(fromDate: String, index: Int, toDate: String?) {
        val day = itin.days.first { d -> d.date == fromDate }
        val stop = day.stops[index]
        var days = itin.days.map { d -> if (d.date == fromDate) Planner.retime(trip, d.copy(stops = d.stops.filterIndexed { i, _ -> i != index })) else d }
        var unassigned = itin.unassigned
        if (toDate == null) unassigned = unassigned + stop.placeId
        else days = days.map { d -> if (d.date == toDate) Planner.retime(trip, d.copy(stops = d.stops + stop)) else d }
        save(days, unassigned)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            SectionCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Title(if (itin.days.isEmpty()) "Arma tu itinerario" else "Itinerario ${if (itin.byAi) "hecho con IA" else "automático"}")
                Muted("${toPlan.size} lugares por acomodar en ${com.ivan.tabiplan.plan.tripDates(trip).size} días, de ${trip.dayStart} a ${trip.dayEnd}.")
                val noCoords = toPlan.count { p -> !p.hasCoords }
                if (noCoords > 0) Muted("⚠️ $noCoords lugar(es) sin ubicación: búscalos en Lugares para ordenarlos bien.")
                if (trip.places.none { p -> p.kind == PlaceKind.HOTEL }) Muted("Tip: agrega tus hoteles con fechas para que cada día empiece y termine ahí.")
                val noHours = toPlan.count { p -> (p.kind == PlaceKind.VISIT || p.kind == PlaceKind.FOOD) && p.hours?.known != true }
                if (noHours > 0) Muted("🕘 $noHours lugar(es) sin horario: investígalos con IA o búscalos con Google para no llegar cuando esté cerrado.")
                val calendar = TravelCalendar.summary(trip.country, com.ivan.tabiplan.plan.tripDates(trip))
                if (calendar.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("En las fechas de tu viaje", fontWeight = FontWeight.SemiBold)
                    calendar.forEach { Muted(it) }
                }
                Spacer(Modifier.height(8.dp))
                if (generating) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(22.dp).height(22.dp)); HSpace(); Text("La IA está armando tu itinerario… (puede tardar 1–2 min)")
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = toPlan.isNotEmpty(), onClick = { if (deps.ai.hasKey) askAi = true else ctx.toast("Agrega tu clave de Claude en Ajustes, o usa Automático") }) {
                        Icon(Icons.Default.AutoAwesome, null); HSpace(4); Text("Con IA")
                    }
                    OutlinedButton(enabled = toPlan.isNotEmpty(), onClick = {
                        deps.store.update(trip.id) { t -> t.copy(itinerary = Planner.plan(t)) }
                    }) { Icon(Icons.Default.Route, null); HSpace(4); Text("Automático") }
                }
                if (itin.notes.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(itin.notes, style = MaterialTheme.typography.bodyMedium) }
            }
        }
        items(itin.days, key = { d -> d.date }) { day ->
            DayCard(
                trip, day, itin.days.map { d -> d.date },
                showMap = mapDay == day.date,
                onToggleMap = { mapDay = if (mapDay == day.date) null else day.date },
                onEditHours = { hoursFor = day.date },
                onMoveUp = { i -> if (i > 0) save(itin.days.map { d -> if (d.date == day.date) Planner.retime(trip, d.copy(stops = d.stops.toMutableList().apply { add(i - 1, removeAt(i)) })) else d }) },
                onMoveDown = { i -> if (i < day.stops.lastIndex) save(itin.days.map { d -> if (d.date == day.date) Planner.retime(trip, d.copy(stops = d.stops.toMutableList().apply { add(i + 1, removeAt(i)) })) else d }) },
                onMoveTo = { i, to -> moveStop(day.date, i, to) },
                onToday = onGoToday,
            )
        }
        if (itin.unassigned.isNotEmpty()) item {
            SectionCard(color = MaterialTheme.colorScheme.errorContainer) {
                Title("No cupieron (${itin.unassigned.size})")
                itin.unassigned.mapNotNull { id -> trip.place(id) }.forEach { p ->
                    var menu by remember { mutableStateOf(false) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { menu = true }) { Text("Agregar a…") }
                            DropdownMenu(menu, { menu = false }) {
                                itin.days.forEach { d ->
                                    DropdownMenuItem(text = { Text(prettyDate(d.date)) }, onClick = {
                                        menu = false
                                        val stop = com.ivan.tabiplan.data.Stop(p.id, "", "")
                                        save(itin.days.map { x -> if (x.date == d.date) Planner.retime(trip, x.copy(stops = x.stops + stop)) else x }, itin.unassigned - p.id)
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }

    if (askAi) {
        var wishes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askAi = false },
            title = { Text("Itinerario con IA") },
            text = {
                Column {
                    Text("La IA usa tus lugares, tiempos, horas fijas, hoteles y horarios (y lo que investigó de cada lugar), además de festivos, temporadas altas y horas pico de comida, para repartir los días.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(wishes, { wishes = it }, label = { Text("Algo más (opcional)") }, placeholder = { Text("Ej.: el primer día tranquilo por el jet lag; comer en Tsukiji") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askAi = false; generating = true
                    scope.launch {
                        runCatching { deps.ai.planItinerary(trip, wishes) }
                            .onSuccess { plan -> deps.store.update(trip.id) { t -> t.copy(itinerary = plan) } }
                            .onFailure { e -> ctx.toast((e.message ?: "Error") + ". Prueba con Automático.") }
                        generating = false
                    }
                }) { Text("Generar") }
            },
            dismissButton = { TextButton(onClick = { askAi = false }) { Text("Cancelar") } },
        )
    }

    hoursFor?.let { date ->
        val (s, e) = trip.hoursFor(date)
        var start by remember(date) { mutableStateOf(s) }
        var end by remember(date) { mutableStateOf(e) }
        AlertDialog(
            onDismissRequest = { hoursFor = null },
            title = { Text("Horario del ${prettyDate(date)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Muted("Útil para el día de llegada (vuelo a las 15:00) o el de regreso.")
                    OutlinedTextField(start, { start = it }, label = { Text("Empieza") }, singleLine = true)
                    OutlinedTextField(end, { end = it }, label = { Text("Termina") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val value = "${normTime(start, trip.dayStart)}-${normTime(end, trip.dayEnd)}"
                    deps.store.update(trip.id) { t ->
                        val nt = t.copy(dayHours = t.dayHours + (date to value))
                        nt.copy(itinerary = nt.itinerary.copy(days = nt.itinerary.days.map { d -> if (d.date == date) Planner.retime(nt, d) else d }))
                    }
                    hoursFor = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { deps.store.update(trip.id) { t -> t.copy(dayHours = t.dayHours - date) }; hoursFor = null }) { Text("Usar el normal") }
            },
        )
    }
}

@Composable
private fun DayCard(
    trip: Trip,
    day: PlanDay,
    allDates: List<String>,
    showMap: Boolean,
    onToggleMap: () -> Unit,
    onEditHours: () -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onMoveTo: (Int, String?) -> Unit,
    onToday: () -> Unit,
) {
    val ctx = LocalContext.current
    val (s, e) = trip.hoursFor(day.date)
    val hotel = trip.place(day.startPlaceId) ?: startHotelFor(trip, day.date)
    val isToday = day.date == java.time.LocalDate.now().toString()
    SectionCard(color = if (isToday) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Title(prettyDate(day.date).replaceFirstChar { it.uppercase() } + if (isToday) " · HOY" else "")
                Text("🕘 $s – $e", Modifier.clickable { onEditHours() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                hotel?.let { Muted(if (it.kind == PlaceKind.AIRPORT) "✈️ Llegada: ${it.name}" else "Sale de: ${it.name}") }
            }
            IconButton(onClick = onToggleMap) { Icon(Icons.Default.Map, "Mapa del día") }
            if (isToday) TextButton(onClick = onToday) { Text("Ir a Hoy") }
        }
        Advisor.dayHints(trip, day.date).forEach { HintLine(it) }
        if (day.note.isNotBlank()) Muted(day.note)
        if (showMap) DayMap(ctx, trip, day)
        if (day.stops.isEmpty()) Muted("Día libre.")
        day.stops.forEachIndexed { i, stop ->
            val p = trip.place(stop.placeId) ?: return@forEachIndexed
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            if (stop.mealBefore > 0) {
                val leaveMeal = toMin(stop.arrive) - stop.wait - stop.travelMinutes
                val lunch = TravelCalendar.meal(trip.country, Meal.LUNCH)
                Muted("   🍱 ${fromMin(leaveMeal - stop.mealBefore)}–${fromMin(leaveMeal)} comida libre por la zona (hora pico ${lunch.peakText()})")
            }
            if (stop.travelMinutes > 0) Muted("   ↓ traslado ~${minutesText(stop.travelMinutes)}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${stop.arrive}\n${stop.leave}", Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Column(Modifier.weight(1f)) {
                    Text((if (p.completed) "✅ " else "") + p.name + if (p.mustVisit) " ⭐" else "", fontWeight = FontWeight.SemiBold)
                    if (stop.note.isNotBlank()) Muted(stop.note)
                    if (!p.completed) Advisor.stopHints(trip, day, i).forEach { HintLine(it) }
                }
                var menu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Subir") }, leadingIcon = { Icon(Icons.Default.ArrowUpward, null) }, onClick = { menu = false; onMoveUp(i) })
                        DropdownMenuItem(text = { Text("Bajar") }, leadingIcon = { Icon(Icons.Default.ArrowDownward, null) }, onClick = { menu = false; onMoveDown(i) })
                        allDates.filter { it != day.date }.forEach { d ->
                            DropdownMenuItem(text = { Text("Mover al ${prettyDate(d)}") }, onClick = { menu = false; onMoveTo(i, d) })
                        }
                        DropdownMenuItem(text = { Text("Quitar del itinerario") }, onClick = { menu = false; onMoveTo(i, null) })
                    }
                }
            }
        }
    }
}

fun mapsAvailable(ctx: Context): Boolean =
    BuildConfig.MAPS_API_KEY.isNotBlank() &&
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS

@Composable
private fun DayMap(ctx: Context, trip: Trip, day: PlanDay) {
    val points = buildList {
        (trip.place(day.startPlaceId) ?: startHotelFor(trip, day.date))?.takeIf { it.hasCoords }?.let { add(it.name to LatLng(it.lat!!, it.lng!!)) }
        day.stops.mapNotNull { trip.place(it.placeId) }.filter { it.hasCoords }.forEach { add(it.name to LatLng(it.lat!!, it.lng!!)) }
    }
    if (points.isEmpty()) { Muted("No hay lugares con ubicación este día."); return }
    if (!mapsAvailable(ctx)) {
        SectionCard(color = Color(0xFFEDE7E3)) {
            Muted("El mapa necesita una clave de Google Maps (MAPS_API_KEY en local.properties al compilar) y Google Play services.")
            TextButton(onClick = {
                val o = points.first().second
                val d = points.last().second
                val way = points.drop(1).dropLast(1).joinToString("|") { "${it.second.latitude},${it.second.longitude}" }
                ctx.openUrl("https://www.google.com/maps/dir/?api=1&origin=${o.latitude},${o.longitude}&destination=${d.latitude},${d.longitude}" + if (way.isNotEmpty()) "&waypoints=$way" else "")
            }) { Text("Ver el día en Google Maps") }
        }
        return
    }
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(points.first().second, 12f) }
    GoogleMap(
        Modifier.fillMaxWidth().height(260.dp).padding(vertical = 6.dp),
        cameraPositionState = camera,
        onMapLoaded = {
            if (points.size > 1) {
                val b = LatLngBounds.builder().apply { points.forEach { include(it.second) } }.build()
                camera.move(com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(b, 80))
            }
        },
    ) {
        points.forEachIndexed { i, (name, ll) ->
            Marker(state = remember(ll) { MarkerState(ll) }, title = if (i == 0 && day.startPlaceId.isNotEmpty()) "🏨 $name" else "$i. $name")
        }
        Polyline(points = points.map { it.second }, color = Color(0xFFC8102E), width = 8f)
    }
}
