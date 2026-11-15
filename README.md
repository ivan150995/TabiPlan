# TabiPlan

App de Android para planear viajes a **Japón** y **México**, hecha para uso personal y sin fines comerciales.

- Lista de lugares con tiempo de visita (o "no sé": la IA lo investiga) e itinerario por días, con IA o automático.
- Pestaña **Hoy**: marcas un lugar como visitado y te dice cómo llegar al siguiente (caminando, tren/metro/autobús, taxi, Uber, DiDi o GO) con precio aproximado de cada opción.
- Guías ilustradas: llegar a Narita/Haneda, Suica/PASMO, máquinas de boletos, Shinkansen, taxis, dinero, etiqueta.
- Checklist para viajar de México a Japón y documentos del viaje (boletos, reservas, QR) guardados en la app.
- Búsqueda de hoteles por descripción con IA, con filtro de hoteles friendly para extranjeros.

## Claves

Ninguna clave va en el código.

- **Claude** (IA): se pone en *Ajustes* dentro de la app.
- **Google Maps** (opcional): en `local.properties` como `MAPS_API_KEY=...` (mapa dentro de la app) o en *Ajustes*. Activa Maps SDK for Android, Routes API y Places API (New).

## Compilar

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/TabiPlan-<versión>-debug.apk`.

## Datos y atribución

- Rutas de transporte público en Japón (y de respaldo en México): [Transitous](https://transitous.org), con datos de los [operadores de transporte](https://transitous.org/sources/) y © [colaboradores de OpenStreetMap](https://www.openstreetmap.org/copyright). Transitous no da tarifas: la app las estima por distancia y operador.
- Precio exacto en Japón (opcional): [NAVITIME Route (totalnavi)](https://rapidapi.com/navitimejapan-navitimejapan/api/navitime-route-totalnavi) por RapidAPI, con clave `NAVITIME_RAPIDAPI_KEY` en `local.properties` o en *Ajustes* (plan gratis: 500 consultas al mes; cada ruta se guarda en el teléfono).
- Rutas en auto, caminando y transporte público en México: Google Routes API. Búsqueda de lugares: Google Places API.
- Precios y reglas de las guías revisados en noviembre de 2026; pueden cambiar.

## Licencia

[MIT](LICENSE)
