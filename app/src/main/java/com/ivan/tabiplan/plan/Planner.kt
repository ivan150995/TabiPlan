package com.ivan.tabiplan.plan

import com.ivan.tabiplan.data.Itinerary
import com.ivan.tabiplan.data.Meal
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.PlanDay
import com.ivan.tabiplan.data.Stop
import com.ivan.tabiplan.data.Trip
import com.ivan.tabiplan.maps.LatLngD
import com.ivan.tabiplan.maps.haversineKm
import java.time.LocalDate
import kotlin.math.abs
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

/**
 * Hotel desde donde sale el día: el de la noche anterior si ese día cambias de hotel (sales con maletas
 * y terminas en el nuevo); si no, el de esa noche.
 */
fun startHotelFor(trip: Trip, date: String): Place? {
    val tonight = hotelFor(trip, date)
    if (date <= trip.startDate) return tonight
    val prev = runCatching { LocalDate.parse(date).minusDays(1).toString() }.getOrNull() ?: return tonight
    return hotelFor(trip, prev)?.takeIf { it.id != tonight?.id && it.checkOut == date } ?: tonight
}

/** Minutos estimados de traslado entre dos puntos (sin Google). */
fun travelMinutes(a: LatLngD, b: LatLngD): Int {
    val km = haversineKm(a, b) * 1.3
    return when {
        km <= 1.2 -> (km / 4.5 * 60).roundToInt().coerceAtLeast(3)
        km <= 15 -> (15 + km / 25 * 60).roundToInt()
        // Trayectos medianos (aeropuerto, Fuji, Nara): tren rápido o autobús.
        km <= 100 -> (25 + km).roundToInt()
        // Otra ciudad lejos (Tokio–Osaka, Kumamoto–Kioto): Shinkansen, ~240 km/h contando estaciones.
        else -> (125 + (km - 100) / 4).roundToInt()
    }
}

internal fun Place.ll() = LatLngD(lat!!, lng!!)

/** Una visita calculada: cuándo se llega, cuánto se espera y si respeta horarios. */
private data class Visit(
    val place: Place,
    val travel: Int,
    val mealBefore: Int,
    val wait: Int,
    val arrive: Int,
    val leave: Int,
    /** false = no cumple horario (cerrado, llega tarde…); solo pasa al reacomodar a mano. */
    val ok: Boolean,
)

/**
 * Simula un día parada por parada: traslados, esperar a que abra, horarios de comida y
 * tiempo libre para comer si no hay restaurante en la lista.
 */
private class DaySim(
    val trip: Trip,
    val date: String,
    startClock: Int,
    var pos: LatLngD?,
    /** Hora límite para terminar (ya descontando el regreso al aeropuerto si aplica). */
    val dayEnd: Int,
    val endPoint: Place?,
) {
    val country = trip.country
    val ctx = TravelCalendar.day(country, date)!!
    val lunch = TravelCalendar.meal(country, Meal.LUNCH)
    var clock = startClock
    val mealsDone = mutableSetOf<Meal>()
    val stops = mutableListOf<Stop>()

    fun travelTo(p: Place): Int = when {
        pos != null && p.hasCoords -> travelMinutes(pos!!, p.ll())
        stops.isEmpty() -> 0
        else -> 30
    }

    fun back(p: Place): Int =
        if (endPoint != null && endPoint.hasCoords && p.hasCoords && endPoint.id != p.id) travelMinutes(p.ll(), endPoint.ll()) else 0

    /** Horas de llegada permitidas: (desde, hasta). null = sin restricción. Vacío = imposible ese día. */
    fun ranges(p: Place): List<Pair<Int, Int>>? {
        if (p.kind == PlaceKind.HOTEL || p.kind == PlaceKind.AIRPORT) return null
        val open: List<Pair<Int, Int>>? = when (val h = Hours.on(p, country, date)) {
            is DayHours.Closed -> return emptyList()
            DayHours.Unknown -> null
            is DayHours.Open -> h.windows.map { w ->
                val lastEntry = p.hours?.lastEntry ?: 0
                val need = minOf(p.plannedMinutes, w.end - w.start)
                w.start to (w.end - maxOf(lastEntry, need))
            }
        }
        if (p.kind != PlaceKind.FOOD) return open
        val meals = TravelCalendar.mealsFor(country, p.meal).filter { it.meal !in mealsDone }
            .map { it.quietBefore() to it.lastArrival }
        if (open == null) return meals
        return open.flatMap { a -> meals.mapNotNull { b -> (maxOf(a.first, b.first) to minOf(a.second, b.second)).takeIf { it.first <= it.second } } }
    }

    /**
     * Calcula la visita. [strict]: si no cumple horarios devuelve null (para elegir). Si no, la acomoda
     * igual y la marca. [lunchAhead]: hay un restaurante más adelante para la comida.
     */
    fun peek(p: Place, strict: Boolean, lunchAhead: Boolean): Visit? {
        val travel = travelTo(p)
        // Tiempo libre para comer antes de seguir, si ya es hora y no hay restaurante en la lista.
        val needLunch = p.kind != PlaceKind.FOOD && Meal.LUNCH !in mealsDone && !lunchAhead &&
            clock >= lunch.quietBefore() - 15 && clock <= lunch.lastArrival && p.kind != PlaceKind.AIRPORT
        // Sin hacerte llegar tarde a una hora fija.
        val fixedAt = p.fixedOn(date)
        val meal = if (needLunch && (fixedAt == null || clock + 60 + travel <= fixedAt)) 60 else 0
        val arrive0 = clock + meal + travel
        var arrive = arrive0
        var ok = true
        val fixed = fixedAt
        val r = ranges(p)
        if (fixed != null) {
            // Hora que tú elegiste: se espera lo que haga falta; si no da tiempo, se marca el conflicto.
            arrive = maxOf(arrive0, fixed)
            ok = arrive0 <= fixed
            if (strict && !ok) return null
        } else if (r != null) {
            val slot = r.firstOrNull { maxOf(arrive0, it.first) <= it.second }
            when {
                slot == null -> { if (strict) return null; ok = false }
                // Al elegir no se esperan más de 2 h; si va porque lo pediste ese día, se espera a que abra o a la hora de comer.
                slot.first - arrive0 > 120 && strict -> return null
                else -> arrive = maxOf(arrive0, slot.first)
            }
        }
        val leave = arrive + p.plannedMinutes
        if (strict && leave + back(p) > dayEnd) return null
        return Visit(p, travel, meal, arrive - arrive0, arrive, leave, ok)
    }

    fun commit(v: Visit, note: String = "") {
        stops += Stop(v.place.id, fromMin(v.arrive), fromMin(v.leave), v.travel, note, v.mealBefore, v.wait)
        if (v.mealBefore > 0) mealsDone += Meal.LUNCH
        if (v.place.kind == PlaceKind.FOOD) {
            TravelCalendar.meals(country).firstOrNull { v.arrive in it.start..it.end }?.let { mealsDone += it.meal }
        } else if (v.place.plannedMinutes >= 180 && v.arrive <= lunch.start + 30 && v.leave >= lunch.lastArrival) {
            mealsDone += Meal.LUNCH // visita larga a la hora de comer: se come ahí
        }
        clock = v.leave
        if (v.place.hasCoords) pos = v.place.ll()
    }

    /** Penalización (en "km equivalentes") por mala hora: esperar, hora pico, mucha gente, momento del día. */
    fun penalty(v: Visit, lunchFoodPending: Boolean, quietDayLater: Boolean): Double {
        val p = v.place
        var s = v.wait / 10.0
        val info = p.info
        if (p.kind == PlaceKind.FOOD) {
            s -= 6.0 // llenar una comida pendiente vale la pena
            if (TravelCalendar.meals(country).any { it.inPeak(v.arrive) }) s += 4.0
        } else if (lunchFoodPending && Meal.LUNCH !in mealsDone && v.arrive < lunch.lastArrival && v.leave > lunch.lastArrival && p.plannedMinutes < 180) {
            s += 5.0 // esta visita haría perder la hora de comida en el restaurante elegido
        }
        val crowd = info?.crowd ?: 0
        if (crowd >= 3) {
            val opens = (Hours.on(p, country, date) as? DayHours.Open)?.windows?.firstOrNull()?.start
            if (opens != null) s += ((v.arrive - opens).coerceAtLeast(0) / 45.0).coerceAtMost(4.0)
            if (ctx.crowdLevel > 0 && p.preferredDate.isEmpty() && quietDayLater) s += if (ctx.crowdLevel == 2) 7.0 else 4.0
        }
        val sunset = TravelCalendar.sunset(country, ctx.date, p.lat, p.lng)
        when (info?.bestSlot) {
            "morning" -> s += ((v.arrive - toMin("11:00")).coerceAtLeast(0) / 60.0).coerceAtMost(4.0)
            "sunset" -> s += (abs(v.arrive - (sunset - 60)) / 30.0).coerceAtMost(6.0)
            "night" -> {
                val night = maxOf(sunset, toMin("17:30"))
                if (v.arrive < night) s += ((night - v.arrive) / 30.0).coerceAtMost(8.0)
            }
        }
        return s
    }
}

/**
 * Itinerario sin IA: reparte los lugares por días agrupando los cercanos.
 * Cada día sale del hotel de esa noche y va al lugar más conveniente mientras quepa en el horario:
 * cerca, abierto ese día, a buena hora (comidas fuera de la hora pico, lugares llenos temprano y en
 * días normales, miradores al atardecer, barrios de noche).
 */
object Planner {
    fun plan(trip: Trip): Itinerary {
        val dates = tripDates(trip)
        val pending = trip.places
            .filter { it.kind != PlaceKind.HOTEL && !it.completed }
            .toMutableList()
        val quietDays = dates.filter { (TravelCalendar.day(trip.country, it)?.crowdLevel ?: 0) == 0 }
        val days = mutableListOf<PlanDay>()
        for (date in dates) {
            val hotel = hotelFor(trip, date)
            val (startS, endS) = trip.hoursFor(date)
            var clock = toMin(startS)
            var end = toMin(endS)
            var pos: LatLngD? = hotel?.ll()
            // Día de cambio de hotel: se sale del hotel de la noche anterior (con maletas) y se termina en el nuevo.
            var startPlace: Place? = startHotelFor(trip, date)
            if (startPlace?.hasCoords == true) pos = startPlace!!.ll()

            // Día de llegada: el día empieza en el aeropuerto (migración, maletas) y no en el hotel.
            fun airportFor(d: String) = pending.firstOrNull { it.kind == PlaceKind.AIRPORT && it.preferredDate == d }
                ?: pending.firstOrNull { it.kind == PlaceKind.AIRPORT && it.preferredDate.isEmpty() }
            if (date == dates.first()) airportFor(date)?.let { a ->
                startPlace = a
                if (a.hasCoords) pos = a.ll()
                clock += a.plannedMinutes
                pending.remove(a)
            }
            // Día de regreso: el aeropuerto va al final; se guarda tiempo para llegar.
            val departure = if (date == dates.last() && dates.size > 1) airportFor(date) else null
            departure?.let { a ->
                pending.remove(a)
                val toAirport = if (hotel != null && a.hasCoords) travelMinutes(hotel.ll(), a.ll()) else 90
                end -= toAirport + a.plannedMinutes
            }
            val sim = DaySim(trip, date, clock, pos, end, departure ?: hotel)
            val quietDayLater = quietDays.any { it > date }

            // Lugares con hora fija este día: son anclas; lo demás se acomoda entre ellas.
            val anchors = pending.filter { it.kind != PlaceKind.AIRPORT && it.fixedOn(date) != null }.sortedBy { it.fixedOn(date) }
            while (true) {
                val anchor = anchors.firstOrNull { it in pending }
                val anchorAt = anchor?.fixedOn(date)
                val cands = pending.filter {
                    it.kind != PlaceKind.AIRPORT && it.fixedOn(date) == null && (it.preferredDate.isEmpty() || it.preferredDate <= date)
                }
                val lunchFood = (cands + listOfNotNull(anchor)).any { f ->
                    f.kind == PlaceKind.FOOD && f.meal in listOf(Meal.ANY, Meal.LUNCH) &&
                        sim.peek(f, strict = true, lunchAhead = true)?.let { it.arrive in sim.lunch.start..sim.lunch.end } == true
                }
                val ref = sim.pos
                val best = cands.mapNotNull { p ->
                    val v = sim.peek(p, strict = true, lunchAhead = lunchFood) ?: return@mapNotNull null
                    // Tiene que dar tiempo de llegar a la siguiente hora fija.
                    if (anchor != null && anchorAt != null) {
                        val toAnchor = if (p.hasCoords && anchor.hasCoords) travelMinutes(p.ll(), anchor.ll()) else 30
                        if (v.leave + toAnchor > anchorAt) return@mapNotNull null
                    }
                    val dist = if (ref != null && p.hasCoords) haversineKm(ref, p.ll()) else 50.0
                    var score = (if (p.mustVisit) dist * 0.6 else dist) + sim.penalty(v, lunchFood, quietDayLater)
                    if (p.preferredDate == date) score -= 20
                    Triple(v, dist, score)
                }.minByOrNull { it.third }
                // Si lo mejor está muy lejos (>25 km) y ya hay paradas, mejor otro día.
                val tooFar = best != null && sim.stops.isNotEmpty() && best.second > 25 && best.first.place.preferredDate != date
                if (best == null || tooFar) {
                    if (anchor == null) break
                    sim.commit(sim.peek(anchor, strict = false, lunchAhead = false)!!)
                    pending.remove(anchor)
                    continue
                }
                sim.commit(best.first)
                pending.remove(best.first.place)
            }
            // Lo que pediste para este día va aunque no quepa en el horario.
            pending.filter { it.preferredDate == date && it.kind != PlaceKind.AIRPORT }.forEach { p ->
                sim.commit(sim.peek(p, strict = false, lunchAhead = false)!!)
                pending.remove(p)
            }
            departure?.let { a ->
                val travel = sim.travelTo(a)
                // Se llega al aeropuerto de modo que termine a la hora fin del día (pon ahí la hora del vuelo).
                val arrive = maxOf(sim.clock + travel, toMin(endS) - a.plannedMinutes)
                sim.commit(Visit(a, travel, 0, 0, arrive, arrive + a.plannedMinutes, true))
            }
            days += PlanDay(date = date, startPlaceId = startPlace?.id.orEmpty(), stops = sim.stops.toList())
        }
        return Itinerary(
            days = days,
            unassigned = pending.map { it.id },
            notes = if (pending.isEmpty()) "" else "No cupieron ${pending.size} lugar(es) en el horario (o estaban cerrados los días libres). Alarga los días, quita tiempo en algún lugar o agrega días.",
            byAi = false,
            createdAt = System.currentTimeMillis(),
        )
    }

    /** Recalcula horas de llegada/salida de un día tras reordenar o cambiar duraciones. */
    fun retime(trip: Trip, day: PlanDay): PlanDay {
        val (startS, endS) = trip.hoursFor(day.date)
        val startPlace = trip.place(day.startPlaceId)
        val clock = toMin(startS) + if (startPlace?.kind == PlaceKind.AIRPORT) startPlace.plannedMinutes else 0
        val dates = tripDates(trip)
        val sim = DaySim(trip, day.date, clock, startPlace?.takeIf { it.hasCoords }?.ll(), toMin(endS), hotelFor(trip, day.date))
        val places = day.stops.mapNotNull { s -> trip.place(s.placeId)?.let { s to it } }
        places.forEachIndexed { i, (s, p) ->
            val lunchAhead = places.drop(i + 1).any { it.second.kind == PlaceKind.FOOD && it.second.meal in listOf(Meal.ANY, Meal.LUNCH) }
            var v = sim.peek(p, strict = false, lunchAhead = lunchAhead)!!
            if (p.kind == PlaceKind.AIRPORT && i == places.lastIndex && day.date == dates.lastOrNull() && dates.size > 1) {
                val arrive = maxOf(v.arrive, toMin(endS) - p.plannedMinutes)
                v = v.copy(arrive = arrive, leave = arrive + p.plannedMinutes, wait = 0)
            }
            sim.commit(v, s.note)
        }
        return day.copy(stops = sim.stops.toList())
    }
}
