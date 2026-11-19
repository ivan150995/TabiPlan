package com.ivan.tabiplan.plan

import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.Stay
import com.ivan.tabiplan.data.Trip
import java.net.URLEncoder
import java.time.LocalDate

/** Plan de hospedaje: varias estancias seguidas (Tokio → Osaka → Kioto → Tokio). */
object Stays {
    private fun d(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()

    /** Noches del viaje (la noche de cada fecha, sin la última). */
    fun tripNights(trip: Trip): List<String> = tripDates(trip).dropLast(1)

    /** Estancias guardadas; si no hay, se arman de los hoteles que ya tengas o una sola para todo el viaje. */
    fun initial(trip: Trip): List<Stay> {
        if (trip.stays.isNotEmpty()) return trip.stays
        val hotels = trip.places.filter { it.kind == PlaceKind.HOTEL && it.checkIn.isNotBlank() }.sortedBy { it.checkIn }
        if (hotels.isNotEmpty()) return hotels.map { h ->
            Stay(city = h.address.substringAfterLast(", ").ifBlank { h.address }, checkIn = h.checkIn, checkOut = h.checkOut.ifBlank { trip.endDate }, hotelId = h.id)
        }
        return listOf(Stay(city = "", checkIn = trip.startDate, checkOut = trip.endDate))
    }

    /** Nueva estancia al final: empieza donde termina la anterior, 2 noches (sin pasarse del viaje). */
    fun add(trip: Trip, stays: List<Stay>): List<Stay> {
        val start = stays.lastOrNull()?.checkOut ?: trip.startDate
        val s = d(start) ?: return stays
        val end = d(trip.endDate)
        var out = s.plusDays(2)
        if (end != null && out.isAfter(end) && s.isBefore(end)) out = end
        return stays + Stay(city = "", checkIn = start, checkOut = out.toString())
    }

    /** Cambia las noches de una estancia y recorre las siguientes para que sigan pegadas. */
    fun setNights(stays: List<Stay>, index: Int, nights: Int): List<Stay> {
        val list = stays.toMutableList()
        val s = list.getOrNull(index) ?: return stays
        val start = d(s.checkIn) ?: return stays
        list[index] = s.copy(checkOut = start.plusDays(nights.coerceAtLeast(1).toLong()).toString())
        return chain(list, index)
    }

    /** Cambia el check-in de una estancia (conserva sus noches) y recorre las siguientes. */
    fun setCheckIn(stays: List<Stay>, index: Int, checkIn: String): List<Stay> {
        val list = stays.toMutableList()
        val s = list.getOrNull(index) ?: return stays
        val n = s.nights.coerceAtLeast(1)
        val start = d(checkIn) ?: return stays
        list[index] = s.copy(checkIn = checkIn, checkOut = start.plusDays(n.toLong()).toString())
        return chain(list, index)
    }

    private fun chain(list: MutableList<Stay>, from: Int): List<Stay> {
        for (j in from + 1 until list.size) {
            val prev = list[j - 1]
            val n = list[j].nights.coerceAtLeast(1)
            val start = d(prev.checkOut) ?: continue
            list[j] = list[j].copy(checkIn = prev.checkOut, checkOut = start.plusDays(n.toLong()).toString())
        }
        return list
    }

    /** Problemas del plan: noches sin hotel, estancias encimadas o fuera del viaje. */
    fun issues(trip: Trip, stays: List<Stay>): List<String> {
        val out = mutableListOf<String>()
        val nights = tripNights(trip)
        val covered = mutableMapOf<String, Int>()
        stays.forEach { s ->
            val a = d(s.checkIn) ?: return@forEach
            (0 until s.nights.coerceAtLeast(0)).forEach { i -> a.plusDays(i.toLong()).toString().let { covered[it] = (covered[it] ?: 0) + 1 } }
            if (s.checkOut > trip.endDate) out += "${s.city.ifBlank { "Una estancia" }} termina después del regreso (${trip.endDate})."
            if (s.checkIn < trip.startDate) out += "${s.city.ifBlank { "Una estancia" }} empieza antes del viaje."
        }
        val missing = nights.filter { (covered[it] ?: 0) == 0 }
        if (missing.isNotEmpty()) out += "Noches sin hotel: " + ranges(missing) + "."
        val twice = nights.filter { (covered[it] ?: 0) > 1 }
        if (twice.isNotEmpty()) out += "Noches con dos hoteles: " + ranges(twice) + "."
        return out
    }

    /** "15–17 nov, 20 nov" (en formato corto de fechas). */
    private fun ranges(dates: List<String>): String {
        val ds = dates.mapNotNull { d(it) }.sorted()
        val groups = mutableListOf<MutableList<LocalDate>>()
        ds.forEach { x -> if (groups.lastOrNull()?.last()?.plusDays(1) == x) groups.last() += x else groups += mutableListOf(x) }
        val month = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        return groups.joinToString(", ") { g ->
            val a = g.first(); val b = g.last()
            if (a == b) "${a.dayOfMonth} ${month[a.monthValue - 1]}" else "${a.dayOfMonth}–${b.dayOfMonth} ${month[b.monthValue - 1]}"
        }
    }

    /** Lugares que visitas durante la estancia (según el itinerario o el día preferido). */
    fun placesDuring(trip: Trip, stay: Stay): List<Place> {
        val inside = { date: String -> date >= stay.checkIn && date <= stay.checkOut }
        val fromPlan = trip.itinerary.days.filter { inside(it.date) }.flatMap { it.stops }.mapNotNull { trip.place(it.placeId) }
        val fromDates = trip.places.filter { it.preferredDate.isNotBlank() && inside(it.preferredDate) }
        return (fromPlan + fromDates).filter { it.kind == PlaceKind.VISIT || it.kind == PlaceKind.FOOD }.distinctBy { it.id }
    }

    // ---------------------------------------------------------------------------------------------
    // Enlaces para ver precio y disponibilidad reales (abren la app de Booking si está instalada).
    // ---------------------------------------------------------------------------------------------

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun bookingUrl(query: String, checkIn: String, checkOut: String, adults: Int, country: Country) =
        "https://www.booking.com/searchresults.es.html?ss=${enc(query)}&checkin=$checkIn&checkout=$checkOut" +
            "&group_adults=${adults.coerceAtLeast(1)}&no_rooms=1&group_children=0&selected_currency=${country.currency}"

    /** Google Hoteles compara Booking, Agoda, Expedia, el sitio oficial… */
    fun googleHotelsUrl(query: String, checkIn: String, checkOut: String) =
        "https://www.google.com/travel/search?q=${enc("$query hotel $checkIn $checkOut")}"
}
