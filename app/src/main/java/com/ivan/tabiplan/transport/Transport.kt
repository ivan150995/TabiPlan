package com.ivan.tabiplan.transport

import com.ivan.tabiplan.data.Country
import com.ivan.tabiplan.maps.GoogleMaps
import com.ivan.tabiplan.maps.LatLngD
import com.ivan.tabiplan.maps.Mode
import com.ivan.tabiplan.maps.Money
import com.ivan.tabiplan.maps.RouteResult
import com.ivan.tabiplan.maps.haversineKm
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Rango de precio aproximado para taxi / apps (Uber, DiDi, GO). */
data class RideEstimate(val app: String, val low: Money, val high: Money, val note: String)

data class LegOptions(
    val walk: RouteResult,
    val transit: List<RouteResult>,
    val drive: RouteResult,
    val rides: List<RideEstimate>,
    val recommended: Mode,
    val reason: String,
    val usedGoogle: Boolean,
    val googleError: String? = null,
) {
    /** Tarifa del transporte público: la de Google o la estimada. */
    fun transitFare(country: Country): Money? = transit.firstOrNull()?.fare ?: transit.firstOrNull()?.let {
        Fares.transitEstimate(country, it.meters / 1000.0)
    }
}

object Fares {
    /** Distancia por calle aproximada a partir de la línea recta. */
    private const val DETOUR = 1.3

    fun walkEstimate(from: LatLngD, to: LatLngD): RouteResult {
        val km = haversineKm(from, to) * DETOUR
        return RouteResult(Mode.WALK, max(1, (km / 4.5 * 60).roundToInt()), (km * 1000).toInt(), estimated = true)
    }

    fun transitEstimate(from: LatLngD, to: LatLngD, country: Country): RouteResult {
        val km = haversineKm(from, to) * DETOUR
        // En ciudad ~28 km/h con esperas; lo que pasa de 20 km va en tren rápido/exprés.
        val (city, fast) = if (country == Country.JP) 28.0 to 65.0 else 22.0 to 40.0
        val minutes = (12 + minOf(km, 20.0) / city * 60 + max(0.0, km - 20) / fast * 60).roundToInt()
        return RouteResult(Mode.TRANSIT, minutes, (km * 1000).toInt(), fare = transitEstimate(country, km), estimated = true)
    }

    fun driveEstimate(from: LatLngD, to: LatLngD, country: Country): RouteResult {
        val km = haversineKm(from, to) * 1.35
        // En ciudad ~22 km/h; lo que pasa de 15 km suele ir por autopista.
        val (city, highway) = if (country == Country.JP) 22.0 to 60.0 else 18.0 to 55.0
        val minutes = 4 + minOf(km, 15.0) / city * 60 + max(0.0, km - 15) / highway * 60
        return RouteResult(Mode.DRIVE, max(3, minutes.roundToInt()), (km * 1000).toInt(), estimated = true)
    }

    /**
     * Tarifa aproximada de metro/tren por distancia. Japón: escala típica de metro de Tokio y, para
     * tramos largos, tarifa por km de JR. México: Metro CDMX (boleto fijo) con un transbordo de pago
     * si el trayecto es largo.
     */
    fun transitEstimate(country: Country, km: Double): Money = when (country) {
        Country.JP -> Money(
            when {
                km <= 6 -> 180.0
                km <= 11 -> 210.0
                km <= 19 -> 260.0
                km <= 27 -> 300.0
                km <= 40 -> 330.0 + (km - 27) * 15
                else -> 530.0 + (km - 40) * 17
            }.let { ceil(it / 10) * 10 },
            "JPY",
        )
        Country.MX -> Money(if (km <= 12) 5.0 else 11.0, "MXN")
    }

    /** Taxi y apps de viaje. Precios de referencia, sin tarifa dinámica. */
    fun rides(country: Country, drive: RouteResult, hour: Int): List<RideEstimate> {
        val km = drive.meters / 1000.0
        return when (country) {
            Country.JP -> {
                // Tokio (23 distritos): ¥500 los primeros 1.096 km y ¥100 cada 255 m, más tiempo en tráfico.
                var meter = 500.0 + max(0.0, ceil((km - 1.096) / 0.255)) * 100
                meter *= 1.1
                val night = hour >= 22 || hour < 5
                if (night) meter *= 1.2
                val nightNote = (if (night) " Incluye recargo nocturno (22:00–5:00)." else "") +
                    // Trayectos largos (aeropuertos): el taxímetro sale caro; hay tarifas fijas por zona.
                    if (km > 40) " Si sales del aeropuerto, pide la tarifa fija (定額, ≈¥20,000–30,000 a Tokio centro): suele salir más barata que el taxímetro. Peajes aparte." else ""
                listOf(
                    RideEstimate(
                        "Taxi en la calle", Money(round10(meter), "JPY"), Money(round10(meter * 1.2), "JPY"),
                        "Taxímetro. Puertas automáticas, no se deja propina.$nightNote",
                    ),
                    RideEstimate(
                        "GO (app de taxis)", Money(round10(meter + 100), "JPY"), Money(round10(meter * 1.2 + 500), "JPY"),
                        "Pide un taxi normal; suma una cuota de reserva.$nightNote",
                    ),
                    RideEstimate(
                        "Uber", Money(round10(meter + 300), "JPY"), Money(round10(meter * 1.3 + 500), "JPY"),
                        "En Japón Uber manda taxis con taxímetro; acepta tu tarjeta de México.$nightNote",
                    ),
                )
            }
            Country.MX -> {
                val min = drive.minutes.toDouble()
                val uber = max(45.0, 12 + km * 6.0 + min * 2.0)
                val didi = uber * 0.9
                listOf(
                    RideEstimate("Uber", Money(uber * 0.85, "MXN"), Money(uber * 1.4, "MXN"), "Sube con tarifa dinámica en horas pico o lluvia."),
                    RideEstimate("DiDi", Money(didi * 0.85, "MXN"), Money(didi * 1.4, "MXN"), "Suele ser un poco más barato que Uber."),
                    RideEstimate("Taxi de sitio", Money(uber * 1.0, "MXN"), Money(uber * 1.6, "MXN"), "Pide el precio antes de subir o usa uno de sitio autorizado."),
                )
            }
        }
    }

    private fun round10(v: Double) = ceil(v / 10) * 10

    /** Calcula las opciones para ir de un punto a otro y elige una. */
    suspend fun options(
        maps: GoogleMaps,
        from: LatLngD,
        to: LatLngD,
        country: Country,
        travelers: Int,
        hour: Int,
        departureEpochSec: Long?,
    ): LegOptions {
        var error: String? = null
        var walk = walkEstimate(from, to)
        var transit = listOf(transitEstimate(from, to, country))
        var drive = driveEstimate(from, to, country)
        var usedGoogle = false
        if (maps.hasKey) {
            runCatching {
                maps.route(from, to, Mode.WALK).firstOrNull()?.let { walk = it }
                val t = maps.route(from, to, Mode.TRANSIT, departureEpochSec)
                if (t.isNotEmpty()) transit = t.sortedBy { it.minutes }
                maps.route(from, to, Mode.DRIVE).firstOrNull()?.let { drive = it }
                usedGoogle = true
            }.onFailure { error = it.message }
        }
        val rides = rides(country, drive, hour)
        val (mode, reason) = recommend(country, walk, transit.firstOrNull(), drive, rides, travelers, hour)
        return LegOptions(walk, transit, drive, rides, mode, reason, usedGoogle, error)
    }

    fun recommend(
        country: Country,
        walk: RouteResult,
        transit: RouteResult?,
        drive: RouteResult,
        rides: List<RideEstimate>,
        travelers: Int,
        hour: Int,
    ): Pair<Mode, String> {
        val rideLow = rides.first().low.amount
        val transitFare = transit?.fare?.amount ?: transit?.let { transitEstimate(country, it.meters / 1000.0).amount }
        if (walk.minutes <= 15) {
            return Mode.WALK to "Está a ${walk.minutes} min caminando: no vale la pena pagar."
        }
        if (country == Country.JP && hour < 5) {
            return Mode.DRIVE to "A esta hora casi no hay trenes ni metro en Japón (último tren ≈ 0:00, primero ≈ 5:00). Toma taxi."
        }
        if (transit == null) {
            return Mode.DRIVE to "No encontré ruta en transporte público."
        }
        val transitTotal = (transitFare ?: 0.0) * travelers
        val timeSaved = transit.minutes - drive.minutes
        val transfers = max(0, transit.steps.size - 1)
        return when {
            travelers >= 3 && rideLow <= transitTotal * 1.5 && timeSaved > 10 ->
                Mode.DRIVE to "Son $travelers personas: el taxi sale casi igual que pagar ${travelers} boletos y ahorran ~$timeSaved min."
            timeSaved >= 35 && walk.minutes > 40 ->
                Mode.DRIVE to "El transporte público tarda ~$timeSaved min más; si van con prisa o con maletas, el taxi conviene."
            else -> {
                val extra = if (transfers > 0) " con $transfers transbordo(s)" else " directo"
                val card = if (country == Country.JP) " Paga con tu Suica/PASMO." else " En CDMX paga con la tarjeta de Movilidad Integrada."
                Mode.TRANSIT to "Transporte público${extra}: ${transit.minutes} min y mucho más barato que el taxi.$card"
            }
        }
    }
}

/** Muestra un precio y, si es en yenes, su equivalente en pesos. */
fun Money.withMxn(mxnPerJpy: Double, show: Boolean): String =
    if (show && currency == "JPY") "${format()} (≈MX$${"%,.0f".format(amount * mxnPerJpy)})" else format()

fun rangeText(low: Money, high: Money, mxnPerJpy: Double, show: Boolean): String {
    val base = "${low.format()} – ${high.format()}"
    return if (show && low.currency == "JPY") "$base (≈MX$${"%,.0f".format(low.amount * mxnPerJpy)}–${"%,.0f".format(high.amount * mxnPerJpy)})" else base
}
