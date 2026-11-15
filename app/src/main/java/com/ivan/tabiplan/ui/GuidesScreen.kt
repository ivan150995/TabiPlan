package com.ivan.tabiplan.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivan.tabiplan.Nav
import com.ivan.tabiplan.Screen
import com.ivan.tabiplan.content.GUIDES_CHECKED
import com.ivan.tabiplan.content.Guides
import com.ivan.tabiplan.data.Country

@Composable
fun GuidesScreen(nav: Nav, initial: Country?) {
    var country by remember { mutableStateOf(initial ?: Country.JP) }
    Scaffold(topBar = { BackBar("Guías de viaje", { nav.back() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Country.entries.forEach { c -> FilterChip(country == c, { country = c }, label = { Text(c.label) }) }
                }
            }
            items(Guides.forCountry(country), key = { it.id }) { g ->
                SectionCard(Modifier.clickable { nav.go(Screen.Guide(g.id)) }) {
                    Text("${g.emoji}  ${g.title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Muted(g.subtitle)
                }
            }
            item { Muted("Revisadas el $GUIDES_CHECKED. En cada guía puedes pedir a la IA que confirme precios y reglas de hoy.") }
        }
    }
}

@Composable
fun GuideScreen(nav: Nav, id: String) {
    val g = Guides.byId(id) ?: run { nav.back(); return }
    val ctx = LocalContext.current
    Scaffold(topBar = { BackBar(g.title, { nav.back() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${g.emoji} ${g.title}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Muted(g.subtitle)
            }
            items(g.sections) { s ->
                SectionCard {
                    Title(s.title)
                    if (s.text.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(s.text, style = MaterialTheme.typography.bodyMedium) }
                    s.illus?.let { Spacer(Modifier.height(8.dp)); Illustration(it) }
                    s.steps.forEachIndexed { i, step ->
                        Row(Modifier.padding(top = 6.dp)) {
                            Text("${i + 1}.", Modifier.width(24.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (s.tip.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("💡 ${s.tip}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (g.links.isNotEmpty()) item {
                SectionCard {
                    Title("Sitios oficiales")
                    g.links.forEach { (label, url) -> TextButton(onClick = { ctx.openUrl(url) }) { Text(label) } }
                }
            }
            item {
                Button(onClick = { nav.go(Screen.Assistant(null, g.askAi)) }) {
                    Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Preguntar a la IA si cambió algo")
                }
                Muted("Revisado el $GUIDES_CHECKED. Los dibujos son esquemas: las máquinas reales varían por compañía.")
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}
