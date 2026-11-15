package com.ivan.tabiplan.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.ContextCompat
import com.ivan.tabiplan.AppDeps
import com.ivan.tabiplan.Nav
import com.ivan.tabiplan.Screen
import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.Trip
import com.ivan.tabiplan.maps.LatLngD
import com.ivan.tabiplan.maps.Mode
import com.ivan.tabiplan.maps.RouteResult
import com.ivan.tabiplan.plan.hotelFor
import com.ivan.tabiplan.plan.tripDates
import com.ivan.tabiplan.transport.Fares
import com.ivan.tabiplan.transport.LegOptions
import com.ivan.tabiplan.transport.rangeText
import com.ivan.tabiplan.transport.withMxn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Un punto de partida o llegada para el siguiente tramo. */
data class Point(val name: String, val ll: LatLngD)

private fun Place.point() = if (hasCoords) Point(name, LatLngD(lat!!, lng!!)) else null

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayTab(deps: AppDeps, nav: Nav, trip: Trip) {
    val ctx = LocalContext.current
    val dates = tripDates(trip)
    val today = LocalDate.now().toString()
    var date by remember { mutableStateOf(if (today in dates) today else dates.firstOrNull() ?: today) }
    var here by remember { mutableStateOf<Point?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) lastLocation(ctx)?.let { here = Point("Mi ubicación", it) } ?: ctx.toast("No hay ubicación todavía; abre Google Maps un momento y vuelve a intentar")
    }

    val day = trip.itinerary.days.firstOrNull { it.date == date }
    val stops = day?.stops.orEmpty().mapNotNull { s -> trip.place(s.placeId)?.let { s to it } }
    // Hotel de esa noche y punto de salida del día (el hotel, o el aeropuerto el día de llegada).
    val hotel = hotelFor(trip, date)
    val start = trip.place(day?.startPlaceId.orEmpty()) ?: hotel
    val next = stops.firstOrNull { !it.second.completed }
    val lastDone = stops.lastOrNull { it.second.completed }?.second
    val endsAtAirport = stops.lastOrNull()?.second?.kind == PlaceKind.AIRPORT
    val origin: Point? = here ?: lastDone?.point() ?: start?.point()
    val destination: Point? = next?.second?.point() ?: if (!endsAtAirport) hotel?.point() else null
    val hasPlan = day != null && (stops.isNotEmpty() || (start != null && start.id != hotel?.id))

    fun setDone(p: Place, done: Boolean) {
        here = null // tras completar, el origen pasa a ser el lugar completado
        deps.store.update(trip.id) { t ->
            t.copy(places = t.places.map { if (it.id == p.id) it.copy(completedAt = if (done) System.currentTimeMillis() else 0) else it })
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            dates.forEach { d -> FilterChip(date == d, { date = d; here = null }, label = { Text(prettyDate(d) + if (d == today) " (hoy)" else "") }) }
        }
        if (!hasPlan) {
            SectionCard {
                Title("Sin plan para este día")
                Muted("Arma el itinerario en la pestaña Itinerario. Mientras, el asistente te puede ayudar.")
                TextButton(onClick = { nav.go(Screen.Assistant(trip.id)) }) { Text("Abrir asistente") }
            }
        } else {
            // Siguiente tramo
            if (origin != null && destination != null && origin.ll != destination.ll) {
                NextLeg(deps, trip, origin, destination, isReturn = next == null)
            } else if (next != null && !next.second.hasCoords) {
                SectionCard { Muted("\"${next.second.name}\" no tiene ubicación; búscala en Lugares para ver la ruta.") }
            }
            if (next == null && stops.isNotEmpty()) SectionCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                when {
                    endsAtAirport -> { Title("✈️ ¡Buen viaje de regreso!"); Muted("Recuerda el reembolso tax-free en la aduana antes de documentar.") }
                    hotel != null -> { Title("🎉 ¡Terminaste el día!"); Muted("Arriba tienes cómo volver a ${hotel.name}.") }
                    else -> { Title("🎉 ¡Terminaste el día!"); Muted("Agrega tu hotel para ver cómo regresar.") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (perms.any { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) {
                        lastLocation(ctx)?.let { here = Point("Mi ubicación", it) } ?: ctx.toast("No hay ubicación todavía")
                    } else permission.launch(perms)
                }) { Icon(Icons.Default.MyLocation, null); HSpace(4); Text(if (here != null) "Desde mi ubicación ✓" else "Salir desde donde estoy") }
                TextButton(onClick = {
                    val where = origin?.name ?: trip.country.label
                    nav.go(Screen.Assistant(trip.id, "Estoy en $where. ¿Qué hago ahora / qué me recomiendas?"))
                }) { Text("Preguntar a la IA") }
            }
            // Guías útiles si la parada es un aeropuerto
            val airportNow = if (lastDone == null && start?.kind == PlaceKind.AIRPORT) start else null
            (airportNow ?: next?.second?.takeIf { it.kind == PlaceKind.AIRPORT })?.let { airport ->
                val guide = when {
                    airport.name.contains("narita", true) -> "narita"
                    airport.name.contains("haneda", true) -> "haneda"
                    trip.country == Country.MX -> "aicm"
                    else -> null
                }
                guide?.let { g -> SectionCard(color = MaterialTheme.colorScheme.secondaryContainer) {
                    Title("✈️ ${airport.name}"); Muted("Paso a paso de qué hacer al llegar y cómo comprar boletos.")
                    TextButton(onClick = { nav.go(Screen.Guide(g)) }) { Text("Abrir guía") }
                } }
            }
            // Lista del día
            SectionCard {
                Title("Paradas del ${prettyDate(date)}")
                start?.let { Muted(if (it.kind == PlaceKind.AIRPORT) "✈️ Llegas a ${it.name}" else "🏨 Sales de ${it.name}") }
                stops.forEach { (stop, p) ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(p.completed, { setDone(p, it) })
                        Column(Modifier.weight(1f)) {
                            Text(p.name + if (p == next?.second) "  ← siguiente" else "", fontWeight = if (p == next?.second) FontWeight.Bold else FontWeight.Normal)
                            Muted("${stop.arrive}–${stop.leave} · ${minutesText(p.plannedMinutes)}")
                            if (stop.note.isNotBlank()) Muted(stop.note)
                        }
                    }
                }
                if (next != null) Button(onClick = { setDone(next.second, true) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("Completé \"${next.second.name}\" → ¿cómo llego al siguiente?")
                }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@SuppressLint("MissingPermission")
private fun lastLocation(ctx: Context): LatLngD? {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }?.let { LatLngD(it.latitude, it.longitude) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextLeg(deps: AppDeps, trip: Trip, from: Point, to: Point, isReturn: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember(from, to) { mutableStateOf<LegOptions?>(null) }
    var advice by remember(from, to) { mutableStateOf<String?>(null) }
    var advising by remember(from, to) { mutableStateOf(false) }
    val hour = LocalTime.now().hour
    LaunchedEffect(from, to) {
        options = Fares.options(deps.maps, from.ll, to.ll, trip.country, trip.travelers, hour, System.currentTimeMillis() / 1000)
    }
    val rate = deps.settings.mxnPerJpy
    val showMxn = deps.settings.showMxn
    SectionCard(color = MaterialTheme.colorScheme.primaryContainer) {
        Muted(if (isReturn) "De regreso al hotel" else "Siguiente parada")
        Title("${from.name} → ${to.name}")
        val o = options
        if (o == null) {
            Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); HSpace(); Text("Calculando rutas y precios…") }
            return@SectionCard
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(10.dp)).padding(10.dp)) {
            Column {
                Text("Recomendado: ${o.recommended.label}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(o.reason, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!o.usedGoogle) Muted(if (o.googleError != null) "Google no respondió (${o.googleError}); tiempos y precios son estimados." else "Tiempos y precios estimados por distancia. Con clave de Google Maps verás líneas, estaciones y tarifas reales.")

        // Caminando (solo si es razonable: hasta ~1 h)
        val walkable = o.walk.minutes <= 60 || o.recommended == Mode.WALK
        if (walkable) OptionHeader(Icons.Default.DirectionsWalk, "Caminando", "${minutesText(o.walk.minutes)} · ${"%.1f".format(o.walk.meters / 1000.0)} km · Gratis", o.recommended == Mode.WALK)
        // Transporte público
        val t = o.transit.firstOrNull()
        if (t != null) {
            val fare = o.transitFare(trip.country)
            val fareText = fare?.let { f -> (if (t.fare == null) "≈ " else "") + f.withMxn(rate, showMxn) + if (trip.travelers > 1) " c/u" else "" } ?: "precio no disponible"
            OptionHeader(Icons.Default.Train, "Transporte público", "${minutesText(t.minutes)} · $fareText", o.recommended == Mode.TRANSIT)
            TransitSteps(t)
            if (o.transit.size > 1) Muted("+${o.transit.size - 1} ruta(s) alternativa(s) en Google Maps.")
        }
        // Taxi y apps
        OptionHeader(Icons.Default.LocalTaxi, "Taxi / apps", "${minutesText(o.drive.minutes)} en auto · ${"%.1f".format(o.drive.meters / 1000.0)} km", o.recommended == Mode.DRIVE)
        o.rides.forEach { r ->
            Text("• ${r.app}: ${rangeText(r.low, r.high, rate, showMxn)}", style = MaterialTheme.typography.bodyMedium)
            Muted("   ${r.note}")
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { ctx.openUrl(mapsUrl(from, to, o.recommended)) }) { Icon(Icons.Default.Navigation, null); HSpace(4); Text("Ir con Google Maps") }
            listOf(Mode.WALK, Mode.TRANSIT, Mode.DRIVE).filter { it != o.recommended && (it != Mode.WALK || walkable) }.forEach { m ->
                OutlinedButton(onClick = { ctx.openUrl(mapsUrl(from, to, m)) }) { Text(m.label) }
            }
            OutlinedButton(onClick = {
                ctx.openAppOrStore(
                    "com.ubercab",
                    "https://m.uber.com/ul/?action=setPickup&pickup[latitude]=${from.ll.lat}&pickup[longitude]=${from.ll.lng}" +
                        "&dropoff[latitude]=${to.ll.lat}&dropoff[longitude]=${to.ll.lng}&dropoff[nickname]=${Uri.encode(to.name)}",
                    "Uber",
                )
            }) { Text("Uber") }
            if (trip.country == Country.MX) OutlinedButton(onClick = { ctx.openAppOrStore("com.didiglobal.passenger", null, "DiDi Rider") }) { Text("DiDi") }
            if (trip.country == Country.JP) OutlinedButton(onClick = { ctx.openAppOrStore("com.dena.automotive.taxibell", null, "GO taxi app Japan") }) { Text("GO (taxi)") }
        }
        Spacer(Modifier.height(6.dp))
        when {
            advising -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp)); HSpace(); Muted("Pidiendo consejo a la IA…") }
            advice != null -> SectionCard(color = Color.White) { Text("🤖 " + advice, style = MaterialTheme.typography.bodyMedium) }
            else -> TextButton(onClick = {
                if (!deps.ai.hasKey) { ctx.toast("Agrega tu clave de Claude en Ajustes"); return@TextButton }
                advising = true
                scope.launch {
                    advice = runCatching {
                        deps.ai.adviseLeg(trip, from.name, to.name, summary(o, trip, rate), "%02d:%02d".format(hour, LocalTime.now(ZoneId.systemDefault()).minute))
                    }.getOrElse { "No se pudo: ${it.message}" }
                    advising = false
                }
            }) { Icon(Icons.Default.AutoAwesome, null); HSpace(4); Text("Consejo de la IA para este tramo") }
        }
    }
}

@Composable
private fun OptionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, recommended: Boolean) {
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (recommended) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        HSpace()
        Column {
            Text(title + if (recommended) "  ★" else "", fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TransitSteps(r: RouteResult) {
    if (r.steps.isEmpty()) return
    Column(Modifier.padding(start = 32.dp)) {
        r.steps.forEach { s ->
            val c = runCatching { Color(android.graphics.Color.parseColor(s.color)) }.getOrDefault(MaterialTheme.colorScheme.secondary)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(Modifier.width(6.dp).height(34.dp).background(c, RoundedCornerShape(3.dp)))
                HSpace()
                Column {
                    Text("${s.vehicle.ifBlank { "Línea" }} ${s.line}" + if (s.departs.isNotBlank()) " · sale ${s.departs}" else "", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Muted("${s.from} → ${s.to}" + (if (s.headsign.isNotBlank()) " (dir. ${s.headsign})" else "") + " · ${s.stops} paradas · ${s.minutes} min")
                }
            }
        }
        if (r.walkMinutes > 0) Muted("Más ~${r.walkMinutes} min caminando en total.")
    }
}

private fun mapsUrl(from: Point, to: Point, mode: Mode): String {
    val m = when (mode) { Mode.WALK -> "walking"; Mode.TRANSIT -> "transit"; Mode.DRIVE -> "driving" }
    return "https://www.google.com/maps/dir/?api=1&origin=${from.ll.lat},${from.ll.lng}&destination=${to.ll.lat},${to.ll.lng}&travelmode=$m"
}

private fun summary(o: LegOptions, trip: Trip, rate: Double): String = buildString {
    appendLine("- Caminando: ${o.walk.minutes} min, ${o.walk.meters} m")
    o.transit.firstOrNull()?.let { t ->
        appendLine("- Transporte público: ${t.minutes} min, tarifa ${o.transitFare(trip.country)?.format() ?: "?"}" + if (t.fare == null) " (estimada)" else "")
        t.steps.forEach { s -> appendLine("   · ${s.vehicle} ${s.line} (${s.agency}) de ${s.from} a ${s.to}, ${s.stops} paradas") }
    }
    appendLine("- Auto: ${o.drive.minutes} min, ${o.drive.meters} m")
    o.rides.forEach { r -> appendLine("   · ${r.app}: ${r.low.format()}–${r.high.format()}") }
    appendLine("Recomendación de la app: ${o.recommended.label} (${o.reason})")
    if (trip.country == Country.JP) appendLine("Tipo de cambio que uso: 1 JPY = $rate MXN")
}
