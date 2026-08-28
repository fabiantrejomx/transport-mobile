# Drivo — Contexto del proyecto

App de transporte privado (competidor directo de InDrive/Uber/Didi) para Android nativo. Este archivo resume las decisiones ya tomadas para que cualquier sesión de Claude Code parta con el contexto correcto sin tener que repetirlo.

**Documentos de referencia (léelos antes de generar código):**
- `docs/analisis-inicial.md` — análisis funcional y de arquitectura completo del proyecto.
- `docs/prototipo-navegable.html` — prototipo interactivo con los flujos de pantallas de pasajero y conductor. Úsalo como referencia de lógica de navegación y contenido de cada pantalla (no como referencia visual pixel-perfect: es HTML/CSS, no Views/XML de Android).

---

## Stack tecnológico

- **Lenguaje UI:** Java (no Kotlin). Si en algún punto una funcionalidad de Material 3 resultara inviable en Java/Views, avisar antes de cambiar a Kotlin — no asumir el cambio.
- **Sistema de UI:** Views/XML clásico con `com.google.android.material:material` (1.9+) para Material 3. No usar Jetpack Compose.
- **Backend (fase futura):** Java Spring Boot + PostgreSQL. No existe aún — no generar llamadas a un backend propio todavía.
- **Fase actual (prototipo):** toda la lógica de datos vive en Firebase (Auth, Firestore/Realtime Database).
- **Tiempo real y push:** Firebase (Realtime Database + Firebase Cloud Messaging). Esta pieza se queda en Firebase incluso después de migrar el resto al backend propio.
- **Mapas y tráfico:** Google Maps Platform — Maps SDK for Android (visualización) + Routes API con `TRAFFIC_AWARE_OPTIMAL` (cálculo de ETA/precio con tráfico en tiempo real).
- **Pagos:** efectivo + transferencia interbancaria vía CLABE usando tecnología STP.

## Arquitectura obligatoria: capa de repositorios

Toda la lógica de datos debe pasar por interfaces de repositorio, nunca acceder a Firebase directamente desde ViewModels/Activities/Fragments:

```
Interfaz (ej. TripRepository, UserRepository, AuthRepository, AddressRepository)
   → Implementación Firebase (única implementación por ahora)
```

Esto es intencional: permite migrar a Spring Boot más adelante sustituyendo solo la implementación, y sirve de referencia estable al portar la lógica de negocio a Swift/iOS con ayuda de Claude Code en una fase posterior. No generar código que acople la UI directamente al SDK de Firebase.

## Sistema de diseño

- **Lenguaje de diseño:** Material Design 3 (Material You), con soporte nativo de tema claro/oscuro (`lightColorScheme` / `darkColorScheme`).
- **Paleta de marca:**
  - Primario (claro): `#0F2C4C` — azul marino profundo. Primario (oscuro): `#A9C7E8`.
  - Secundario/acento (claro): `#B08D2B` — dorado discreto, usado en CTAs de negociación/confirmación. Secundario (oscuro): `#E0BE63`.
  - Superficie clara: `#FFFFFF` / fondo `#F6F8FA`. Superficie oscura: `#142130` / fondo `#0D1620` (evitar negro puro).
  - Éxito: `#2E7D5B` (claro) / `#6FD3A6` (oscuro), usado para confirmaciones y el punto de origen en el mapa.
- **Tipografía:** Roboto, siguiendo la escala tipográfica de Material 3 (Display/Headline/Title/Body/Label).
- El detalle completo de tokens está en `docs/prototipo-navegable.html` (los mismos valores hex están embebidos ahí).

## Notificaciones — comportamiento esperado por plataforma

- **Android:** control total vía `NotificationChannel`, incluyendo selección de tono del sistema mediante `RingtoneManager`.
- **iOS (fase futura, fuera de alcance por ahora):** no habrá selector de tonos del sistema (restricción de Apple, no de la app); sí sonidos personalizados propios empaquetados, y uso de `interruptionLevel: .timeSensitive` para solicitudes de viaje.

## Referencia UX externa

Se recibió un set de mockups de referencia (`https://librego.tiiny.site/`, plantilla nombrada "LibreGO" por el diseñador — el nombre del proyecto sigue siendo **Drivo**). Solo aporta estructura de pantallas y contenido/copy, no diseño visual — el diseño visual sigue el sistema de marca definido abajo, no el de esa referencia.

## Flujos principales a implementar (ver `docs/prototipo-navegable.html` para el detalle interactivo pantalla por pantalla)

**Pasajero:** Splash → Auth/OTP (SMS, sin contraseña) → completar perfil (nombre + correo opcional) → Home (mapa, direcciones guardadas, **añadir parada**) → confirmar tarifa (**slider ajustable 80%-140%** sobre la tarifa base, pago en efectivo) → **Subasta** (el viaje se difunde a varios conductores a la vez; los que lo aceptan —contraofertando o no— aparecen en una **lista simultánea** con nombre, auto, calificación y precio, ordenada de menor a mayor precio; el pasajero elige con quién viaja o descarta a los que no le sirvan. Cada conductor oferta una sola vez y **no puede cambiar su oferta**; la última palabra siempre es del pasajero. **La subasta entera dura 180 s** (`search_ttl_seconds`) y es **un solo reloj**: las ofertas vencen cuando vence la búsqueda, nunca antes ni después) → viaje activo, con las opciones cambiando por fase: **mientras el conductor viene y mientras espera** hay **llamar/mensaje** y **cancelar viaje** (siempre con alert de confirmación); **al iniciar el viaje** esos tres desaparecen y quedan **compartir viaje**, **S.O.S.** y el precio acordado, más la **ruta del viaje dibujada en el mapa** con el coche encima, para que el pasajero pueda contrastar por dónde va. Cuando el conductor marca "llegué al punto" corre un **cronómetro de cortesía de 5 minutos** (con barra) para que el pasajero salga al vehículo — visible igual en las dos apps y anclado en `driver_arrived_at`, la hora del servidor; agotarlo todavía no tiene consecuencias definidas → finalizar → recibo (total pagado + calificación + comentario opcional) → vuelve a inicio. Navegación inferior: Inicio / Viajes (historial) / Perfil.

**Conductor:** registro en **7 pasos obligatorios** (tipo de vehículo Auto Particular/Taxi, información personal + foto, licencia, identificación oficial, datos del vehículo + año + foto exterior, tarjeta de circulación con manejo de "no soy el propietario" → carta de autorización + INE del dueño, póliza de seguro, verificación de seguridad) → Home (ganancias del día visibles + botón grande Conectarse/Desconectarse) → solicitud entrante (distancia al pasajero + distancia total del viaje; **contraoferta con incrementos rápidos +$10/+$20/+$50**, o Ignorar/**Ofertar** — nunca "Aceptar": postularse **no** gana el viaje, entra a la subasta junto con los demás conductores y el pasajero decide) → **la oferta se va a un banner sobre el mapa** y el modal vuelve al radar: ofertar no bloquea al conductor, que puede tener hasta **3 ofertas vivas a la vez** (`max_live_offers_per_driver`) y sigue recibiendo solicitudes. Cuando el pasajero lo elige, el banner lo dice y **el viaje se abre solo** (`GET /driver/current-ride`, no solo el push); las demás ofertas suyas se cancelan. Con un viaje asignado deja de recibir solicitudes hasta cerrarlo → viaje activo (**deep link "Navegar con Waze"** en vez de navegación propia; paso explícito **"Llegué al punto"** separado de iniciar/finalizar el viaje; llamar/mensaje al pasajero solo hasta que el viaje arranca, y el mismo **cronómetro de espera de 5 minutos** desde que marca su llegada) → finalizar y ver ganancia. Navegación inferior: Inicio / Ganancias / Perfil.

## Convenciones de trabajo

- Pide implementación por pantalla o por flujo pequeño, no la app completa de una sola vez.
- Tras cada pantalla, verificar que compila y se ve correctamente en el emulador antes de continuar con la siguiente.
- Cualquier decisión de stack no cubierta aquí debe consultarse antes de asumir un cambio (ej. no introducir Kotlin, no introducir un backend propio, no cambiar el proveedor de mapas).