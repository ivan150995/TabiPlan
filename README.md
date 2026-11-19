# TabiPlan

App de Android para planear viajes a **Japón** y **México**, hecha para uso personal y sin fines comerciales.

- Lista de lugares con tiempo de visita (o "no sé": la IA lo investiga) e itinerario por días, con IA o automático.
- El itinerario respeta horarios de apertura, última entrada y días de cierre; festivos de Japón y México (calculados) y temporadas altas (Golden Week, Obon, sakura, Semana Santa, Día de Muertos…); comidas en horario normal pero fuera de la hora pico; lugares llenos temprano y en días normales; miradores al atardecer; y horas fijas que tú pongas (avisa si no da tiempo por el traslado y la estancia anterior).
- Pestaña **Hoy**: marcas un lugar como visitado y te dice cómo llegar al siguiente (caminando, tren/metro/autobús, taxi, Uber, DiDi o GO) con precio aproximado de cada opción.
- Guías ilustradas: llegar a Narita/Haneda, Suica/PASMO, máquinas de boletos, Shinkansen, taxis, dinero, etiqueta.
- Checklist para viajar de México a Japón y documentos del viaje (boletos, reservas, QR) guardados en la app.
- Boletos digitales por lugar: marcas que un lugar necesita boleto y le adjuntas el PDF o la captura del QR. Se ve dentro de la app sin internet, a pantalla completa y con el brillo al máximo, desde Lugares, Hoy o Documentos.
- Plan de hospedaje por ciudades (p. ej. Tokio → Osaka → Kioto → Tokio): eliges las noches de cada estancia y las siguientes se recorren solas; avisa de noches sin hotel o con dos. Para cada estancia la IA busca hoteles según lo que describes y lo que visitas esos días (filtro friendly para extranjeros, cerca de estación, etc.).
- Precio y disponibilidad reales en **Booking** (abre la app si la tienes) y Google Hoteles, con las fechas y personas del viaje ya puestas. Booking no tiene una API pública para apps personales (su Demand API es solo para socios afiliados aprobados), así que la app usa enlaces de búsqueda.

## Claves

Ninguna clave va en el código.

- **Claude** (IA): se pone en *Ajustes* dentro de la app.
- **Google Maps** (opcional): en `local.properties` como `MAPS_API_KEY=...` (mapa dentro de la app) o en *Ajustes*. Activa Maps SDK for Android, Routes API y Places API (New).

## Compilar

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/TabiPlan-<versión>-debug.apk`. Pruebas del planificador: `./gradlew testDebugUnitTest`.

## Datos y atribución

- Rutas de transporte público en Japón (y de respaldo en México): [Transitous](https://transitous.org), con datos de los [operadores de transporte](https://transitous.org/sources/) y © [colaboradores de OpenStreetMap](https://www.openstreetmap.org/copyright). Transitous no da tarifas: la app las estima por distancia y operador.
- Precio exacto en Japón (opcional): [NAVITIME Route (totalnavi)](https://rapidapi.com/navitimejapan-navitimejapan/api/navitime-route-totalnavi) por RapidAPI, con clave `NAVITIME_RAPIDAPI_KEY` en `local.properties` o en *Ajustes* (plan gratis: 500 consultas al mes; cada ruta se guarda en el teléfono).
- Rutas en auto, caminando y transporte público en México: Google Routes API. Búsqueda de lugares y su horario semanal: Google Places API (el horario se pide solo al elegir un lugar).
- Precios y reglas de las guías revisados en noviembre de 2026; pueden cambiar.

## Licencia

[MIT](LICENSE)
