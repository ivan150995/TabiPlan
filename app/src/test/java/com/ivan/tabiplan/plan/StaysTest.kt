package com.ivan.tabiplan.plan

import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.data.Stay
import com.ivan.tabiplan.data.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaysTest {
    // 15 → 25 nov: 10 noches. Tokio 3, Osaka 2, Kioto 2, Tokio 3.
    private val trip = Trip(name = "t", country = Country.JP, startDate = "2026-11-15", endDate = "2026-11-25")

    private fun plan(): List<Stay> {
        var s = listOf(Stay(city = "Tokio", checkIn = "2026-11-15", checkOut = "2026-11-18"))
        listOf("Osaka", "Kioto", "Tokio").forEach { c -> s = Stays.add(trip, s).let { l -> l.dropLast(1) + l.last().copy(city = c) } }
        return Stays.setNights(s, 3, 3)
    }

    @Test fun chainedStaysCoverTheTrip() {
        val s = plan()
        assertEquals(listOf("2026-11-15", "2026-11-18", "2026-11-20", "2026-11-22"), s.map { it.checkIn })
        assertEquals("2026-11-25", s.last().checkOut)
        assertTrue(Stays.issues(trip, s).isEmpty())
    }

    @Test fun moreNightsShiftTheNextStays() {
        val s = Stays.setNights(plan(), 0, 4)
        assertEquals("2026-11-19", s[1].checkIn)
        assertEquals("2026-11-26", s.last().checkOut)
        assertTrue(Stays.issues(trip, s).any { "después del regreso" in it })
    }

    @Test fun missingNightsAreReported() {
        val s = plan().dropLast(1)
        assertEquals(listOf("Noches sin hotel: 22–24 nov."), Stays.issues(trip, s))
    }

    @Test fun bookingLinkHasDatesAndPeople() {
        val u = Stays.bookingUrl("Hotel Gracery Shinjuku", "2026-11-15", "2026-11-18", 2, Country.JP)
        assertTrue(u.contains("ss=Hotel+Gracery+Shinjuku"))
        assertTrue(u.contains("checkin=2026-11-15&checkout=2026-11-18&group_adults=2"))
        assertTrue(u.contains("selected_currency=JPY"))
    }
}
