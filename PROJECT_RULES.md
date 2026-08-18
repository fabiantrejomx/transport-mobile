# PROJECT_RULES.md — nota histórica

Este archivo describía originalmente una app de prueba/aprendizaje aislada, limitada a una sola
pantalla de mapa sin autenticación ni backend. Ese alcance quedó **obsoleto**: fue el punto de
partida del proyecto, antes de que se decidiera construir Drivo como producto completo.

**La fuente de verdad actual del proyecto es [`CLAUDE.md`](./CLAUDE.md)** — stack, arquitectura de
repositorios, sistema de diseño, flujos de pasajero/conductor y convenciones de trabajo. Este
archivo ya no impone restricciones propias; cualquier regla aquí que contradiga `CLAUDE.md` no
aplica.

## Qué queda de la fase original

- `MainActivity` (Kotlin + Jetpack Compose) es la pantalla de prueba del SDK de Google Maps de esa
  primera fase. Sigue en el código como referencia, **no es la pantalla de inicio** (el launcher
  real es `ui.auth.SplashActivity`) y no forma parte de ningún flujo de Drivo. Las dependencias de
  Compose en `app/build.gradle.kts` existen solo por esa pantalla.
- El resto del proyecto (Java, Views/XML, Material 3) sigue el stack definido en `CLAUDE.md`.

## Estado real de la integración de datos

Drivo ya tiene autenticación, backend real (`transport-api`, desplegado en Cloud Run) y Firebase
(`transport-mx`) en proceso de integración — ver `CLAUDE.md` para el detalle del backend y del
sistema de diseño. Antes de este trabajo, la capa de datos vivía en repositorios mock; se está
migrando por fases a las implementaciones reales.
