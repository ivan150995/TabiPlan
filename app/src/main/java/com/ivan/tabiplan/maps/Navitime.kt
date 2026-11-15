package com.ivan.tabiplan.maps

import android.content.Context
import com.ivan.tabiplan.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * NAVITIME Route (totalnavi) por RapidAPI: rutas de Japón con horarios reales y **tarifa exacta**
 * (base + cargo de expreso/Shinkansen, y precio con Suica/PASMO).
 *
 * Plan gratis: 500 consultas al mes con límite duro (no cobra extra), sin nombres en inglés y con
 * tiempos promedio (no el horario real; ese lo da Transitous).
 * Por eso: cada respuesta se guarda en el teléfono (misma ruta y hora = no gasta otra consulta)
 * y el número de consultas que quedan se lee de los encabezados que manda RapidAPI.
 */
class Navitime(private val context: Context, private val settings: Settings, private val key: () -> String) {
    private val host = "navitime-route-totalnavi.p.rapidapi.com"
    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val cacheFile get() = File(context.filesDir, "navitime_cache.json")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    val hasKey get() = key().isNotBlank()

    /** Consultas que quedan este mes (null = aún no se sabe). */
    val remaining: Int? get() =
        if (settings.navitimeResetAt in 1..System.currentTimeMillis()) null // ya se renovó el mes
        else settings.navitimeRemaining.takeIf { it >= 0 }

    /** Hasta 3 rutas con tarifa exacta, la más rápida primero. */
    suspend fun route(from: LatLngD, to: LatLngD, departureEpochSec: Long? = null): List<RouteResult> = withContext(Dispatchers.IO) {
        val start = Instant.ofEpochSecond(departureEpochSec ?: (System.currentTimeMillis() / 1000)).atZone(tokyo)
        val startTime = start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val cacheKey = "%.3f,%.3f>%.3f,%.3f@%s".format(java.util.Locale.US, from.lat, from.lng, to.lat, to.lng, startTime.take(13))
        val body = readCache(cacheKey) ?: run {
            val url = "https://$host/route_transit".toHttpUrl().newBuilder()
                .addQueryParameter("start", "${from.lat},${from.lng}")
                .addQueryParameter("goal", "${to.lat},${to.lng}")
                .addQueryParameter("start_time", startTime)
                .addQueryParameter("limit", "3")
                .build()
            val req = Request.Builder().url(url).header("x-rapidapi-host", host).header("x-rapidapi-key", key()).get().build()
            http.newCall(req).execute().use { r ->
                r.header("X-RateLimit-Requests-Remaining")?.toIntOrNull()?.let { settings.navitimeRemaining = it }
                r.header("X-RateLimit-Requests-Reset")?.toLongOrNull()?.let { settings.navitimeResetAt = System.currentTimeMillis() + it * 1000 }
                val text = r.body?.string().orEmpty()
                when {
                    r.code == 429 -> error("Se acabaron las consultas de NAVITIME de este mes")
                    r.code == 401 || r.code == 403 -> error("Clave de NAVITIME no válida o sin suscripción")
                    !r.isSuccessful -> error("NAVITIME HTTP ${r.code}: " + runCatching { JSONObject(text).optString("message") }.getOrDefault("").take(80))
                }
                writeCache(cacheKey, text)
                text
            }
        }
        parse(body)
    }

    private fun parse(body: String): List<RouteResult> {
        val items = JSONObject(body).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { i ->
            val item = items.getJSONObject(i)
            val move = item.getJSONObject("summary").getJSONObject("move")
            val fare = move.optJSONObject("fare")
            val surcharge = fare?.optDouble("unit_2")?.takeIf { !it.isNaN() } ?: 0.0
            val base = fare?.optDouble("unit_0")?.takeIf { !it.isNaN() }
            val ic = fare?.optDouble("unit_48")?.takeIf { !it.isNaN() }
            val sections = item.getJSONArray("sections")
            val steps = mutableListOf<TransitStep>()
            var walk = 0
            for (k in 0 until sections.length()) {
                val s = sections.getJSONObject(k)
                if (s.optString("type") != "move") continue
                val mv = s.optString("move")
                if (mv == "walk" || mv == "car" || mv == "bicycle") { walk += s.optInt("time"); continue }
                val prev = sections.optJSONObject(k - 1)
                val next = sections.optJSONObject(k + 1)
                val t = s.optJSONObject("transport")
                val lineJp = nfkc(t?.optString("name")?.takeIf { it.isNotBlank() } ?: s.optString("line_name"))
                val stepFare = t?.optJSONObject("fare")
                val express = mv == "superexpress_train" || mv == "ultraexpress_train" || mv == "sleeper_ultraexpress" ||
                    (stepFare?.optDouble("unit_2")?.let { !it.isNaN() && it > 0 } == true)
                steps += TransitStep(
                    vehicle = vehicleName(mv, lineJp),
                    vehicleType = mv,
                    line = Names.line(lineJp),
                    color = t?.optString("color").orEmpty(),
                    agency = nfkc(t?.optJSONObject("company")?.optString("name").orEmpty()),
                    from = station(prev, "departure"),
                    to = station(next, "arrival"),
                    headsign = Names.station(nfkc(t?.optJSONArray("links")?.optJSONObject(0)?.optJSONObject("destination")?.optString("name").orEmpty())),
                    stops = 0,
                    // El plan gratis calcula con tiempos promedio, no con el horario real: no se muestra la hora de salida.
                    departs = "",
                    minutes = s.optInt("time"),
                    express = express,
                    highSpeed = mv == "superexpress_train",
                )
            }
            RouteResult(
                mode = Mode.TRANSIT,
                minutes = move.optInt("time"),
                meters = move.optInt("distance"),
                fare = base?.let { Money(it + surcharge, "JPY") },
                fareIc = ic?.let { Money(it + surcharge, "JPY") },
                surcharge = surcharge.takeIf { it > 0 }?.let { Money(it, "JPY") },
                steps = steps,
                walkMinutes = walk,
                source = "navitime",
            )
        }.filter { it.steps.isNotEmpty() }.sortedBy { it.minutes }
    }

    /** "日暮里" + numeración "KS02" → "Nippori (日暮里) · KS02", como en los letreros. */
    private fun station(point: JSONObject?, side: String): String {
        if (point == null) return ""
        val name = Names.station(nfkc(point.optString("name")))
        val num = point.optJSONObject("numbering")?.optJSONArray(side)?.optJSONObject(0)
            ?.let { it.optString("symbol") + it.optString("number") }.orEmpty()
        return if (num.isBlank()) name else "$name · $num"
    }

    private fun vehicleName(move: String, line: String) = when (move) {
        "superexpress_train" -> "Shinkansen"
        "ultraexpress_train", "sleeper_ultraexpress" -> "Tren expreso (cargo extra)"
        "express_train" -> "Tren expreso"
        "rapid_train" -> "Tren rápido"
        "semiexpress_train" -> "Tren semiexpreso"
        "local_bus", "shuttle_bus" -> "Autobús"
        "highway_bus" -> "Autobús de carretera"
        "domestic_flight" -> "Avión"
        "ferry" -> "Ferri"
        else -> if ("メトロ" in line || "地下鉄" in line || "都営" in line) "Metro" else "Tren"
    }

    private fun nfkc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)

    private fun readCache(key: String): String? = runCatching {
        val j = JSONObject(cacheFile.readText()).optJSONObject(key) ?: return null
        // Una ruta guardada sirve 2 días (horarios iguales a la misma hora).
        if (System.currentTimeMillis() - j.optLong("t") > 2 * 24 * 3600_000L) null else j.optString("body").ifBlank { null }
    }.getOrNull()

    @Synchronized
    private fun writeCache(key: String, body: String) {
        val all = runCatching { JSONObject(cacheFile.readText()) }.getOrDefault(JSONObject())
        all.put(key, JSONObject().put("t", System.currentTimeMillis()).put("body", body))
        // Máximo 150 rutas guardadas: se borran las más viejas.
        if (all.length() > 150) {
            val keys = all.keys().asSequence().toList().sortedBy { all.optJSONObject(it)?.optLong("t") ?: 0 }
            keys.take(all.length() - 150).forEach { all.remove(it) }
        }
        cacheFile.writeText(all.toString())
    }
}

/** Traducciones de líneas y estaciones frecuentes para turistas; lo demás queda en japonés. */
object Names {
    private val lines = listOf(
        "成田エクスプレス" to "Narita Express (N'EX)", "スカイライナー" to "Keisei Skyliner", "アクセス特急" to "Keisei Access Express",
        "成田スカイアクセス" to "Narita Sky Access", "京成本線" to "Keisei Main Line", "京急" to "Keikyū", "東京モノレール" to "Tokyo Monorail",
        "山手線" to "Yamanote", "中央線快速" to "Chūō Rapid", "中央・総武" to "Chūō-Sōbu", "総武快速" to "Sōbu Rapid", "総武線" to "Sōbu",
        "京浜東北" to "Keihin-Tōhoku", "埼京線" to "Saikyō", "湘南新宿" to "Shōnan-Shinjuku", "京葉線" to "Keiyō", "上野東京" to "Ueno-Tokyo",
        "銀座線" to "Ginza", "丸ノ内線" to "Marunouchi", "日比谷線" to "Hibiya", "東西線" to "Tōzai", "千代田線" to "Chiyoda",
        "有楽町線" to "Yūrakuchō", "半蔵門線" to "Hanzōmon", "南北線" to "Namboku", "副都心線" to "Fukutoshin",
        "浅草線" to "Toei Asakusa", "三田線" to "Toei Mita", "都営新宿線" to "Toei Shinjuku", "大江戸線" to "Toei Ōedo",
        "ゆりかもめ" to "Yurikamome", "りんかい線" to "Rinkai", "東海道新幹線" to "Tōkaidō Shinkansen", "山陽新幹線" to "San'yō Shinkansen",
        "東北新幹線" to "Tōhoku Shinkansen", "北陸新幹線" to "Hokuriku Shinkansen", "上越新幹線" to "Jōetsu Shinkansen",
        "大阪環状線" to "Osaka Loop", "御堂筋線" to "Midōsuji", "はるか" to "Haruka", "ラピート" to "Nankai rapi:t",
        "嵐山" to "Arashiyama", "奈良線" to "Nara", "京都線" to "Kyōto", "小田急" to "Odakyū", "ロマンスカー" to "Romancecar",
    )
    private val stations = listOf(
        "空港第2ビル" to "Narita T2/T3", "成田空港" to "Narita T1", "羽田空港" to "Haneda", "東京" to "Tokyo", "新宿" to "Shinjuku",
        "渋谷" to "Shibuya", "池袋" to "Ikebukuro", "上野" to "Ueno", "日暮里" to "Nippori", "浅草" to "Asakusa", "秋葉原" to "Akihabara",
        "品川" to "Shinagawa", "新橋" to "Shimbashi", "有楽町" to "Yūrakuchō", "銀座" to "Ginza", "豊洲" to "Toyosu", "市場前" to "Shijō-mae",
        "新木場" to "Shin-Kiba", "原宿" to "Harajuku", "六本木" to "Roppongi", "押上" to "Oshiage (Skytree)", "京成高砂" to "Keisei-Takasago",
        "浜松町" to "Hamamatsuchō", "新大阪" to "Shin-Osaka", "大阪" to "Osaka", "梅田" to "Umeda", "なんば" to "Namba", "難波" to "Namba",
        "京都" to "Kyoto", "奈良" to "Nara", "名古屋" to "Nagoya", "横浜" to "Yokohama",
    )

    /** "ＪＲ山手線" → "Yamanote (JR山手線)". */
    fun line(jp: String): String = lines.firstOrNull { it.first in jp }?.let { "${it.second} ($jp)" } ?: jp

    /** "日暮里" → "Nippori (日暮里)"; solo nombres exactos o con paréntesis para no confundir estaciones. */
    fun station(jp: String): String {
        if (jp.isBlank()) return jp
        val base = jp.substringBefore("(").trim()
        return stations.firstOrNull { it.first == base }?.let { "${it.second} ($jp)" } ?: jp
    }
}
