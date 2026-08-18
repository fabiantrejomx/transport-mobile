# Reglas del Proyecto — Drivo (App de prueba Google Maps SDK)

## Propósito

App de prueba/aprendizaje para entender el funcionamiento del **SDK de Google Maps** en Android.
No es un producto final ni se publicará en ninguna tienda de aplicaciones. Es un experimento
autocontenido pensado como base de conocimiento para un proyecto más grande.

## Alcance funcional (lo único que hace la app)

1. Una **única pantalla principal** que renderiza un mapa de Google Maps.
2. Al abrir la pantalla, la app **solicita permiso de ubicación** al usuario (`ACCESS_FINE_LOCATION`
   y/o `ACCESS_COARSE_LOCATION`).
3. Si el permiso es concedido, el mapa muestra **en tiempo real la posición del usuario** mientras
   se mueve (ubicación actualizándose de forma continua, sin histórico ni rutas).
4. Un **menú de ajustes simple** con exactamente dos opciones:
   - Habilitar / deshabilitar el permiso de ubicación (o el seguimiento en el mapa).
   - Cambiar el tema: claro / oscuro.

Nada fuera de esta lista debe implementarse salvo que se pida explícitamente.

## Restricciones explícitas (NO hacer)

- ❌ **NO** autenticación / login / usuarios.
- ❌ **NO** base de datos, persistencia local compleja ni backend.
- ❌ **NO** pantallas de prueba, demos o placeholders adicionales.
- ❌ **NO** menús ni navegación compleja (nada de bottom nav, drawers múltiples, deep links, etc.).
- ❌ **NO** tests (unitarios, instrumentados, UI). No se pide cobertura de pruebas en este proyecto.
- ❌ **NO** diseño elaborado ni componentes visuales innecesarios. Minimalismo funcional.
- ❌ **NO** features especulativas "por si acaso" (historial de rutas, geofencing, notificaciones,
  compartir ubicación, etc.) a menos que se solicite explícitamente.

## Stack técnico

- **UI**: Jetpack Compose + **Material 3**.
- **Mapa**: Google Maps SDK for Android (vía `Maps Compose` si es posible, para mantener el código
  simple e idiomático con Compose).
- **Ubicación**: Fused Location Provider API (`com.google.android.gms:play-services-location`) para
  obtener actualizaciones de ubicación en tiempo real.
- **Persistencia mínima**: solo lo estrictamente necesario para recordar la preferencia de tema
  (claro/oscuro) y el estado del toggle de permiso — usar `SharedPreferences` / `DataStore`
  simple, sin base de datos.
- **Arquitectura**: la más simple posible que resuelva el caso de uso. No sobre-diseñar (evitar
  capas, patrones o abstracciones que no aporten valor inmediato a esta app pequeña).

## Principios de desarrollo para este proyecto

- Priorizar código simple, legible y directo sobre "buenas prácticas" pensadas para apps grandes.
- No añadir dependencias, librerías o herramientas que no sean necesarias para el alcance descrito.
- Si una solicitud implica añadir complejidad no mencionada aquí (tests, DB, auth, más pantallas,
  arquitectura compleja), se debe confirmar con el usuario antes de implementarla.
- El objetivo final es **aprender el funcionamiento del SDK de Google Maps**, no construir un
  producto pulido.
