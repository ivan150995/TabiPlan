package com.ivan.tabiplan.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivan.tabiplan.AppDeps
import com.ivan.tabiplan.Nav
import com.ivan.tabiplan.ai.HotelRequest
import com.ivan.tabiplan.ai.HotelSuggestion
import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.Stay
import com.ivan.tabiplan.data.Trip
import com.ivan.tabiplan.plan.Stays
import kotlinx.coroutines.launch

/**
 * Plan de hospedaje: cuántos hoteles quieres y dónde (Tokio → Osaka → Kioto → Tokio), y para cada
 * estancia la IA busca hoteles según lo que describes y los lugares que visitas esos días.
 * Precio y disponibilidad reales: Booking (abre la app si la tienes) y Google Hoteles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HotelSearchScreen(deps: AppDeps, nav: Nav, tripId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val trips by deps.store.trips.collectAsState()
    val trip = trips.firstOrNull { it.id == tripId } ?: run { nav.back(); return }
    var stays by remember { mutableStateOf(Stays.initial(trip)) }
    var foreign by remember { mutableStateOf(true) }
    var station by remember { mutableStateOf(false) }
    var quiet by remember { mutableStateOf(false) }
    var tattoo by remember { mutableStateOf(false) }
    var nearMine by remember { mutableStateOf(true) }
    var maxPrice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<Set<String>>(emptySet()) }
    var results by remember { mutableStateOf<Map<String, List<HotelSuggestion>>>(emptyMap()) }

    /** Guarda el plan y mueve las fechas de los hoteles ya elegidos si cambiaron. */
    fun save(list: List<Stay>) {
        stays = list
        deps.store.update(trip.id) { t ->
            t.copy(
                stays = list,
                places = t.places.map { p ->
                    list.firstOrNull { it.hotelId == p.id }?.let { s -> p.copy(checkIn = s.checkIn, checkOut = s.checkOut) } ?: p
                },
            )
        }
    }

    fun request(s: Stay) = HotelRequest(
        description = s.wishes.trim(), checkIn = s.checkIn, checkOut = s.checkOut,
        foreignFriendly = foreign, nearStation = station, quiet = quiet, tattooOk = tattoo,
        maxPerNight = maxPrice.toIntOrNull() ?: 0, city = s.city.trim(),
        nearPlaces = if (nearMine) Stays.placesDuring(trip, s).map { it.name } else emptyList(),
    )

    suspend fun runSearch(s: Stay) {
        busy = busy + s.id
        runCatching { deps.ai.searchHotels(trip, request(s)) }
            .onSuccess { r -> results = results + (s.id to r); if (r.isEmpty()) ctx.toast("No encontré hoteles en ${s.city}; describe la zona con otras palabras") }
            .onFailure { ctx.toast(it.message ?: "Error") }
        busy = busy - s.id
    }

    fun search(s: Stay) {
        if (!deps.ai.hasKey) { ctx.toast("Agrega tu clave de Claude en Ajustes"); return }
        if (s.city.isBlank() && s.wishes.isBlank()) { ctx.toast("Pon la ciudad o describe qué buscas"); return }
        scope.launch { runSearch(s) }
    }

    fun searchAll() {
        if (!deps.ai.hasKey) { ctx.toast("Agrega tu clave de Claude en Ajustes"); return }
        // Una tras otra para no saturar la API; solo las que tienen ciudad y aún no tienen resultados.
        scope.launch { stays.filter { it.city.isNotBlank() && it.id !in results }.forEach { runSearch(it) } }
    }

    /** Elige un hotel para la estancia (reemplaza al que hubieras elegido antes para esa estancia). */
    fun choose(h: HotelSuggestion, s: Stay) {
        scope.launch {
            var lat = h.lat
            var lng = h.lng
            var address = listOf(h.area, h.city).filter { it.isNotBlank() }.joinToString(", ")
            // Confirma la ubicación con Google/Android: las coordenadas de la IA son aproximadas.
            runCatching { deps.maps.search("${h.name} ${h.city}", trip.country).firstOrNull() }.getOrNull()?.let {
                lat = it.lat; lng = it.lng; address = it.address.ifBlank { address }
            }
            val place = Place(
                name = h.name, address = address, lat = lat, lng = lng, kind = PlaceKind.HOTEL,
                checkIn = s.checkIn, checkOut = s.checkOut,
                notes = listOf(h.pricePerNight.let { if (it.isBlank()) "" else "Precio: $it" }, h.station.let { if (it.isBlank()) "" else "Estación: $it" }, h.website)
                    .filter { it.isNotBlank() }.joinToString("\n"),
            )
            val old = s.hotelId
            val list = stays.map { if (it.id == s.id) it.copy(hotelId = place.id) else it }
            stays = list
            deps.store.update(trip.id) { t -> t.copy(stays = list, places = t.places.filterNot { it.id == old && old.isNotBlank() } + place) }
            ctx.toast("Hotel de ${s.city.ifBlank { "esta estancia" }}: ${h.name}. Cuando reserves, guarda la confirmación en Documentos.")
        }
    }

    Scaffold(topBar = { BackBar("Hoteles", { nav.back() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SectionCard(color = MaterialTheme.colorScheme.primaryContainer) {
                    Title("Tu hospedaje")
                    Muted("Pon cuántos hoteles quieres y en qué ciudad, en orden (p. ej. Tokio 3 noches, Osaka 2, Kioto 2 y Tokio al final). Al cambiar las noches, las siguientes estancias se recorren solas.")
                    Stays.issues(trip, stays).forEach { Muted("⚠️ $it", color = MaterialTheme.colorScheme.error) }
                }
            }
            itemsIndexed(stays, key = { _, s -> s.id }) { i, s ->
                StayCard(
                    trip = trip, index = i, stay = s, canDelete = stays.size > 1,
                    busy = s.id in busy, results = results[s.id].orEmpty(),
                    onChange = { ns -> save(stays.map { if (it.id == s.id) ns else it }) },
                    onCheckIn = { d -> save(Stays.setCheckIn(stays, i, d)) },
                    onNights = { n -> save(Stays.setNights(stays, i, n)) },
                    onDelete = { save(stays.filterNot { it.id == s.id }); results = results - s.id },
                    onSearch = { search(s) },
                    onChoose = { h -> choose(h, s) },
                )
            }
            item {
                OutlinedButton(onClick = { save(Stays.add(trip, stays)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null); HSpace(4); Text("Agregar otra ciudad / hotel")
                }
            }
            item {
                SectionCard {
                    Title("Para todas las búsquedas")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(nearMine, { nearMine = !nearMine }, label = { Text("Cerca de lo que visito") })
                        FilterChip(foreign, { foreign = !foreign }, label = { Text("Friendly para extranjeros") })
                        FilterChip(station, { station = !station }, label = { Text("Cerca de estación") })
                        FilterChip(quiet, { quiet = !quiet }, label = { Text("Zona tranquila") })
                        if (trip.country == Country.JP) FilterChip(tattoo, { tattoo = !tattoo }, label = { Text("Onsen con tatuajes OK") })
                    }
                    OutlinedTextField(maxPrice, { maxPrice = it.filter(Char::isDigit) }, label = { Text("Máximo por noche (${trip.country.symbol}, opcional)") }, singleLine = true)
                    Muted("\"Cerca de lo que visito\" usa los lugares de tu itinerario (o con día preferido) en las fechas de cada estancia. \"Friendly para extranjeros\": personal que habla inglés, check-in sencillo, acepta tarjeta extranjera y buenas reseñas de turistas.")
                    Spacer(Modifier.height(6.dp))
                    val pending = stays.count { it.city.isNotBlank() && it.id !in results }
                    Button(onClick = { searchAll() }, enabled = pending > 0 && busy.isEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Search, null); HSpace(4); Text(if (pending > 1) "Buscar en las $pending ciudades con IA" else "Buscar con IA")
                    }
                    Muted("Cada búsqueda usa la IA con búsqueda web (cuesta unos centavos de dólar). Los precios son aproximados: el precio y la disponibilidad reales están en Booking o Google Hoteles.")
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StayCard(
    trip: Trip,
    index: Int,
    stay: Stay,
    canDelete: Boolean,
    busy: Boolean,
    results: List<HotelSuggestion>,
    onChange: (Stay) -> Unit,
    onCheckIn: (String) -> Unit,
    onNights: (Int) -> Unit,
    onDelete: () -> Unit,
    onSearch: () -> Unit,
    onChoose: (HotelSuggestion) -> Unit,
) {
    val ctx = LocalContext.current
    val hotel = trip.place(stay.hotelId)
    val where = stay.city.ifBlank { trip.country.label }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}.", fontWeight = FontWeight.Bold)
            HSpace(6)
            OutlinedTextField(stay.city, { onChange(stay.copy(city = it)) }, Modifier.weight(1f), label = { Text("Ciudad o zona") },
                placeholder = { Text(if (trip.country == Country.JP) "Tokio, Osaka, Kioto, Hakone…" else "CDMX, Oaxaca…") }, singleLine = true)
            if (canDelete) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Quitar estancia") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateField("Llegas", stay.checkIn, onCheckIn, Modifier.weight(1f))
            HSpace(6)
            IconButton(onClick = { onNights(stay.nights - 1) }, enabled = stay.nights > 1) { Icon(Icons.Default.Remove, "Una noche menos") }
            Text("${stay.nights} ${if (stay.nights == 1) "noche" else "noches"}", fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { onNights(stay.nights + 1) }) { Icon(Icons.Default.Add, "Una noche más") }
        }
        Muted("Sales: ${prettyDate(stay.checkOut)}")
        OutlinedTextField(stay.wishes, { onChange(stay.copy(wishes = it)) }, Modifier.fillMaxWidth(), label = { Text("Qué buscas aquí (opcional)") },
            placeholder = { Text(if (trip.country == Country.JP) "Ej.: con vista al Fuji; cerca de Dotonbori; ryokan con onsen" else "Ej.: en la Roma, tranquilo") }, minLines = 1)
        val visits = Stays.placesDuring(trip, stay)
        if (visits.isNotEmpty()) Muted("📍 Esos días visitas: " + visits.joinToString { it.name })
        hotel?.let { h ->
            Text("🏨 ${h.name}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { ctx.openUrl(Stays.bookingUrl(h.name, stay.checkIn, stay.checkOut, trip.travelers, trip.country)) }) { Text("Ver precio y disponibilidad en Booking") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onSearch, enabled = !busy) { Icon(Icons.Default.Search, null); HSpace(4); Text(if (results.isEmpty()) "Buscar con IA" else "Buscar otra vez") }
            OutlinedButton(onClick = { ctx.openUrl(Stays.bookingUrl(where, stay.checkIn, stay.checkOut, trip.travelers, trip.country)) }) { Text("Booking") }
            OutlinedButton(onClick = { ctx.openUrl(Stays.googleHotelsUrl(where, stay.checkIn, stay.checkOut)) }) { Text("Google Hoteles") }
        }
        if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp)); HSpace(); Text("Buscando hoteles reales en $where… (1–2 min)")
        }
        results.firstOrNull()?.advice?.takeIf { it.isNotBlank() }?.let { Text("🤖 $it", style = MaterialTheme.typography.bodyMedium) }
        results.forEach { h ->
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            HotelResult(trip, h, stay, chosen = hotel?.name == h.name, onChoose = { onChoose(h) })
        }
        if (results.isNotEmpty()) results.first().sources.forEach { s ->
            Text("🔗 $s", Modifier.clickable { ctx.openUrl(s) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotelResult(trip: Trip, h: HotelSuggestion, stay: Stay, chosen: Boolean, onChoose: () -> Unit) {
    val ctx = LocalContext.current
    Text(h.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Muted(listOf(h.area, h.city).filter { it.isNotBlank() }.joinToString(" · "))
    if (h.pricePerNight.isNotBlank()) Text("💴 ${h.pricePerNight} / noche (aprox.)", fontWeight = FontWeight.Medium)
    if (h.why.isNotBlank()) Text(h.why, style = MaterialTheme.typography.bodyMedium)
    if (h.station.isNotBlank()) Text("🚉 ${h.station}", style = MaterialTheme.typography.bodySmall)
    if (h.foreignFriendly.isNotBlank()) Text("🌏 ${h.foreignFriendly}", style = MaterialTheme.typography.bodySmall)
    if (h.warning.isNotBlank()) Text("⚠️ ${h.warning}", style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = onChoose, enabled = !chosen) { Text(if (chosen) "Elegido ✓" else "Elegir este") }
        OutlinedButton(onClick = { ctx.openUrl(Stays.bookingUrl("${h.name} ${h.city}", stay.checkIn, stay.checkOut, trip.travelers, trip.country)) }) { Text("Booking") }
        OutlinedButton(onClick = { ctx.openUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(h.name + " " + h.city)}") }) { Text("Mapa") }
        if (h.website.startsWith("http")) TextButton(onClick = { ctx.openUrl(h.website) }) { Text("Sitio oficial") }
    }
}
