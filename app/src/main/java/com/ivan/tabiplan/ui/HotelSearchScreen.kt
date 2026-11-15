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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import kotlinx.coroutines.launch

/** Buscar hoteles describiendo lo que quieres ("donde pueda ver el Monte Fuji"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HotelSearchScreen(deps: AppDeps, nav: Nav, tripId: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val trip = deps.store.trip(tripId) ?: run { nav.back(); return }
    var description by remember { mutableStateOf("") }
    var checkIn by remember { mutableStateOf(trip.startDate) }
    var checkOut by remember { mutableStateOf(trip.endDate) }
    var foreign by remember { mutableStateOf(true) }
    var station by remember { mutableStateOf(false) }
    var quiet by remember { mutableStateOf(false) }
    var tattoo by remember { mutableStateOf(false) }
    var maxPrice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<HotelSuggestion>>(emptyList()) }
    var added by remember { mutableStateOf<Set<String>>(emptySet()) }
    val examples = if (trip.country == Country.JP) listOf(
        "Un hotel donde pueda ver el Monte Fuji",
        "Cerca de una estación de metro en Tokio pero en una zona no tan concurrida",
        "Cerca de la calle de comida de Osaka (Dotonbori)",
        "Ryokan con onsen privado en Hakone",
        "En Kioto, caminando a Gion y templos",
    ) else listOf(
        "En la Roma o Condesa, tranquilo y caminable",
        "Cerca del Centro Histórico con terraza",
    )

    fun search() {
        if (!deps.ai.hasKey) { ctx.toast("Agrega tu clave de Claude en Ajustes"); return }
        busy = true; results = emptyList()
        scope.launch {
            runCatching {
                deps.ai.searchHotels(
                    trip,
                    HotelRequest(description.trim(), checkIn, checkOut, foreign, station, quiet, tattoo, maxPrice.toIntOrNull() ?: 0),
                )
            }.onSuccess { results = it; if (it.isEmpty()) ctx.toast("No encontré hoteles; describe la zona con otras palabras") }
                .onFailure { ctx.toast(it.message ?: "Error") }
            busy = false
        }
    }

    fun add(h: HotelSuggestion) {
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
                checkIn = checkIn, checkOut = checkOut,
                notes = listOf(h.pricePerNight.let { if (it.isBlank()) "" else "Precio: $it" }, h.station.let { if (it.isBlank()) "" else "Estación: $it" }, h.website)
                    .filter { it.isNotBlank() }.joinToString("\n"),
            )
            deps.store.update(trip.id) { t -> t.copy(places = t.places + place) }
            added = added + h.name
            ctx.toast("Agregado a Lugares → Hospedaje. Cuando reserves, guarda la confirmación en Documentos.")
        }
    }

    Scaffold(topBar = { BackBar("Buscar hotel con IA", { nav.back() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Describe el hotel que buscas") }, minLines = 2)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    examples.forEach { e -> AssistChip(onClick = { description = e }, label = { Text(e) }) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Check-in", checkIn, { checkIn = it; if (checkOut < it) checkOut = it }, Modifier.weight(1f))
                    DateField("Check-out", checkOut, { checkOut = it }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(foreign, { foreign = !foreign }, label = { Text("Friendly para extranjeros") })
                    FilterChip(station, { station = !station }, label = { Text("Cerca de estación") })
                    FilterChip(quiet, { quiet = !quiet }, label = { Text("Zona tranquila") })
                    if (trip.country == Country.JP) FilterChip(tattoo, { tattoo = !tattoo }, label = { Text("Onsen con tatuajes OK") })
                }
                OutlinedTextField(maxPrice, { maxPrice = it.filter(Char::isDigit) }, label = { Text("Máximo por noche (${trip.country.symbol}, opcional)") }, singleLine = true)
                Muted("\"Friendly para extranjeros\": personal que habla inglés, check-in sencillo, acepta tarjeta extranjera y buenas reseñas de turistas.")
                Spacer(Modifier.height(6.dp))
                Button(onClick = { search() }, enabled = description.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Search, null); HSpace(4); Text("Buscar")
                }
            }
            if (busy) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp)); HSpace(); Text("Buscando hoteles reales y revisando reseñas… (1–2 min)")
                }
            }
            results.firstOrNull()?.advice?.takeIf { it.isNotBlank() }?.let { advice -> item { SectionCard(color = MaterialTheme.colorScheme.primaryContainer) { Text("🤖 $advice") } } }
            items(results, key = { it.name }) { h -> HotelCard(h, h.name in added, checkIn, checkOut, onAdd = { add(h) }) }
            if (results.isNotEmpty()) item {
                Muted("Precios aproximados según la búsqueda; confirma disponibilidad y precio final al reservar.")
                results.first().sources.forEach { s ->
                    Text("🔗 $s", Modifier.clickable { ctx.openUrl(s) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotelCard(h: HotelSuggestion, added: Boolean, checkIn: String, checkOut: String, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    SectionCard {
        Text(h.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Muted(listOf(h.area, h.city).filter { it.isNotBlank() }.joinToString(" · "))
        if (h.pricePerNight.isNotBlank()) Text("💴 ${h.pricePerNight} / noche", fontWeight = FontWeight.Medium)
        if (h.why.isNotBlank()) Text(h.why, style = MaterialTheme.typography.bodyMedium)
        if (h.station.isNotBlank()) Text("🚉 ${h.station}", style = MaterialTheme.typography.bodySmall)
        if (h.foreignFriendly.isNotBlank()) Text("🌏 ${h.foreignFriendly}", style = MaterialTheme.typography.bodySmall)
        if (h.warning.isNotBlank()) Text("⚠️ ${h.warning}", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onAdd, enabled = !added) { Text(if (added) "Agregado ✓" else "Agregar a mi viaje") }
            OutlinedButton(onClick = { ctx.openUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(h.name + " " + h.city)}") }) { Text("Mapa") }
            OutlinedButton(onClick = {
                ctx.openUrl("https://www.booking.com/searchresults.html?ss=${Uri.encode(h.name + " " + h.city)}&checkin=$checkIn&checkout=$checkOut")
            }) { Text("Booking") }
            if (h.website.startsWith("http")) TextButton(onClick = { ctx.openUrl(h.website) }) { Text("Sitio oficial") }
        }
    }
}
