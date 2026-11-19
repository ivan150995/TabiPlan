package com.ivan.tabiplan.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import com.ivan.tabiplan.AppDeps
import com.ivan.tabiplan.data.AttachedFile
import com.ivan.tabiplan.data.DocKind
import com.ivan.tabiplan.data.Place
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.TravelDoc
import com.ivan.tabiplan.data.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// -------------------------------------------------------------------------------------------------
// Boletos digitales: QR, PDF o capturas guardados dentro de la app y ligados a un lugar.
// -------------------------------------------------------------------------------------------------

/** Boletos de un lugar: botón para verlos (sin internet) y para guardar uno nuevo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaceTickets(deps: AppDeps, trip: Trip, place: Place, compact: Boolean = false) {
    val docs = trip.docsFor(place.id)
    var viewing by remember { mutableStateOf<TravelDoc?>(null) }
    var adding by remember { mutableStateOf(false) }
    val hotel = place.kind == PlaceKind.HOTEL
    if (docs.isEmpty() && compact) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        docs.forEach { d ->
            FilledTonalButton(onClick = { viewing = d }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text((if (hotel) "🏨 " else "🎟️ ") + d.title, maxLines = 1)
            }
        }
        if (!compact && (docs.isEmpty() || place.needsTicket || hotel)) TextButton(onClick = { adding = true }) {
            Icon(Icons.Default.Add, null); HSpace(4)
            Text(
                when {
                    hotel -> if (docs.isEmpty()) "Guardar reserva (PDF, captura…)" else "Otra reserva"
                    docs.isEmpty() -> "Guardar boleto (QR, PDF, captura…)"
                    else -> "Otro boleto"
                },
            )
        }
    }
    viewing?.let { d -> TicketViewer(deps, d) { viewing = null } }
    if (adding) DocEditor(
        deps, trip,
        initial = TravelDoc(
            kind = if (hotel) DocKind.HOTEL else DocKind.TICKET, title = place.name,
            date = if (hotel) place.checkIn else place.preferredDate, placeId = place.id,
        ),
        isNew = true, onDismiss = { adding = false }, onDelete = {},
    ) { d -> saveDoc(deps, trip, d); adding = false }
}

fun saveDoc(deps: AppDeps, trip: Trip, d: TravelDoc) = deps.store.update(trip.id) { t ->
    if (t.docs.any { it.id == d.id }) t.copy(docs = t.docs.map { if (it.id == d.id) d else it }) else t.copy(docs = t.docs + d)
}

/**
 * Muestra el boleto a pantalla completa con el brillo al máximo para que el lector del torniquete
 * lea el QR. Imágenes y PDF se dibujan dentro de la app (no necesita internet ni otra app).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TicketViewer(deps: AppDeps, doc: TravelDoc, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { w ->
                w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
                w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose { }
        }
        var pages by remember { mutableStateOf<List<Bitmap>?>(null) }
        LaunchedEffect(doc.id) {
            pages = withContext(Dispatchers.IO) { doc.files.flatMap { f -> runCatching { render(deps.store.fileOf(f), f) }.getOrDefault(emptyList()) } }
        }
        Box(Modifier.fillMaxSize().background(Color.White)) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(doc.title, Modifier.weight(1f), color = Color.Black, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cerrar", tint = Color.Black) }
                    }
                    if (doc.date.isNotBlank()) Text(prettyDate(doc.date), color = Color.DarkGray)
                    if (doc.code.isNotBlank()) Text(doc.code, color = Color.Black, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    if (doc.notes.isNotBlank()) Text(doc.notes, color = Color.DarkGray, style = MaterialTheme.typography.bodyMedium)
                }
                when (val p = pages) {
                    null -> item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(); HSpace(); Text("Abriendo…", color = Color.Black) } }
                    else -> {
                        items(p) { b ->
                            Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                        }
                        val other = doc.files.filter { !it.isImage && !it.isPdf }
                        if (p.isEmpty() && doc.files.isEmpty()) item { Text("Este documento no tiene archivos; agrega el PDF o la captura en Documentos.", color = Color.DarkGray) }
                        if (other.isNotEmpty()) item { Text("Estos archivos se abren con otra app:", color = Color.DarkGray) }
                    }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        doc.files.forEach { f -> OutlinedButton(onClick = { openFile(ctx, deps, f) }) { Text("Abrir ${f.displayName}", maxLines = 1) } }
                        if (doc.link.isNotBlank()) OutlinedButton(onClick = { ctx.openUrl(doc.link) }) { Text("Abrir enlace") }
                    }
                }
            }
        }
    }
}

/** Dibuja una imagen o cada página de un PDF (ancho máx. 1600 px, suficiente para un QR). */
private fun render(file: File, f: AttachedFile): List<Bitmap> {
    val maxW = 1600
    if (f.isImage) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW) sample *= 2
        return listOfNotNull(BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }))
    }
    if (!f.isPdf) return emptyList()
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { pdf ->
            return (0 until minOf(pdf.pageCount, 20)).map { i ->
                pdf.openPage(i).use { page ->
                    val w = maxW
                    val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
                        b.eraseColor(AColor.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }
}

fun openFile(ctx: Context, deps: AppDeps, f: AttachedFile) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", deps.store.fileOf(f))
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, f.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }.onFailure { ctx.toast("No hay una app para abrir ${f.displayName}") }
}
