package com.ivan.tabiplan.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File

/**
 * Todos los viajes en un JSON dentro de la app (filesDir/viajes.json). Se reescribe entero en cada
 * cambio: son pocos datos y así no hay migraciones de base de datos.
 */
class TripStore(private val context: Context) {
    private val file = File(context.filesDir, "viajes.json")
    private val docsDir = File(context.filesDir, "docs").apply { mkdirs() }
    private val _trips = MutableStateFlow(load())
    val trips: StateFlow<List<Trip>> = _trips.asStateFlow()

    private fun load(): List<Trip> = runCatching {
        if (!file.exists()) emptyList()
        else JSONArray(file.readText(Charsets.UTF_8)).objects().map { Trip.fromJson(it) }
    }.getOrElse { emptyList() }

    private fun save(list: List<Trip>) {
        val tmp = File(file.parentFile, "viajes.json.tmp")
        tmp.writeText(JSONArray(list.map { it.toJson() }).toString(), Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun trip(id: String): Trip? = _trips.value.firstOrNull { it.id == id }

    @Synchronized
    fun upsert(trip: Trip) {
        val list = _trips.value.toMutableList()
        val i = list.indexOfFirst { it.id == trip.id }
        if (i >= 0) list[i] = trip else list.add(0, trip)
        _trips.value = list
        save(list)
    }

    @Synchronized
    fun update(id: String, change: (Trip) -> Trip) {
        val current = trip(id) ?: return
        upsert(change(current))
    }

    @Synchronized
    fun delete(id: String) {
        val trip = trip(id) ?: return
        trip.docs.flatMap { it.files }.forEach { File(docsDir, it.fileName).delete() }
        val list = _trips.value.filterNot { it.id == id }
        _trips.value = list
        save(list)
    }

    /** Copia un archivo elegido por el usuario (PDF, imagen…) dentro de la app. */
    fun importFile(uri: Uri): AttachedFile {
        val resolver = context.contentResolver
        var display = "archivo"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) display = c.getString(0) ?: display
        }
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val ext = display.substringAfterLast('.', "").take(5).lowercase()
        val name = newId() + if (ext.isNotEmpty()) ".$ext" else ""
        resolver.openInputStream(uri)?.use { input ->
            File(docsDir, name).outputStream().use { input.copyTo(it) }
        } ?: error("No se pudo leer el archivo")
        return AttachedFile(name, display, mime)
    }

    fun fileOf(f: AttachedFile) = File(docsDir, f.fileName)

    fun deleteFile(f: AttachedFile) {
        File(docsDir, f.fileName).delete()
    }
}

/** Claves de API y preferencias. Las claves solo se guardan en el teléfono. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    var claudeKey: String
        get() = prefs.getString("claudeKey", "") ?: ""
        set(v) = prefs.edit().putString("claudeKey", v.trim()).apply()

    /** Clave para Routes/Places. Vacía = la de local.properties usada al compilar. */
    var mapsKey: String
        get() = prefs.getString("mapsKey", "") ?: ""
        set(v) = prefs.edit().putString("mapsKey", v.trim()).apply()

    var model: String
        get() = prefs.getString("model", AiModel.OPUS.id) ?: AiModel.OPUS.id
        set(v) = prefs.edit().putString("model", v).apply()

    /** Pesos mexicanos por 1 yen, para mostrar precios en las dos monedas. */
    var mxnPerJpy: Double
        get() = prefs.getFloat("mxnPerJpy", 0.12f).toDouble()
        set(v) = prefs.edit().putFloat("mxnPerJpy", v.toFloat()).apply()

    var showMxn: Boolean
        get() = prefs.getBoolean("showMxn", true)
        set(v) = prefs.edit().putBoolean("showMxn", v).apply()
}

enum class AiModel(val id: String, val label: String, val note: String) {
    OPUS("claude-opus-5-5", "Claude Opus 5.5", "El más capaz (recomendado para itinerarios)"),
    SONNET("claude-sonnet-5-5", "Claude Sonnet 5.5", "Más barato, muy bueno"),
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", "El más barato y rápido"),
}
