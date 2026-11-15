package com.ivan.tabiplan.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.WebSearchTool20260209
import com.ivan.tabiplan.data.AiModel
import com.ivan.tabiplan.data.Itinerary
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceInfo
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.PlanDay
import com.ivan.tabiplan.data.Settings
import com.ivan.tabiplan.data.Stop
import com.ivan.tabiplan.data.Trip
import com.ivan.tabiplan.data.objects
import com.ivan.tabiplan.data.strings
import com.ivan.tabiplan.plan.hotelFor
import com.ivan.tabiplan.plan.tripDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration

data class ChatTurn(val fromUser: Boolean, val text: String, val sources: List<String> = emptyList())

data class HotelRequest(
    val description: String,
    val checkIn: String = "",
    val checkOut: String = "",
    val foreignFriendly: Boolean = true,
    val nearStation: Boolean = false,
    val quiet: Boolean = false,
    val tattooOk: Boolean = false,
    /** 0 = sin límite. En moneda local del viaje. */
    val maxPerNight: Int = 0,
)

data class HotelSuggestion(
    val name: String,
    val city: String,
    val area: String,
    val why: String,
    val pricePerNight: String,
    val station: String,
    val foreignFriendly: String,
    val warning: String,
    val lat: Double?,
    val lng: Double?,
    val website: String,
    val advice: String,
    val sources: List<String>,
)

/**
 * Todo lo que hace la IA (Claude): investigar lugares con búsqueda web, armar el itinerario,
 * aconsejar cómo moverse y responder preguntas del viaje ("ya llegué a Narita, ¿qué hago?").
 */
class TravelAi(private val settings: Settings) {
    private var cachedKey = ""
    private var cachedClient: AnthropicClient? = null

    val hasKey get() = settings.claudeKey.isNotBlank()

    private fun client(): AnthropicClient {
        val key = settings.claudeKey
        if (key.isBlank()) error("Falta la clave de la API de Claude (Ajustes).")
        if (key != cachedKey || cachedClient == null) {
            cachedClient = AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofMinutes(4)).build()
            cachedKey = key
        }
        return cachedClient!!
    }

    private val model get() = settings.model

    /** Opus y Sonnet: si los filtros de seguridad rechazan la petición, el servidor reintenta con otro modelo. */
    private fun MessageCreateParams.Builder.withFallback(): MessageCreateParams.Builder {
        if (model != AiModel.HAIKU.id) {
            putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        return this
    }

    private fun webSearch(maxUses: Long) = WebSearchTool20260209.builder().maxUses(maxUses).build()

    /** Llama a la API; si la búsqueda web pausa el turno (pause_turn), lo continúa. */
    private suspend fun run(build: (MessageCreateParams.Builder) -> Unit): Message = withContext(Dispatchers.IO) {
        try {
            val base = MessageCreateParams.builder().model(model).maxTokens(16000L).withFallback()
            build(base)
            var params = base.build()
            var message = client().messages().create(params)
            var rounds = 0
            while (message.stopReason().orElse(null) == StopReason.PAUSE_TURN && rounds < 4) {
                params = params.toBuilder().addMessage(message).build()
                message = client().messages().create(params)
                rounds++
            }
            when (message.stopReason().orElse(null)) {
                StopReason.REFUSAL -> error("La IA no quiso responder esta petición.")
                StopReason.MAX_TOKENS -> error("La respuesta se cortó; intenta de nuevo con menos lugares.")
                else -> message
            }
        } catch (e: UnauthorizedException) {
            error("La clave de Claude no es válida.")
        } catch (e: RateLimitException) {
            error("Demasiadas peticiones seguidas a la IA; espera un minuto.")
        } catch (e: AnthropicServiceException) {
            error("Error de la API de Claude (${e.statusCode()}): ${e.message}")
        }
    }

    private fun Message.text(): String =
        content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("").trim()

    private fun Message.sources(): List<String> = content()
        .flatMap { b -> b.text().orElse(null)?.citations()?.orElse(null).orEmpty() }
        .mapNotNull { c -> c.webSearchResultLocation().orElse(null)?.url() }
        .distinct()

    /** Saca el JSON de una respuesta que puede traer texto alrededor o un bloque ```json. */
    private fun extractJson(text: String): JSONObject {
        val fenced = Regex("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```").findAll(text).lastOrNull()?.groupValues?.get(1)
        val raw = fenced ?: text.substring(text.indexOf('{').coerceAtLeast(0), text.lastIndexOf('}') + 1)
        return JSONObject(raw)
    }

    // ---------------------------------------------------------------------------------------------
    // 1) Investigar un lugar
    // ---------------------------------------------------------------------------------------------

    suspend fun research(place: Place, trip: Trip): PlaceInfo {
        val prompt = """
            Voy de viaje a ${trip.country.label} (${trip.startDate} a ${trip.endDate}), ${trip.travelers} persona(s).
            Investiga este lugar con búsqueda web para planear la visita:
            Nombre: ${place.name}
            ${if (place.address.isNotBlank()) "Dirección: ${place.address}" else ""}
            ${if (place.notes.isNotBlank()) "Notas mías: ${place.notes}" else ""}

            Quiero saber, con datos actuales (fuentes oficiales primero):
            - Qué es, en 1-2 frases.
            - Cuánto tiempo suele pasar un turista ahí (minutos, número realista).
            - Horario y días que cierra (fíjate en las fechas de mi viaje; avisa si hay cierres o temporadas).
            - Precio de entrada (en moneda local) y si hay que reservar con anticipación.
            - Mejor hora para ir y consejos prácticos (filas, reservas, cómo llegar, qué no perderse).

            Escribe en español de México, breve. Termina tu respuesta SOLO con un bloque ```json con este formato:
            {"summary": "...", "suggestedMinutes": 90, "hours": "...", "closedDays": "...", "price": "...", "bestTime": "...", "tips": "..."}
        """.trimIndent()
        val msg = run { it.addTool(webSearch(4)).addUserMessage(prompt).outputConfig(effort(OutputConfig.Effort.LOW)) }
        val j = extractJson(msg.text())
        return PlaceInfo(
            summary = j.optString("summary"),
            suggestedMinutes = j.optInt("suggestedMinutes").coerceIn(0, 600),
            hours = j.optString("hours"),
            price = j.optString("price"),
            bestTime = j.optString("bestTime"),
            tips = j.optString("tips"),
            closedDays = j.optString("closedDays"),
            sources = msg.sources().take(6),
            researchedAt = System.currentTimeMillis(),
        )
    }

    private fun effort(e: OutputConfig.Effort) = OutputConfig.builder().effort(pick(e)).build()

    /** Nivel elegido en Ajustes; en "Automático" se queda el de cada tarea. */
    private fun pick(default: OutputConfig.Effort): OutputConfig.Effort = when (settings.effort) {
        "low" -> OutputConfig.Effort.LOW
        "medium" -> OutputConfig.Effort.MEDIUM
        "high" -> OutputConfig.Effort.HIGH
        else -> default
    }

    // ---------------------------------------------------------------------------------------------
    // 2) Armar el itinerario
    // ---------------------------------------------------------------------------------------------

    suspend fun planItinerary(trip: Trip, extraWishes: String): Itinerary {
        val dates = tripDates(trip)
        val places = trip.places.filter { !it.completed }
        val data = JSONObject()
            .put("pais", trip.country.label)
            .put("viajeros", trip.travelers)
            .put("dias", JSONArray(dates.map { d ->
                val (s, e) = trip.hoursFor(d)
                JSONObject().put("fecha", d).put("desde", s).put("hasta", e)
                    .put("hotel", hotelFor(trip, d)?.let { "${it.id}: ${it.name}" } ?: "")
            }))
            .put("lugares", JSONArray(places.map { p ->
                JSONObject().put("id", p.id).put("nombre", p.name).put("tipo", p.kind.label)
                    .put("lat", p.lat ?: JSONObject.NULL).put("lng", p.lng ?: JSONObject.NULL)
                    .put("minutos", if (p.minutes != null) p.minutes else JSONObject.NULL)
                    .put("minutosSugeridosIA", p.info?.suggestedMinutes ?: 0)
                    .put("horario", p.info?.hours.orEmpty()).put("cierra", p.info?.closedDays.orEmpty())
                    .put("mejorHora", p.info?.bestTime.orEmpty())
                    .put("imprescindible", p.mustVisit).put("diaPreferido", p.preferredDate)
                    .put("checkIn", p.checkIn).put("checkOut", p.checkOut).put("notas", p.notes)
            }))
        val prompt = """
            Arma el itinerario de mi viaje con estos datos (JSON):
            $data

            Reglas:
            - Cada día empieza y termina en el hotel de esa noche (los hoteles no son paradas a visitar, salvo check-in/check-out si hace falta).
            - Respeta "desde"/"hasta" de cada día, los minutos que yo puse ("minutos"); si es null usa "minutosSugeridosIA" o tu mejor estimación.
            - Agrupa lugares cercanos el mismo día para no ir y venir; respeta horarios, días de cierre, "diaPreferido" y "mejorHora".
            - Incluye el tiempo de traslado realista entre paradas (transporte público en ciudad, caminar si está cerca).
            - Prioriza los "imprescindible". Si no cabe todo, deja fuera lo menos importante y ponlo en "unassigned".
            - Los aeropuertos van el día que corresponde (llegada/salida) con tiempo de sobra.
            ${if (extraWishes.isNotBlank()) "- Además: $extraWishes" else ""}
            En "note" de cada parada pon un consejo corto (cómo llegar, reservar, qué ver). En "notes" un resumen general breve.
            Usa solo los id de lugares que te di. Horas en formato HH:mm. Todo en español de México.
        """.trimIndent()
        val msg = run {
            it.addUserMessage(prompt)
                .outputConfig(
                    OutputConfig.builder().effort(pick(OutputConfig.Effort.MEDIUM))
                        .format(JsonOutputFormat.builder().schema(itinerarySchema()).build()).build(),
                )
        }
        val j = JSONObject(msg.text())
        val valid = trip.places.map { it.id }.toSet()
        val days = j.optJSONArray("days").objects().map { d ->
            PlanDay(
                date = d.optString("date"),
                startPlaceId = d.optString("startPlaceId").takeIf { it in valid }.orEmpty(),
                stops = d.optJSONArray("stops").objects().filter { it.optString("placeId") in valid }.map { s ->
                    Stop(s.getString("placeId"), s.optString("arrive"), s.optString("leave"), s.optInt("travelMinutes"), s.optString("note"))
                },
                note = d.optString("note"),
            )
        }.filter { it.date in dates }
        val planned = days.flatMap { d -> d.stops.map { it.placeId } }.toSet()
        val unassigned = (j.optJSONArray("unassigned").strings().filter { it in valid } +
            places.filter { it.kind != PlaceKind.HOTEL && it.id !in planned }.map { it.id }).distinct()
            .filter { it !in planned }
        // Días sin nada (la IA puede omitirlos): se agregan vacíos para que se vean.
        val all = dates.map { date -> days.firstOrNull { it.date == date } ?: PlanDay(date, hotelFor(trip, date)?.id.orEmpty()) }
        return Itinerary(all, unassigned, j.optString("notes"), byAi = true, createdAt = System.currentTimeMillis())
    }

    private fun itinerarySchema(): JsonOutputFormat.Schema {
        val str = mapOf("type" to "string")
        val stop = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "placeId" to str, "arrive" to str, "leave" to str,
                "travelMinutes" to mapOf("type" to "integer"), "note" to str,
            ),
            "required" to listOf("placeId", "arrive", "leave", "travelMinutes", "note"),
            "additionalProperties" to false,
        )
        val day = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "date" to str, "startPlaceId" to str, "note" to str,
                "stops" to mapOf("type" to "array", "items" to stop),
            ),
            "required" to listOf("date", "startPlaceId", "note", "stops"),
            "additionalProperties" to false,
        )
        return JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "days" to mapOf("type" to "array", "items" to day),
                        "unassigned" to mapOf("type" to "array", "items" to str),
                        "notes" to str,
                    ),
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("days", "unassigned", "notes")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }

    // ---------------------------------------------------------------------------------------------
    // 2b) Buscar hoteles por descripción ("donde pueda ver el Monte Fuji")
    // ---------------------------------------------------------------------------------------------

    suspend fun searchHotels(trip: Trip, request: HotelRequest): List<HotelSuggestion> {
        val filters = buildList {
            if (request.foreignFriendly) add("friendly para extranjeros: personal que habla inglés, check-in sencillo, acepta tarjetas extranjeras, buenas reseñas de turistas")
            if (request.nearStation) add("a menos de 10 minutos caminando de una estación de metro o tren")
            if (request.quiet) add("zona tranquila, no tan concurrida")
            if (request.tattooOk) add("si tiene onsen o baño público, que permita tatuajes")
            if (request.maxPerNight > 0) add("precio por noche máximo ${request.maxPerNight} ${trip.country.currency} por habitación")
        }
        val dates = if (request.checkIn.isNotBlank()) "del ${request.checkIn} al ${request.checkOut}" else "en las fechas de mi viaje (${trip.startDate} a ${trip.endDate})"
        val prompt = """
            Busca hoteles reales en ${trip.country.label} $dates para ${trip.travelers} persona(s).
            Lo que quiero: ${request.description}
            ${if (filters.isNotEmpty()) "Filtros obligatorios:\n- " + filters.joinToString("\n- ") else ""}

            Usa búsqueda web (sitios oficiales de hoteles, Booking, Agoda, Japanican, Google Maps, reseñas recientes).
            Dame de 4 a 6 opciones que existan y estén operando, variadas en precio. Para cada una:
            nombre exacto, ciudad y zona, por qué cumple lo que pedí, rango de precio por noche en moneda local,
            estación más cercana y minutos caminando, qué tan friendly es para extranjeros, una pega o advertencia,
            coordenadas aproximadas y la página oficial si la encuentras.
            Responde en español de México y termina SOLO con un bloque ```json con este formato:
            {"hotels":[{"name":"","city":"","area":"","why":"","pricePerNight":"","station":"","foreignFriendly":"","warning":"","lat":0,"lng":0,"website":""}],"advice":""}
        """.trimIndent()
        val msg = run { it.addTool(webSearch(6)).addUserMessage(prompt).outputConfig(effort(OutputConfig.Effort.MEDIUM)) }
        val j = extractJson(msg.text())
        val sources = msg.sources()
        return j.optJSONArray("hotels").objects().map { h ->
            HotelSuggestion(
                name = h.optString("name"),
                city = h.optString("city"),
                area = h.optString("area"),
                why = h.optString("why"),
                pricePerNight = h.optString("pricePerNight"),
                station = h.optString("station"),
                foreignFriendly = h.optString("foreignFriendly"),
                warning = h.optString("warning"),
                lat = h.optDouble("lat").takeIf { !it.isNaN() && it != 0.0 },
                lng = h.optDouble("lng").takeIf { !it.isNaN() && it != 0.0 },
                website = h.optString("website"),
                advice = j.optString("advice"),
                sources = sources.take(6),
            )
        }.filter { it.name.isNotBlank() }
    }

    // ---------------------------------------------------------------------------------------------
    // 3) Consejo para ir al siguiente lugar
    // ---------------------------------------------------------------------------------------------

    suspend fun adviseLeg(trip: Trip, fromName: String, toName: String, optionsSummary: String, time: String): String {
        val prompt = """
            Estoy en ${trip.country.label}, son las $time, somos ${trip.travelers} persona(s).
            Voy de "$fromName" a "$toName". Estas son las opciones que calculó mi app:
            $optionsSummary

            Recomiéndame cómo ir (transporte público, taxi/Uber/DiDi/GO o caminar) en máximo 6 líneas:
            cuál conviene y por qué, precio aproximado, en qué estación/andén subir o qué salida tomar si lo sabes,
            y cómo pagar (Suica/PASMO, boleto, efectivo, tarjeta). Español de México, directo y práctico.
        """.trimIndent()
        return run { it.addUserMessage(prompt).outputConfig(effort(OutputConfig.Effort.LOW)) }.text()
    }

    // ---------------------------------------------------------------------------------------------
    // 4) Asistente de viaje (con búsqueda web)
    // ---------------------------------------------------------------------------------------------

    suspend fun ask(trip: Trip?, history: List<ChatTurn>, question: String, where: String): ChatTurn {
        val context = buildString {
            append("Eres un asistente de viaje para un viajero mexicano. Respondes en español de México, ")
            append("con pasos concretos y numerados cuando sea un trámite (comprar boletos, tarjetas, máquinas). ")
            append("Cuando algo puede haber cambiado (precios, horarios, reglas de migración, tarjetas Suica/PASMO), ")
            append("búscalo en la web y prefiere fuentes oficiales. Da precios en moneda local y aproximados en pesos. ")
            append("Si describes una máquina, di qué botones buscar (en inglés y en japonés si aplica).\n")
            if (trip != null) {
                append("\nViaje actual: ${trip.name}, ${trip.country.label}, ${trip.startDate} a ${trip.endDate}, ${trip.travelers} persona(s).\n")
                val hotels = trip.places.filter { it.kind == PlaceKind.HOTEL }.joinToString { "${it.name} (${it.checkIn}→${it.checkOut})" }
                if (hotels.isNotBlank()) append("Hoteles: $hotels\n")
                val todo = trip.places.filter { it.kind != PlaceKind.HOTEL }.joinToString { it.name + if (it.completed) " (visitado)" else "" }
                if (todo.isNotBlank()) append("Lugares: $todo\n")
            }
            if (where.isNotBlank()) append("Ubicación aproximada del usuario: $where\n")
        }
        val msg = run { b ->
            b.system(context).addTool(webSearch(5)).outputConfig(effort(OutputConfig.Effort.MEDIUM))
            // La conversación debe empezar con un mensaje del usuario.
            history.takeLast(12).dropWhile { !it.fromUser }.forEach { t -> if (t.fromUser) b.addUserMessage(t.text) else b.addAssistantMessage(t.text) }
            b.addUserMessage(question)
        }
        return ChatTurn(false, msg.text(), msg.sources().take(6))
    }
}
