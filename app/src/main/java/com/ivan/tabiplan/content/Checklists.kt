package com.ivan.tabiplan.content

import com.ivan.tabiplan.data.CheckItem
import com.ivan.tabiplan.data.Country

/** Lista de pendientes para viajar desde México. Se copia al viaje y el usuario la marca y edita. */
object Checklists {
    private fun item(section: String, text: String, detail: String = "", link: String = "") =
        CheckItem(section = section, text = text, detail = detail, link = link)

    fun template(country: Country): List<CheckItem> = when (country) {
        Country.JP -> japan()
        Country.MX -> mexico()
    }

    private fun japan() = listOf(
        // Documentos
        item("Documentos", "Pasaporte mexicano vigente", "Debe estar vigente toda la estancia; se recomienda 6 meses de margen y al menos una hoja libre. Guarda una foto en Documentos."),
        item("Documentos", "Sin visa: entras como \"Temporary Visitor\" hasta 90 días", "Mexicanos no necesitan visa para turismo (hasta 90 días; se puede pedir prórroga en Japón). No se permite trabajar.", "https://www.mx.emb-japan.go.jp/"),
        item("Documentos", "Boleto de avión de regreso o salida de Japón", "Migración puede pedirlo. Adjunta el itinerario del vuelo en Documentos."),
        item("Documentos", "Reservas de hotel (al menos la primera noche)", "Ten la dirección en japonés para el taxi y para Visit Japan Web."),
        item("Documentos", "Si haces escala en EE. UU.: visa estadounidense vigente", "Para conectar en EE. UU. aunque no salgas del aeropuerto necesitas visa B1/B2 (los mexicanos no usan ESTA)."),
        item("Documentos", "Si haces escala en Canadá: eTA o visa canadiense", "Revisa en el sitio oficial de Canadá si calificas para eTA (por ejemplo con visa de EE. UU. vigente) o si necesitas visa.", "https://www.canada.ca/en/immigration-refugees-citizenship/services/visit-canada/eta.html"),
        item("Documentos", "Licencia internacional de manejo (solo si vas a manejar)", "Japón acepta el permiso internacional de Ginebra 1949. La licencia mexicana sola no sirve."),
        // Permisos en línea
        item("Permisos en línea", "Visit Japan Web: registro y QR de migración y aduana", "Hazlo 1–2 semanas antes. Captura pasaporte, vuelo y hotel; guarda las capturas de los QR en Documentos.", "https://services.digital.go.jp/en/visit-japan-web/"),
        item("Permisos en línea", "JESTA (autorización electrónica): aún no se exige", "Japón planea pedirla a viajeros sin visa a partir de ~2028. Revisa antes de cada viaje.", ""),
        // Dinero
        item("Dinero", "Avisar al banco y llevar 2 tarjetas (Visa/Mastercard)", "Una de respaldo guardada aparte. Revisa comisiones por uso en el extranjero."),
        item("Dinero", "Efectivo en yenes para los primeros días", "Puedes sacar en cajeros 7-Eleven al llegar. Calcula ¥5,000–10,000 por día por persona para comida, transporte y templos."),
        item("Dinero", "Tarjeta IC (Suica/PASMO) o app Welcome Suica Mobile (iPhone)", "Ver la guía de tarjetas IC."),
        item("Dinero", "Decidir si conviene JR Pass", "Solo si harás viajes largos en Shinkansen. Compara en la guía del Shinkansen."),
        // Salud y seguro
        item("Salud y seguro", "Seguro de viaje con gastos médicos", "En Japón la atención médica es cara para turistas. Guarda la póliza y el teléfono de asistencia en Documentos."),
        item("Salud y seguro", "Revisar medicinas permitidas", "Algunas medicinas comunes en México están prohibidas o limitadas en Japón (estimulantes, algunos antigripales con pseudoefedrina, codeína en dosis altas). Más de 1 mes de tratamiento requiere trámite (Yunyu Kakunin-sho).", "https://www.mhlw.go.jp/english/"),
        item("Salud y seguro", "Receta médica en inglés para tus medicinas"),
        // Conectividad
        item("Celular e internet", "eSIM o pocket Wi-Fi", "Compra la eSIM antes de salir y verifica que tu teléfono sea compatible y esté liberado."),
        item("Celular e internet", "Descargar mapas sin conexión y apps", "Google Maps (descarga Tokio), Google Translate (japonés sin conexión), GO o Uber, app de tu aerolínea."),
        item("Celular e internet", "Contactos de alojamiento y de emergencia guardados", "Embajada de México en Japón: +81 3-3581-1131. Japan Visitor Hotline: 050-3816-2787."),
        // Equipaje
        item("Equipaje", "Contactos: no necesitas adaptador", "Japón usa enchufes tipo A (igual que México) a 100 V. Cargadores de celular y laptop funcionan. Secadoras/planchas de 127 V pueden ir lentas."),
        item("Equipaje", "Power bank en el equipaje de mano", "Nunca en la maleta documentada. Algunas aerolíneas japonesas limitan su uso a bordo."),
        item("Equipaje", "Zapatos cómodos y fáciles de quitar", "Se camina mucho (15–25 mil pasos al día) y te descalzas en templos, ryokan y algunos restaurantes."),
        item("Equipaje", "Bolsa para basura y pañuelos", "Hay muy pocos botes de basura y algunos baños no tienen papel para secarse."),
        item("Equipaje", "Espacio en la maleta para compras", "O usa takkyubin para mandar la maleta grande entre hoteles."),
        // Reservas
        item("Reservas", "Atracciones que se agotan", "Ghibli Museum, teamLab, Shibuya Sky, Pokémon Café, Tokyo Disney, Universal Studios Japan, Nintendo Museum… suelen venderse semanas antes. Adjunta los boletos en Documentos."),
        item("Reservas", "Shinkansen con maletas grandes", "Si tu maleta pasa de 160 cm (suma de lados) reserva el asiento con espacio de equipaje."),
        item("Reservas", "Restaurantes populares", "Algunos solo aceptan reserva por apps como TableCheck u Omakase."),
        // Al regresar
        item("Regreso", "Llegar con 3 h al aeropuerto si pediste reembolso tax-free", "Desde nov 2026 hay que mostrar las compras en la aduana antes de documentar."),
        item("Regreso", "Declaración de aduana mexicana (si traes compras de más)", "Franquicia por persona al regresar por avión: equivalente a US\$500 en compras (revisa la cantidad vigente con la ANAM)."),
    )

    private fun mexico() = listOf(
        item("Documentos", "INE o pasaporte vigente", "Para vuelos nacionales basta la INE."),
        item("Documentos", "Pase de abordar y reservas de hotel", "Adjúntalos en Documentos."),
        item("Dinero", "Efectivo para propinas, mercados y taxis de sitio"),
        item("Dinero", "Tarjeta de Movilidad Integrada si vas a la CDMX", "Ver guía de la CDMX."),
        item("Celular e internet", "Uber y DiDi instalados con tarjeta registrada"),
        item("Salud y seguro", "Seguro de gastos médicos o de viaje"),
        item("Equipaje", "Ropa según el clima del destino y bloqueador"),
    )
}
