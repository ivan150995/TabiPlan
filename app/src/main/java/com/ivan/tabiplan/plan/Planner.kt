package com.ivan.tabiplan.plan

import com.ivan.tabiplan.data.Itinerary
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.PlanDay
import com.ivan.tabiplan.data.Stop
import com.ivan.tabiplan.data.Trip
import com.ivan.tabiplan.maps.LatLngD
import com.ivan.tabiplan.maps.haversineKm
import java.time.LocalDate
import kotlin.math.roundToInt

/** Fechas del viaje (inclusive). */
fun tripDates(trip: Trip): List<String> {
    val start = runCatching { LocalDate.parse(trip.startDate) }.getOrNull() ?: return emptyList()
    val end = runCatching { LocalDate.parse(trip.endDate) }.getOrNull() ?: start
    if (end.isBefore(start)) return listOf(start.toString())
    return generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.take(60).map { it.toString() }.toList()
}

fun toMin(hhmm: String): Int {
    val p = hhmm.split(":")
    return (p.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0)
}

fun fromMin(m: Int): String = "%02d:%02d".format((m / 60).coerceIn(0, 47), m % 60)

/** Hotel donde se duerme la noche de esa fecha (o el último antes). */
fun hotelFor(trip: Trip, date: String): Place? {
    val hotels = trip.places.filter { it.kind == PlaceKind.HOTEL && it.hasCoords }
    return hotels.firstOrNull { it.checkIn.isNotEmpty() && it.checkIn <= date && (it.checkOut.isEmpty() || date < it.checkOut) }
        ?: hotels.lastOrNull { it.checkIn.isNotEmpty() && it.checkIn <= date }
        ?: hotels.firstOrNull { it.checkIn.isEmpty() }
}

/** Minutos estimados de traslado entre dos puntos (sin Google). */
fun travelMinutes(a: LatLngD, b: LatLngD): Int {
    val km = haversineKm(a, b) * 1.3
    return when {
        km <= 1.2 -> (km / 4.5 * 60).roundToInt().coerceAtLeast(3)
        km <= 15 -> (15 + km / 25 * 60).roundToInt()
        // Trayectos largos (aeropuerto, otra ciudad): trenes rápidos.
        else -> (25 + km / 60 * 60).roundToInt()
    }
}

private fun Place.ll() = LatLngD(lat!!, lng!!)

/**
 * Itinerario sin IA: reparte los lugares por días agrupando los cercanos.
 * Cada día sale del hotel de esa noche, toma primero los "imprescindibles" y los del día preferido,
 * y luego va al lugar más cercano mientras quepa en el horario (dejando tiempo para volver).
 */
object Planner {
    fun plan(trip: Trip): Itinerary {
        val dates = tripDates(trip)
        val pending = trip.places
            .filter { it.kind != PlaceKind.HOTEL && !it.completed }
            .toMutableList()
        // Los que no tienen coordenadas no se pueden ordenar por distancia: van al final de cada día si caben.
        val days = mutableListOf<PlanDay>()
        for (date in dates) {
            val hotel = hotelFor(trip, date)
            val (startS, endS) = trip.hoursFor(date)
            val start = toMin(startS)
            var end = toMin(endS)
            var clock = start
            var pos: LatLngD? = hotel?.ll()
            val stops = mutableListOf<Stop>()
            var startPlace: Place? = hotel

            // Día de llegada: el día empieza en el aeropuerto (migración, maletas) y no en el hotel.
            fun airportFor(d: String) = pending.firstOrNull { it.kind == PlaceKind.AIRPORT && it.preferredDate == d }
                ?: pending.firstOrNull { it.kind == PlaceKind.AIRPORT && it.preferredDate.isEmpty() }
            if (date == dates.first()) airportFor(date)?.let { a ->
                startPlace = a
                if (a.hasCoords) pos = a.ll()
                clock = start + a.plannedMinutes
                pending.remove(a)
            }
            // Día de regreso: el aeropuerto va al final; se guarda tiempo para llegar.
            val departure = if (date == dates.last() && dates.size > 1) airportFor(date) else null
            departure?.let { a ->
                pending.remove(a)
                val toAirport = if (hotel != null && a.hasCoords) travelMinutes(hotel.ll(), a.ll()) else 90
                end -= toAirport + a.plannedMinutes
            }
            val endPoint: Place? = departure ?: hotel

            fun fits(p: Place): Int? {
                val travel = if (pos != null && p.hasCoords) travelMinutes(pos!!, p.ll()) else if (stops.isEmpty()) 0 else 30
                val back = if (endPoint != null && endPoint.hasCoords && p.hasCoords) travelMinutes(p.ll(), endPoint.ll()) else 0
                val total = clock + travel + p.plannedMinutes
                return if (total + back <= end) travel else null
            }

            fun add(p: Place, travel: Int) {
                val arrive = clock + travel
                val leave = arrive + p.plannedMinutes
                stops += Stop(p.id, fromMin(arrive), fromMin(leave), travel)
                clock = leave
                if (p.hasCoords) pos = p.ll()
                pending.remove(p)
            }

            // 1) Lugares fijados para este día.
            pending.filter { it.preferredDate == date }.sortedByDescending { it.mustVisit }.forEach { p ->
                // Se agrega aunque se pase de la hora: el usuario lo pidió ese día.
                val travel = fits(p) ?: if (pos != null && p.hasCoords) travelMinutes(pos!!, p.ll()) else 30
                add(p, travel)
            }
            // 2) Vecino más cercano, priorizando imprescindibles (cuentan como si estuvieran 40 % más cerca).
            while (true) {
                val candidates = pending.filter { it.kind != PlaceKind.AIRPORT && (it.preferredDate.isEmpty() || it.preferredDate < date) }
                if (candidates.isEmpty()) break
                val ref = pos
                val next = candidates.mapNotNull { p ->
                    val travel = fits(p) ?: return@mapNotNull null
                    val dist = if (ref != null && p.hasCoords) haversineKm(ref, p.ll()) else 50.0
                    Triple(p, travel, if (p.mustVisit) dist * 0.6 else dist)
                }.minByOrNull { it.third } ?: break
                // Si el más cercano está muy lejos (>25 km) y ya hay paradas, mejor otro día.
                if (stops.isNotEmpty() && next.third > 25) break
                add(next.first, next.second)
            }
            departure?.let { a ->
                val travel = if (pos != null && a.hasCoords) travelMinutes(pos!!, a.ll()) else 90
                // Se llega al aeropuerto de modo que termine a la hora fin del día (pon ahí la hora del vuelo).
                clock = maxOf(clock, toMin(endS) - a.plannedMinutes - travel)
                add(a, travel)
            }
            days += PlanDay(date = date, startPlaceId = startPlace?.id.orEmpty(), stops = stops)
        }
        return Itinerary(
            days = days,
            unassigned = pending.map { it.id },
            notes = if (pending.isEmpty()) "" else "No cupieron ${pending.size} lugar(es) en el horario. Alarga los días, quita tiempo en algún lugar o agrega días.",
            byAi = false,
            createdAt = System.currentTimeMillis(),
        )
    }

    /** Recalcula horas de llegada/salida de un día tras reordenar o cambiar duraciones. */
    fun retime(trip: Trip, day: PlanDay): PlanDay {
        val (startS, _) = trip.hoursFor(day.date)
        val startPlace = trip.place(day.startPlaceId)
        var clock = toMin(startS) + if (startPlace?.kind == PlaceKind.AIRPORT) startPlace.plannedMinutes else 0
        var pos = startPlace?.takeIf { it.hasCoords }?.ll()
        val stops = day.stops.mapNotNull { s ->
            val p = trip.place(s.placeId) ?: return@mapNotNull null
            val travel = if (pos != null && p.hasCoords) travelMinutes(pos!!, p.ll()) else s.travelMinutes
            val arrive = clock + travel
            val leave = arrive + p.plannedMinutes
            clock = leave
            if (p.hasCoords) pos = p.ll()
            s.copy(arrive = fromMin(arrive), leave = fromMin(leave), travelMinutes = travel)
        }
        return day.copy(stops = stops)
    }
}
