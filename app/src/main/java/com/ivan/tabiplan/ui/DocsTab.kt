package com.ivan.tabiplan.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivan.tabiplan.AppDeps
import com.ivan.tabiplan.Nav
import com.ivan.tabiplan.Screen
import com.ivan.tabiplan.content.Checklists
import com.ivan.tabiplan.content.Guides
import com.ivan.tabiplan.data.AttachedFile
import com.ivan.tabiplan.data.CheckItem
import com.ivan.tabiplan.data.DocKind
import com.ivan.tabiplan.data.PlaceKind
import com.ivan.tabiplan.data.TravelDoc
import com.ivan.tabiplan.data.Trip

// -------------------------------------------------------------------------------------------------
// Documentos: boletos de avión, hoteles, permisos, entradas…
// -------------------------------------------------------------------------------------------------

@Composable
fun DocsTab(deps: AppDeps, trip: Trip) {
    var editing by remember { mutableStateOf<TravelDoc?>(null) }
    var adding by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<TravelDoc?>(null) }
    val ctx = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = { adding = true }) { Icon(Icons.Default.Add, null); HSpace(4); Text("Agregar documento") }
            Muted("Guarda aquí boletos de avión, reservas de hotel, QR de Visit Japan Web, entradas a atracciones, seguro… Los archivos se copian dentro de la app y se ven sin internet. Liga cada boleto a su lugar para tenerlo a la mano en Hoy y en Lugares.")
        }
        DocKind.entries.forEach { kind ->
            val docs = trip.docs.filter { it.kind == kind }.sortedBy { it.date }
            if (docs.isNotEmpty()) {
                item { Title(kind.label) }
                items(docs, key = { it.id }) { d ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.title, fontWeight = FontWeight.SemiBold)
                                if (d.date.isNotBlank()) Muted(prettyDate(d.date))
                                trip.place(d.placeId)?.let { Muted("📍 ${it.name}") }
                                if (d.code.isNotBlank()) Text("Código: ${d.code}", style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { editing = d }) { Icon(Icons.Default.Edit, "Editar") }
                        }
                        if (d.notes.isNotBlank()) Text(d.notes, style = MaterialTheme.typography.bodySmall)
                        if (d.link.isNotBlank()) Row(Modifier.clickable { ctx.openUrl(d.link) }, verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.secondary); HSpace(4)
                            Text(d.link, color = MaterialTheme.colorScheme.secondary, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        }
                        d.files.forEach { f ->
                            Row(Modifier.clickable { if (f.isImage || f.isPdf) viewing = d else openFile(ctx, deps, f) }, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AttachFile, null); HSpace(4); Text(f.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
    viewing?.let { d -> TicketViewer(deps, d) { viewing = null } }
    if (adding || editing != null) DocEditor(deps, trip, editing, editing == null, onDismiss = { adding = false; editing = null }, onDelete = { d ->
        d.files.forEach { deps.store.deleteFile(it) }
        deps.store.update(trip.id) { t -> t.copy(docs = t.docs.filterNot { it.id == d.id }, checklist = t.checklist.map { if (it.docId == d.id) it.copy(docId = "") else it }) }
        editing = null
    }) { d ->
        saveDoc(deps, trip, d)
        adding = false; editing = null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocEditor(deps: AppDeps, trip: Trip, initial: TravelDoc?, isNew: Boolean, onDismiss: () -> Unit, onDelete: (TravelDoc) -> Unit, onSave: (TravelDoc) -> Unit) {
    val ctx = LocalContext.current
    var placeId by remember { mutableStateOf(initial?.placeId ?: "") }
    var choosingPlace by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(initial?.kind ?: DocKind.FLIGHT) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(initial?.date ?: "") }
    var link by remember { mutableStateOf(initial?.link ?: "") }
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var files by remember { mutableStateOf(initial?.files ?: emptyList()) }
    val removed = remember { mutableListOf<AttachedFile>() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri -> runCatching { deps.store.importFile(uri) }.onSuccess { files = files + it }.onFailure { ctx.toast(it.message ?: "Error") } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Nuevo documento" else "Editar documento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DocKind.entries.forEach { k -> FilterChip(kind == k, { kind = k }, label = { Text(k.label) }) }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Título (p. ej. Vuelo CDMX → Narita)") })
                DateField("Fecha", date, { date = it }, allowClear = true)
                OutlinedTextField(code, { code = it }, label = { Text("Código de reserva / folio") }, singleLine = true)
                OutlinedTextField(link, { link = it.trim() }, label = { Text("Enlace (reserva, permiso en línea…)") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas") })
                Box {
                    OutlinedButton(onClick = { choosingPlace = true }) {
                        Text("📍 " + (trip.place(placeId)?.name ?: "Ligar a un lugar (opcional)"), maxLines = 1)
                    }
                    DropdownMenu(choosingPlace, { choosingPlace = false }) {
                        DropdownMenuItem(text = { Text("Ninguno") }, onClick = { placeId = ""; choosingPlace = false })
                        trip.places.sortedBy { it.kind != PlaceKind.HOTEL }.forEach { p ->
                            DropdownMenuItem(text = { Text((if (p.kind == PlaceKind.HOTEL) "🏨 " else "") + p.name) }, onClick = {
                                placeId = p.id; choosingPlace = false
                                if (title.isBlank()) title = p.name
                            })
                        }
                    }
                }
                files.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null); Text(f.displayName, Modifier.weight(1f))
                        IconButton(onClick = { files = files - f; removed += f }) { Icon(Icons.Default.Close, "Quitar") }
                    }
                }
                TextButton(onClick = { picker.launch(arrayOf("application/pdf", "image/*", "*/*")) }) {
                    Icon(Icons.Default.AttachFile, null); HSpace(4); Text("Adjuntar PDF o imagen (QR, captura…)")
                }
                if (files.isEmpty()) Muted("Tip: si el boleto te llegó por correo o en la app de la atracción, haz captura del QR o descarga el PDF y adjúntalo aquí.")
                if (!isNew && initial != null) TextButton(onClick = { onDelete(initial) }) { Text("Borrar documento", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                removed.forEach { deps.store.deleteFile(it) }
                onSave((initial ?: TravelDoc(title = title)).copy(kind = kind, title = title.trim(), date = date, link = link, code = code, notes = notes, files = files, placeId = placeId))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

// -------------------------------------------------------------------------------------------------
// Checklist: qué necesito para viajar de México a Japón
// -------------------------------------------------------------------------------------------------

@Composable
fun ChecklistTab(deps: AppDeps, nav: Nav, trip: Trip) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<CheckItem?>(null) }
    val sections = trip.checklist.map { it.section }.distinct()
    fun toggle(i: CheckItem) = deps.store.update(trip.id) { t -> t.copy(checklist = t.checklist.map { if (it.id == i.id) it.copy(done = !it.done) else it }) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            val done = trip.checklist.count { it.done }
            SectionCard(color = MaterialTheme.colorScheme.primaryContainer) {
                Title("Listo: $done de ${trip.checklist.size}")
                Muted("Requisitos revisados el ${com.ivan.tabiplan.content.GUIDES_CHECKED}. Las reglas cambian: confirma con la IA o en los sitios oficiales antes de viajar.")
                Row {
                    TextButton(onClick = { adding = true }) { Text("Agregar punto") }
                    TextButton(onClick = {
                        nav.go(Screen.Assistant(trip.id, "Voy de México a ${trip.country.label} del ${trip.startDate} al ${trip.endDate}. Revisa con fuentes oficiales qué documentos, permisos en línea y requisitos necesito hoy, y si cambió algo (visa, Visit Japan Web, JESTA, impuestos, medicinas)."))
                    }) { Text("Revisar con IA") }
                    if (trip.checklist.isEmpty()) TextButton(onClick = {
                        deps.store.update(trip.id) { t -> t.copy(checklist = Checklists.template(t.country)) }
                    }) { Text("Cargar lista") }
                }
            }
        }
        sections.forEach { section ->
            item { Title(section) }
            items(trip.checklist.filter { it.section == section }, key = { it.id }) { i ->
                var open by remember { mutableStateOf(false) }
                SectionCard(Modifier.clickable { open = !open }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i.done, { toggle(i) })
                        Text(i.text, Modifier.weight(1f), fontWeight = if (i.done) FontWeight.Normal else FontWeight.Medium)
                    }
                    if (open) {
                        if (i.detail.isNotBlank()) Text(i.detail, style = MaterialTheme.typography.bodySmall)
                        Row {
                            if (i.link.isNotBlank()) TextButton(onClick = { ctx.openUrl(i.link) }) { Text("Abrir sitio") }
                            TextButton(onClick = { linking = i }) { Text(if (i.docId.isBlank()) "Vincular documento" else "Cambiar documento") }
                            TextButton(onClick = { deps.store.update(trip.id) { t -> t.copy(checklist = t.checklist.filterNot { it.id == i.id }) } }) { Text("Quitar") }
                        }
                    }
                    trip.docs.firstOrNull { it.id == i.docId }?.let { Muted("📎 ${it.title}") }
                }
            }
        }
        item {
            Title("Guías útiles")
            Guides.forCountry(trip.country).take(4).forEach { g ->
                TextButton(onClick = { nav.go(Screen.Guide(g.id)) }) { Text("${g.emoji} ${g.title}") }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (adding) {
        var text by remember { mutableStateOf("") }
        var section by remember { mutableStateOf(sections.firstOrNull() ?: "Otros") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Nuevo punto") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, label = { Text("Qué falta") })
                    OutlinedTextField(section, { section = it }, label = { Text("Sección") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = {
                    deps.store.update(trip.id) { t -> t.copy(checklist = t.checklist + CheckItem(section = section.ifBlank { "Otros" }, text = text.trim())) }
                    adding = false
                }) { Text("Agregar") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancelar") } },
        )
    }
    linking?.let { item ->
        AlertDialog(
            onDismissRequest = { linking = null },
            title = { Text("Documento para: ${item.text}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (trip.docs.isEmpty()) Muted("Primero agrega el documento en la pestaña Documentos.")
                    trip.docs.forEach { d ->
                        Text(d.title, Modifier.clickable {
                            deps.store.update(trip.id) { t -> t.copy(checklist = t.checklist.map { if (it.id == item.id) it.copy(docId = d.id, done = true) else it }) }
                            linking = null
                        }.padding(vertical = 10.dp))
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { linking = null }) { Text("Cerrar") } },
        )
    }
}
