package com.ivan.tabiplan.content

import com.ivan.tabiplan.data.Country

/** Dibujos propios (ui/Illustrations.kt) que acompañan a una sección. */
enum class Illus { TICKET_MACHINE, CHARGE_MACHINE, IC_CARDS, GATE, SHINKANSEN_TICKETS, SHINKANSEN_MACHINE, AIRPORT_ROUTES, FARE_MAP, TAXI_JP }

data class GuideSection(
    val title: String,
    val steps: List<String> = emptyList(),
    val text: String = "",
    val illus: Illus? = null,
    val tip: String = "",
)

data class Guide(
    val id: String,
    val country: Country,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val sections: List<GuideSection>,
    val links: List<Pair<String, String>> = emptyList(),
    /** Pregunta lista para mandar al asistente (con búsqueda web) y confirmar que nada cambió. */
    val askAi: String,
)

/** Fecha en que se revisó el contenido. Precios y reglas cambian: la app ofrece confirmarlos con la IA. */
const val GUIDES_CHECKED = "15 nov 2026"

object Guides {
    val all: List<Guide> = listOf(
        Guide(
            id = "narita",
            country = Country.JP,
            emoji = "🛬",
            title = "Ya llegué a Narita, ¿qué hago?",
            subtitle = "Migración, aduana, dinero, internet y cómo llegar a Tokio",
            sections = listOf(
                GuideSection(
                    "1. Migración y aduana",
                    steps = listOf(
                        "Antes de bajar del avión ten abierto el QR de Visit Japan Web (migración y aduana). Lo puedes guardar en Documentos de este viaje.",
                        "Sigue los letreros \"Arrival / 到着\" → \"Immigration\". Fila de \"Foreigners\" (extranjeros).",
                        "Te toman huellas y foto; muestra pasaporte y el QR. Te pegan el sello/calcomanía de \"Temporary Visitor\" (hasta 90 días para mexicanos).",
                        "Recoge tu maleta en \"Baggage Claim\" (pantallas con tu número de vuelo).",
                        "Aduana: escanea el QR de aduana en la máquina y pasa por la puerta electrónica, o entrega la declaración en papel.",
                    ),
                    tip = "Si traes medicinas controladas o más de un mes de tratamiento revisa antes las reglas (ver checklist).",
                ),
                GuideSection(
                    "2. Dinero e internet en el aeropuerto",
                    steps = listOf(
                        "Cajeros: los de 7-Eleven (Seven Bank) y Japan Post aceptan tarjetas de México. Elige \"cobrar en yenes\" (no en pesos) para evitar la conversión cara del cajero.",
                        "Lleva algo de efectivo: muchos templos, puestos y máquinas de recarga solo aceptan billetes.",
                        "eSIM: actívala antes de salir de México y enciende \"datos en roaming\" al aterrizar. Si no, hay mostradores de SIM y pocket Wi-Fi en llegadas.",
                    ),
                ),
                GuideSection(
                    "3. Elige cómo llegar a Tokio",
                    illus = Illus.AIRPORT_ROUTES,
                    text = "Precios aproximados por adulto (revisados $GUIDES_CHECKED):",
                    steps = listOf(
                        "Keisei Skyliner → Nippori / Ueno: ≈ ¥2,580, 36–45 min, asiento reservado. Ideal si te hospedas en Ueno, Asakusa o conectas a la línea Yamanote.",
                        "JR Narita Express (N'EX) → Tokio, Shinagawa, Shibuya, Shinjuku: ≈ ¥3,070–3,330, ~60 min, asiento reservado. Gratis con JR Pass.",
                        "Keisei Access Express / línea principal → Ueno, Asakusa: ≈ ¥1,240, 60–90 min, sin reserva. La opción barata (puedes pagar con Suica/PASMO).",
                        "Autobús (Airport Limousine / bus directo a Tokio Station o Ginza): ≈ ¥1,300–3,600, 60–120 min según tráfico. Cómodo con maletas grandes.",
                        "Taxi: ≈ ¥20,000–30,000 (hay tarifa fija a algunas zonas). Solo si van varios con mucho equipaje.",
                    ),
                ),
                GuideSection(
                    "4. Comprar el boleto: mostrador o máquina",
                    steps = listOf(
                        "Las estaciones están en el piso B1 del aeropuerto (\"Railways / 鉄道\"). Terminal 1: estación \"Narita Airport\"; Terminales 2 y 3: estación \"Airport Terminal 2\".",
                        "Mostrador: Skyliner en el mostrador de Keisei (azul). N'EX y JR Pass en el \"JR East Travel Service Center\". Di destino y hora; pagas con tarjeta.",
                        "Máquina: toca \"English\" → elige el tren (Skyliner o N'EX) → destino → hora y número de personas → asiento → paga (tarjeta o efectivo) → recoge boleto(s).",
                        "Con Skyliner/N'EX salen 2 papeles o uno combinado: mételos juntos en el torniquete y no olvides recogerlos al otro lado.",
                    ),
                    illus = Illus.TICKET_MACHINE,
                    tip = "Aquí mismo puedes comprar la tarjeta Suica/PASMO para el resto del viaje (ver guía de tarjetas).",
                ),
                GuideSection(
                    "5. Al llegar al hotel",
                    steps = listOf(
                        "Marca el aeropuerto como completado en la pestaña \"Hoy\": la app te dará la ruta al hotel con precio.",
                        "Si llegas en la noche, el último Skyliner/N'EX sale ~22:00–23:00; después solo taxi o bus nocturno.",
                        "¿Mucho equipaje? Envía la maleta al hotel con \"takkyubin\" (mostradores ABC/Yamato en llegadas, ≈ ¥2,500–3,500, llega al día siguiente).",
                    ),
                ),
            ),
            links = listOf(
                "Aeropuerto de Narita (oficial)" to "https://www.narita-airport.jp/en/",
                "Keisei Skyliner (oficial)" to "https://www.keisei.co.jp/keisei/tetudou/skyliner/us/",
                "JR East en inglés (Narita Express)" to "https://www.jreast.co.jp/multi/en/",
                "Visit Japan Web" to "https://services.digital.go.jp/en/visit-japan-web/",
            ),
            askAi = "Acabo de llegar al aeropuerto de Narita. ¿Cuáles son hoy los precios y horarios del Skyliner, Narita Express y Access Express hasta Tokio, y dónde se vende la Suica/Welcome Suica en Narita? Confirma con fuentes oficiales.",
        ),
        Guide(
            id = "haneda",
            country = Country.JP,
            emoji = "✈️",
            title = "Ya llegué a Haneda",
            subtitle = "El aeropuerto más cerca del centro de Tokio",
            sections = listOf(
                GuideSection(
                    "Migración y aduana",
                    text = "Igual que en Narita: QR de Visit Japan Web, fila de extranjeros, maleta y aduana. Los vuelos internacionales llegan a la Terminal 3.",
                ),
                GuideSection(
                    "Cómo llegar al centro",
                    steps = listOf(
                        "Keikyu → Shinagawa (≈ ¥330, 15 min) y de ahí línea JR Yamanote. Hay trenes directos hacia Asakusa/Oshiage por la línea Toei Asakusa.",
                        "Tokyo Monorail → Hamamatsucho (≈ ¥520, 15 min) y cambio a JR Yamanote.",
                        "Autobús Airport Limousine a Shinjuku, Tokio Station y hoteles grandes (≈ ¥1,000–1,800).",
                        "Taxi al centro: ≈ ¥7,000–12,000 (hay tarifas fijas por zona).",
                    ),
                ),
                GuideSection(
                    "Tarjeta IC en Haneda",
                    text = "La Welcome Suica se vende en la Terminal 3 (máquinas y centro de servicio JR EAST/Keikyu). También puedes comprar PASMO en las máquinas de Keikyu.",
                ),
            ),
            links = listOf(
                "Aeropuerto de Haneda (oficial)" to "https://tokyo-haneda.com/en/",
                "Keikyu (oficial)" to "https://www.haneda-tokyo-access.com/en/",
            ),
            askAi = "Llegué a Haneda Terminal 3. ¿Cuál es hoy la mejor forma y precio para llegar a mi hotel, y dónde compro la Welcome Suica? Confírmalo con fuentes oficiales.",
        ),
        Guide(
            id = "ic",
            country = Country.JP,
            emoji = "💳",
            title = "Suica, Welcome Suica y PASMO",
            subtitle = "Qué tarjeta conviene, dónde se compra y cómo",
            sections = listOf(
                GuideSection(
                    "¿Qué son?",
                    illus = Illus.IC_CARDS,
                    text = "Son tarjetas recargables (\"IC\") para metro, tren, autobús, máquinas expendedoras y konbinis (7-Eleven, Lawson, FamilyMart). Suica (JR East) y PASMO (metros y trenes privados) funcionan igual en casi todo Japón.",
                    steps = listOf(
                        "Suica / PASMO normal: depósito de ¥500 que te devuelven al regresarla; el saldo no caduca en 10 años.",
                        "Welcome Suica (turistas): sin depósito, vale 28 días, no se devuelve; diseño especial. Se vende sobre todo en Haneda y en algunos centros de JR East.",
                        "Welcome Suica Mobile (app, solo iPhone): la tarjeta en el teléfono, se recarga con tu tarjeta de crédito. No hace falta hacer fila.",
                        "Suica en Apple Wallet (iPhone 8 o más nuevo): se agrega desde Wallet y se recarga con Apple Pay.",
                        "Android de México: casi nunca sirve como Suica (necesita chip FeliCa de modelos japoneses). Usa tarjeta física.",
                    ),
                    tip = "Hay versiones de PASMO para turistas que cambian seguido; si no hay, compra la PASMO o Suica normal. Toca \"Preguntar a la IA\" para confirmar qué hay hoy.",
                ),
                GuideSection(
                    "Comprarla en la máquina (paso a paso)",
                    illus = Illus.TICKET_MACHINE,
                    steps = listOf(
                        "Busca una máquina con el logo de Suica o PASMO (las de JR son verdes; las de metro, azules/grises).",
                        "Toca \"English\" en la pantalla.",
                        "Elige \"Purchase new Suica\" / \"New PASMO\" (o \"Welcome Suica\" si aparece).",
                        "Tipo de tarjeta: \"My Suica\" (con nombre) o \"Suica\" sin nombre. Sin nombre es más rápido.",
                        "Elige el monto: ¥1,000–¥10,000 (incluye los ¥500 de depósito en la normal).",
                        "Mete billetes (casi siempre solo efectivo) y recoge la tarjeta y el recibo.",
                    ),
                ),
                GuideSection(
                    "Comprarla en mostrador",
                    text = "En la oficina \"JR EAST Travel Service Center\" o \"Ticket Office\" (みどりの窓口) di \"Suica, please\" y el monto. Aquí sí suelen aceptar tarjeta para Welcome Suica.",
                ),
                GuideSection(
                    "Usarla",
                    illus = Illus.GATE,
                    steps = listOf(
                        "En el torniquete toca el lector (círculo azul/luz) con la tarjeta al entrar y al salir: se cobra según la distancia.",
                        "Si te falta saldo, la puerta se cierra: ve a la \"Fare Adjustment\" (精算機) junto a la salida, mete la tarjeta y paga la diferencia.",
                        "En autobuses de Tokio se toca al subir (tarifa fija); en otras ciudades al subir y al bajar.",
                        "Saldo máximo: ¥20,000.",
                    ),
                ),
            ),
            links = listOf(
                "JR East en inglés (Suica)" to "https://www.jreast.co.jp/multi/en/",
                "PASMO (oficial)" to "https://www.pasmo.co.jp/visitors/en/",
            ),
            askAi = "¿Qué tarjeta IC conviene hoy a un turista mexicano en Japón (Suica, Welcome Suica, Welcome Suica Mobile, PASMO o la versión turística de PASMO), dónde se venden ahora en Narita y Haneda y cuánto cuestan? Usa fuentes oficiales.",
        ),
        Guide(
            id = "charge",
            country = Country.JP,
            emoji = "🔋",
            title = "Recargar Suica / PASMO",
            subtitle = "En la máquina de la estación o en la konbini",
            sections = listOf(
                GuideSection(
                    "En la máquina de la estación",
                    illus = Illus.CHARGE_MACHINE,
                    steps = listOf(
                        "Cualquier máquina de boletos con el logo IC sirve (también las de color rosa \"Charge\" del metro).",
                        "Toca \"English\" y luego \"Charge\" (チャージ).",
                        "Mete la tarjeta en la ranura o ponla en la bandeja, según la máquina.",
                        "Elige el monto (¥1,000, ¥2,000, ¥3,000, ¥5,000, ¥10,000).",
                        "Mete los billetes. Solo efectivo (no aceptan tarjeta para recargar).",
                        "Recoge la tarjeta: la pantalla muestra el saldo nuevo.",
                    ),
                ),
                GuideSection(
                    "En 7-Eleven, Lawson o FamilyMart",
                    text = "Dile al cajero \"Suica charge, ¥3,000\" (\"suika chāji, sanzen en\"), pon la tarjeta en el lector y paga en efectivo.",
                ),
                GuideSection(
                    "En el iPhone",
                    text = "Suica en Apple Wallet o Welcome Suica Mobile se recargan desde el teléfono con tarjeta. Revisa que tu banco permita cargos en el extranjero.",
                ),
            ),
            askAi = "¿Cómo recargo la Suica/PASMO hoy en Japón y se puede recargar con tarjeta de crédito mexicana en alguna parte? Confirma con fuentes oficiales.",
        ),
        Guide(
            id = "kippu",
            country = Country.JP,
            emoji = "🎫",
            title = "Boleto sencillo de metro o tren",
            subtitle = "Si no tienes tarjeta IC: el mapa de tarifas y la máquina",
            sections = listOf(
                GuideSection(
                    "Encuentra la tarifa",
                    illus = Illus.FARE_MAP,
                    text = "Arriba de las máquinas hay un mapa con todas las estaciones y un número debajo de cada una: es el precio desde donde estás. Busca tu destino (está en inglés y japonés). Si no sabes, compra el más barato y paga la diferencia al salir en la máquina \"Fare Adjustment\".",
                ),
                GuideSection(
                    "En la máquina",
                    illus = Illus.TICKET_MACHINE,
                    steps = listOf(
                        "Toca \"English\".",
                        "Elige \"Ticket\" y el número de personas (adultos / niños).",
                        "Toca el precio que viste en el mapa (o busca la estación en la pantalla, en algunas máquinas).",
                        "Paga con monedas o billetes; recoge boleto y cambio.",
                        "Mete el boleto en el torniquete y recógelo; al salir lo mete de nuevo y se queda.",
                    ),
                ),
                GuideSection(
                    "Pases de día",
                    text = "Si harás 4 o más viajes en un día, revisa el Tokyo Subway Ticket (24/48/72 h para Tokyo Metro y Toei; se vende a turistas en aeropuertos y algunas tiendas con pasaporte).",
                ),
            ),
            askAi = "¿Cuánto cuesta hoy el boleto sencillo más barato del metro de Tokio y el Tokyo Subway Ticket de 24/48/72 horas, y dónde se compra?",
        ),
        Guide(
            id = "shinkansen",
            country = Country.JP,
            emoji = "🚄",
            title = "Shinkansen (tren bala)",
            subtitle = "Boletos en máquina, mostrador o en línea; JR Pass y maletas",
            sections = listOf(
                GuideSection(
                    "Cómo funciona el boleto",
                    illus = Illus.SHINKANSEN_TICKETS,
                    text = "El precio es \"tarifa base\" (乗車券) + \"cargo del Shinkansen\" (特急券). A veces salen en 2 boletos y a veces en uno. En el torniquete mete todos juntos. Ejemplo Tokio → Kioto en Nozomi con asiento reservado: ≈ ¥14,000–14,500, 2 h 15 min.",
                    steps = listOf(
                        "Reservado (指定席 / Reserved): asiento asegurado. Recomendado en fines de semana, vacaciones y con maletas.",
                        "No reservado (自由席 / Non-reserved): un poco más barato, vagones 1–3 aprox.; haz fila en el andén.",
                        "Green Car: primera clase, más espacio.",
                    ),
                ),
                GuideSection(
                    "Comprar en la máquina de boletos reservados",
                    illus = Illus.SHINKANSEN_MACHINE,
                    steps = listOf(
                        "Busca la máquina \"Reserved Seat Ticket Machine\" / 指定席券売機 (pantalla grande, a veces con logo del Shinkansen).",
                        "Toca \"English\" → \"Shinkansen\" o \"Purchase tickets\".",
                        "Elige estación de salida y llegada (por ejemplo Tokyo → Kyoto).",
                        "Fecha, hora y número de personas → elige el tren (Nozomi es el más rápido en Tokaido).",
                        "\"Reserved\" o \"Non-reserved\"; puedes elegir ventanilla en el mapa de asientos (Monte Fuji: lado E/derecho yendo de Tokio a Kioto).",
                        "Paga con tarjeta de crédito o efectivo y recoge todos los boletos y el recibo.",
                    ),
                ),
                GuideSection(
                    "En mostrador o en línea",
                    steps = listOf(
                        "Mostrador: \"Ticket Office\" / みどりの窓口 (Midori no Madoguchi). Escribe en tu teléfono: estación, fecha, hora y \"reserved, window\".",
                        "En línea: smartEX (Tokaido/Sanyo: Tokio–Kioto–Osaka–Hiroshima–Hakata) o JR-EAST Train Reservation (norte: Sendai, Kanazawa…). Pagas con tarjeta y entras con QR o tu IC.",
                    ),
                ),
                GuideSection(
                    "JR Pass",
                    text = "El Japan Rail Pass nacional de 7 días cuesta ≈ ¥50,000. Solo conviene si haces viajes largos (Tokio–Kioto–Hiroshima ida y vuelta). No incluye Nozomi/Mizuho sin un boleto adicional. Compara en la app con el precio de los boletos sueltos.",
                ),
                GuideSection(
                    "Maletas grandes",
                    text = "En el Tokaido-Sanyo-Kyushu Shinkansen, las maletas cuya suma de lados pasa de 160 cm requieren reservar un \"asiento con espacio para equipaje extra grande\" (última fila). Sin reserva cobran un cargo a bordo.",
                ),
            ),
            links = listOf(
                "smartEX (oficial)" to "https://smart-ex.jp/en/",
                "JR-EAST Train Reservation" to "https://www.eki-net.com/en/jreast-train-reservation/Top/index",
                "Japan Rail Pass (oficial)" to "https://japanrailpass.net/en/",
            ),
            askAi = "¿Cuánto cuesta hoy el Shinkansen Nozomi Tokio–Kioto reservado y el JR Pass de 7 días? ¿Me conviene el JR Pass con mi itinerario?",
        ),
        Guide(
            id = "taxi_jp",
            country = Country.JP,
            emoji = "🚕",
            title = "Taxis, GO y Uber en Japón",
            subtitle = "Cuándo convienen y cómo se paga",
            sections = listOf(
                GuideSection(
                    "Lo básico",
                    illus = Illus.TAXI_JP,
                    steps = listOf(
                        "Tokio: ≈ ¥500 al subir (primer 1 km) y ≈ ¥100 cada 255 m. Recargo nocturno 22:00–5:00 (+20 %).",
                        "La puerta trasera izquierda se abre y cierra sola: no la toques.",
                        "No se da propina. Aceptan tarjeta y casi todos Suica/PASMO.",
                        "Apps: GO (la más usada en Japón) y Uber (en Japón llama taxis normales). Las dos aceptan tarjeta mexicana.",
                        "Conviene cuando van 3–4 personas en trayectos cortos, con maletas o después del último tren (~0:00).",
                    ),
                ),
            ),
            askAi = "¿Cuál es hoy la tarifa de taxi en Tokio y conviene GO o Uber para un turista?",
        ),
        Guide(
            id = "money_jp",
            country = Country.JP,
            emoji = "💴",
            title = "Dinero, compras y tax-free",
            subtitle = "Efectivo, tarjetas y el nuevo sistema de reembolso de impuestos",
            sections = listOf(
                GuideSection(
                    "Pagos",
                    steps = listOf(
                        "Tarjetas Visa/Mastercard funcionan en tiendas grandes, konbinis y restaurantes de cadena; lleva efectivo para templos, puestos y pueblos.",
                        "Cajeros: 7-Eleven y Japan Post. Avisa a tu banco que viajas.",
                        "No hay propinas en ningún lado.",
                    ),
                ),
                GuideSection(
                    "Tax-free (desde el 1 de noviembre de 2026)",
                    text = "Ahora pagas el precio con el 10 % de impuesto incluido y te lo reembolsan después: en la tienda registran tu compra con tu pasaporte, y al salir de Japón presentas los productos en la aduana del aeropuerto ANTES de documentar la maleta. El reembolso llega días o semanas después a tu tarjeta o cuenta. Llega más temprano al aeropuerto el día de regreso.",
                ),
                GuideSection(
                    "Impuesto de salida",
                    text = "Desde el 1 de julio de 2026 el International Tourist Tax es de ¥3,000 por persona; normalmente ya viene incluido en el boleto de avión.",
                ),
            ),
            links = listOf("Turismo de Japón – tax-free" to "https://www.mlit.go.jp/kankocho/tax-free/"),
            askAi = "¿Cómo funciona hoy el reembolso del tax-free en el aeropuerto de Narita/Haneda para un turista? ¿Qué debo hacer el día del vuelo de regreso?",
        ),
        Guide(
            id = "etiquette",
            country = Country.JP,
            emoji = "🙇",
            title = "Etiqueta y tips del día a día",
            subtitle = "Lo que conviene saber para no batallar",
            sections = listOf(
                GuideSection(
                    "En la calle y el transporte",
                    steps = listOf(
                        "Escaleras eléctricas: en Tokio te paras a la izquierda; en Osaka a la derecha.",
                        "En el tren no hables por teléfono y habla bajito; la mochila al frente o arriba.",
                        "Casi no hay botes de basura: lleva una bolsita. Las konbinis tienen botes.",
                        "No se come caminando en muchas zonas (en templos y calles comerciales tradicionales).",
                        "Hora pico (7:30–9:30 y 17:30–19:30): evita mover maletas en tren.",
                    ),
                ),
                GuideSection(
                    "Templos y onsen",
                    steps = listOf(
                        "En santuarios: inclínate al pasar el torii, lávate manos y boca en el temizuya.",
                        "Onsen: báñate antes de entrar al agua, sin traje de baño; los tatuajes pueden estar prohibidos (busca \"tattoo friendly\").",
                        "Quítate los zapatos donde veas un escalón o pantuflas en la entrada.",
                    ),
                ),
            ),
            askAi = "Dame tips de etiqueta en Japón para mi itinerario (templos, onsen, restaurantes).",
        ),
        Guide(
            id = "emergency_jp",
            country = Country.JP,
            emoji = "🆘",
            title = "Emergencias en Japón",
            subtitle = "Teléfonos y qué hacer",
            sections = listOf(
                GuideSection(
                    "Teléfonos",
                    steps = listOf(
                        "Policía: 110",
                        "Ambulancia y bomberos: 119",
                        "Japan Visitor Hotline (JNTO, 24 h, en inglés): 050-3816-2787",
                        "Embajada de México en Japón (Tokio): +81 3-3581-1131",
                        "Objetos perdidos: pregunta en la estación (\"Lost and found\") o en la koban (caseta de policía). En Japón casi siempre aparecen.",
                    ),
                ),
                GuideSection(
                    "Sismos",
                    text = "Si tiembla: aléjate de ventanas, protégete bajo una mesa y sigue las instrucciones del personal. Instala la app \"Safety tips\" (Agencia de Turismo de Japón) para alertas en español.",
                ),
            ),
            links = listOf("JNTO – Ayuda y emergencias" to "https://www.japan.travel/en/plan/hotline/"),
            askAi = "¿Cuáles son los teléfonos de emergencia y de la Embajada de México en Japón, y qué hago si pierdo mi pasaporte?",
        ),
        // ------------------------------- México -------------------------------
        Guide(
            id = "aicm",
            country = Country.MX,
            emoji = "🛬",
            title = "Llegar a la CDMX (AICM)",
            subtitle = "Taxi autorizado, Uber/DiDi, Metro y Metrobús",
            sections = listOf(
                GuideSection(
                    "Opciones",
                    steps = listOf(
                        "Taxi autorizado: compra el boleto en las taquillas dentro de la sala (precio fijo por zona) y toma el taxi en el andén indicado.",
                        "Uber / DiDi: pide el viaje y sigue los letreros al punto de abordaje de apps (puerta indicada en la app).",
                        "Metrobús Línea 4: va al Centro Histórico y Buenavista; se paga con la tarjeta de Movilidad Integrada (≈ \$30 MXN desde el aeropuerto).",
                        "Metro Línea 5 \"Terminal Aérea\" (Terminal 1): \$5 MXN. No recomendable con maletas grandes ni en hora pico.",
                    ),
                ),
            ),
            askAi = "¿Cuánto cuesta hoy el taxi autorizado, Uber y el Metrobús del AICM a la colonia de mi hotel?",
        ),
        Guide(
            id = "cdmx_card",
            country = Country.MX,
            emoji = "💳",
            title = "Tarjeta de Movilidad Integrada (CDMX)",
            subtitle = "Metro, Metrobús, Tren Ligero, Cablebús y Ecobici",
            sections = listOf(
                GuideSection(
                    "Comprar y recargar",
                    steps = listOf(
                        "Se compra en las máquinas o taquillas del Metro y Metrobús (la tarjeta cuesta ≈ \$15–20 MXN).",
                        "Recarga en las máquinas de las estaciones (efectivo) o en tiendas autorizadas.",
                        "Metro: \$5 MXN por viaje (sin límite de transbordos dentro del Metro). Metrobús: ≈ \$6 MXN.",
                    ),
                ),
                GuideSection(
                    "Uber y DiDi",
                    text = "Comprueba placas y nombre antes de subir y comparte tu viaje. En hora pico (7–10 y 18–21 h) el tráfico puede duplicar el tiempo y el precio.",
                ),
            ),
            askAi = "¿Cuánto cuesta hoy la tarjeta de Movilidad Integrada y el pasaje del Metro y Metrobús en CDMX?",
        ),
    )

    fun forCountry(c: Country) = all.filter { it.country == c }
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
