package com.ivan.tabiplan.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.ivan.tabiplan.content.Illus

private val Ink = Color(0xFF2B2B2B)
private val JrGreen = Color(0xFF2E9A3E)
private val MetroBlue = Color(0xFF1F6FB8)
private val Screen = Color(0xFFDDEFFF)
private val Highlight = Color(0xFFFF6A3D)
private val Paper = Color(0xFFF7F3EA)
private val Steel = Color(0xFFB9C1C9)

/**
 * Dibujos esquemáticos hechos para la app (no fotos): muestran dónde están los botones y ranuras
 * de las máquinas. Los números naranjas siguen el orden de los pasos de la guía.
 */
@Composable
fun Illustration(illus: Illus, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val ratio = when (illus) {
        Illus.AIRPORT_ROUTES, Illus.FARE_MAP, Illus.SHINKANSEN_TICKETS, Illus.IC_CARDS -> 1.6f
        Illus.GATE, Illus.TAXI_JP -> 1.5f
        else -> 1.1f
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(ratio)) {
        drawRoundRect(Paper, cornerRadius = CornerRadius(24f))
        when (illus) {
            Illus.TICKET_MACHINE -> ticketMachine(tm, JrGreen, charge = false)
            Illus.CHARGE_MACHINE -> ticketMachine(tm, MetroBlue, charge = true)
            Illus.SHINKANSEN_MACHINE -> shinkansenMachine(tm)
            Illus.IC_CARDS -> icCards(tm)
            Illus.GATE -> gate(tm)
            Illus.SHINKANSEN_TICKETS -> shinkansenTickets(tm)
            Illus.AIRPORT_ROUTES -> airportRoutes(tm)
            Illus.FARE_MAP -> fareMap(tm)
            Illus.TAXI_JP -> taxi(tm)
        }
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset, size: Float = 11f, color: Color = Ink, bold: Boolean = false) {
    drawText(tm, text, at, TextStyle(fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
}

private fun DrawScope.badge(tm: TextMeasurer, n: Int, c: Offset) {
    val r = size.minDimension * 0.045f
    drawCircle(Highlight, r, c)
    label(tm, "$n", Offset(c.x - r * 0.38f, c.y - r * 0.8f), 11f, Color.White, bold = true)
}

private fun DrawScope.ticketMachine(tm: TextMeasurer, brand: Color, charge: Boolean) {
    val w = size.width
    val h = size.height
    val body = Offset(w * 0.18f, h * 0.06f)
    val bodySize = Size(w * 0.64f, h * 0.88f)
    drawRoundRect(Steel, body, bodySize, CornerRadius(18f))
    drawRect(brand, body, Size(bodySize.width, h * 0.07f))
    label(tm, if (charge) "きっぷ・チャージ  Tickets / Charge" else "きっぷうりば  Tickets", Offset(body.x + 10f, body.y + 4f), 10f, Color.White, true)
    // Pantalla táctil
    val scr = Offset(w * 0.24f, h * 0.17f)
    val scrSize = Size(w * 0.52f, h * 0.38f)
    drawRoundRect(Screen, scr, scrSize, CornerRadius(10f))
    // Botón English
    drawRoundRect(Color.White, Offset(scr.x + scrSize.width * 0.62f, scr.y + 8f), Size(scrSize.width * 0.34f, h * 0.06f), CornerRadius(8f))
    label(tm, "English", Offset(scr.x + scrSize.width * 0.66f, scr.y + 12f), 10f)
    badge(tm, 1, Offset(scr.x + scrSize.width * 0.97f, scr.y + 4f))
    // Botones grandes
    val btnW = scrSize.width * 0.44f
    val btnH = h * 0.1f
    val by = scr.y + h * 0.11f
    drawRoundRect(if (charge) Color.White else Color(0xFFFFE08A), Offset(scr.x + 8f, by), Size(btnW, btnH), CornerRadius(8f))
    label(tm, if (charge) "Ticket" else "Ticket / Suica", Offset(scr.x + 14f, by + 8f), 10f)
    drawRoundRect(if (charge) Color(0xFFFFB3C7) else Color.White, Offset(scr.x + scrSize.width - btnW - 8f, by), Size(btnW, btnH), CornerRadius(8f))
    label(tm, "Charge チャージ", Offset(scr.x + scrSize.width - btnW - 2f, by + 8f), 10f)
    badge(tm, 2, Offset(if (charge) scr.x + scrSize.width - 10f else scr.x + btnW, by))
    // Montos
    val amounts = if (charge) listOf("¥1000", "¥3000", "¥5000", "¥10000") else listOf("¥180", "¥210", "¥260", "¥300")
    amounts.forEachIndexed { i, a ->
        val x = scr.x + 8f + i * (scrSize.width - 16f) / 4f
        drawRoundRect(Color.White, Offset(x, by + btnH + 10f), Size((scrSize.width - 16f) / 4f - 6f, h * 0.07f), CornerRadius(6f))
        label(tm, a, Offset(x + 4f, by + btnH + 14f), 9f)
    }
    badge(tm, if (charge) 4 else 3, Offset(scr.x + scrSize.width - 6f, by + btnH + 10f))
    // Ranuras
    val slotY = h * 0.62f
    drawRoundRect(Ink, Offset(w * 0.26f, slotY), Size(w * 0.12f, h * 0.025f), CornerRadius(4f))
    label(tm, if (charge) "Tarjeta" else "Monedas", Offset(w * 0.24f, slotY + h * 0.035f), 9f)
    drawRoundRect(Ink, Offset(w * 0.44f, slotY), Size(w * 0.14f, h * 0.025f), CornerRadius(4f))
    label(tm, "Billetes", Offset(w * 0.45f, slotY + h * 0.035f), 9f)
    badge(tm, if (charge) 3 else 4, Offset(w * 0.26f, slotY))
    badge(tm, 5, Offset(w * 0.58f, slotY))
    drawRoundRect(Ink, Offset(w * 0.62f, slotY), Size(w * 0.1f, h * 0.025f), CornerRadius(4f))
    label(tm, "Tarjeta IC", Offset(w * 0.6f, slotY + h * 0.035f), 9f)
    // Bandeja de salida
    drawRoundRect(Color(0xFF8E979F), Offset(w * 0.3f, h * 0.8f), Size(w * 0.4f, h * 0.08f), CornerRadius(8f))
    label(tm, "Sale boleto / tarjeta / cambio", Offset(w * 0.31f, h * 0.81f), 9f, Color.White)
    badge(tm, 6, Offset(w * 0.7f, h * 0.8f))
}

private fun DrawScope.shinkansenMachine(tm: TextMeasurer) {
    val w = size.width
    val h = size.height
    drawRoundRect(Steel, Offset(w * 0.15f, h * 0.05f), Size(w * 0.7f, h * 0.9f), CornerRadius(18f))
    drawRect(Color(0xFF1E5AA8), Offset(w * 0.15f, h * 0.05f), Size(w * 0.7f, h * 0.07f))
    label(tm, "指定席券売機  Reserved Seat Ticket Machine", Offset(w * 0.17f, h * 0.06f), 9f, Color.White, true)
    val scr = Offset(w * 0.2f, h * 0.15f)
    val sw = w * 0.6f
    drawRoundRect(Screen, scr, Size(sw, h * 0.45f), CornerRadius(10f))
    val rows = listOf("Tokyo → Kyoto", "15 Dic  10:00  ·  2 adultos", "Nozomi 215  ·  Reserved", "Asiento: 8E (ventanilla)")
    rows.forEachIndexed { i, r ->
        drawRoundRect(Color.White, Offset(scr.x + 10f, scr.y + 12f + i * h * 0.1f), Size(sw - 20f, h * 0.08f), CornerRadius(6f))
        label(tm, r, Offset(scr.x + 18f, scr.y + 18f + i * h * 0.1f), 10f)
        badge(tm, i + 3, Offset(scr.x + sw - 10f, scr.y + 12f + i * h * 0.1f))
    }
    // Lectores de pago
    drawRoundRect(Ink, Offset(w * 0.22f, h * 0.66f), Size(w * 0.16f, h * 0.03f), CornerRadius(4f))
    label(tm, "Tarjeta de crédito", Offset(w * 0.2f, h * 0.7f), 9f)
    drawRoundRect(Ink, Offset(w * 0.45f, h * 0.66f), Size(w * 0.14f, h * 0.03f), CornerRadius(4f))
    label(tm, "Billetes", Offset(w * 0.47f, h * 0.7f), 9f)
    drawRoundRect(Ink, Offset(w * 0.64f, h * 0.66f), Size(w * 0.12f, h * 0.03f), CornerRadius(4f))
    label(tm, "QR / pase", Offset(w * 0.64f, h * 0.7f), 9f)
    badge(tm, 8, Offset(w * 0.38f, h * 0.66f))
    drawRoundRect(Color(0xFF8E979F), Offset(w * 0.3f, h * 0.8f), Size(w * 0.4f, h * 0.08f), CornerRadius(8f))
    label(tm, "Boletos + recibo", Offset(w * 0.4f, h * 0.81f), 9f, Color.White)
}

private fun DrawScope.icCards(tm: TextMeasurer) {
    val w = size.width
    val h = size.height
    data class C(val name: String, val sub: String, val color: Color)
    val cards = listOf(
        C("Suica", "JR East · depósito ¥500", JrGreen),
        C("Welcome Suica", "Turistas · 28 días · sin depósito", Color(0xFFE05A8A)),
        C("PASMO", "Metro y trenes privados · ¥500", Color(0xFFE8A33D)),
    )
    cards.forEachIndexed { i, c ->
        val x = w * 0.04f + i * w * 0.32f
        val y = h * 0.18f
        drawRoundRect(c.color, Offset(x, y), Size(w * 0.29f, h * 0.42f), CornerRadius(14f))
        drawRoundRect(Color(0x55FFFFFF), Offset(x + 10f, y + h * 0.06f), Size(w * 0.07f, h * 0.08f), CornerRadius(4f))
        label(tm, c.name, Offset(x + 10f, y + h * 0.26f), 12f, Color.White, true)
        label(tm, c.sub, Offset(x, y + h * 0.48f), 8f)
    }
    label(tm, "Funcionan igual en metro, tren, bus y konbinis de casi todo Japón", Offset(w * 0.04f, h * 0.85f), 9f)
}

private fun DrawScope.gate(tm: TextMeasurer) {
    val w = size.width
    val h = size.height
    // Dos módulos de torniquete
    listOf(0.12f, 0.6f).forEach { fx ->
        drawRoundRect(Steel, Offset(w * fx, h * 0.3f), Size(w * 0.28f, h * 0.45f), CornerRadius(12f))
    }
    // Lector IC
    drawCircle(MetroBlue, h * 0.08f, Offset(w * 0.33f, h * 0.38f))
    drawCircle(Color.White, h * 0.05f, Offset(w * 0.33f, h * 0.38f), style = Stroke(3f))
    label(tm, "IC: toca aquí", Offset(w * 0.22f, h * 0.17f), 10f, bold = true)
    badge(tm, 1, Offset(w * 0.41f, h * 0.3f))
    // Ranura de boleto
    drawRoundRect(Ink, Offset(w * 0.16f, h * 0.55f), Size(w * 0.1f, h * 0.03f), CornerRadius(4f))
    label(tm, "Boleto entra", Offset(w * 0.13f, h * 0.6f), 9f)
    drawRoundRect(Ink, Offset(w * 0.64f, h * 0.55f), Size(w * 0.1f, h * 0.03f), CornerRadius(4f))
    label(tm, "y sale aquí: tómalo", Offset(w * 0.6f, h * 0.6f), 9f)
    badge(tm, 2, Offset(w * 0.74f, h * 0.55f))
    // Flecha de paso
    val p = Path().apply {
        moveTo(w * 0.42f, h * 0.88f); lineTo(w * 0.56f, h * 0.88f); lineTo(w * 0.56f, h * 0.84f)
        lineTo(w * 0.62f, h * 0.9f); lineTo(w * 0.56f, h * 0.96f); lineTo(w * 0.56f, h * 0.92f); lineTo(w * 0.42f, h * 0.92f); close()
    }
    drawPath(p, JrGreen)
    label(tm, "Puerta abierta si la pantalla muestra tu saldo", Offset(w * 0.08f, h * 0.02f), 9f)
}

private fun DrawScope.shinkansenTickets(tm: TextMeasurer) {
    val w = size.width
    val h = size.height
    listOf("乗車券  Tarifa base" to 0.08f, "特急券  Shinkansen + asiento" to 0.5f).forEach { (t, fx) ->
        drawRoundRect(Color(0xFFDDEBF7), Offset(w * fx, h * 0.18f), Size(w * 0.42f, h * 0.42f), CornerRadius(8f))
        drawRect(Color(0xFF6FA0D0), Offset(w * fx, h * 0.18f), Size(w * 0.42f, h * 0.06f))
        label(tm, t, Offset(w * fx + 8f, h * 0.27f), 10f, bold = true)
        label(tm, "Tokyo → Kyoto", Offset(w * fx + 8f, h * 0.37f), 10f)
        label(tm, if (fx < 0.3f) "¥8,360 aprox." else "Nozomi · 8号車 12E", Offset(w * fx + 8f, h * 0.46f), 9f)
    }
    label(tm, "Mételos JUNTOS al torniquete y recógelos al otro lado", Offset(w * 0.08f, h * 0.7f), 10f, Highlight, true)
    label(tm, "号車 = vagón · 番 / E = asiento", Offset(w * 0.08f, h * 0.82f), 9f)
}

private fun DrawScope.airportRoutes(tm: TextMeasurer) {
    val w = size.width
    val h = size.height
    val nrt = Offset(w * 0.86f, h * 0.5f)
    drawCircle(Ink, h * 0.06f, nrt)
    label(tm, "Narita", Offset(nrt.x - w * 0.02f, nrt.y + h * 0.08f), 10f, bold = true)
    data class R(val to: String, val y: Float, val color: Color, val text: String)
    val routes = listOf(
        R("Ueno / Nippori", 0.18f, MetroBlue, "Skyliner ≈ ¥2,580 · 40 min"),
        R("Tokio / Shinjuku", 0.42f, Color(0xFFD9342B), "N'EX ≈ ¥3,070+ · 60 min"),
        R("Ueno / Asakusa", 0.64f, Color(0xFF6C8EBF), "Access Express ≈ ¥1,240 · 70 min"),
        R("Tokio / Ginza", 0.86f, Color(0xFFE8A33D), "Bus ≈ ¥1,300+ · 60–120 min"),
    )
    routes.forEach { r ->
        // Tramo horizontal (con el texto debajo) y luego diagonal hacia Narita: así nada se encima.
        val end = Offset(w * 0.14f, h * r.y)
        val bend = Offset(w * 0.68f, h * r.y)
        drawLine(r.color, end, bend, strokeWidth = 7f)
        drawLine(r.color, bend, nrt, strokeWidth = 7f)
        drawCircle(r.color, h * 0.035f, end)
        label(tm, r.to, Offset(end.x - w * 0.1f, end.y - h * 0.11f), 9f, bold = true)
        label(tm, r.text, Offset(w * 0.2f, end.y + h * 0.025f), 8f)
    }
}

private fun DrawScope.fareMap(tm: TextMeasurer) {
    val w = size.width
    val h = size.height
    drawRoundRect(Color.White, Offset(w * 0.05f, h * 0.08f), Size(w * 0.9f, h * 0.7f), CornerRadius(10f))
    // Líneas
    drawLine(Color(0xFF9ACD32), Offset(w * 0.12f, h * 0.45f), Offset(w * 0.88f, h * 0.45f), 8f)
    drawLine(Color(0xFFF39700), Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.72f), 8f)
    val stations = listOf(
        Triple(0.12f to 0.45f, "Shinjuku", "260"),
        Triple(0.3f to 0.45f, "Yotsuya", "210"),
        Triple(0.5f to 0.45f, "Aquí (現在地)", ""),
        Triple(0.7f to 0.45f, "Ginza", "180"),
        Triple(0.88f to 0.45f, "Asakusa", "210"),
        Triple(0.5f to 0.18f, "Ueno", "210"),
        Triple(0.5f to 0.7f, "Shibuya", "260"),
    )
    stations.forEach { (pos, name, fare) ->
        val c = Offset(w * pos.first, h * pos.second)
        drawCircle(if (fare.isEmpty()) Highlight else Color.White, h * 0.03f, c)
        drawCircle(Ink, h * 0.03f, c, style = Stroke(2f))
        label(tm, name, Offset(c.x - w * 0.06f, c.y - h * 0.1f), 8f)
        if (fare.isNotEmpty()) label(tm, fare, Offset(c.x - w * 0.025f, c.y + h * 0.035f), 10f, Highlight, true)
    }
    label(tm, "El número bajo cada estación = precio desde \"Aquí\"", Offset(w * 0.06f, h * 0.83f), 9f)
}

private fun DrawScope.taxi(tm: TextMeasurer) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(w * 0.1f, h * 0.65f); lineTo(w * 0.18f, h * 0.45f); lineTo(w * 0.32f, h * 0.42f)
        lineTo(w * 0.42f, h * 0.28f); lineTo(w * 0.7f, h * 0.28f); lineTo(w * 0.8f, h * 0.42f)
        lineTo(w * 0.9f, h * 0.46f); lineTo(w * 0.9f, h * 0.65f); close()
    }
    drawPath(body, Color(0xFF2D4A8A))
    drawRoundRect(Color(0xFFFFD34D), Offset(w * 0.5f, h * 0.2f), Size(w * 0.1f, h * 0.07f), CornerRadius(4f))
    drawCircle(Ink, h * 0.07f, Offset(w * 0.27f, h * 0.67f))
    drawCircle(Ink, h * 0.07f, Offset(w * 0.75f, h * 0.67f))
    drawRect(Color(0x66FFFFFF), Offset(w * 0.44f, h * 0.31f), Size(w * 0.11f, h * 0.1f))
    drawRect(Color(0x66FFFFFF), Offset(w * 0.57f, h * 0.31f), Size(w * 0.11f, h * 0.1f))
    label(tm, "La puerta se abre sola ↙", Offset(w * 0.1f, h * 0.08f), 10f, Highlight, true)
    label(tm, "空車 (rojo) = libre · 賃走 = ocupado", Offset(w * 0.1f, h * 0.82f), 9f)
}
