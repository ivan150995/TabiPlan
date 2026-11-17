package com.ivan.tabiplan.plan

import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.data.Meal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Cómo es un día del viaje: festivo, temporada alta, fin de semana. */
data class DayContext(
    val date: LocalDate,
    val holiday: String?,
    /** Temporada alta o evento (Golden Week, Semana Santa…). */
    val peak: String?,
    /** Aviso extra (cierres de año nuevo…). */
    val warning: String?,
    /** 0 = día normal, 1 = fin de semana, 2 = festivo o temporada alta. */
    val crowdLevel: Int,
) {
    val weekend get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val dayName get() = DAY_NAMES[date.dayOfWeek.value - 1]

    /** "sábado", "festivo (Día de la Cultura)", "Golden Week"… para explicar por qué hay más gente. */
    val crowdReason: String?
        get() = holiday?.let { "festivo ($it)" } ?: peak ?: if (weekend) dayName else null
}

val DAY_NAMES = listOf("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
val DAY_SHORT = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

/**
 * Festivos oficiales, temporadas altas, horarios de comida y atardecer de Japón y México.
 * Los festivos se calculan con las reglas de cada país (no hay que actualizar una lista cada año).
 */
object TravelCalendar {
    private val cache = HashMap<Pair<Country, Int>, Map<LocalDate, String>>()

    fun holidays(country: Country, year: Int): Map<LocalDate, String> =
        cache.getOrPut(country to year) { if (country == Country.JP) japan(year) else mexico(year) }

    fun holiday(country: Country, date: LocalDate): String? = holidays(country, date.year)[date]

    fun day(country: Country, date: String): DayContext? = runCatching { LocalDate.parse(date) }.getOrNull()?.let { day(country, it) }

    fun day(country: Country, date: LocalDate): DayContext {
        val h = holiday(country, date)
        val (peak, warning, strong) = season(country, date)
        val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
        val level = when {
            h != null || strong -> 2
            weekend || peak != null -> 1
            else -> 0
        }
        return DayContext(date, h, peak, warning, level)
    }

    // ---------------------------------------------------------------------------------------------
    // Japón
    // ---------------------------------------------------------------------------------------------

    private fun nthMonday(y: Int, m: Int, n: Int) = LocalDate.of(y, m, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY))

    private fun japan(y: Int): Map<LocalDate, String> {
        val h = sortedMapOf<LocalDate, String>()
        fun d(m: Int, day: Int) = LocalDate.of(y, m, day)
        val k = y - 1980
        val vernal = floor(20.8431 + 0.242194 * k - floor(k / 4.0)).toInt()
        val autumnal = floor(23.2488 + 0.242194 * k - floor(k / 4.0)).toInt()
        h[d(1, 1)] = "Año Nuevo"
        h[nthMonday(y, 1, 2)] = "Día de la Mayoría de Edad"
        h[d(2, 11)] = "Fundación Nacional"
        h[d(2, 23)] = "Cumpleaños del Emperador"
        h[d(3, vernal)] = "Equinoccio de primavera"
        h[d(4, 29)] = "Día de Shōwa"
        h[d(5, 3)] = "Día de la Constitución"
        h[d(5, 4)] = "Día del Verde"
        h[d(5, 5)] = "Día del Niño"
        h[nthMonday(y, 7, 3)] = "Día del Mar"
        h[d(8, 11)] = "Día de la Montaña"
        h[nthMonday(y, 9, 3)] = "Día del Respeto a los Mayores"
        h[d(9, autumnal)] = "Equinoccio de otoño"
        h[nthMonday(y, 10, 2)] = "Día del Deporte"
        h[d(11, 3)] = "Día de la Cultura"
        h[d(11, 23)] = "Día del Trabajo"
        // Festivo en domingo: se descansa el siguiente día que no sea festivo.
        h.keys.filter { it.dayOfWeek == DayOfWeek.SUNDAY }.toList().forEach { sun ->
            var next = sun.plusDays(1)
            while (h.containsKey(next)) next = next.plusDays(1)
            h[next] = "Descanso por ${h[sun]}"
        }
        // Día entre dos festivos (Silver Week): también es festivo.
        h.keys.toList().forEach { a ->
            val mid = a.plusDays(1)
            if (!h.containsKey(mid) && h.containsKey(mid.plusDays(1)) && mid.dayOfWeek != DayOfWeek.SUNDAY) h[mid] = "Festivo entre festivos"
        }
        return h
    }

    // ---------------------------------------------------------------------------------------------
    // México (Ley Federal del Trabajo, art. 74)
    // ---------------------------------------------------------------------------------------------

    private fun mexico(y: Int): Map<LocalDate, String> {
        val h = sortedMapOf<LocalDate, String>()
        h[LocalDate.of(y, 1, 1)] = "Año Nuevo"
        h[LocalDate.of(y, 2, 1).with(TemporalAdjusters.dayOfWeekInMonth(1, DayOfWeek.MONDAY))] = "Día de la Constitución"
        h[nthMonday(y, 3, 3)] = "Natalicio de Benito Juárez"
        h[LocalDate.of(y, 5, 1)] = "Día del Trabajo"
        h[LocalDate.of(y, 9, 16)] = "Día de la Independencia"
        h[nthMonday(y, 11, 3)] = "Día de la Revolución"
        h[LocalDate.of(y, 12, 25)] = "Navidad"
        if ((y - 2024) % 6 == 0) h[LocalDate.of(y, 10, 1)] = "Cambio de gobierno"
        val easter = easter(y)
        h[easter.minusDays(3)] = "Jueves Santo"
        h[easter.minusDays(2)] = "Viernes Santo"
        return h
    }

    /** Domingo de Pascua (algoritmo gregoriano anónimo). */
    fun easter(y: Int): LocalDate {
        val a = y % 19; val b = y / 100; val c = y % 100
        val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(y, month, day)
    }

    // ---------------------------------------------------------------------------------------------
    // Temporadas altas: (nombre, aviso, muy fuerte)
    // ---------------------------------------------------------------------------------------------

    private fun md(d: LocalDate) = d.monthValue * 100 + d.dayOfMonth

    private fun season(country: Country, date: LocalDate): Triple<String?, String?, Boolean> {
        val x = md(date)
        if (country == Country.JP) return when {
            x >= 1229 || x <= 103 -> Triple("Año Nuevo", "Muchos museos, tiendas y restaurantes cierran del 29 dic al 3 ene; templos y santuarios llenísimos del 1 al 3 de enero (hatsumōde).", true)
            x in 1226..1228 || x in 104..105 -> Triple("Vacaciones de Año Nuevo", "Trenes y Shinkansen muy llenos (gente viajando a su ciudad).", false)
            x in 429..506 -> Triple("Golden Week", "Una de las semanas más llenas del año: reserva trenes, hoteles y atracciones con tiempo.", true)
            x in 810..816 -> Triple("Obon", "Trenes y Shinkansen llenísimos; algunos negocios familiares cierran.", true)
            x in 320..410 -> Triple("Temporada de cerezos (sakura)", "Parques, castillos y templos con cerezos muy llenos, sobre todo en fin de semana; ve temprano.", false)
            x in 1110..1205 -> Triple("Temporada de hojas rojas (momiji)", "Templos de Kioto y parques muy llenos, sobre todo en fin de semana; ve a la apertura.", false)
            x in 720..831 -> Triple("Vacaciones de verano", "Más familias y estudiantes en parques temáticos y atracciones.", false)
            else -> Triple(null, null, false)
        }
        val easter = easter(date.year)
        return when {
            !date.isBefore(easter.minusDays(7)) && !date.isAfter(easter.plusDays(7)) ->
                Triple("Semana Santa y de Pascua", "Playas, pueblos mágicos y carreteras llenos; en la ciudad hay menos tráfico. Jueves y viernes santo algunos negocios cierran.", date in easter.minusDays(3)..easter)
            x in 915..916 -> Triple("Fiestas patrias", "El Zócalo y el centro de CDMX se cierran por el Grito (15) y el desfile (16).", true)
            x in 1031..1102 -> Triple("Día de Muertos", "Panteones, Mixquic, Pátzcuaro y el desfile de CDMX muy llenos; reserva hospedaje.", true)
            x in 1211..1212 -> Triple("Virgen de Guadalupe", "La Basílica y sus alrededores están llenísimos (millones de peregrinos).", true)
            x in 1224..1225 || x == 1231 || x == 101 -> Triple("Navidad y Año Nuevo", "Muchos restaurantes y museos cierran o abren menos horas; confirma antes de ir.", true)
            x >= 1215 || x <= 106 -> Triple("Vacaciones de invierno", "Más gente en atracciones y destinos de playa.", false)
            x in 715..820 -> Triple("Vacaciones de verano", "Más familias en museos, parques y destinos turísticos.", false)
            else -> Triple(null, null, false)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Horarios de comida: ventana normal y hora pico (cuando hay filas).
    // ---------------------------------------------------------------------------------------------

    data class MealWindow(val meal: Meal, val start: Int, val end: Int, val peakStart: Int, val peakEnd: Int, val label: String) {
        /** Último momento razonable para llegar a comer. */
        val lastArrival get() = end - 30
        fun inPeak(t: Int) = t in peakStart until peakEnd
        /** La hora buena más cercana (justo antes o justo después de la hora pico). */
        fun quietBefore() = maxOf(start, peakStart - 45)
        fun quietAfter() = minOf(lastArrival, peakEnd)
        fun peakText() = "${fromMin(peakStart)}–${fromMin(peakEnd)}"
    }

    fun meals(country: Country): List<MealWindow> = if (country == Country.JP) listOf(
        MealWindow(Meal.BREAKFAST, toMin("07:00"), toMin("10:00"), toMin("08:00"), toMin("09:00"), "desayuno"),
        MealWindow(Meal.LUNCH, toMin("11:00"), toMin("14:30"), toMin("12:00"), toMin("13:00"), "comida"),
        MealWindow(Meal.DINNER, toMin("17:30"), toMin("21:00"), toMin("18:30"), toMin("20:00"), "cena"),
    ) else listOf(
        MealWindow(Meal.BREAKFAST, toMin("08:00"), toMin("11:30"), toMin("09:30"), toMin("11:00"), "desayuno"),
        MealWindow(Meal.LUNCH, toMin("13:30"), toMin("17:00"), toMin("14:30"), toMin("16:00"), "comida"),
        MealWindow(Meal.DINNER, toMin("19:30"), toMin("22:30"), toMin("20:30"), toMin("21:30"), "cena"),
    )

    fun meal(country: Country, meal: Meal) = meals(country).first { it.meal == meal }

    /** Ventanas en las que un lugar de comida tiene sentido. */
    fun mealsFor(country: Country, meal: Meal): List<MealWindow> = when (meal) {
        Meal.ANY -> meals(country).filter { it.meal != Meal.BREAKFAST }
        else -> listOf(meal(country, meal))
    }

    /** Horas pico del transporte (entre semana). */
    fun rushHour(country: Country, date: LocalDate, t: Int): Boolean {
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY || holiday(country, date) != null) return false
        return if (country == Country.JP) t in toMin("07:30")..toMin("09:30") || t in toMin("17:30")..toMin("19:30")
        else t in toMin("07:00")..toMin("09:30") || t in toMin("18:00")..toMin("20:30")
    }

    // ---------------------------------------------------------------------------------------------
    // Atardecer (fórmula de la NOAA, ±2 min)
    // ---------------------------------------------------------------------------------------------

    fun zone(country: Country): ZoneId = ZoneId.of(if (country == Country.JP) "Asia/Tokyo" else "America/Mexico_City")

    /** Minutos desde medianoche (hora local) de la puesta de sol. */
    fun sunset(country: Country, date: LocalDate, lat: Double?, lng: Double?): Int {
        val la = Math.toRadians(lat ?: if (country == Country.JP) 35.68 else 19.43)
        val lo = lng ?: if (country == Country.JP) 139.76 else -99.13
        val g = 2 * Math.PI / 365 * (date.dayOfYear - 1)
        val eq = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val ha = Math.toDegrees(acos((cos(Math.toRadians(90.833)) / (cos(la) * cos(decl)) - tan(la) * tan(decl)).coerceIn(-1.0, 1.0)))
        val utc = 720 - 4 * (lo - ha) - eq
        val offset = zone(country).rules.getOffset(date.atTime(12, 0).atZone(zone(country)).toInstant()).totalSeconds / 60
        return ((utc + offset).toInt() % 1440 + 1440) % 1440
    }

    /** Festivos y temporadas dentro del viaje, para el resumen. */
    fun summary(country: Country, dates: List<String>): List<String> {
        val out = mutableListOf<String>()
        var lastPeak: String? = null
        dates.mapNotNull { day(country, it) }.forEach { d ->
            d.holiday?.let { out += "🎌 ${d.dayName} ${d.date.dayOfMonth}: $it (festivo)" }
            if (d.peak != null && d.peak != lastPeak) out += "📈 ${d.peak}" + (d.warning?.let { ": $it" } ?: "")
            lastPeak = d.peak
        }
        return out
    }
}
