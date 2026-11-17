package com.ivan.tabiplan.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

val TabiColors: ColorScheme = lightColorScheme(
    primary = Color(0xFFC8102E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410003),
    secondary = Color(0xFF2E5E8C),
    secondaryContainer = Color(0xFFD6E6F7),
    tertiary = Color(0xFF2E9A3E),
    tertiaryContainer = Color(0xFFD5F2D8),
    background = Color(0xFFFBF7F4),
    surface = Color(0xFFFBF7F4),
    surfaceVariant = Color(0xFFF1E9E4),
)

@Composable
fun TabiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TabiColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
        },
        actions = { actions() },
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceVariant, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

@Composable
fun Title(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = color)
}

/** Aviso del itinerario: en rojo si conviene corregir algo. */
@Composable
fun HintLine(h: com.ivan.tabiplan.plan.Hint) =
    Muted(h.text, color = if (h.warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)

private val niceDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale("es", "MX"))

fun prettyDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(niceDate) }.getOrDefault(iso)

/** Campo de fecha que abre un calendario. Valor en yyyy-MM-dd ("" = sin fecha). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, allowClear: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = if (value.isBlank()) "" else prettyDate(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Default.CalendarMonth, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Capa transparente para que el toque abra el calendario (el campo es de solo lectura).
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val initial = runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    open = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                Row {
                    if (allowClear) TextButton(onClick = { onChange(""); open = false }) { Text("Quitar") }
                    TextButton(onClick = { open = false }) { Text("Cancelar") }
                }
            },
        ) { DatePicker(state) }
    }
}

fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        toast("No hay una app para abrir el enlace")
    }
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

/** Abre una app si está instalada; si no, la busca en Play Store. */
fun Context.openAppOrStore(pkg: String, deepLink: String? = null, storeQuery: String) {
    if (deepLink != null) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(packageManager) != null) {
            startActivity(i); return
        }
    }
    val launch = packageManager.getLaunchIntentForPackage(pkg)
    if (launch != null) {
        startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
    }
    openUrl("https://play.google.com/store/search?q=${Uri.encode(storeQuery)}&c=apps")
}

@Composable
fun Chip2(text: String, color: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Card(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
        colors = CardDefaults.cardColors(containerColor = color),
    ) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium) }
}

@Composable
fun RowSpaced(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun HSpace(w: Int = 8) = Spacer(Modifier.width(w.dp))

fun minutesText(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60} min"
}
