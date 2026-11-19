package com.ivan.tabiplan.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

/** País del viaje: decide guías, monedas y fórmulas de taxi. */
enum class Country(val label: String, val currency: String, val symbol: String) {
    JP("Japón", "JPY", "¥"),
    MX("México", "MXN", "MX$"),
}

enum class PlaceKind(val label: String) {
    VISIT("Lugar"),
    HOTEL("Hotel / hospedaje"),
    AIRPORT("Aeropuerto / estación"),
    FOOD("Comida"),
}

/** Lo que la IA averiguó de un lugar (horario, precio, cuánto tiempo pasar ahí…). */
data class PlaceInfo(
    val summary: String = "",
    val suggestedMinutes: Int = 0,
    val hours: String = "",
    val price: String = "",
    val bestTime: String = "",
    val tips: String = "",
    val closedDays: String = "",
    /** Qué tan lleno se pone: 0 = no se sabe, 1 = poco, 2 = normal, 3 = muy lleno (filas). */
    val crowd: Int = 0,
    /** Cuándo se pone más lleno ("sábados 11–15 h"). */
    val peakTimes: String = "",
    /** Mejor momento: morning, afternoon, sunset, night o vacío (cualquiera). */
    val bestSlot: String = "",
    val reservation: Boolean = false,
    val sources: List<String> = emptyList(),
    val researchedAt: Long = 0,
) {
    fun toJson() = JSONObject()
        .put("summary", summary).put("suggestedMinutes", suggestedMinutes).put("hours", hours)
        .put("price", price).put("bestTime", bestTime).put("tips", tips).put("closedDays", closedDays)
        .put("crowd", crowd).put("peakTimes", peakTimes).put("bestSlot", bestSlot).put("reservation", reservation)
        .put("sources", JSONArray(sources)).put("researchedAt", researchedAt)

    companion object {
        fun fromJson(o: JSONObject) = PlaceInfo(
            summary = o.optString("summary"),
            suggestedMinutes = o.optInt("suggestedMinutes"),
            hours = o.optString("hours"),
            price = o.optString("price"),
            bestTime = o.optString("bestTime"),
            tips = o.optString("tips"),
            closedDays = o.optString("closedDays"),
            crowd = o.optInt("crowd"),
            peakTimes = o.optString("peakTimes"),
            bestSlot = o.optString("bestSlot"),
            reservation = o.optBoolean("reservation"),
            sources = o.optJSONArray("sources").strings(),
            researchedAt = o.optLong("researchedAt"),
        )
    }
}

/**
 * Horario de apertura por día de la semana.
 * [week] va de lunes (0) a domingo (6): "09:00-17:00", varios tramos "11:00-14:30,17:00-22:00",
 * "" = cerrado, "?" = no se sabe. Si cierra después de medianoche: "18:00-02:00".
 */
data class OpenHours(
    val week: List<String> = List(7) { "?" },
    /** Días sin abrir durante el viaje (yyyy-MM-dd): año nuevo, mantenimiento, eventos privados… */
    val closedDates: List<String> = emptyList(),
    /** Minutos antes del cierre en que ya no dejan entrar (museos: 30). */
    val lastEntry: Int = 0,
    /** En días festivos: "open" abre normal, "closed" cierra, "shift" si su día de descanso cae en festivo abre y descansa el siguiente día hábil (museos de Japón). */
    val holidayRule: String = "",
    /** google, ia o tú. */
    val source: String = "",
) {
    val known get() = week.any { it != "?" }

    fun toJson() = JSONObject().put("week", JSONArray(week)).put("closedDates", JSONArray(closedDates))
        .put("lastEntry", lastEntry).put("holidayRule", holidayRule).put("source", source)

    companion object {
        fun fromJson(o: JSONObject): OpenHours {
            val w = o.optJSONArray("week")
            val week = (0 until 7).map { i -> w?.optString(i, "?") ?: "?" }
            return OpenHours(week, o.optJSONArray("closedDates").strings(), o.optInt("lastEntry"), o.optString("holidayRule"), o.optString("source"))
        }
    }
}

/** Qué comida se hace en un lugar de comida. */
enum class Meal(val label: String) {
    ANY("Cualquiera"),
    BREAKFAST("Desayuno"),
    LUNCH("Comida"),
    DINNER("Cena"),
}

data class Place(
    val id: String = newId(),
    val name: String,
    val address: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val kind: PlaceKind = PlaceKind.VISIT,
    /** Minutos que el usuario quiere estar. null = que lo decida la IA (o 90 min por defecto). */
    val minutes: Int? = null,
    val mustVisit: Boolean = false,
    /** Día preferido (yyyy-MM-dd) o vacío. */
    val preferredDate: String = "",
    /** Hora fija de llegada ("HH:mm") ese día: reserva, boleto con horario… Vacío = cuando convenga. */
    val fixedTime: String = "",
    /** Para hoteles: noches que cubre (check-in inclusive, check-out exclusive). */
    val checkIn: String = "",
    val checkOut: String = "",
    val notes: String = "",
    val info: PlaceInfo? = null,
    val completedAt: Long = 0,
    val hours: OpenHours? = null,
    /** Solo lugares de comida. */
    val meal: Meal = Meal.ANY,
    /** El usuario marcó que necesita boleto/entrada digital (QR, PDF…). */
    val ticket: Boolean = false,
) {
    val hasCoords get() = lat != null && lng != null
    /** Necesita boleto: lo marcaste tú o la IA encontró que requiere reserva. */
    val needsTicket get() = ticket || info?.reservation == true
    /** Hora fija en minutos si aplica ese día. */
    fun fixedOn(date: String): Int? = if (fixedTime.isNotBlank() && preferredDate == date) {
        fixedTime.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    } else null
    val completed get() = completedAt > 0

    /** Duración efectiva para planear. */
    val plannedMinutes: Int
        get() = minutes ?: info?.suggestedMinutes?.takeIf { it > 0 } ?: when (kind) {
            PlaceKind.FOOD -> 60
            PlaceKind.HOTEL, PlaceKind.AIRPORT -> 0
            PlaceKind.VISIT -> 90
        }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("address", address)
        .putOpt("lat", lat).putOpt("lng", lng).put("kind", kind.name)
        .putOpt("minutes", minutes).put("mustVisit", mustVisit).put("preferredDate", preferredDate).put("fixedTime", fixedTime)
        .put("checkIn", checkIn).put("checkOut", checkOut).put("notes", notes)
        .putOpt("info", info?.toJson()).put("completedAt", completedAt)
        .putOpt("hours", hours?.toJson()).put("meal", meal.name).put("ticket", ticket)

    companion object {
        fun fromJson(o: JSONObject) = Place(
            id = o.getString("id"),
            name = o.optString("name"),
            address = o.optString("address"),
            lat = o.optDoubleOrNull("lat"),
            lng = o.optDoubleOrNull("lng"),
            kind = enumOr(o.optString("kind"), PlaceKind.VISIT),
            minutes = if (o.has("minutes") && !o.isNull("minutes")) o.getInt("minutes") else null,
            mustVisit = o.optBoolean("mustVisit"),
            preferredDate = o.optString("preferredDate"),
            fixedTime = o.optString("fixedTime"),
            checkIn = o.optString("checkIn"),
            checkOut = o.optString("checkOut"),
            notes = o.optString("notes"),
            info = o.optJSONObject("info")?.let { PlaceInfo.fromJson(it) },
            completedAt = o.optLong("completedAt"),
            hours = o.optJSONObject("hours")?.let { OpenHours.fromJson(it) },
            meal = enumOr(o.optString("meal"), Meal.ANY),
            ticket = o.optBoolean("ticket"),
        )
    }
}

/** Una parada del itinerario. Horas en "HH:mm". */
data class Stop(
    val placeId: String,
    val arrive: String,
    val leave: String,
    val travelMinutes: Int = 0,
    val note: String = "",
    /** Tiempo libre para comer antes de salir hacia esta parada (no hay restaurante en la lista a esa hora). */
    val mealBefore: Int = 0,
    /** Minutos de espera al llegar (p. ej. a que abra). */
    val wait: Int = 0,
) {
    fun toJson() = JSONObject().put("placeId", placeId).put("arrive", arrive).put("leave", leave)
        .put("travelMinutes", travelMinutes).put("note", note).put("mealBefore", mealBefore).put("wait", wait)

    companion object {
        fun fromJson(o: JSONObject) = Stop(
            o.getString("placeId"), o.optString("arrive"), o.optString("leave"),
            o.optInt("travelMinutes"), o.optString("note"), o.optInt("mealBefore"), o.optInt("wait"),
        )
    }
}

data class PlanDay(
    val date: String,
    /** Hotel o lugar desde donde empieza el día (si se conoce). */
    val startPlaceId: String = "",
    val stops: List<Stop> = emptyList(),
    val note: String = "",
) {
    fun toJson() = JSONObject().put("date", date).put("startPlaceId", startPlaceId)
        .put("stops", JSONArray(stops.map { it.toJson() })).put("note", note)

    companion object {
        fun fromJson(o: JSONObject) = PlanDay(
            o.getString("date"), o.optString("startPlaceId"),
            o.optJSONArray("stops").objects().map { Stop.fromJson(it) }, o.optString("note"),
        )
    }
}

data class Itinerary(
    val days: List<PlanDay> = emptyList(),
    val unassigned: List<String> = emptyList(),
    val notes: String = "",
    val byAi: Boolean = false,
    val createdAt: Long = 0,
) {
    fun toJson() = JSONObject().put("days", JSONArray(days.map { it.toJson() }))
        .put("unassigned", JSONArray(unassigned)).put("notes", notes).put("byAi", byAi)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(o: JSONObject) = Itinerary(
            o.optJSONArray("days").objects().map { PlanDay.fromJson(it) },
            o.optJSONArray("unassigned").strings(), o.optString("notes"),
            o.optBoolean("byAi"), o.optLong("createdAt"),
        )
    }
}

enum class DocKind(val label: String) {
    FLIGHT("Boleto de avión"),
    HOTEL("Reserva de hotel"),
    PERMIT("Permiso / trámite"),
    TICKET("Boleto de atracción"),
    TRAIN("Tren / pase"),
    INSURANCE("Seguro"),
    ID("Pasaporte / identificación"),
    OTHER("Otro"),
}

/** Boletos, reservas, permisos: enlace, archivos adjuntos (copiados dentro de la app) y notas. */
data class TravelDoc(
    val id: String = newId(),
    val kind: DocKind = DocKind.OTHER,
    val title: String,
    val date: String = "",
    val link: String = "",
    val code: String = "",
    val notes: String = "",
    /** Archivos en filesDir/docs/. */
    val files: List<AttachedFile> = emptyList(),
    /** Lugar al que pertenece (boleto de una atracción, reserva de un hotel…), o vacío. */
    val placeId: String = "",
) {
    fun toJson() = JSONObject().put("id", id).put("kind", kind.name).put("title", title)
        .put("date", date).put("link", link).put("code", code).put("notes", notes)
        .put("files", JSONArray(files.map { it.toJson() })).put("placeId", placeId)

    companion object {
        fun fromJson(o: JSONObject) = TravelDoc(
            o.getString("id"), enumOr(o.optString("kind"), DocKind.OTHER), o.optString("title"),
            o.optString("date"), o.optString("link"), o.optString("code"), o.optString("notes"),
            o.optJSONArray("files").objects().map { AttachedFile.fromJson(it) },
            o.optString("placeId"),
        )
    }
}

data class AttachedFile(val fileName: String, val displayName: String, val mime: String) {
    val isImage get() = mime.startsWith("image/")
    val isPdf get() = mime == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)

    fun toJson() = JSONObject().put("fileName", fileName).put("displayName", displayName).put("mime", mime)

    companion object {
        fun fromJson(o: JSONObject) = AttachedFile(o.getString("fileName"), o.optString("displayName"), o.optString("mime"))
    }
}

data class CheckItem(
    val id: String = newId(),
    val section: String,
    val text: String,
    val detail: String = "",
    val link: String = "",
    val done: Boolean = false,
    /** Documento del viaje que cumple este punto (p. ej. el QR de Visit Japan Web). */
    val docId: String = "",
) {
    fun toJson() = JSONObject().put("id", id).put("section", section).put("text", text)
        .put("detail", detail).put("link", link).put("done", done).put("docId", docId)

    companion object {
        fun fromJson(o: JSONObject) = CheckItem(
            o.getString("id"), o.optString("section"), o.optString("text"), o.optString("detail"),
            o.optString("link"), o.optBoolean("done"), o.optString("docId"),
        )
    }
}

/**
 * Una parte del viaje en una ciudad, con su hotel: "Tokio 3 noches, luego Osaka 2…".
 * [hotelId] es el hotel elegido (un Place de tipo HOTEL) o vacío.
 */
data class Stay(
    val id: String = newId(),
    val city: String,
    val checkIn: String,
    val checkOut: String,
    /** Lo que buscas en esta estancia ("con vista al Fuji", "cerca de Dotonbori"). */
    val wishes: String = "",
    val hotelId: String = "",
) {
    val nights: Int
        get() = runCatching { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(checkIn), java.time.LocalDate.parse(checkOut)).toInt() }.getOrDefault(0)

    fun toJson() = JSONObject().put("id", id).put("city", city).put("checkIn", checkIn).put("checkOut", checkOut)
        .put("wishes", wishes).put("hotelId", hotelId)

    companion object {
        fun fromJson(o: JSONObject) = Stay(o.getString("id"), o.optString("city"), o.optString("checkIn"), o.optString("checkOut"), o.optString("wishes"), o.optString("hotelId"))
    }
}

data class Trip(
    val id: String = newId(),
    val name: String,
    val country: Country = Country.JP,
    val startDate: String,
    val endDate: String,
    val dayStart: String = "09:00",
    val dayEnd: String = "20:00",
    /** Horario distinto para ciertos días: fecha -> "HH:mm-HH:mm" (día de llegada, vuelo de regreso…). */
    val dayHours: Map<String, String> = emptyMap(),
    val travelers: Int = 1,
    val places: List<Place> = emptyList(),
    val itinerary: Itinerary = Itinerary(),
    val docs: List<TravelDoc> = emptyList(),
    val checklist: List<CheckItem> = emptyList(),
    /** Plan de hospedaje por ciudades. */
    val stays: List<Stay> = emptyList(),
) {
    fun place(id: String) = places.firstOrNull { it.id == id }

    /** Boletos / reservas guardados para un lugar. */
    fun docsFor(placeId: String) = if (placeId.isBlank()) emptyList() else docs.filter { it.placeId == placeId }

    fun hoursFor(date: String): Pair<String, String> {
        val custom = dayHours[date]?.split("-")
        return if (custom != null && custom.size == 2) custom[0] to custom[1] else dayStart to dayEnd
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("country", country.name)
        .put("startDate", startDate).put("endDate", endDate)
        .put("dayStart", dayStart).put("dayEnd", dayEnd)
        .put("dayHours", JSONObject(dayHours as Map<*, *>)).put("travelers", travelers)
        .put("places", JSONArray(places.map { it.toJson() }))
        .put("itinerary", itinerary.toJson())
        .put("docs", JSONArray(docs.map { it.toJson() }))
        .put("checklist", JSONArray(checklist.map { it.toJson() }))
        .put("stays", JSONArray(stays.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject): Trip {
            val hours = o.optJSONObject("dayHours")
            return Trip(
                id = o.getString("id"),
                name = o.optString("name"),
                country = enumOr(o.optString("country"), Country.JP),
                startDate = o.optString("startDate"),
                endDate = o.optString("endDate"),
                dayStart = o.optString("dayStart", "09:00"),
                dayEnd = o.optString("dayEnd", "20:00"),
                dayHours = hours?.keys()?.asSequence()?.associateWith { hours.getString(it) } ?: emptyMap(),
                travelers = o.optInt("travelers", 1).coerceAtLeast(1),
                places = o.optJSONArray("places").objects().map { Place.fromJson(it) },
                itinerary = o.optJSONObject("itinerary")?.let { Itinerary.fromJson(it) } ?: Itinerary(),
                docs = o.optJSONArray("docs").objects().map { TravelDoc.fromJson(it) },
                checklist = o.optJSONArray("checklist").objects().map { CheckItem.fromJson(it) },
                stays = o.optJSONArray("stays").objects().map { Stay.fromJson(it) },
            )
        }
    }
}

// --- utilidades de JSON ---

fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotEmpty() }

fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
