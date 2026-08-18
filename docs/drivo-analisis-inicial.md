# Drivo — Análisis Inicial del Proyecto

App de transporte privado (competidor directo de InDrive), desarrollo inicial para **Android nativo**.

---

## 1. Resumen del concepto

Plataforma donde el pasajero define punto A y B, sugiere un precio y publica el viaje. El conductor ve el viaje en tiempo real y puede **aceptar, ignorar, declinar o negociar** el precio sugerido. Registro único de usuario que puede operar como pasajero, conductor, o ambos.

---

## 2. Roles y flujo de usuario

### Pasajero
- Define punto A (origen) y punto B (destino), con opción de **añadir paradas adicionales**
- Sugiere un precio para el viaje, ajustable dentro de un **rango de 80%-140%** sobre la tarifa base calculada
- Publica el viaje y ve conductores respondiendo en tiempo real, uno a la vez (con contador de disponibles, ej. "1/3")
- Si el conductor asignado hace una contraoferta, la ve y decide **aceptar o rechazar esa oferta puntual** (no una lista de ofertas simultáneas)
- Durante el viaje: llamar, mensaje, compartir viaje en vivo, botón S.O.S.
- Historial de viajes, direcciones guardadas (casa, trabajo, favoritos)
- Recibo final con total pagado y calificación + comentario opcional al conductor

### Conductor
- Registro en **7 pasos obligatorios**: tipo de vehículo (Auto Particular / Taxi), información personal + foto de perfil, licencia de conducir, identificación oficial, información del vehículo (año, foto exterior), tarjeta de circulación (con manejo de caso "no soy el propietario" → carta de autorización + INE del dueño), póliza de seguro, verificación de seguridad final
- Alterna estado disponible / no disponible (Conectarse/Desconectarse); ve sus ganancias del día en la misma pantalla
- Recibe solicitudes de viaje cercanas en tiempo real, con distancia al pasajero y distancia total del viaje
- Acepta, ignora, o contraoferta con incrementos rápidos (+$10 / +$20 / +$50) sobre el precio sugerido
- Navega al punto de recogida y destino delegando la navegación turn-by-turn a **Waze** (deep link externo, no navegación propia dentro de la app)
- Marca explícitamente "Llegué al punto" como paso separado de iniciar el viaje
- Panel de viajes realizados / ganancias
- Es calificado por el pasajero

### Rol dual
Un mismo usuario puede tener ambos perfiles habilitados y alternar entre ellos desde la misma cuenta.

---

## 3. Módulos funcionales (core)

| Módulo | Descripción |
|---|---|
| **Auth & Onboarding** | Registro único (correo/teléfono), verificación, selección/alternancia de rol |
| **Perfil** | Nombre, foto, datos de vehículo (conductor), métodos de cobro/pago |
| **Mapa y geolocalización** | Mapa en vivo, autocompletado de direcciones, cálculo de ruta/tiempo/distancia con tráfico (Google Maps Platform) |
| **Motor de viajes** | Publicación, matching por cercanía, paradas adicionales, negociación de precio 1 a 1 (rango 80%-140% / incrementos rápidos), estados del viaje |
| **Navegación del conductor** | Deep link a Waze para navegación turn-by-turn (no se construye motor de navegación propio) |
| **Tiempo real** | Ubicación en vivo del conductor, notificación instantánea de solicitudes (Firebase) |
| **Chat / llamada in-app** | Comunicación directa pasajero–conductor |
| **Direcciones guardadas** | Casa, trabajo, favoritos |
| **Historial** | Viajes anteriores, recibos |
| **Pagos** | Efectivo + transferencia interbancaria (CLABE vía STP) |
| **Calificaciones** | Bidireccional (pasajero ↔ conductor), con comentario opcional |
| **Seguridad** | Botón S.O.S., compartir viaje en vivo, verificación visible del conductor |
| **Notificaciones push** | Solicitudes, estados de viaje, mensajes |
| **Backoffice (futuro)** | Aprobación de conductores, disputas — fuera del alcance de la app móvil |

---

## 4. Stack tecnológico

| Capa | Tecnología |
|---|---|
| UI Android | **Java** + Views/XML — Material Components (Material 3 no requiere Kotlin/Compose) |
| Backend (futuro) | Java Spring Boot + PostgreSQL |
| Tiempo real (prototipo y futuro) | Firebase (Realtime Database/Firestore) |
| Push notifications | Firebase Cloud Messaging |
| Mapas y tráfico | Google Maps Platform — SDK nativo de Google Maps para Android + Routes API (ETA/precio con tráfico en tiempo real) |
| Pagos | Efectivo + transferencias CLABE vía STP (proceso ya conocido de otros proyectos) |

### Nota técnica — Java y Material 3
Material 3 tiene soporte completo en el sistema de Views clásico vía `com.google.android.material:material` (1.9+): temas `Theme.Material3.*`, dynamic color, tema claro/oscuro nativo. Nada de esto requiere Compose ni Kotlin — **Java es totalmente viable** para el lenguaje de diseño solicitado.

---

## 5. Arquitectura del prototipo — preparado para migrar

**Fase prototipo:** toda la lógica corre sobre Firebase (auth, datos de viajes, usuarios, perfiles, realtime, push).

**Fase producción (objetivo):** backend propio en Spring Boot + PostgreSQL asume todo excepto:
- Realtime (ubicación en vivo)
- Push Notifications (FCM)

**Recomendación de diseño para no rehacer trabajo:** introducir desde el día uno una **capa de repositorio/abstracción** en la app Android:

```
Repositorio (interfaz) → Implementación Firebase (ahora)
                       → Implementación API Spring Boot (futuro)
```

Ejemplos: `AuthRepository`, `TripRepository`, `UserRepository`, `AddressRepository`. La UI y la lógica de negocio nunca llaman a Firebase directamente, sino a la interfaz — así el día de la migración solo se sustituye la implementación, sin tocar pantallas ni lógica de presentación.

---

## 6. Sistema de diseño — Material 3

- **Lenguaje de diseño:** Material Design 3 (Material You), lo más reciente publicado por Google.
- **Soporte de tema:** claro/oscuro nativo mediante `lightColorScheme` / `darkColorScheme`.
- **Paleta base propuesta** (para transmitir seguridad, profesionalismo, confianza y elegancia):
  - **Primario:** azul marino / petróleo profundo — mismo territorio cromático que usan bancos y plataformas serias de transporte.
  - **Secundario / acento:** dorado o verde azulado discreto para CTAs (aceptar viaje, confirmar), evitando el amarillo genérico de la competencia.
  - **Superficie (modo oscuro):** grises cálidos, evitando negro puro, para mantener elegancia.

---

## 7. Estrategia multiplataforma — Android nativo → Swift vía Claude Code, vs. React Native

**Decisión:** mantener el plan original — Android nativo primero, y usar Claude Code para replicar la lógica de negocio a Swift/iOS más adelante, en lugar de ir directo con React Native.

Razones clave:
- La causa raíz de los problemas de notificaciones que tuviste con Ionic/Capacitor es la calidad de la capa de abstracción del plugin, no el enfoque híbrido en sí — pero eso significa que React Native reintroduce ese mismo tipo de riesgo (dependencia de librerías de terceros como Notifee), aunque con mejores herramientas disponibles.
- La **capa de repositorios/interfaces** definida en la sección 5 (pensada originalmente para la migración de Firebase a Spring Boot) también sirve como puente Android→iOS: la lógica de negocio queda desacoplada de la UI, lo que hace que portar de Java a Swift con ayuda de Claude Code sea un trabajo más mecánico y verificable.
- Cada plataforma se ve 100% nativa (Material 3 en Android, Human Interface Guidelines en iOS), sin comprometer diseño para que "funcione en ambos" como exigiría un lenguaje de diseño compartido en RN.
- Un mapa con tracking en tiempo real (crítico en una app de transporte) tiene mejor rendimiento y menor consumo de batería en nativo puro que a través del puente JS de RN, sobre todo en sesiones largas y continuas (turnos de varias horas).
- El SDK de Google Maps para Android usado en RN (`react-native-maps` u otro wrapper) sigue siendo una capa comunitaria adicional sobre el SDK nativo — mismo tipo de riesgo de soporte que se identificó también al evaluar Mapbox en RN.

---

## 8. Notificaciones push — comportamiento por plataforma

### Android
Control total vía `NotificationChannel`/`NotificationManager`, incluyendo prioridad, sonido personalizado, y acceso al selector de tonos del sistema (`RingtoneManager`) para que el usuario elija entre los tonos del dispositivo.

### iOS — limitaciones de plataforma (no de framework)
- **No es posible** que el usuario elija un tono del sistema (como en FaceTime o Calendario): ese selector usa frameworks privados exclusivos de las apps propias de Apple. Ninguna app de terceros —nativa, RN o Flutter— puede acceder a él, ni con entitlement.
- **Sí es posible** ofrecer sonidos personalizados propios de Drivo empaquetados en la app, con una pantalla de ajustes propia para elegirlos (igual que hace WhatsApp).
- **Time Sensitive Notifications** (`interruptionLevel: .timeSensitive`): recomendado para solicitudes de viaje/negociación de precio. No requiere aprobación especial de Apple, y aparece automáticamente como ajuste "Notificaciones urgentes" en Ajustes del sistema una vez usado (como en Duolingo).
- **Critical Alerts** (`interruptionLevel: .critical`): rompe el modo silencio/No Molestar por completo, pero requiere una entitlement aprobada caso por caso por Apple, reservada casi exclusivamente para salud y seguridad pública — poco realista de conseguir para una app de transporte.

---

## 9. Mapas: decisión final — Google Maps Platform sobre Mapbox

Se evaluaron ambos SDKs nativos. Mapbox ofrecía ventajas en costo a escala, personalización visual (Mapbox Studio) y soporte offline. Sin embargo, dado que el costo no es una restricción para el proyecto y la prioridad es **precisión en el cálculo del cobro considerando tráfico en tiempo real**, se optó por Google Maps Platform:

- **Tráfico en tiempo real**: el modelo de Google se alimenta de la telemetría de miles de millones de dispositivos Android, ofreciendo la predicción de congestión más precisa disponible — clave para el precio sugerido del viaje.
- **Routes API** (modo `TRAFFIC_AWARE_OPTIMAL`): motor de cálculo de ETA/precio considerando tráfico en vivo.
- **Datos de direcciones/Places**: geocodificación y autocompletado generalmente más completos en Latinoamérica, reduciendo fricción al capturar origen/destino.
- **Cloud-based Map Styling**: suficiente para aplicar la paleta de marca (seguridad/confianza/elegancia) definida en la sección 6, aunque con menor control fino que Mapbox Studio — una cesión aceptable dado que el costo ya no es la prioridad.
- **Contrapartida asumida**: soporte offline más limitado que Mapbox — a vigilar si Drivo opera en zonas de conectividad irregular.

---

## 11. Referencia UX — mockups compartidos por el cliente

Se recibió un set de mockups de referencia (`https://librego.tiiny.site/`, plantilla nombrada "LibreGO" por quien la diseñó — el nombre del proyecto se mantiene como **Drivo**). Aporta el detalle de pantalla-por-pantalla que ya se incorporó en las secciones 2 y 3. Mapa de pantallas de referencia:

**Pasajero:** Splash → Auth/OTP → Completar perfil → Inicio (mapa + direcciones guardadas + añadir parada) → Tarifa (slider 80%-140%) → Radar (buscando conductor, contraoferta 1 a 1) → Viaje (mapa en vivo, S.O.S., compartir) → Recibo (calificación + comentario).

**Conductor:** Registro (7 pasos) → Home conductor (ganancias del día + Conectarse/Desconectarse) → Solicitud entrante (distancia, contraoferta rápida +$10/+$20/+$50) → Viaje (Navegar con Waze, "Llegué al punto").

Esta referencia es solo de estructura/flujo y contenido textual — el diseño visual (color, tipografía, espaciado) sigue el sistema de marca Material 3 ya definido en la sección 6, no el de la referencia. Si se comparten capturas visuales puntuales de los mockups, se puede afinar aún más la fidelidad del prototipo.

---

## 12. Prototipado sin Figma (herramientas gratuitas/open source)

Dado que Figma es de pago, alternativas que corren sobre Claude Code:

1. **Excalidraw + skill "Pencil MCP"** — bocetos gratuitos en Excalidraw convertidos a mockups de alta fidelidad vía MCP.
2. **`open-design` / `open-codesign`** (alternativas open source al producto de pago "Claude Design") — apps de escritorio local-first, MIT, que usan la sesión de Claude Code como motor de diseño y generan prototipos reales (HTML/React) de pantallas móviles, exportables, sin costo adicional.

**Flujo sugerido:** bocetar pantallas en Excalidraw → generar prototipo navegable en HTML con `open-design`/`open-codesign` aplicando la paleta Material 3 definida arriba.

---

## 13. Próximos pasos sugeridos

1. Definir wireframes de pantallas clave (registro, mapa/publicar viaje, negociación, viaje en curso, perfil).
2. Prototipar en herramienta open source (sección 12).
3. Modelar entidades base en Firebase (usuarios, viajes, ofertas, direcciones, paradas).
4. Definir contratos de las interfaces de repositorio (para migración futura y para el puente Android→iOS).
5. Diseñar el flujo de negociación de precio 1 a 1 (rango 80%-140%, contraoferta con incrementos rápidos, expiración de oferta) integrando Routes API para el cálculo con tráfico.
6. Configurar credenciales de Google Maps Platform (Maps SDK for Android + Routes API) y definir el Map ID con el estilo de marca vía Cloud-based Map Styling.
7. Implementar Time Sensitive Notifications para solicitudes de viaje en iOS, y capa de sonidos personalizados propios en ambas plataformas.
8. Definir el flujo de registro de conductor de 7 pasos, incluyendo validación de propiedad del vehículo (carta de autorización + INE del dueño cuando aplique).
9. Integrar deep link a Waze para la navegación del conductor.
10. ~~Activar Places API (New) y añadir Places SDK for Android~~ — hecho: `SetDestinationActivity` y `AddEditAddressActivity` usan el overlay de autocompletado de `com.google.android.libraries.places:places` (`Autocomplete.IntentBuilder`, modo overlay) vía `PlacesAutocompleteService`, inicializado con `Places.initializeWithNewPlacesApiEnabled(...)` en `DrivoApplication`. Pendiente: sustituir `AuthRepository`/`UserRepository`/`AddressRepository` mock (SharedPreferences) por implementaciones Firebase reales cuando exista backend — ver el diseño de interfaces en `data/repository/`.
