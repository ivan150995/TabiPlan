package com.ivan.tabiplan.plan

import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.PlanDay
import com.ivan.tabiplan.data.Trip
import java.time.LocalDate

/** Un aviso para mostrar en el itinerario. [warn] = algo que conviene corregir. */
data class Hint(val text: String, val warn: Boolean = false)

private fun dur(m: Int) = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}

/**
 * Revisa el itinerario (hecho a mano, automático o por la IA) contra horarios, festivos,
 * horas pico, gente y horas fijas, y explica qué está bien o qué conviene cambiar.
 */
object Advisor {
    fun dayHints(trip: Trip, date: String): List<Hint> {
        val ctx = TravelCalendar.day(trip.country, date) ?: return emptyList()
        return buildList {
            ctx.holiday?.let { add(Hint("🎌 Festivo: $it. Más gente; algunos lugares cierran o cambian horario.", true)) }
            ctx.peak?.let {
                // El detalle va solo el primer día de la temporada dentro del viaje; después, solo el nombre.
                val first = date == trip.startDate || TravelCalendar.day(trip.country, ctx.date.minusDays(1))?.peak != it
                add(Hint("📈 $it" + (if (first) ctx.warning?.let { w -> ": $w" } ?: "" else ""), ctx.crowdLevel == 2))
            }
            if (ctx.holiday == null && ctx.weekend) add(Hint("📅 Es ${ctx.dayName}: los lugares populares se llenan más."))
            conflicts(trip, date, trip.places).forEach { add(Hint("⏰ $it", true)) }
        }
    }

    /** Horas fijas del mismo día que no se pueden cumplir (estancia + traslado no caben). */
    fun conflicts(trip: Trip, date: String, places: List<Place>): List<String> {
        val fixed = places.filter { !it.completed && it.fixedOn(date) != null }.sortedBy { it.fixedOn(date) }
        val (dayStart, _) = trip.hoursFor(date)
        val out = mutableListOf<String>()
        fixed.firstOrNull()?.let { f ->
            val hotel = startHotelFor(trip, date)
            val t = f.fixedOn(date)!!
            val from = toMin(dayStart) + if (hotel != null && hotel.hasCoords && f.hasCoords) travelMinutes(hotel.ll(), f.ll()) else 0
            if (t < toMin(dayStart)) out += "${f.name} (${f.fixedTime}) es antes de que empiece tu día ($dayStart); cambia el horario del día si vas a salir antes."
            else if (from > t + 5) out += "Para llegar a ${f.name} a las ${f.fixedTime} hay que salir antes de las $dayStart (traslado ~${dur(from - toMin(dayStart))})."
        }
        fixed.zipWithNext { a, b ->
            val ta = a.fixedOn(date)!!
            val tb = b.fixedOn(date)!!
            val travel = if (a.hasCoords && b.hasCoords) travelMinutes(a.ll(), b.ll()) else 30
            val ready = ta + a.plannedMinutes + travel
            if (ready > tb) out += "No da tiempo entre ${a.name} (${a.fixedTime}) y ${b.name} (${b.fixedTime}): con ${dur(a.plannedMinutes)} en el primero " +
                "y ~${dur(travel)} de traslado llegarías a las ${fromMin(ready)}. Cambia una hora o acorta la estancia."
        }
        return out
    }

    fun stopHints(trip: Trip, day: PlanDay, index: Int): List<Hint> {
        val s = day.stops.getOrNull(index) ?: return emptyList()
        val p = trip.place(s.placeId) ?: return emptyList()
        val country = trip.country
        val ctx = TravelCalendar.day(country, day.date) ?: return emptyList()
        val arrive = toMin(s.arrive)
        val leave = toMin(s.leave)
        val out = mutableListOf<Hint>()

        // Hora fija
        p.fixedOn(day.date)?.let { fixed ->
            if (arrive > fixed + 5) {
                val prev = day.stops.getOrNull(index - 1)?.let { trip.place(it.placeId) } ?: trip.place(day.startPlaceId)
                val prevLeave = day.stops.getOrNull(index - 1)?.leave
                out += Hint(
                    "⏰ Pediste llegar a las ${p.fixedTime}, pero no da tiempo" +
                        (prev?.let { " desde ${it.name}" + (prevLeave?.let { l -> " (sales $l)" } ?: "") } ?: "") +
                        ": con ~${dur(s.travelMinutes)} de traslado llegas a las ${s.arrive}. Acorta la parada anterior o cambia la hora.",
                    true,
                )
            } else out += Hint("⏰ Hora fija: ${p.fixedTime}")
        }

        // Horario de apertura
        if (p.kind != PlaceKind.HOTEL && p.kind != PlaceKind.AIRPORT) when (val h = Hours.on(p, country, day.date)) {
            is DayHours.Closed -> out += Hint("🚫 Cerrado este día: ${h.reason}.", true)
            is DayHours.Open -> {
                val w = h.windows.firstOrNull { arrive in it.start..it.end }
                val last = p.hours?.lastEntry ?: 0
                when {
                    w == null && h.windows.any { it.start > arrive } -> out += Hint("🕘 Abre a las ${fromMin(h.windows.first { it.start > arrive }.start)} y llegas a las ${s.arrive}.", true)
                    w == null -> out += Hint("🚫 Ya cerró a esa hora (horario: ${Hours.format(h.windows)}).", true)
                    last > 0 && arrive > w.end - last -> out += Hint("🚫 Última entrada a las ${fromMin(w.end - last)} y llegas a las ${s.arrive}.", true)
                    leave > w.end + 5 -> out += Hint("🕘 Cierra a las ${fromMin(w.end % 1440)}: solo tendrías ${dur(w.end - arrive)}.", true)
                    else -> out += Hint("🕘 Abierto: ${Hours.format(h.windows)}" + if (last > 0) " (última entrada ${fromMin(w.end - last)})" else "")
                }
            }
            DayHours.Unknown -> if (ctx.warning != null && ctx.crowdLevel == 2) out += Hint("🕘 Horario sin confirmar y es ${ctx.peak}: puede estar cerrado; revísalo.", true)
        }
        if (s.wait >= 45) out += Hint("⏳ ~${dur(s.wait)} libres antes de llegar: puedes pasear por la zona o agregar otro lugar cercano.")
        else if (s.wait >= 10) out += Hint("⏳ Esperas ~${dur(s.wait)} al llegar" + if (p.fixedOn(day.date) != null) " (hora fija)." else " a que abra o a buena hora.")

        // Comida: dentro del horario normal y fuera de la hora pico
        if (p.kind == PlaceKind.FOOD) {
            val mw = TravelCalendar.meals(country).firstOrNull { arrive in it.start..it.end }
            when {
                mw == null -> out += Hint("🍽️ Fuera del horario normal de comida.", true)
                mw.inPeak(arrive) -> out += Hint("🍽️ Llegas en la hora pico de la ${mw.label} (${mw.peakText()}): habrá fila. Mejor a las ${fromMin(mw.quietBefore())} o ${fromMin(mw.quietAfter())}.", true)
                else -> out += Hint("🍽️ Buena hora para la ${mw.label}: evitas la hora pico (${mw.peakText()}).")
            }
        }

        // Gente
        val info = p.info
        if ((info?.crowd ?: 0) >= 3) {
            val opens = (Hours.on(p, country, day.date) as? DayHours.Open)?.windows?.firstOrNull()?.start
            val late = opens != null && arrive > opens + 60
            val peakTxt = info?.peakTimes?.takeIf { it.isNotBlank() }?.let { " Más lleno: $it." } ?: ""
            ctx.crowdReason?.let { r ->
                out += Hint("👥 ${r.replaceFirstChar { it.uppercase() }}: se llena mucho." + (if (late) " Llega a la apertura (${fromMin(opens!!)})." else "") + peakTxt, late)
            } ?: if (late) out += Hint("👥 Suele llenarse: mejor a la apertura (${fromMin(opens!!)}).$peakTxt") else Unit
        }
        val sunset = TravelCalendar.sunset(country, ctx.date, p.lat, p.lng)
        when (info?.bestSlot) {
            "sunset" -> out += Hint("🌇 Atardecer ~${fromMin(sunset)}", arrive > sunset || leave < sunset - 30)
            "night" -> maxOf(sunset, toMin("17:30")).let { n -> if (arrive < n) out += Hint("🌃 Se disfruta más de noche (después de las ${fromMin(n)}).") }
            "morning" -> if (arrive > toMin("11:00")) out += Hint("🌅 Mejor temprano por la mañana.")
        }
        val tickets = trip.docsFor(p.id)
        when {
            tickets.isNotEmpty() -> out += Hint("🎟️ Boleto guardado en la app: " + tickets.joinToString { it.title } + ".")
            p.needsTicket -> out += Hint("🎟️ Necesita boleto o reserva y aún no lo guardas en la app.", true)
        }

        // Hora pico del transporte (y con maletas el día de llegada)
        val depart = arrive - s.wait - s.travelMinutes
        if (s.travelMinutes >= 15 && TravelCalendar.rushHour(country, ctx.date, depart)) {
            val luggage = index == 0 && trip.place(day.startPlaceId)?.kind == PlaceKind.AIRPORT
            out += Hint(
                (if (country == Country.JP) "🚃 Sales en hora pico: trenes muy llenos." else "🚇 Sales en hora pico: metro y calles llenos.") +
                    if (luggage) " Con maletas conviene taxi, limusina o mandar el equipaje al hotel (takkyūbin)." else "",
                luggage,
            )
        }
        return out
    }

    /** Texto con el horario del día de un lugar, para la pestaña Hoy. */
    fun openToday(trip: Trip, p: Place, date: String = LocalDate.now().toString()): Hint? = when (val h = Hours.on(p, trip.country, date)) {
        is DayHours.Closed -> Hint("🚫 Hoy está cerrado: ${h.reason}.", true)
        is DayHours.Open -> Hint("🕘 Hoy abre ${Hours.format(h.windows)}")
        DayHours.Unknown -> null
    }
}
