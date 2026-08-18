# Deuda técnica

Lista **viva** de deuda técnica consciente: decisiones de aplazar algo, con su justificación,
su impacto a evaluar, y la acción pendiente. Referenciada desde `CLAUDE.md`. **Revisar antes de
cualquier despliegue a producción.**

Cada entrada nueva se agrega acá, no solo en un commit o en un comentario de código — esta es
la fuente única, para que la deuda técnica no quede dispersa en notas sueltas.

---

## 1. [RESUELTO — 2026-08-17] Vulnerabilidades de seguridad en Next.js 14 (5 advisories ALTAS)

**Qué son:** `npm audit` (`frontend/`) reporta **5 vulnerabilidades de severidad ALTA**,
ninguna introducida por código propio — todas rastreables a la cadena de `next@14.2.35` (la
versión fijada por el stack, ver `CLAUDE.md` → Stack) y sus dependencias empaquetadas o
acopladas a su ciclo de release:

| Entrada de `npm audit` | Ámbito | Detalle |
|---|---|---|
| `next` (14.2.35) | **Runtime, producción** | ~19 advisories de Next.js agrupadas bajo esta entrada (rango vulnerable `9.3.4-canary.0 – 16.3.0-preview.10`); rollup de severidad ALTA. Ver desglose por tipo abajo. |
| `postcss` (vendido dentro de `next/node_modules/postcss`, ≤8.5.22) | Build, empaquetado dentro de `next` | 4 advisories de PostCSS (XSS en stringify, lectura arbitraria de archivos y path traversal vía `sourceMappingURL`). Solo se resuelve junto con `next` — no es el `postcss` de nivel superior que usa Tailwind. |
| `glob` (10.2.0–10.4.5) | **Dev-only** (herramienta de ESLint) | Inyección de comandos en su CLI (`-c`/`--cmd`, [GHSA-5j98-mcp5-4vw2](https://github.com/advisories/GHSA-5j98-mcp5-4vw2)) — solo explotable si algo invoca el CLI de `glob` con entrada no confiable, lo que no ocurre en este proyecto. |
| `@next/eslint-plugin-next` | Dev-only | Depende de la versión vulnerable de `glob`. |
| `eslint-config-next` | Dev-only | Depende de `@next/eslint-plugin-next`. |

Fuente: `npm audit` / `npm audit --json` en `frontend/`, ejecutado 2026-08-07. Resumen del
propio `npm audit`: *"5 high severity vulnerabilities"*, 0 moderadas/críticas en el rollup
final (aunque algunas de las ~19 advisories individuales de `next` son moderadas por sí
solas — `npm audit` reporta el peor caso por paquete).

**Por qué no se abordó ahora:** el único `fixAvailable` para las cinco entradas es un salto de
versión **mayor**: `npm audit fix --force` instalaría `next@16.3.0` (y, para `glob`,
`eslint-config-next@16.3.0`, cuya propia versión mayor arrastra el `glob` corregido — no hay
manera de resolver `glob` sin también saltar la versión de `eslint-config-next`). El stack
fijado en `CLAUDE.md` es **Next.js 14**; saltar a Next 16 es un cambio de stack — no un fix
incremental — y excede el alcance del desarrollo funcional actual (breaking changes de App
Router / Server Actions / React aún no evaluados).

**Impacto/exposición a evaluar** — cruzado contra la configuración real de esta app
(`next.config.mjs` está vacío: sin `remotePatterns`, `rewrites`, `i18n` ni servidor custom; el
frontend usa exclusivamente **App Router**, no Pages Router; `next/image` no se usa en ninguna
pantalla):

- **DoS (denegación de servicio)** — Image Optimizer, render de Server Components, payload de
  Server Actions, crecimiento sin límite del caché de `next/image`.
  *Exposición:* la app no usa `next/image` (ese vector no aplica), pero sí usa Server
  Actions/App Router — el resto de los vectores de DoS de renderizado sigue aplicando.
- **SSRF (Server-Side Request Forgery)** — WebSocket upgrades, Server Actions en servidor
  custom, `rewrites` con host controlado por el atacante.
  *Exposición:* no hay servidor custom ni `rewrites` configurados hoy; el vector de WebSocket
  upgrades queda por confirmar según cómo se exponga la app en producción (proxy/CDN delante,
  aún no definido — Etapa actual = desarrollo).
- **Envenenamiento/confusión de caché** (cache poisoning) — respuestas de Server Components,
  redirects de Middleware/Proxy.
  *Exposición:* depende de si hay una capa de caché/CDN delante en producción (no decidido
  todavía).
- **XSS** — bypass de nonces CSP, scripts `beforeInteractive` con input no confiable; más las
  de PostCSS (stringify sin escapar, lectura arbitraria de archivos vía `sourceMappingURL`).
  *Exposición:* la app no acepta CSS ni scripts de usuarios finales — superficie baja hoy, pero
  no nula si en algún momento se acepta contenido enriquecido.
- **HTTP request smuggling** en `rewrites`.
  *Exposición:* no hay `rewrites` configurados hoy.
- **Middleware/Proxy bypass en Pages Router con i18n.**
  *Exposición:* **no aplica** — este proyecto usa exclusivamente App Router, sin `pages/` ni
  i18n configurado.
- **Divulgación de información** — endpoints internos de Server Functions expuestos sin
  autenticar.
  *Exposición:* a confirmar contra el middleware de autenticación real (`middleware.ts`
  protege todas las rutas salvo `/login` y `/api/auth/*`, pero eso cubre rutas de página, no
  necesariamente endpoints internos de Server Functions — falta verificación específica).
- **`glob` / herramientas de ESLint (dev-only):** sin exposición en producción — no se
  empaqueta en el build de producción, no corre en el servidor ni en el navegador del usuario
  final.

**Acción requerida — histórica, ya resuelta (ver abajo):**
1. Aceptar el riesgo con justificación documentada, o
2. Planificar el upgrade a Next 16 como tarea de stack propia.

**Resuelta — opción 2, 2026-08-17.** Sondeada primero en una copia aislada del proyecto (fuera
del árbol real, sin tocar nada — en ese momento el proyecto ni siquiera tenía git; el
init + commit base son de la misma fecha), reportada en detalle (diff del codemod, build,
suite, OIDC en vivo, inventario de compatibilidad, `npm audit`), y aplicada en firme recién con
la confirmación explícita del resultado del sondeo — como su propio commit, separado del commit
base, revertible. Subido a **`next@16.3.1` + `react`/`react-dom@^19.2.8`** (Active LTS, estable;
`eslint-config-next@16.3.1` en sync). Detalle completo del cambio de código (el único real: el
codemod `next-async-request-api` en 4 archivos, `params`/`searchParams` ahora `Promise`) y de
todo lo mecánico (rename `middleware.ts`→`proxy.ts`, migración de ESLint a flat config con
`eslint` fijado en `^9.39.5` — **no** `^10`, que rompe `eslint-plugin-react` de
`eslint-config-next@16` — y `tsconfig.json` renormalizado por `next build`) en
`docs/frontend.md` §20.

**Las 5 vulnerabilidades ALTAS de este ítem quedaron en 0** con la subida (`next`, `postcss`
empaquetado, `glob`, `@next/eslint-plugin-next`, `eslint-config-next` — todas resueltas por la
propia versión mayor). Apareció una **6ª, no relacionada** (`nanoid`, transitiva de `postcss`,
detectada recién en el sondeo — no estaba en el análisis original del 2026-08-07): resuelta con
`npm audit fix` normal, sin salto mayor. **`npm audit` en `frontend/`: 0 vulnerabilidades.**

Verificado contra el stack real, no solo `next build`: imagen Docker reconstruida, login OIDC
completo ejercido contra el Keycloak real con **ambos roles** (`dev.qa`/ADMINISTRADOR,
`dev.qa.operador`/OPERADOR) — sesión, `accessToken`, y una llamada autenticada al backend con
200. Ver §20 de `docs/frontend.md` para el detalle punto por punto.

**Deuda nueva, abierta por esta misma subida — ver ítem 7.**

---

## 2. [RESUELTO — P4, 2026-08-07] Contador de `PENDIENTE_UF` en el resultado de confirmar CSV

`ImportacionCsvRespuestaDto` (resultado de `POST /importaciones/confirmar`) no exponía un
contador de cuántas filas importadas quedaron en `PENDIENTE_UF` — a diferencia de
`ResultadoCicloDto.cantidadPendientesUf` en el ciclo. El frontend
(`components/importacion/ResultadoImportacion.tsx`) lo **estimaba** contando las filas
`ADVERTENCIA` de la previsualización cuyo mensaje contenía el texto fijo "quedará en estado
PENDIENTE_UF" — un estimado derivado, no un dato que devolviera `confirmar` directamente, y
que podía diferir si el archivo se revalidaba de forma distinta entre previsualizar y
confirmar (p. ej. la UF se vuelve disponible mientras tanto).

**Solución aplicada (P4):**
- Backend: `ImportacionCsvRespuestaDto` gana `cantidadPendienteUf` (`Integer`, nullable).
  `ServicioImportacionCsv.confirmar` lo cuenta EN VIVO mientras arma las propuestas de esa
  confirmación (subconjunto real de `filasOk`: `filasOk = con_valor + cantidadPendienteUf`;
  `filasError` queda aparte). En el historial (`listar`) el campo viaja en `null` — no hay
  columna persistida en `importacion_csv` para reconstruirlo después de esa confirmación
  puntual (se evaluó agregarla; ver la nota de "a futuro" más abajo — **no se creó ninguna
  migración**, la próxima sigue siendo V012).
- Frontend: `ResultadoImportacion.tsx` ahora muestra `resultado.cantidadPendienteUf` (el valor
  REAL) en vez de estimarlo desde la previsualización. La estimación se mantiene, mostrada
  explícitamente como tal, únicamente en el diálogo de confirmación PREVIO
  (`ImportarCsv.tsx`, antes de enviar) — es legítima ahí porque la confirmación real todavía
  no se ejecutó.

**Nota a futuro (no bloqueante):** si el historial de importaciones alguna vez necesita
mostrar cuántas propuestas quedaron `PENDIENTE_UF` por importación pasada, hay dos caminos sin
inventar cifras: (a) agregar una columna `cantidad_pendiente_uf` a `importacion_csv` (requiere
migración) y poblarla en `confirmar` con el mismo valor ya calculado, o (b) calcularlo en vivo
con un `count` sobre `propuesta_facturacion` filtrando por `importacion_id` y
`estado = 'PENDIENTE_UF'` (sin migración, pero es una consulta extra por fila del historial).
Ninguna se implementó en P4 porque el alcance pedido era solo el resultado de `confirmar`.

---

## 3. [RESUELTO — P5, 2026-08-08] Discrepancias menores de contrato frontend/backend

Documentadas en detalle en `docs/frontend.md` (por sección); reunidas acá para que no queden
sueltas en un solo documento del frontend:

- **`docs/frontend.md` §5.1 / §5.2 (ya resueltas antes de P5):** faltaban `origen` y
  `numeroFactura`/`fechaFactura` en `PropuestaFacturacionRespuestaDto`; se agregaron en un
  parche de backend posterior. Se deja como referencia de que el patrón "el frontend detecta
  la discrepancia, un parche de backend la cierra" ya funcionó — el mismo patrón se repitió en
  el punto 2 (P4) y en las tres discrepancias de abajo (P5).

**Solución aplicada (P5) — tres discrepancias, alineadas hacia lo más consistente con el resto
del sistema, sin migraciones (la próxima sigue siendo V012):**

- **D1 — `docs/frontend.md` §6.4:** `GET /facturas` filtraba por fecha **exacta**
  (`FacturaEspecificaciones.conFecha`); se reemplazó por **rango** (`fechaDesde`/`fechaHasta`,
  ambos opcionales, inclusivos — `conFechaDesde`/`conFechaHasta`), igual que el informe de
  facturación (`conRangoPeriodo`). Sin compatibilidad hacia atrás con el parámetro `fecha`: se
  priorizó la consistencia del contrato, sin consumidores externos que dependieran de él
  (etapa de desarrollo). Frontend (`ListaFacturas`): dos campos Desde/Hasta, mismo patrón que
  el informe.
- **D2 — `docs/frontend.md` §8.4:** `GET /informes/facturacion/export` siempre devolvía
  `informe-facturacion.csv` genérico; el frontend lo renombraba en el cliente
  (`nombreArchivoExportacion()`). Ahora el backend arma el nombre descriptivo según el filtro
  de período/rango (`informe-facturacion-2026-02.csv`, `informe-facturacion-2026-01_2026-03.csv`
  o el genérico si no hay filtro de período) en el `Content-Disposition`, con la misma
  construcción segura (`ContentDisposition.attachment().filename(nombre, UTF_8)`) que ya se
  usaba en la descarga de PDF de facturas. El frontend dejó de recalcular el nombre: usa
  `descarga.nombreArchivo` (el que entrega el backend) directamente.
- **D3 — orden por defecto:** algunos listados ordenaban por defecto (ciclos, por
  `ejecutado_en desc`) y otros no (importaciones necesitaban `sort` explícito desde el
  frontend). Se definió la convención — listados paginados de eventos/registros temporales
  ordenan por su fecha/timestamp (o período, si no hay timestamp propio) **descendente** por
  defecto — documentada en `estandares-de-codigo.md` §3.8, y aplicada a facturas, propuestas
  (incluyendo el detalle del informe) e importaciones. El frontend de importaciones dejó de
  pedir `sort=fechaImportacion,desc` explícito (ya no hace falta); el override de `sort` sigue
  disponible donde el frontend ya lo usaba.

Cualquier discrepancia nueva que se descubra en trabajo futuro debe agregarse acá (no solo
anotarse en `docs/frontend.md` o en un mensaje de commit), para que esta lista siga siendo la
fuente única de deuda técnica viva del proyecto.

---

## 4. [RESUELTO — 2026-08-16] CORS ausente en el backend (falso "Error de API (status 400)" en Clientes)

**Síntoma reportado:** con sesión real de "Administrador Prueba" contra el frontend
rediseñado, tanto **listar** clientes (`GET /clientes`, sin filtros) como **crear** un cliente
con datos válidos mostraban un toast genérico "Error de API (status 400)". La hipótesis
inicial — que el backend rechazaba `activo`/`estado` u otro parámetro como malformado — se
descartó con evidencia: contra Postgres real (Testcontainers, 11 migraciones), las mismas
peticiones exactas del frontend (`GET /clientes?page=0&size=20` sin `activo`/`texto`, y
`POST /clientes` con el RUT `12.335.545-8`) devuelven **200**/**201** limpios. También se
probaron variantes plausibles (`activo=Todos` literal, `sort` vacío, `//` en la ruta): ninguna
reproduce un 400 — la única que falla lo hace con **500** (con `detail`/`title`, no vacío).

**Causa raíz confirmada:** `SeguridadConfig` no tenía ningún `CorsConfigurationSource` ni
`.cors(...)` en la cadena de filtros. Verificado contra el contenedor `facturacion-backend`
real (no un mock): un preflight real
`OPTIONS /api/v1/clientes -H "Origin: http://localhost:13000" -H "Access-Control-Request-Method: GET" -H "Access-Control-Request-Headers: authorization"`
devolvía `401` **sin ningún header `Access-Control-Allow-*`**. Esto ya estaba anotado (pero
sin resolver) en los comentarios de `frontend/next.config.mjs`, y el propio `.env.local` de
desarrollo (fechado 2026-08-09) ya lo rodeaba con el rewrite same-origin `/api/proxy`. Sin ese
proxy — que es exactamente la situación real del frontend dockerizado (`NEXT_PUBLIC_API_BASE_URL`
apunta directo al backend, puerto distinto) — el navegador bloquea **todo** `fetch` con header
`Authorization` (o sea, toda llamada de `lib/clienteApiCliente.ts`) antes de que llegue al
backend.

**Discrepancia de reporte de errores (frontend), registrada aquí por pedido explícito — no
como causa del "400", sino para que la etiqueta no vuelva a confundir el diagnóstico:** se
revisó línea por línea `lib/clienteApi.ts` y `lib/errores.ts` buscando un camino que
etiquetara un fallo de red/CORS como "status 400". **No existe tal camino.** `ErrorApi` solo
se construye en dos puntos, ambos exigiendo una `Response` real ya resuelta:
`clienteApi.ts:76` (`procesarRespuesta`) y `clienteApi.ts:112` (`procesarRespuestaBinaria`);
`ejecutarFetch` (`clienteApi.ts:40-66`) no envuelve el `fetch()` en ningún `try/catch`, así que
un fallo de red/CORS real (el `fetch()` del navegador rechaza la promesa con `TypeError:
Failed to fetch`, sin `Response` legible) se propaga tal cual y `obtenerMensajeError`
(`lib/errores.ts:4-12`) lo muestra vía su rama `error instanceof Error` → `error.message`
("Failed to fetch"), **no** el texto genérico de `ErrorApi`. Es decir: el texto exacto "Error
de API (status 400)" solo puede salir de un `respuesta.status === 400` real con un cuerpo sin
`detail`/`title` — no hay, hoy, ningún fallback que lo produzca a partir de un fallo de CORS.
La reproducción exacta con sesión autenticada real quedó pendiente (no se reutilizó ninguna
contraseña de cuenta real para obtenerla — ver la nota de cierre abajo); lo que sí se confirmó,
contra infraestructura real, es que el gap de CORS es genuino, severo, y rompe cualquier
llamada autenticada desde un origen distinto al del backend — con o sin relación exacta con el
"400" reportado literalmente.

**Solución aplicada:**
- Backend (`SeguridadConfig`): nuevo bean `CorsConfigurationSource` (`UrlBasedCorsConfigurationSource`
  registrado en `/api/**`), conectado con `.cors(cors -> cors.configurationSource(...))` en la
  cadena de filtros, y `.requestMatchers(CorsUtils::isPreFlightRequest).permitAll()` para que
  el preflight no exija autenticación (el navegador nunca manda `Authorization` en el
  `OPTIONS`). Orígenes permitidos por **variable de entorno** (`CORS_ORIGENES_PERMITIDOS`,
  lista separada por comas — nunca hardcodeados, regla de oro §9), expuesta como
  `app.cors.origenes-permitidos`. Vacío por defecto en `application.yml` (CORS cerrado —
  falla seguro hasta que cada entorno lo declare explícito); `application-local.yml` lo fija
  a los puertos típicos de `npm run dev` (ver "Ajuste posterior — rango de puertos en dev"
  más abajo; se sobrescribe con la variable de entorno si tu puerto cae fuera del rango).
- Despliegue (`deploy/docker-compose.yml`, servicio `backend`): `CORS_ORIGENES_PERMITIDOS:
  ${AUTH_URL}` — reutiliza la misma variable que ya declara el origen público del frontend
  (mismo patrón que `KEYCLOAK_ISSUER_URI` con `KEYCLOAK_PUBLIC_URL`), sin duplicar valores.
  `deploy/.env.example` documenta el reuso.
- Verificado contra el contenedor real, reconstruido y reiniciado: el mismo preflight que
  antes daba `401` sin headers ahora da `200` con `Access-Control-Allow-Origin:
  http://localhost:13000` (el `AUTH_URL` real de este despliegue) y `Access-Control-Allow-Methods`/`-Headers`
  correctos; un origen fuera de la lista (`http://evil.example`) recibe `403 Invalid CORS
  request`, sin headers CORS.
- Pruebas nuevas: `backend/src/test/java/.../comun/config/SeguridadConfigCorsTest.java` —
  preflight de un origen permitido (sin autenticar, headers CORS correctos) y ausencia de esos
  headers para un origen no permitido. Sin migración (no se tocó el esquema; la próxima sigue
  siendo V012).

**Segunda causa encontrada al cerrar el pendiente — imagen de frontend desactualizada:** al ir
a confirmar el punto anterior con una sesión real, apareció una segunda causa, independiente de
CORS: la imagen `facturacion-frontend:local` corriendo en el stack Docker estaba horneada el
**2026-08-09** (`docker image inspect`, `Created: 2026-08-09T20:07:50Z`) — **antes** de que se
tocara ningún archivo del rediseño R1 (`app/(protegido)/layout.tsx` modificado 2026-08-09
18:41, `components/shell/BarraLateral.tsx` 2026-08-10 00:32, `tailwind.config.ts` 2026-08-10
09:48 — todos posteriores al build de esa imagen, ver `plan-rediseno.md` §1.2 para qué rehace
R1). Es decir: `:13000` seguía sirviendo la pantalla previa al rediseño (nav horizontal de
texto, sin logo, sin sidebar), reconstruida recién el **2026-08-16**
(`docker compose build frontend`, imagen nueva `Created: 2026-08-16 23:03:43 -0400`) —
verificado estructuralmente (logo `Logo_Helpcom.png`, clases `bg-marca-azul`/`text-marca-azul`,
fuente `Montserrat` en el CSS servido, ausentes en la imagen vieja).

**Confirmado (cierre del pendiente):** con el stack ya corregido — backend reconstruido con el
fix de CORS de arriba y frontend reconstruido con el rediseño, ambos detrás del mismo origen
(`localhost:13000` → `18080`, el par que ya declara `CORS_ORIGENES_PERMITIDOS: ${AUTH_URL}`) —
se verificó **visualmente en el navegador, con sesión real**, que el toast "Error de API
(status 400)" **ya no aparece**, ni al listar ni al crear un cliente. Esto confirma lo que ya
se había demostrado por otras vías (Postgres real vía Testcontainers: 200/201 limpios;
contenedor real: preflight 401 sin headers CORS): el "400" reportado **no era un 400 real del
backend** — era la combinación de (a) el gap de CORS bloqueando la llamada antes de completar
un intercambio HTTP, y (b) estar mirando una imagen de frontend que ni siquiera era el código
que se estaba diagnosticando. Ninguna contraseña de cuenta real se solicitó ni se reutilizó
para este cierre.

**Lección para el futuro:**
- La imagen Docker del frontend (`facturacion-frontend:local`) **no se reconstruye sola** —
  cualquier cambio de código en `frontend/` (incluidas etapas de `plan-rediseno.md`) exige
  `docker compose build frontend` antes de `up`, o el contenedor sigue sirviendo el build
  viejo en silencio, sin ningún error que lo delate. Vale lo mismo para `facturacion-backend`
  tras un cambio en `backend/`.
- Un "Error de API (status N)" en la UI **no implica que el backend haya emitido ese status**
  — `docs/frontend.md` §2.4 documenta que `ErrorApi` solo se construye con una `Response` HTTP
  real ya resuelta; un fallo de red/CORS nunca llega a ese camino. Ante ese toast, lo primero
  es la pestaña Network del navegador (¿hubo una respuesta real con ese status, o el request ni
  se completó?), no asumir que el backend la generó.

**Ajuste posterior — rango de puertos en dev (2026-08-17):** el fix original solo permitía
`http://localhost:3000` en el perfil `local`, pero `npm run dev` (`frontend/package.json:6`,
`"dev": "next dev"`, sin `-p` fijo) salta al siguiente puerto libre (3001, 3002, …) si el 3000
ya está ocupado por otro proceso — el caso real en esta misma máquina. Con un solo origen fijo,
ese dev aterrizaba en un origen no permitido y el preflight daba `403 Invalid CORS request`
igual, aunque el código estuviera sano. Dos cambios, **solo en el perfil `local`, principio
innegociable de no aflojar producción**:
- `SeguridadConfig#fuenteConfiguracionCors` ya no delega en la conversión implícita de
  `@Value` a `List<String>`: parsea el string crudo a mano
  (`SeguridadConfig#parsearOrigenes`), separando por coma y **recortando espacios** alrededor
  de cada origen — una lista escrita como `"http://localhost:3000, http://localhost:3001"`
  (espacio después de la coma) antes rompía el match exacto de `Origin` si no se recortaba.
- `application-local.yml` amplía el default a `http://localhost:3000,http://localhost:3001,
  http://localhost:3002` — **solo** en ese perfil. `application.yml` (`dev`/`prod`, sin
  override) sigue vacío por defecto, y el compose de despliegue sigue fijando un único origen
  estricto vía `${AUTH_URL}` (§5.4 de `docs/despliegue.md`) — sin cambios, sin wildcard `*` en
  ningún perfil.

Verificado contra un backend del perfil `local` real (Postgres/Redis temporales vía Docker,
11 migraciones aplicadas, sin mocks): preflight desde `http://localhost:3002` (no-3000, dentro
del rango nuevo) → `200` con `Access-Control-Allow-Origin: http://localhost:3002` correcto;
preflight desde `http://localhost:9999` (fuera del rango) → sigue en `403 Invalid CORS
request`, sin headers CORS. `next.config.mjs:14-26` (comentario del rewrite `/api/proxy`)
también se actualizó: ya no describe el CORS ausente como un defecto abierto, sino que el
proxy es una comodidad de dev opcional sobre un backend que ya soporta CORS directo para el
rango de puertos típico.

---

## 5. [RESUELTO — 2026-08-17] `$NaN` en importación CSV — patrón sistémico de 5 sitios, todos cerrados

**Síntoma reportado:** al descargar la plantilla CSV desde la propia app (botón "Descargar
plantilla CSV") y subirla sin editar a "Previsualizar", la fila de ejemplo mostraba **Neto/IVA/
Total = "$NaN"**, además del mensaje (correcto) de que el RUT de ejemplo no era válido.

**Causa raíz — DOS problemas independientes, ambos confirmados contra el stack real
(`:13000`/`:18080`), no asumidos:**

1. **El `$NaN` en sí — bug de frontend, alcance de toda la API:** `application.yml` tiene
   `jackson.default-property-inclusion: non_null`, así que un campo `null` no viaja como
   `"campo":null` en el JSON — se **omite por completo**. `TablaPreviewImportacion.tsx`
   comprobaba `fila.netoClp === null` para decidir si mostrar "—"; tras `JSON.parse`, un campo
   omitido vale `undefined`, no `null`, así que la comparación fallaba, caía a
   `formatearMontoFilaCsv(undefined, fila)` → `Intl.NumberFormat.format(undefined)` → `"$NaN"`
   (reproducido exacto: `POST /importaciones/previsualizar` con la plantilla real devuelve la
   fila **sin las claves** `netoClp`/`ivaClp`/`totalClp`, confirmado byte a byte). El backend
   estaba bien en todo momento — `ServicioImportacionCsv.aPreviewDto` sí deja esos tres campos
   `null` en filas `ERROR`, correcto por diseño (`ImportacionPreviewFilaDto`, javadoc de la
   clase). `monto_neto=12.5` nunca se perdió ni se mal-parseó: el separador `;`, el decimal `.`
   y el nombre de columna de la plantilla son exactamente los que espera `LectorCsv`
   (verificado: `"montoNeto":"12.5"` llega intacto en la respuesta real).
   - **Fix:** `TablaPreviewImportacion.tsx` — `=== null` → `== null` (cubre `null` y
     `undefined` a la vez, mismo idioma ya usado en `FormularioAcuerdo.tsx:48`). Tipo
     `ImportacionPreviewFila.{netoClp,ivaClp,totalClp}` ampliado a `number | null | undefined`
     para que el compilador refleje la forma real y no vuelva a esconder este caso.
   - Prueba de regresión nueva: `TablaPreviewImportacion.test.tsx` — una fila `ERROR` con los
     tres campos `undefined` (la forma exacta de la respuesta real, no una versión
     simplificada con `null`) no debe mostrar `NaN` en ningún lado; falla contra el código
     viejo (verificado revirtiendo el fix temporalmente: los tres `$NaN` aparecen tal cual el
     reporte), pasa con el fix.

2. **El RUT de ejemplo de la plantilla era inválido — bug de datos, en el generador:**
   `frontend/lib/csv.ts` (y el propio ejemplo de `docs/modelo-de-datos.md` §6, copiado de ahí)
   usaban `76543210-9`; el dígito verificador correcto (módulo 11) es **3**, no 9 — el segundo
   RUT de ejemplo del mismo documento (`77111222-3`) tenía el mismo problema (correcto: `-6`).
   **Fix:** ambos RUTs de ejemplo corregidos a dígito verificador válido, en `lib/csv.ts` y en
   `docs/modelo-de-datos.md` §6. Deliberadamente **no** se ligaron a un cliente real sembrado
   (la plantilla no debe depender de qué exista en cada entorno) — verificado que, con el RUT ya
   válido, la fila de ejemplo sin editar ahora falla con el mensaje correcto y útil ("No existe
   un cliente con RUT 76543210-3.", indicando claramente que hay que reemplazar el dato) en vez
   de "RUT no válido" (que sugería, engañosamente, que la propia plantilla tenía un error de
   formato).

**Los otros cuatro sitios — cerrados en una segunda pasada (2026-08-17):** el mismo patrón
(campo `BigDecimal`/`Long` nullable de un DTO, comparado con `!==`/`===` en vez de `!=`/`==`)
se rastreó, sitio por sitio, leyendo el DTO/serialización del backend antes de tocar nada — no
por analogía. Los cuatro CONFIRMADOS omitibles y corregidos:

- **`components/facturacion/propuestas/ListaPropuestas.tsx`** (columna "Valor UF") —
  `PropuestaFacturacionRespuestaDto.valorUf` es `BigDecimal`, `null` en `PENDIENTE_UF` (y en
  propuestas `CLP` sin acuerdo en UF), luego omitido. Bug **vivo**: prueba de regresión con
  `valorUf: undefined` (sin `fechaValorUf` de por medio) falla contra el código viejo con
  `"NaN UF"` en la celda; pasa con `p.valorUf != null`.
- **`components/informes/InformeFacturacion.tsx`** (misma columna, `InformeFacturacionDetalleDto.
  valorUf`) — mismo campo, mismo bug **vivo**, misma prueba (falla con `"NaN UF"` contra el
  código viejo), mismo fix (`!= null`).
- **`components/facturacion/ciclo/EjecutarCiclo.tsx`** — `ResultadoCiclo.ejecucionId`
  (`Long`), `null` cuando el lock del ciclo ya estaba tomado (§9 arquitectura-tecnica.md),
  luego omitido. Acá el efecto **no** era un `$NaN` sino una **rama de UI equivocada**: con
  `ejecucionId` omitido, `!== null` daba `true` y mostraba el enlace "Ver propuestas de este
  período →" como si se hubiera generado algo nuevo, en vez del aviso correcto ("Ya había una
  ejecución en curso..."). Prueba de regresión con `ejecucionId` omitido de la respuesta mock:
  falla contra el código viejo (aparece el enlace `<a href="...">`, verificado); con el fix
  (`!= null`) toma la rama correcta.
- **`components/facturacion/propuestas/DialogoDetallePropuesta.tsx`** (fila "Valor UF") —
  mismo campo `valorUf`, técnicamente omitible, **pero el bug no está vivo hoy**: la
  comparación es `propuesta.valorUf !== null && propuesta.fechaValorUf`, y
  `ArmadorPropuesta.java:79-80/88-89` pone `valorUf`/`fechaValorUf` en `null` **siempre
  juntos** (nunca uno sin el otro) — así que `fechaValorUf` (falsy cuando se omite) ya
  protegía el `&&` aunque `valorUf` viniera `undefined`. Se corrigió igual (`!= null`), por
  consistencia y como defensa ante un futuro cambio que desacople ambos campos — no porque
  hoy se observe el defecto acá. Dos pruebas: una con la forma real actual (`valorUf` y
  `fechaValorUf` omitidos juntos — pasa igual antes y después del fix, confirma el
  comportamiento correcto de hoy) y una que aísla la comparación (`valorUf` omitido con
  `fechaValorUf` presente — combinación que el backend no produce hoy, pero que el tipo
  permite; esta sí falla contra el código viejo con `"NaN UF (...)"`, y es la que realmente
  prueba el fix de esta línea).

**Causa común, en un solo lugar:** toda la API tiene `jackson.default-property-inclusion:
non_null` (`application.yml`) — un campo `null` en Java nunca viaja como `"campo":null`, se
OMITE del JSON. Tras `JSON.parse`, un campo omitido vale `undefined`, no `null`. Cualquier
comparación `=== null`/`!== null` contra un campo nullable de un DTO deja el caso `undefined`
sin cubrir. La regla para todo código nuevo: comparar SIEMPRE con `== null`/`!= null` (cubre
ambos) contra cualquier campo que un DTO de la API declare nullable — nunca `===`/`!==` a
secas. Los cinco tipos TS tocados (`ImportacionPreviewFila`, `PropuestaFacturacion`,
`InformeFacturacionDetalleFila`, `ResultadoCiclo`) documentan esto inline y usan
`| null | undefined` en vez de solo `| null`, para que el compilador refleje la forma real de
la respuesta.

## 6. [RESUELTO — 2026-08-17] `500` en exportación CSV del Informe de facturación — `produces` restrictivo vs `Accept` fijo del cliente

**Síntoma reportado:** al pulsar "Exportar CSV" en el Informe de facturación, la UI mostraba
"Ocurrió un error inesperado. Contacte al administrador." — detectado por el usuario en la
pasada visual de R8 (`docs/plan-rediseno.md`), con el ambiente ya en 63 propuestas reales
(58 de una importación CSV + 5 de ciclo).

**Causa raíz — confirmada contra el stack real, no asumida (dos pistas descartadas con
evidencia antes de llegar a esta):**

`lib/clienteApi.ts::ejecutarFetch` fija `Accept: application/json` en **toda** solicitud sin
excepción — también en las descargas binarias, porque es una función compartida por las
llamadas JSON y por `descargarArchivo` (PDF de facturas, CSV del informe). Por su parte,
`InformeFacturacionControlador.exportar` estaba mapeado con
`@GetMapping(value = "...", produces = "text/csv")`. Con el cliente ofreciendo solo
`application/json` y el endpoint exigiendo `text/csv`, Spring rechaza la negociación de
contenido **antes** de ejecutar el método del controlador, con
`org.springframework.web.HttpMediaTypeNotAcceptableException` (confirmado en el log real del
contenedor `facturacion-backend`). `ManejadorGlobalErrores` no tiene un `@ExceptionHandler`
específico para esa excepción, así que cae al catch-all `@ExceptionHandler(Exception.class)`
→ 500 con el mensaje genérico reportado.

Reproducido primero SIN el header forzado (`200`, CSV correcto con las 63 filas — descartó un
`null` sin proteger en las propuestas de origen CSV, la primera sospecha) y luego CON
`Accept: application/json` exacto (`500` instantáneo, mismo `detail` reportado) — confirmando
que la causa es el choque de negociación de contenido, no un dato ni el volumen (falla
instantánea, no hay timeout de por medio). Descartada también una regresión de R8: la tarea de
re-piel solo tocó `className` en `InformeFacturacion.tsx`/`ResumenInforme.tsx`, nunca
`exportarCsv()`, `descargarArchivo` ni el backend — el bug es preexistente desde que se
construyó la pantalla, invisible porque `InformeFacturacionControladorTest` usaba `MockMvc`
sin fijar `Accept` (negociación trivialmente permisiva por defecto) y nadie había hecho clic
en "Exportar CSV" contra un navegador/cliente real hasta la pasada visual de R8.

**Por qué la descarga de PDF de Facturas nunca mostró este bug, usando el mismo cliente:**
`FacturaControlador.descargarPdf` (`/api/v1/facturas/{id}/pdf`) no declara `produces` en su
`@GetMapping` — fija el `Content-Type` a mano en el `ResponseEntity`, igual que el export de
CSV, pero sin restringir la negociación en el mapping. Nunca chocó con el `Accept:
application/json` fijo del cliente.

**Fix aplicado:** se quitó `produces = "text/csv"` del `@GetMapping` de
`InformeFacturacionControlador.exportar`, igualando el patrón ya probado de `descargarPdf`. El
`Content-Type` real de la respuesta lo sigue fijando
`ResponseEntity.contentType(MediaType.parseMediaType("text/csv"))` — el CSV que baja es byte a
byte el mismo, no cambió el armado ni el formato. Prueba de regresión nueva en
`InformeFacturacionControladorTest` (`deberiaExportarAunConAcceptApplicationJsonComoMandaElClienteReal`):
hace el `GET` con `.accept(MediaType.APPLICATION_JSON)` explícito — falla contra el código
viejo (verificado revirtiendo el fix temporalmente: `500`, mismo `HttpMediaTypeNotAcceptableException`
en el log) y pasa con el fix (`200`, `Content-Type: text/csv`). Verificado además contra el
stack real (contenedor reconstruido y reiniciado, token real de `dev.qa` vía Keycloak): el
mismo request que antes daba 500 ahora da `200` con las 63 filas reales completas.

**¿Es un patrón sistémico, como el de `$NaN` (ítem 5)?** No — se revisó todo `produces` en
`backend/src/main/java/`: tras el fix, **no queda ningún endpoint con un `produces`
restrictivo** en todo el backend. Era el único sitio. La única otra ocurrencia del string
`"text/csv"` en el código (`ServicioImportacionCsv.java`) es para validar el `Content-Type` de
un archivo **subido**, sin relación con este bug. Sí queda como regla para código nuevo: si un
endpoint de descarga fija su `Content-Type` a mano en el `ResponseEntity` (el patrón ya
establecido para PDF y CSV), **no** declarar además un `produces` en el `@GetMapping` — el
cliente (`ejecutarFetch`) siempre manda `Accept: application/json`, sin importar el tipo de
contenido que en realidad se está pidiendo.

Siete pruebas de regresión nuevas en total entre los cinco sitios (2 de la primera pasada —
`TablaPreviewImportacion` — + 5 de esta: `ListaPropuestas`, `InformeFacturacion`,
`EjecutarCiclo` y las 2 de `DialogoDetallePropuesta`, nuevo archivo), cada una verificada
explícitamente contra el código viejo antes de restaurar el fix (o, en el caso de
`DialogoDetallePropuesta`, confirmando explícitamente cuál de sus dos pruebas SÍ discrimina y
cuál no), para confirmar que son una red de seguridad real y no solo cobertura de línea.

---

## 7. [ABIERTO] Refactor pendiente: 7 sitios con `react-hooks/set-state-in-effect`

**Qué es:** al subir a Next 16 (ítem 1, RESUELTO), `eslint-config-next@16` trajo
`eslint-plugin-react-hooks@7`, que agrega la regla `react-hooks/set-state-in-effect`. Marca el
patrón `setCargando(true); setError(null);` al inicio de un `useEffect` de fetching — común en
este proyecto para el ciclo carga/error/datos — porque llamar `setState` de forma síncrona al
principio de un efecto puede disparar un render en cascada (React recomienda mover ese estado
inicial a un `useReducer` o calcularlo derivado, en vez de dos `setState` sueltos).

**Los 7 sitios** (todos con el mismo patrón, detectados por el lint, no por inspección manual):
- `lib/useListadoPaginado.ts` (hook compartido — el de mayor impacto: lo usan la mayoría de los listados paginados de la app)
- `lib/useInformeFacturacion.ts`
- `lib/useDashboardDatos.ts` (nuevo en R9, mismo patrón heredado a propósito — ver
  `docs/frontend.md` §21)
- `components/proyectos/LayoutDetalleProyecto.tsx`
- `components/proyectos/acuerdos/ListaAcuerdos.tsx`
- `components/facturacion/facturas/DetalleFactura.tsx`
- `components/clientes/SelectorCliente.tsx`

**Por qué no se resolvió en la subida de Next 16:** es un cambio de LÓGICA (aunque menor),
deliberadamente no mezclado con un bump de dependencias — mismo criterio que separa re-piel
visual de bugs funcionales en todo este documento. La regla se relajó a `"warn"` en
`eslint.config.mjs` (con un comentario explícito ahí mismo apuntando a este ítem) para que
`npm run lint` no bloquee mientras se decide el refactor.

**Acción pendiente:** decidir el approach del refactor (candidatos: `useReducer` para
carga/error/datos como un solo estado, o extraer el patrón a un hook interno que ya lo resuelva
una sola vez, dado que `useListadoPaginado`/`useInformeFacturacion` ya son la abstracción
compartida) y aplicarlo en los 7 sitios, luego volver la regla a `"error"`.

---

## 8. [RESUELTO — 2026-08-18] `PENDIENTE_UF` permanente por un solo fallo transitorio de mindicador.cl — sin reintento en `FuenteUfMindicador`

**Riesgo de PRODUCCIÓN destapado por el sembrado de dev de R9, no un problema del ambiente de
pruebas.** Durante un sembrado dirigido de datos de dev (proyectos/acuerdos nuevos + ejecución
del ciclo de mayo y junio 2026), 3 de 4 propuestas nuevas quedaron en `PENDIENTE_UF` pese a que
las 4 fechas UF necesarias tenían valor real publicado en mindicador.cl (confirmado consultando
la API directamente). Diagnóstico previo a este ítem (mismo día) midió, contra el mindicador.cl
real: **solo 1/5 éxito** en pruebas puntuales desde el contenedor `facturacion-backend`, con
timings de conexión/TLS siempre rápidos (~500 ms) y las fallas ocurriendo **después** de
conectar, esperando el primer byte de respuesta — mismo patrón desde el host, fuera de Docker,
descartando que fuera un problema de red/DNS del contenedor.

**Causa raíz — nuestra, no de mindicador.cl:** `FuenteUfMindicador.consultarUf`
(`backend/.../uf/fuente/`) hacía **una sola llamada HTTP, sin reintento**, con timeout de 5 s
(`PropiedadesUf`, sin override en este despliegue). Cualquier `RestClientException` se
capturaba genéricamente y degradaba a `Optional.empty()` de inmediato. Como
`propuesta_facturacion` es un *snapshot* inmutable (regla de oro) y el índice único parcial del
ciclo (`uq_prop_ciclo_periodo`, `V009`, sobre `origen='CICLO'` **sin filtro de `estado`**)
bloquea cualquier fila nueva para ese `(proyecto, período)` — confirmado además que
`anular()` (`ServicioPropuestaFacturacion`) solo cambia `estado`, nunca `origen`, así que ni
siquiera anular libera el cupo — un solo hiccup de 5 s en la única ventana de intento dejaba la
propuesta rota **para siempre**, sin ningún camino de recuperación por dominio. Esto puede
repetirse cualquier día 1 real del ciclo de facturación en producción, no solo en un sembrado de
dev.

**Fix — reintento con backoff, solo ante fallo TRANSITORIO:** `FuenteUfMindicador.consultarUf`
reintenta hasta **3 intentos en total** (1 + 2 reintentos), con backoff lineal corto (500 ms
antes del 2º, 1000 ms antes del 3º). Los éxitos contra mindicador.cl observados completan en
1,5-3,4 s (bajo el timeout de 5 s), y la tasa de éxito por intento medida ronda 60-90% incluso
degradada — con eso, agotar 3 intentos seguidos es poco probable sin alargar demasiado el peor
caso. El costo queda acotado porque `ServicioUfImpl` cachea por fecha: dentro de un mismo ciclo,
cada fecha distinta paga el costo de reintento una sola vez, sin importar cuántos proyectos se
facturen ese día.

**Qué SÍ se reintenta (transitorio) vs. qué NO (`PENDIENTE_UF` legítimo) — verificado en vivo,
no solo supuesto:**
- Timeout/error de conexión (`ResourceAccessException`) y 5xx (`HttpServerErrorException`): se
  reintentan.
- **Hallazgo real durante la verificación de este arreglo:** mindicador.cl a veces responde
  `200 OK` con el JSON correcto en el cuerpo pero el header `Content-Type` mal declarado como
  `text/html` en vez de `application/json` — el `RestClient` no puede parsearlo
  (`UnknownContentTypeException`) pese a que la fecha SÍ tenía UF publicada. Confirmado
  intermitente repitiendo la misma URL contra el servicio real (a veces con el header correcto,
  a veces no) — se trata como transitorio y se reintenta también.
- Un `HttpClientErrorException` (4xx, respuesta real y bien formada rechazando la solicitud) NO
  se reintenta — no es un síntoma de mindicador.cl fallando momentáneamente.
- Una respuesta `200` limpia sin la fecha en la `serie` (UF real y legítimamente no publicada
  todavía) tampoco se reintenta — ese camino ni siquiera lanza excepción. **`PENDIENTE_UF` sigue
  siendo un estado válido y necesario** (arquitectura-tecnica.md §8/§9): el arreglo reduce
  `PENDIENTE_UF` a "de verdad no disponible", no lo elimina.

**Pruebas de regresión** (`FuenteUfMindicadorTest`, 4 nuevas sobre las 4 existentes, 7 en
total): fallo transitorio (timeout) seguido de éxito → recupera el valor real, calculable —
verificado que **falla contra el código anterior** (revertido temporalmente: `Optional` vacío,
sin reintento) y pasa con el fix; Content-Type mal declarado seguido de éxito → mismo resultado;
5xx y error de conexión persistentes → agotan los 3 intentos y degradan a `PENDIENTE_UF` (antes
del fix, la prueba equivalente esperaba una sola llamada; ahora espera las 3, en orden); 4xx →
**no** reintenta, una sola llamada. Suite completa del backend: **245/245** (218 sin e2e + 27
e2e).

**Verificación contra el stack real, no solo unit tests:** imagen `facturacion-backend`
reconstruida y contenedor recreado con el fix. Contra el mindicador.cl real (vía
`POST /api/v1/importaciones/previsualizar`, que no persiste nada — solo ejerce el mismo camino
`ArmadorPropuesta`→`ServicioUfImpl`→`FuenteUfMindicador` del ciclo, con fechas UF frescas no
cacheadas), se midió **90% de éxito a nivel de fecha en una tanda de 20** (18/20; las 2
restantes agotaron los 3 intentos legítimamente — el `Content-Type` mal declarado puede
sostenerse varios intentos seguidos para una misma fecha, así que el reintento mejora
materialmente la tasa pero **no la garantiza al 100%** — mindicador.cl sigue siendo
externamente inestable, fuera de nuestro control). Confirmado en los logs del contenedor real
que el reintento efectivamente dispara (`intento 1/3`, `2/3`, `3/3`) y que agota correctamente
sin quedar en un loop infinito.

**Reconocimiento del mecanismo de reemplazo para las 3 propuestas ya rotas por este defecto**
(ids 64, 66, 67 del sembrado de R9, todas `PENDIENTE_UF`) — **verificado, no ejecutado**: no
existe ningún camino limpio por dominio para regenerarlas. El índice único
`uq_prop_ciclo_periodo` no excluye `ANULADA` (confirmado en `V009`), la consulta de idempotencia
del ciclo tampoco filtra por `estado` (confirmado en `PropuestaFacturacionRepositorio`), y
`anular()` nunca cambia `origen` (confirmado en `ServicioPropuestaFacturacion`) — así que anular
esas 3 propuestas **no libera el período**: el ciclo las seguiría viendo como `YA_EXISTIA` para
siempre. Tampoco existe ningún endpoint de reproceso/recálculo en todo el backend (revisado el
inventario completo de controladores). El arreglo de este ítem previene que el problema se
repita hacia adelante; **no** repara retroactivamente lo ya roto — eso requeriría borrar esas
filas específicas por SQL directo o agregar una funcionalidad de recálculo nueva, ambos fuera
del alcance de este ítem por decisión explícita.

---

*Última actualización: 2026-08-18 (ítem 8 RESUELTO — nuevo, riesgo de producción destapado por
el sembrado de dev de R9: `FuenteUfMindicador` hacía una sola llamada sin reintento a
mindicador.cl, dejando `PENDIENTE_UF` permanente ante cualquier hiccup transitorio de esa API de
terceros. Reintento con backoff (3 intentos, 500ms/1000ms) agregado, distinguiendo fallo
transitorio — incluido un Content-Type mal declarado por mindicador.cl, hallazgo real de la
verificación — de `PENDIENTE_UF` legítimo. 245/245 en la suite completa, verificado además
contra el mindicador.cl real tras reconstruir la imagen Docker: 90% de éxito por fecha en una
tanda de 20, mejora material pero no garantiza el 100%. Las 3 propuestas ya rotas por este
defecto en el sembrado — ids 64, 66, 67 — quedan sin camino de dominio para regenerarse,
confirmado por revisión de código, sin tocarlas).
Actualización previa: ítem 1 RESUELTO — subida a Next 16 + React 19 aplicada en
firme, ver `docs/frontend.md` §20: las 5 vulnerabilidades ALTAS originales + una 6ª aparecida
después, `nanoid`, quedaron en 0 con `npm audit`; login OIDC verificado en vivo contra Keycloak
real con ambos roles. Ítem 7 ABIERTO — nuevo, consecuencia directa de la misma subida: 6 sitios
con la regla `react-hooks/set-state-in-effect` en `"warn"`, refactor pendiente de decidir).
Actualización previa: ítem 5 CERRADO — los cuatro sitios restantes del patrón
sistémico corregidos: `ListaPropuestas`/`InformeFacturacion` tenían el bug vivo — "$NaN"/"NaN
UF" reproducido contra el código viejo y corregido con `!= null`; `EjecutarCiclo` tenía una
rama de UI equivocada, no un `$NaN`, también reproducida y corregida; `DialogoDetallePropuesta`
resultó no tener el bug vivo hoy — protegido por un `&&` con `fechaValorUf`, que
`ArmadorPropuesta` siempre omite en el mismo momento que `valorUf` — se corrigió igual por
consistencia/defensa, documentado explícitamente como tal. Causa común: `jackson.
default-property-inclusion: non_null` omite campos `null` del JSON en vez de mandarlos
explícitos; `=== null`/`!== null` no detecta el `undefined` resultante tras `JSON.parse` —
`== null`/`!= null` sí. Cinco pruebas de regresión nuevas en esta pasada (siete en total entre
las dos pasadas del ítem), todas verificadas contra el código viejo antes de restaurar el fix).
Actualización previa: ítem 4 — ajuste
posterior: rango de puertos 3000-3002
permitido en el perfil `local` para `npm run dev`, con recorte de espacios en el parseo de
`app.cors.origenes-permitidos`; producción sigue con un único origen estricto vía `${AUTH_URL}`,
sin cambios). Actualización previa del mismo ítem: cerrado — confirmado en el navegador, con
el stack Docker ya corregido, que el toast "Error de API (status 400)" desaparece; se
documentó la segunda causa real, independiente de CORS: la imagen `facturacion-frontend:local`
estaba horneada antes del rediseño R1 y había que reconstruirla. Actualización anterior:
CORS ausente en `SeguridadConfig` — preflight real devolvía 401 sin headers
`Access-Control-Allow-*`; se agregó `CorsConfigurationSource` con orígenes por variable de
entorno, verificado contra el contenedor real reconstruido. Actualizaciones anteriores: P5 —
alineadas las tres discrepancias de contrato frontend/backend: rango de fechas en facturas,
nombre descriptivo del export del informe y orden por defecto de los listados (ítem 3
resuelto); P4 — el contador de `PENDIENTE_UF` en confirmar CSV pasó de estimado a real (ítem 2
resuelto); P2/P3 — consolidación de las mordidas de Spring Boot 4 en `CLAUDE.md` y registro de
las vulnerabilidades de Next.js.*
