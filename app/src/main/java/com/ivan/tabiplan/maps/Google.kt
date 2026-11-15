package com.ivan.tabiplan.maps

import android.content.Context
import android.location.Geocoder
import com.ivan.tabiplan.data.Country
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLngD(val lat: Double, val lng: Double)

/** Distancia en línea recta en km. */
fun haversineKm(a: LatLngD, b: LatLngD): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

data class FoundPlace(val name: String, val address: String, val lat: Double, val lng: Double)

enum class Mode(val api: String, val label: String) {
    WALK("WALK", "Caminando"),
    TRANSIT("TRANSIT", "Transporte público"),
    DRIVE("DRIVE", "Taxi / Uber / DiDi"),
}

/** Un tramo de la ruta en transporte público (una línea de metro, tren o autobús). */
data class TransitStep(
    val vehicle: String,
    val vehicleType: String,
    val line: String,
    val color: String,
    val agency: String,
    val from: String,
    val to: String,
    val headsign: String,
    val stops: Int,
    val departs: String,
    val minutes: Int,
)

data class RouteResult(
    val mode: Mode,
    val minutes: Int,
    val meters: Int,
    /** Tarifa del transporte público según Google (si la conoce). */
    val fare: Money? = null,
    val steps: List<TransitStep> = emptyList(),
    val walkMinutes: Int = 0,
    val polyline: List<LatLngD> = emptyList(),
    /** true = calculado por la app (sin Google), solo aproximado. */
    val estimated: Boolean = false,
)

data class Money(val amount: Double, val currency: String) {
    fun format(): String = when (currency) {
        "JPY" -> "¥" + "%,d".format(amount.toLong())
        "MXN" -> "MX$" + "%,.0f".format(amount)
        "USD" -> "US$" + "%,.2f".format(amount)
        else -> "%,.0f %s".format(amount, currency)
    }
}

/** Places API (New) y Routes API de Google, por REST con la clave del usuario. */
class GoogleMaps(private val context: Context, private val key: () -> String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    val hasKey get() = key().isNotBlank()

    private fun post(url: String, fieldMask: String, body: JSONObject): JSONObject {
        val req = Request.Builder().url(url)
            .header("X-Goog-Api-Key", key())
            .header("X-Goog-FieldMask", fieldMask)
            .post(body.toString().toRequestBody(json))
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val msg = runCatching { JSONObject(text).getJSONObject("error").getString("message") }.getOrNull()
                error("Google respondió ${res.code}: ${msg ?: text.take(200)}")
            }
            return JSONObject(text.ifBlank { "{}" })
        }
    }

    /** Busca un lugar por nombre. Con clave usa Places API; sin clave, el geocodificador de Android. */
    suspend fun search(query: String, country: Country): List<FoundPlace> = withContext(Dispatchers.IO) {
        if (hasKey) {
            val body = JSONObject().put("textQuery", query).put("languageCode", "es")
                .put("regionCode", country.name).put("pageSize", 6)
            val res = post(
                "https://places.googleapis.com/v1/places:searchText",
                "places.displayName,places.formattedAddress,places.location",
                body,
            )
            val places = res.optJSONArray("places") ?: JSONArray()
            (0 until places.length()).map { i ->
                val p = places.getJSONObject(i)
                val loc = p.getJSONObject("location")
                FoundPlace(
                    p.optJSONObject("displayName")?.optString("text") ?: query,
                    p.optString("formattedAddress"),
                    loc.getDouble("latitude"),
                    loc.getDouble("longitude"),
                )
            }
        } else {
            @Suppress("DEPRECATION")
            val list = runCatching {
                Geocoder(context, Locale("es")).getFromLocationName("$query, ${country.label}", 5)
            }.getOrNull().orEmpty()
            list.map { a ->
                FoundPlace(a.featureName ?: query, a.getAddressLine(0) ?: "", a.latitude, a.longitude)
            }
        }
    }

    /** Ruta con Google. Devuelve la mejor opción para el modo (para transporte público, hasta 3). */
    suspend fun route(from: LatLngD, to: LatLngD, mode: Mode, departureEpochSec: Long? = null): List<RouteResult> =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("origin", waypoint(from))
                .put("destination", waypoint(to))
                .put("travelMode", mode.api)
                .put("languageCode", "es")
                .put("units", "METRIC")
            if (mode == Mode.TRANSIT) {
                body.put("computeAlternativeRoutes", true)
                departureEpochSec?.let { body.put("departureTime", rfc3339(it)) }
            }
            val mask = listOf(
                "routes.duration", "routes.distanceMeters", "routes.polyline.encodedPolyline",
                "routes.travelAdvisory.transitFare",
                "routes.legs.steps.travelMode", "routes.legs.steps.staticDuration",
                "routes.legs.steps.transitDetails",
            ).joinToString(",")
            val res = post("https://routes.googleapis.com/directions/v2:computeRoutes", mask, body)
            val routes = res.optJSONArray("routes") ?: JSONArray()
            (0 until routes.length()).map { parseRoute(routes.getJSONObject(it), mode) }
        }

    private fun parseRoute(r: JSONObject, mode: Mode): RouteResult {
        val steps = mutableListOf<TransitStep>()
        var walkSec = 0
        r.optJSONArray("legs")?.let { legs ->
            for (l in 0 until legs.length()) {
                val st = legs.getJSONObject(l).optJSONArray("steps") ?: continue
                for (s in 0 until st.length()) {
                    val step = st.getJSONObject(s)
                    val sec = seconds(step.optString("staticDuration"))
                    val td = step.optJSONObject("transitDetails")
                    if (td == null) {
                        walkSec += sec
                        continue
                    }
                    val line = td.optJSONObject("transitLine") ?: JSONObject()
                    val vehicle = line.optJSONObject("vehicle") ?: JSONObject()
                    val stopDetails = td.optJSONObject("stopDetails") ?: JSONObject()
                    steps += TransitStep(
                        vehicle = vehicle.optJSONObject("name")?.optString("text").orEmpty(),
                        vehicleType = vehicle.optString("type"),
                        line = line.optString("nameShort").ifBlank { line.optString("name") },
                        color = line.optString("color"),
                        agency = line.optJSONArray("agencies")?.optJSONObject(0)?.optString("name").orEmpty(),
                        from = stopDetails.optJSONObject("departureStop")?.optString("name").orEmpty(),
                        to = stopDetails.optJSONObject("arrivalStop")?.optString("name").orEmpty(),
                        headsign = td.optString("headsign"),
                        stops = td.optInt("stopCount"),
                        departs = td.optJSONObject("localizedValues")?.optJSONObject("departureTime")
                            ?.optJSONObject("time")?.optString("text").orEmpty(),
                        minutes = sec / 60,
                    )
                }
            }
        }
        val fare = r.optJSONObject("travelAdvisory")?.optJSONObject("transitFare")?.let {
            Money(it.optString("units", "0").toDoubleOrNull()?.plus(it.optLong("nanos") / 1e9) ?: 0.0, it.optString("currencyCode"))
        }
        return RouteResult(
            mode = mode,
            minutes = (seconds(r.optString("duration")) + 59) / 60,
            meters = r.optInt("distanceMeters"),
            fare = fare,
            steps = steps,
            walkMinutes = walkSec / 60,
            polyline = decodePolyline(r.optJSONObject("polyline")?.optString("encodedPolyline").orEmpty()),
        )
    }

    private fun waypoint(p: LatLngD) = JSONObject().put(
        "location", JSONObject().put("latLng", JSONObject().put("latitude", p.lat).put("longitude", p.lng)),
    )

    private fun seconds(s: String) = s.removeSuffix("s").toDoubleOrNull()?.toInt() ?: 0

    private fun rfc3339(epochSec: Long): String =
        java.time.Instant.ofEpochSecond(epochSec).toString()
}

/** Decodifica el formato "encoded polyline" de Google. */
fun decodePolyline(encoded: String): List<LatLngD> {
    val out = mutableListOf<LatLngD>()
    var index = 0
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        var result = 0
        var shift = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20 && index < encoded.length)
        lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        result = 0
        shift = 0
        do {
            if (index >= encoded.length) break
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1
        out += LatLngD(lat / 1e5, lng / 1e5)
    }
    return out
}
