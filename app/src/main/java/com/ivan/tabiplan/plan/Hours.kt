package com.ivan.tabiplan.plan

import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.data.OpenHours
import com.ivan.tabiplan.data.Place
import java.time.LocalDate

/** Un tramo abierto en minutos desde la medianoche (end puede pasar de 1440 si cierra de madrugada). */
data class Window(val start: Int, val end: Int)

/** Resultado de revisar si un lugar abre cierto día. */
sealed class DayHours {
    object Unknown : DayHours()
    data class Closed(val reason: String) : DayHours()
    data class Open(val windows: List<Window>) : DayHours()
}

object Hours {
    /** "09:00-17:00,18:00-22:00" → tramos. "" → vacío (cerrado). "?" o basura → null. */
    fun parse(s: String): List<Window>? {
        val t = s.trim()
        if (t == "?") return null
        if (t.isEmpty()) return emptyList()
        val out = t.split(",").mapNotNull { part ->
            val m = Regex("(\\d{1,2}):?(\\d{2})\\s*[-–]\\s*(\\d{1,2}):?(\\d{2})").find(part) ?: return@mapNotNull null
            val (h1, m1, h2, m2) = m.destructured
            val a = h1.toInt() * 60 + m1.toInt()
            var b = h2.toInt() * 60 + m2.toInt()
            if (b <= a) b += 1440
            Window(a, b)
        }
        return out.takeIf { it.isNotEmpty() }
    }

    fun format(w: List<Window>) = w.joinToString(", ") { "${fromMin(it.start)}–${fromMin(it.end % 1440)}" }

    /** Horario de un lugar en una fecha, tomando en cuenta festivos y cierres especiales. */
    fun on(place: Place, country: Country, date: String): DayHours {
        val h = place.hours ?: return DayHours.Unknown
        val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return DayHours.Unknown
        if (date in h.closedDates) return DayHours.Closed("cierre especial ese día")
        val idx = d.dayOfWeek.value - 1
        val holiday = TravelCalendar.holiday(country, d)
        val today = parse(h.week[idx])
        if (holiday != null) when (h.holidayRule) {
            "closed" -> return DayHours.Closed("cierra en festivos ($holiday)")
            "shift", "open" -> if (today != null && today.isEmpty()) {
                // Su día de descanso cae en festivo: abre con el horario de un día normal.
                val usual = h.week.mapNotNull { parse(it) }.filter { it.isNotEmpty() }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                if (usual != null) return DayHours.Open(usual)
            }
        }
        if (h.holidayRule == "shift" && holiday == null) {
            // Si ayer (o antes, en puentes) fue festivo y era su día de descanso, descansa hoy.
            var p = d.minusDays(1)
            while (TravelCalendar.holiday(country, p) != null) {
                if (parse(h.week[p.dayOfWeek.value - 1])?.isEmpty() == true) return DayHours.Closed("descansa hoy porque abrió en el festivo")
                p = p.minusDays(1)
            }
        }
        return when {
            today == null -> DayHours.Unknown
            today.isEmpty() -> DayHours.Closed("cierra los ${DAY_NAMES[idx]}")
            else -> DayHours.Open(today)
        }
    }

    /** Texto corto de la semana: "Mar–Dom 09:30–17:00 · cierra lunes". */
    fun weekText(h: OpenHours): String {
        if (!h.known) return ""
        val groups = mutableListOf<Pair<IntRange, String>>()
        h.week.forEachIndexed { i, s ->
            val v = parse(s)?.let { if (it.isEmpty()) "" else format(it) } ?: "?"
            val last = groups.lastOrNull()
            if (last != null && last.second == v && last.first.last == i - 1) groups[groups.lastIndex] = (last.first.first..i) to v
            else groups += (i..i) to v
        }
        val open = groups.filter { it.second.isNotEmpty() && it.second != "?" }.joinToString(" · ") { (r, v) ->
            val days = if (r.first == r.last) DAY_SHORT[r.first] else "${DAY_SHORT[r.first]}–${DAY_SHORT[r.last]}"
            if (r.first == 0 && r.last == 6) "Todos los días $v" else "$days $v"
        }
        val closed = h.week.indices.filter { h.week[it].isEmpty() }.map { DAY_NAMES[it] }
        val rule = when (h.holidayRule) {
            "closed" -> "cierra en festivos"
            "shift" -> "si su día de descanso es festivo, abre y descansa al día siguiente"
            else -> ""
        }
        return listOf(open, if (closed.isNotEmpty()) "cierra ${closed.joinToString(", ")}" else "", rule,
            if (h.lastEntry > 0) "última entrada ${h.lastEntry} min antes del cierre" else "")
            .filter { it.isNotBlank() }.joinToString(" · ")
    }
}
