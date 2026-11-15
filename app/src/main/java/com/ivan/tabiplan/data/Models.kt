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
    val sources: List<String> = emptyList(),
    val researchedAt: Long = 0,
) {
    fun toJson() = JSONObject()
        .put("summary", summary).put("suggestedMinutes", suggestedMinutes).put("hours", hours)
        .put("price", price).put("bestTime", bestTime).put("tips", tips).put("closedDays", closedDays)
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
            sources = o.optJSONArray("sources").strings(),
            researchedAt = o.optLong("researchedAt"),
        )
    }
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
    /** Para hoteles: noches que cubre (check-in inclusive, check-out exclusive). */
    val checkIn: String = "",
    val checkOut: String = "",
    val notes: String = "",
    val info: PlaceInfo? = null,
    val completedAt: Long = 0,
) {
    val hasCoords get() = lat != null && lng != null
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
        .putOpt("minutes", minutes).put("mustVisit", mustVisit).put("preferredDate", preferredDate)
        .put("checkIn", checkIn).put("checkOut", checkOut).put("notes", notes)
        .putOpt("info", info?.toJson()).put("completedAt", completedAt)

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
            checkIn = o.optString("checkIn"),
            checkOut = o.optString("checkOut"),
            notes = o.optString("notes"),
            info = o.optJSONObject("info")?.let { PlaceInfo.fromJson(it) },
            completedAt = o.optLong("completedAt"),
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
) {
    fun toJson() = JSONObject().put("placeId", placeId).put("arrive", arrive).put("leave", leave)
        .put("travelMinutes", travelMinutes).put("note", note)

    companion object {
        fun fromJson(o: JSONObject) = Stop(
            o.getString("placeId"), o.optString("arrive"), o.optString("leave"),
            o.optInt("travelMinutes"), o.optString("note"),
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
) {
    fun toJson() = JSONObject().put("id", id).put("kind", kind.name).put("title", title)
        .put("date", date).put("link", link).put("code", code).put("notes", notes)
        .put("files", JSONArray(files.map { it.toJson() }))

    companion object {
        fun fromJson(o: JSONObject) = TravelDoc(
            o.getString("id"), enumOr(o.optString("kind"), DocKind.OTHER), o.optString("title"),
            o.optString("date"), o.optString("link"), o.optString("code"), o.optString("notes"),
            o.optJSONArray("files").objects().map { AttachedFile.fromJson(it) },
        )
    }
}

data class AttachedFile(val fileName: String, val displayName: String, val mime: String) {
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
) {
    fun place(id: String) = places.firstOrNull { it.id == id }

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
