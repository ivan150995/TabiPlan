package com.ivan.tabiplan.maps

import com.ivan.tabiplan.BuildConfig
import com.ivan.tabiplan.data.Country
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Rutas de transporte público con Transitous (https://transitous.org), servicio abierto y gratuito
 * basado en horarios públicos (GTFS). Lo usamos en Japón, donde Google no da rutas de tren a las apps,
 * y como respaldo si Google no encuentra ruta. No trae tarifas: se estiman aparte.
 *
 * Reglas de uso: app de código abierto y no comercial, User-Agent con contacto, enlace de atribución
 * visible y poco uso (< ~720 rutas al día). Sin garantía: si falla, la app vuelve al estimado.
 */
object Transitous {
    const val SOURCES_URL = "https://transitous.org/sources/"
    const val OSM_URL = "https://www.openstreetmap.org/copyright"
    private const val API = "https://api.transitous.org/api/v5/plan"
    private val userAgent = "TabiPlan/${BuildConfig.VERSION_NAME} (Android; +https://github.com/ivan150995/TabiPlan)"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun zone(country: Country): ZoneId = ZoneId.of(if (country == Country.JP) "Asia/Tokyo" else "America/Mexico_City")

    /** Hasta 3 rutas, la más conveniente primero (tiempo + 5 min de castigo por transbordo). */
    suspend fun plan(from: LatLngD, to: LatLngD, country: Country, departureEpochSec: Long?): List<RouteResult> = withContext(Dispatchers.IO) {
        val time = Instant.ofEpochSecond(departureEpochSec ?: (System.currentTimeMillis() / 1000))
        val url = API.toHttpUrl().newBuilder()
            .addQueryParameter("fromPlace", "${from.lat},${from.lng}")
            .addQueryParameter("toPlace", "${to.lat},${to.lng}")
            .addQueryParameter("time", time.toString())
            .addQueryParameter("numItineraries", "3")
            .build()
        val req = Request.Builder().url(url).header("User-Agent", userAgent).get().build()
        val body = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("Transitous HTTP ${r.code}")
            r.body?.string().orEmpty()
        }
        val its = JSONObject(body).optJSONArray("itineraries") ?: return@withContext emptyList()
        val fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(zone(country))
        (0 until its.length()).mapNotNull { i -> parse(its.getJSONObject(i), fmt) }
            .filter { it.steps.isNotEmpty() }
            .sortedBy { it.minutes + 5 * (it.steps.size - 1) }
            .take(3)
    }

    private fun parse(it: JSONObject, fmt: DateTimeFormatter): RouteResult? {
        val legs = it.optJSONArray("legs") ?: return null
        val steps = mutableListOf<TransitStep>()
        var walkSec = 0
        var meters = 0.0
        for (k in 0 until legs.length()) {
            val l = legs.getJSONObject(k)
            val mode = l.optString("mode")
            val sec = l.optInt("duration")
            val a = l.optJSONObject("from")
            val b = l.optJSONObject("to")
            val km = if (a != null && b != null) haversineKm(LatLngD(a.optDouble("lat"), a.optDouble("lon")), LatLngD(b.optDouble("lat"), b.optDouble("lon"))) * 1.2 else 0.0
            meters += if (l.has("distance")) l.optDouble("distance") else km * 1000
            if (mode == "WALK" || mode == "BIKE" || mode == "CAR") { walkSec += sec; continue }
            val head = l.optString("headsign")
            val trip = l.optString("tripShortName")
            val long = l.optString("routeLongName")
            val short = l.optString("routeShortName")
            // Solo los expresos de pago (有料特急: N'EX, Skyliner, Haruka, Romancecar…). Muchos 特急 de
            // trenes privados (Keisei Access Express, Keiō…) no cobran extra.
            val express = "有料特急" in head || "Toll Limited" in head ||
                PAID_EXPRESS.any { it in long || it in trip || it in short || it in head }
            val lineName = bilingualLatin(long).ifBlank { short.ifBlank { l.optString("displayName") } }
            steps += TransitStep(
                vehicle = vehicleName(mode, express),
                vehicleType = mode,
                line = if (short.isNotBlank() && short != lineName && short.length <= 4) "$lineName ($short)" else lineName,
                color = l.optString("routeColor").takeIf { it.isNotBlank() }?.let { "#$it" }.orEmpty(),
                agency = bilingualLatin(l.optString("agencyName")).ifBlank { l.optString("agencyName") },
                from = bilingual(a?.optString("name").orEmpty()),
                to = bilingual(b?.optString("name").orEmpty()),
                headsign = bilingual(head.replace(Regex("^\\([^)]*\\)\\s*"), "")),
                stops = (l.optJSONArray("intermediateStops")?.length() ?: 0) + 1,
                departs = l.optString("startTime").takeIf { it.isNotBlank() }?.let { runCatching { fmt.format(Instant.parse(it)) }.getOrNull() }.orEmpty(),
                minutes = sec / 60,
                km = km,
                express = express,
                highSpeed = mode == "HIGHSPEED_RAIL",
            )
        }
        return RouteResult(
            mode = Mode.TRANSIT,
            minutes = it.optInt("duration") / 60,
            meters = meters.toInt(),
            steps = steps,
            walkMinutes = walkSec / 60,
            source = "transitous",
        )
    }

    private val PAID_EXPRESS = listOf("成田エクスプレス", "N’EX", "N'EX", "スカイライナー", "Skyliner", "はるか", "Haruka", "ラピート", "rapi:t", "ロマンスカー", "Romancecar", "ひたち", "あずさ", "かいじ", "踊り子", "サンダーバード")

    private fun vehicleName(mode: String, express: Boolean) = when (mode) {
        "HIGHSPEED_RAIL" -> "Tren bala"
        "SUBWAY", "METRO" -> "Metro"
        "BUS", "COACH" -> "Autobús"
        "TRAM" -> "Tranvía"
        "FERRY" -> "Ferri"
        "AIRPLANE" -> "Avión"
        "FUNICULAR", "AERIAL_LIFT" -> "Teleférico"
        else -> if (express) "Tren expreso (cargo extra)" else "Tren"
    }

    private fun isCjk(c: Char) = Character.UnicodeScript.of(c.code).let {
        it == Character.UnicodeScript.HAN || it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA
    } || c in '＀'..'￯' || c == 'ー'

    /** "空港第2ビル Narita Airport Terminal 2·3" → "Narita Airport Terminal 2·3". */
    private fun bilingualLatin(s: String): String {
        val last = s.indexOfLast { isCjk(it) }
        if (last < 0) return s.trim()
        return s.substring(last + 1).trim()
    }

    /** "東京 Tōkyō" → "Tōkyō (東京)": se lee en español y se reconoce en los letreros. */
    private fun bilingual(s: String): String {
        val latin = bilingualLatin(s)
        val cjk = s.substring(0, s.length - latin.length).trim()
        return when {
            latin.isBlank() || latin == s.trim() -> s.trim()
            cjk.isBlank() -> latin
            else -> "$latin ($cjk)"
        }
    }
}
