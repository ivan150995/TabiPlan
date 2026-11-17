package com.ivan.tabiplan.plan

import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.data.Meal
import com.ivan.tabiplan.data.OpenHours
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun japanHolidays() {
        val h = TravelCalendar.holidays(Country.JP, 2026)
        assertNotNull(h[d("2026-03-20")]) // equinoccio
        assertNotNull(h[d("2026-05-06")]) // descanso por el 3 de mayo (domingo)
        assertNotNull(h[d("2026-09-21")])
        assertNotNull(h[d("2026-09-22")]) // entre festivos (Silver Week)
        assertNotNull(h[d("2026-09-23")])
        assertNotNull(h[d("2026-10-12")])
        assertNotNull(h[d("2026-11-23")])
        assertNull(h[d("2026-11-24")])
        assertNotNull(TravelCalendar.holidays(Country.JP, 2027)[d("2027-03-21")])
    }

    @Test fun mexicoHolidays() {
        val h = TravelCalendar.holidays(Country.MX, 2026)
        assertEquals(d("2026-04-05"), TravelCalendar.easter(2026))
        assertNotNull(h[d("2026-04-03")]) // viernes santo
        assertNotNull(h[d("2026-02-02")])
        assertNotNull(h[d("2026-03-16")])
        assertNotNull(h[d("2026-11-16")])
        assertEquals(2, TravelCalendar.day(Country.MX, "2026-11-02")!!.crowdLevel) // Día de Muertos
        assertEquals(2, TravelCalendar.day(Country.JP, "2026-05-01")!!.crowdLevel) // Golden Week
    }

    @Test fun museumMondayRule() {
        val museum = Place(name = "Museo", hours = OpenHours(week = listOf("", "09:30-17:00", "09:30-17:00", "09:30-17:00", "09:30-17:00", "09:30-17:00", "09:30-17:00"), holidayRule = "shift", lastEntry = 30))
        assertTrue(Hours.on(museum, Country.JP, "2026-11-16") is DayHours.Closed) // lunes normal
        assertTrue(Hours.on(museum, Country.JP, "2026-11-23") is DayHours.Open) // lunes festivo: abre
        assertTrue(Hours.on(museum, Country.JP, "2026-11-24") is DayHours.Closed) // y descansa el martes
        assertTrue(Hours.on(museum, Country.JP, "2026-09-24") is DayHours.Closed) // tras Silver Week
        assertTrue(Hours.on(museum, Country.JP, "2026-11-25") is DayHours.Open)
        assertEquals("Mar–Dom 09:30–17:00 · cierra lunes · si su día de descanso es festivo, abre y descansa al día siguiente · última entrada 30 min antes del cierre", Hours.weekText(museum.hours!!))
    }

    @Test fun sunsetTokyo() {
        val s = TravelCalendar.sunset(Country.JP, d("2026-12-01"), 35.68, 139.76)
        assertTrue("atardecer ${fromMin(s)}", s in toMin("16:20")..toMin("16:40"))
        val m = TravelCalendar.sunset(Country.MX, d("2026-06-21"), 19.43, -99.13)
        assertTrue("atardecer CDMX ${fromMin(m)}", m in toMin("19:05")..toMin("19:25"))
    }

    // Lunes 16 y martes 17 de noviembre de 2026, Tokio.
    private val hotel = Place(name = "Hotel Shinjuku", lat = 35.6938, lng = 139.7034, kind = PlaceKind.HOTEL, checkIn = "2026-11-16", checkOut = "2026-11-18")
    private val museum = Place(name = "Museo Nacional", lat = 35.7188, lng = 139.7765, minutes = 120,
        hours = OpenHours(week = listOf("", "09:30-17:00", "09:30-17:00", "09:30-17:00", "09:30-17:00", "09:30-17:00", "09:30-17:00"), lastEntry = 30))
    private val ramen = Place(name = "Ramen", lat = 35.6906, lng = 139.7000, kind = PlaceKind.FOOD, meal = Meal.LUNCH, minutes = 45)
    private val shrine = Place(name = "Meiji Jingu", lat = 35.6764, lng = 139.6993, minutes = 60)
    private val sky = Place(name = "Shibuya Sky", lat = 35.6585, lng = 139.7020, minutes = 60, preferredDate = "2026-11-16", fixedTime = "15:00")

    private fun trip(vararg places: Place) = Trip(name = "t", country = Country.JP, startDate = "2026-11-16", endDate = "2026-11-17", places = listOf(hotel, *places))

    @Test fun plannerRespectsHoursMealsAndFixedTime() {
        val t = trip(museum, ramen, shrine, sky)
        val plan = Planner.plan(t)
        plan.days.forEach { day ->
            println(day.date + ": " + day.stops.joinToString { s -> "${t.place(s.placeId)!!.name} ${s.arrive}-${s.leave}" + (if (s.mealBefore > 0) " (comida antes)" else "") + (if (s.wait > 0) " (espera ${s.wait})" else "") })
            day.stops.indices.forEach { i -> Advisor.stopHints(t, day, i).forEach { println("   " + it) } }
        }
        val monday = plan.days.first { it.date == "2026-11-16" }
        val tuesday = plan.days.first { it.date == "2026-11-17" }
        assertFalse("el museo cierra el lunes", monday.stops.any { it.placeId == museum.id })
        assertTrue(tuesday.stops.any { it.placeId == museum.id } || plan.unassigned.contains(museum.id))
        assertEquals("15:00", monday.stops.first { it.placeId == sky.id }.arrive)
        val lunch = plan.days.flatMap { it.stops }.first { it.placeId == ramen.id }
        val at = toMin(lunch.arrive)
        assertTrue("comida a las ${lunch.arrive}", at in toMin("11:00")..toMin("14:00") && at !in toMin("12:00") until toMin("13:00"))
        assertTrue(plan.unassigned.isEmpty())
    }

    @Test fun fixedTimesConflict() {
        // Dos horas fijas a 1 h de distancia, con 1 h de estancia en el primero y ~1 h de traslado a Yokohama.
        val a = Place(name = "Tsukiji", lat = 35.6655, lng = 139.7707, minutes = 60, preferredDate = "2026-11-16", fixedTime = "10:00")
        val b = Place(name = "Cup Noodles Museum", lat = 35.4553, lng = 139.6380, minutes = 90, preferredDate = "2026-11-16", fixedTime = "11:00")
        val t = trip(a, b)
        val c = Advisor.conflicts(t, "2026-11-16", t.places)
        println(c)
        assertEquals(1, c.size)
        val plan = Planner.plan(t)
        val stops = plan.days.first().stops
        val hint = Advisor.stopHints(t, plan.days.first(), stops.indexOfFirst { it.placeId == b.id })
        println(stops.map { "${t.place(it.placeId)!!.name} ${it.arrive}" } + hint)
        assertTrue(hint.any { it.warn && "no da tiempo" in it.text })
        // Con 3 h de diferencia sí da.
        val ok = trip(a, b.copy(fixedTime = "13:00"))
        assertTrue(Advisor.conflicts(ok, "2026-11-16", ok.places).isEmpty())
    }
}
