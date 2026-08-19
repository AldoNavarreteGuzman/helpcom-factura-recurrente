# Frontend — Fundación

Complementa `estandares-de-codigo.md` §5 con el detalle de la fundación construida en esta
etapa (autenticación OIDC contra Keycloak, shell de la aplicación, ruteo protegido, cliente
API conectado a la sesión real) y, desde la etapa siguiente, los patrones reutilizables de
pantalla (§3) que usan Clientes y Tipos de servicio — las primeras pantallas de negocio
reales — y que reutilizarán los módulos que faltan (`proyectos`, `facturacion`,
`importacion`, `informes`).

---

## 1. Levantar el frontend contra un Keycloak local

### 1.1 Keycloak

Cualquier Keycloak reciente sirve para desarrollo local. Con Docker:

```bash
docker run -p 8081:8080 \
  -e KEYCLOAK_ADMIN=admin \
  -e KEYCLOAK_ADMIN_PASSWORD=admin \
  quay.io/keycloak/keycloak:latest start-dev
```

Con la consola de administración en `http://localhost:8081`:

1. Crea el realm **`helpcom`** (coincide con `docs/arquitectura-tecnica.md` §4/§7).
2. Crea un cliente **confidential** (Client authentication: *On*) con id
   `facturacion-recurrente-frontend`:
   - **Valid redirect URIs**: `http://localhost:3000/api/auth/callback/keycloak`
   - **Valid post logout redirect URIs**: `http://localhost:3000/login`
   - **Web origins**: `http://localhost:3000`
3. En la pestaña **Credentials** del cliente, copia el *Client secret* → `AUTH_KEYCLOAK_SECRET`.
4. Los roles de realm `ADMINISTRADOR` y `OPERADOR` deben existir (Realm roles) y estar
   asignados a los usuarios de prueba. El scope por defecto `roles` ya incluye el mapper de
   "realm roles" que expone `realm_access.roles` en el token — es el mismo claim que lee el
   backend (`arquitectura-tecnica.md` §7), así que **no se necesita un mapper adicional**.
5. Crea al menos un usuario de prueba con contraseña y con uno o ambos roles asignados.

### 1.2 Frontend

```bash
cd frontend
cp .env.example .env.local   # completar con los valores del cliente Keycloak
npm install
npm run dev
```

Abre `http://localhost:3000`: el middleware redirige a `/login`; el botón lleva al login de
Keycloak; tras autenticarse, vuelve a la app con el shell (header + navegación) y el
dashboard placeholder.

### 1.3 Variables de entorno

| Variable | Descripción |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | URL base del backend, con el prefijo `/api/v1`. Pública (queda en el bundle del navegador). |
| `AUTH_KEYCLOAK_ISSUER` | Issuer OIDC, **con el realm incluido**: `http://<host>/realms/<realm>`. |
| `AUTH_KEYCLOAK_ID` | Client ID del cliente confidential creado en Keycloak. |
| `AUTH_KEYCLOAK_SECRET` | Client secret del mismo cliente. **Nunca se versiona** — solo vive en `.env.local` / el gestor de secretos del entorno. |
| `AUTH_SECRET` | Secreto para firmar/cifrar la sesión de Auth.js. Generar uno por entorno: `openssl rand -base64 32`. |

Todas están en `frontend/.env.example` como placeholders. Sin secretos reales en el
repositorio (regla de oro #9 de `CLAUDE.md`).

---

## 2. Decisiones de la fundación

### 2.1 Auth.js (NextAuth) v5 + provider Keycloak

Se confirma **Auth.js v5** (`next-auth@5.0.0-beta`) como librería OIDC, según lo previsto en
`arquitectura-tecnica.md` §7 (Authorization Code + PKCE contra Keycloak). Razones:

- Integración nativa con el App Router de Next.js (Server Components, Route Handlers,
  middleware, server actions) sin adaptar nada a mano.
- El provider `keycloak` viene incluido; solo requiere `issuer`/`clientId`/`clientSecret`.
- El patrón de refresco de token (ver más abajo) es el recomendado por la propia
  documentación de Auth.js para providers OAuth con `refresh_token`.

**División `auth.config.ts` / `auth.ts`** (patrón oficial de Auth.js para el middleware/proxy):
el proxy (`proxy.ts` — `middleware.ts` hasta Next 15, renombrado al subir a Next 16, §20) corre en
el runtime **Edge**, que no soporta todo lo que necesita el provider de Keycloak (el fetch de
refresco sí es compatible, pero mantener el client secret y los callbacks pesados fuera del
Edge es más seguro y es el patrón documentado). Por eso:

- `lib/auth.config.ts` — configuración liviana: sin providers reales, con el callback
  `authorized` (la función `estaAutorizado`, testeada en `lib/auth.config.test.ts`) que
  decide si una ruta requiere sesión. La usa `proxy.ts` directamente.
- `lib/auth.ts` — configuración completa (runtime Node): agrega el provider `Keycloak` y los
  callbacks `jwt`/`session` que arman y renuevan la sesión. La usan las Server Components,
  Route Handlers y `lib/clienteApiServidor.ts`.

**Renovación de token:** el callback `jwt` guarda `accessToken`, `refreshToken` y
`accessTokenExpires` (epoch ms) del login inicial. En cada llamada posterior (toda
invocación de `auth()`/`getSession()` re-ejecuta este callback), si falta menos de 30s para
que expire, pide un token nuevo al endpoint `${issuer}/protocol/openid-connect/token` con
`grant_type=refresh_token`. Si el refresco falla (refresh token vencido o revocado), el token
queda con `error: "RefreshAccessTokenError"` — no se sigue usando un access token inválido en
silencio. `lib/clienteApiServidor.ts` y `lib/clienteApiCliente.ts` detectan ese error y fuerzan
un re-login (redirect a `/login` en el servidor; `signIn("keycloak")` en el cliente).

**Roles:** se extraen del claim `realm_access.roles` del access token (el mismo que mapea el
backend, `arquitectura-tecnica.md` §7) decodificando el JWT a mano
(`lib/jwt.ts::decodificarPayloadJwt`, sin verificar firma — el token viene de una respuesta
HTTPS directa de Keycloak, así que ya es confiable) tanto en el login inicial como después de
cada renovación, para que sean siempre el reflejo del access token vigente. `lib/roles.ts`
valida que sean `ADMINISTRADOR`/`OPERADOR` conocidos antes de confiar en ellos.

**Consultar el rol de la sesión:**
- Server Components / Route Handlers: `const sesion = await auth(); sesion?.roles`.
- Client Components: `const { data: sesion } = useSession(); sesion?.roles` (requiere estar
  dentro de `<ProveedorSesion>`, montado en `app/layout.tsx`).

### 2.2 Cliente API: dos entradas, no una

`lib/clienteApi.ts` quedó como la base compartida (normalización `problem+json` → `ErrorApi`,
`fetch` crudo) sin importar nada de Auth.js — cada contexto resuelve el token de forma
distinta y no pueden compartir código para eso:

- **`lib/clienteApiServidor.ts`** (Server Components, Route Handlers): usa `auth()`. Si no
  hay sesión o ya quedó en error de renovación, o si el backend igual responde 401,
  redirige a `/login` (`next/navigation`) — un Server Component no puede reintentar
  interactivamente.
- **`lib/clienteApiCliente.ts`** (Client Components, `"use client"`): usa `getSession()` de
  `next-auth/react`, que ya dispara la renovación si hace falta. Si el backend responde 401
  igual, pide la sesión una vez más y reintenta; si eso tampoco alcanza, fuerza
  `signIn("keycloak")`.

Mezclar ambos en un solo archivo habría forzado a que cualquier Server Component que solo
necesitara `clienteApiServidor` arrastrara `next-auth/react` (pensado para ejecutarse en el
navegador) en su bundle.

### 2.3 Componentes base: Tailwind a mano, sin shadcn/ui

Se evaluó shadcn/ui pero se optó por componentes propios (`components/ui/`) sobre Tailwind
puro:

- El set pedido es chico (botón, campo, tabla, paginación, diálogo, notificación) y no
  justifica sumar Radix UI + la ceremonia de generación de shadcn todavía.
- El elemento nativo `<dialog>` (`components/ui/Dialogo.tsx`) ya resuelve foco atrapado,
  cierre con ESC y backdrop sin ninguna librería — cubre el caso de uso sin Radix.
- Coherente con el principio de "simplicidad primero" de `arquitectura-tecnica.md` §2.

Si más adelante los formularios de negocio necesitan componentes más ricos (selects
searchable, date pickers, etc.), sumar shadcn/ui en ese momento es una decisión aislada y
reversible — no bloquea nada de lo construido acá.

### 2.4 Manejo de errores `problem+json`

`lib/errores.ts` traduce un `ErrorApi` (o cualquier error) a lo que necesita la UI:

- `obtenerMensajeError(error)` → el `detail` (o `title`, o el mensaje genérico) para mostrar
  como notificación (`useNotificaciones().notificarError(error)`, ver
  `components/ui/Notificaciones.tsx`).
- `obtenerErrorDeCampo(error, "nombreDelCampo")` → el mensaje a mostrar junto a un campo
  específico. El backend (`ManejadorGlobalErrores.manejarValidacionArgumentos`) solo entrega
  los **nombres** de los campos inválidos en `problema.errores`, no un mensaje por campo — por
  eso el texto que se muestra junto al campo es el `detail` general de la respuesta, que es lo
  único que hay disponible hoy.

**Un fallo de red/CORS NO produce el texto genérico "Error de API (status N)"** —
investigado a fondo diagnosticando el gap de CORS de `docs/deuda-tecnica.md` ítem 4:
`ErrorApi` solo se construye en `clienteApi.ts` (`procesarRespuesta`/`procesarRespuestaBinaria`),
ambos exigiendo una `Response` real ya resuelta; `ejecutarFetch` no envuelve `fetch()` en
`try/catch`, así que un preflight CORS bloqueado o cualquier error de red se propaga como el
`TypeError` crudo del navegador (`"Failed to fetch"`), no como un `ErrorApi`. Si algún día se
reporta un toast con el texto genérico `Error de API (status N)`, es evidencia de que **sí**
hubo una respuesta HTTP real con ese status y sin `detail`/`title` en el cuerpo — no asumas
que es un fallo de red disfrazado sin antes mirar la pestaña Network.

### 2.5 Pruebas

Se sumó **Vitest + Testing Library** (no existía ningún test runner en el frontend). Cubre:

- `lib/auth.config.test.ts` — el guard de rutas (`estaAutorizado`): sin sesión en una ruta
  protegida se deniega (el middleware redirige a `/login`); `/login` siempre se permite.
- `lib/navegacion.test.ts` — `enlacesVisibles` filtra correctamente por rol (con una lista de
  enlaces de prueba, incluyendo casos "solo ADMINISTRADOR", "solo OPERADOR" y "ambos").
- `components/shell/Navegacion.test.tsx` — el componente real, con `useSession` mockeado,
  muestra los 6 enlaces para OPERADOR y para ADMINISTRADOR (regla provisional: todos
  visibles para ambos roles) y no muestra ninguno para una sesión sin roles reconocidos.
- `components/tiposServicio/ListaTiposServicio.test.tsx` — el listado con datos mockeados
  (`@/lib/clienteApiCliente` mockeado con `vi.mock`), el estado vacío y el estado de error
  (banner con `problema.detail`).
- `components/clientes/FormularioCliente.test.tsx` — un RUT con dígito verificador incorrecto
  bloquea el envío sin llamar al cliente API; un RUT válido se normaliza antes de enviarse
  (`11.111.111-1` → `rut: "11111111-1"` en el body); un 400 con `errores: ["razonSocial"]`
  muestra el mensaje junto al campo; un 409 de duplicado se muestra igual aunque no mapee a
  ningún campo.
- `components/clientes/ListaClientes.test.tsx` — con rol ADMINISTRADOR el botón "+ Nuevo
  cliente" y las acciones de fila están habilitados; con rol OPERADOR el botón no se
  renderiza y Editar/Desactivar/Eliminar quedan deshabilitados.
- `lib/vigencia.test.ts` — `calcularEstadoVigencia` (vigente/futuro/pasado, bordes inclusive),
  `seSuperponen`, y `calcularTerminoDesdeMeses` (incluido el caso de "clamp" al último día del
  mes, ver §4.5).
- `components/proyectos/ListaProyectos.test.tsx` — listado con filtros (verifica que
  `page`/`size` viajan como query params), vacío, error, y el mismo patrón de rol de
  `ListaClientes.test.tsx`.
- `components/proyectos/FormularioProyecto.test.tsx` — `fechaTermino < fechaInicio` bloquea
  el envío antes de llamar al cliente API; un envío válido incluye lo elegido en
  `SelectorCliente`; un 400 con campo en `errores` aterriza en el campo.
- `components/proyectos/acuerdos/FormularioAcuerdo.test.tsx` — los campos condicionales
  aparecen/desaparecen según `tipo` (moneda oculta solo en `DESCUENTO_PORCENTAJE`; aclaración
  de "reemplaza el precio base" solo en `PRECIO_PACTADO`); el modo de vigencia alterna entre
  fecha de término y meses pactados, mostrando el término calculado y enviando
  `mesesPactados`/`fechaTermino: null` cuando corresponde; un 409 `ACUERDO_TRASLAPADO` muestra
  su `detail` (el mensaje específico del conflicto, no un genérico).
- `components/facturacion/ciclo/EjecutarCiclo.test.tsx` — el botón está deshabilitado para
  OPERADOR; un resultado `CON_ADVERTENCIAS` con `cantidadPendientesUf > 0` muestra el badge
  correcto y el callout de advertencia (nunca se calla ni se pinta como éxito pleno); un
  `EXITOSA` sin pendientes no muestra la advertencia; el botón queda deshabilitado mientras la
  llamada al backend está en curso (promesa controlada a mano en el test).
- `components/facturacion/ciclo/HistorialCiclos.test.tsx` — render con datos mockeados y
  estado vacío.
- `components/facturacion/propuestas/ListaPropuestas.test.tsx` — los filtros viajan como query
  params; una fila `PENDIENTE_UF` muestra `"— (sin UF)"` en las cuatro columnas de monto
  (nunca `$0`); una fila `FACTURADA` muestra los montos reales; el botón Anular no aparece en
  estados no anulables, y queda deshabilitado para OPERADOR en un estado que sí lo es.

`vitest.setup.ts` incluye un polyfill de `HTMLDialogElement.prototype.showModal/close` — jsdom
implementa `<dialog>` pero no esos métodos imperativos
([jsdom#3294](https://github.com/jsdom/jsdom/issues/3294)), y todos los modales de la app
(`Dialogo`, y por extensión `FormularioDialogo`/`DialogoConfirmacion`) los necesitan para
poder testearse.

`npm run test` corre la suite una vez; `npm run test:watch` la deja en modo watch.

---

## 3. Patrones reutilizables de pantalla

Extraídos al construir **Clientes** y **Tipos de servicio** (las primeras pantallas de
negocio) para que los módulos siguientes (proyectos, facturación, importación, informe) no
repitan esta plomería. Cada uno vive en un único lugar; ninguna pantalla la reimplementa.

### 3.1 Listado: `PanelListado` + `useListadoPaginado`

- **`components/listado/PanelListado.tsx`** — la vista: título, slot de filtros (`filtros`,
  `ReactNode` libre — cada entidad arma los suyos con `CampoFormulario`/`Entrada`/
  `Seleccion`), slot de acción principal (`accionPrincipal`, típicamente el botón "+ Nuevo…",
  ya gateado por rol por quien arma la pantalla), y los tres estados: error (banner rojo con
  `obtenerMensajeError`), cargando, o `Tabla` + `Paginacion`. No sabe nada de cómo se
  obtienen los datos.
- **`lib/useListadoPaginado.ts`** — el hook que sí sabe: recibe un `fetcher` (sin argumentos,
  ya "cerrado" sobre los filtros/página actuales de la pantalla) y un arreglo `dependencias`
  que declara qué debe disparar una recarga (`[texto, filtroActivo, pagina]`, por ejemplo).
  Devuelve `datos`, `totalPaginas`, `cargando`, `error` y `recargar()` (para volver a pedir
  la misma página después de crear/editar/eliminar, sin tocar filtros). `fetcher`
  deliberadamente NO es una dependencia del `useEffect` interno (con
  `eslint-disable-next-line react-hooks/exhaustive-deps` explícito) — de lo contrario, al no
  venir memoizado por quien llama, dispararía una recarga en cada render. El costo es que
  quien usa el hook debe declarar en `dependencias` todo lo que realmente hace variar el
  resultado.
- **`components/listado/AccionesFila.tsx`** — los tres botones estándar por fila (Editar,
  Activar/Desactivar, Eliminar), con `puedeEditar`/`puedeEliminar` viniendo de
  `lib/useRoles.ts::useTieneAlgunRol` (ver §3.4). Se **deshabilitan**, no se ocultan, cuando
  el rol no alcanza — con un `title` explicando por qué — para que quede claro que la acción
  existe pero no está permitida, sin ofrecer algo que terminaría en 403.

La columna "Acciones" es simplemente la última columna de `Tabla` con
`renderizar: (fila) => <AccionesFila ... />`; no hace falta ningún mecanismo especial en
`Tabla` para eso.

### 3.2 Formulario: `FormularioDialogo` + `useFormularioApi` + `lib/errores.ts`

- **`lib/useFormularioApi.ts`** — estado de envío (`enviando`) y el error capturado
  (`error`), con `manejarEnvio(accion)` envolviendo la llamada al cliente API: pone
  `enviando=true`, ejecuta, captura cualquier excepción en `error`, y retorna `true`/`false`
  según el resultado — para que el formulario decida qué hacer (cerrar el modal y notificar
  éxito) solo si `true`.
- **`components/formularios/FormularioDialogo.tsx`** — envoltorio del modal
  (`components/ui/Dialogo.tsx`) con los botones Cancelar/Guardar ya resueltos
  (`enviando` deshabilita Cancelar y pone el spinner en Guardar). Muestra el error general
  **dos veces a propósito**: como notificación (toast, para que no se pierda si el modal se
  cierra) y como banner dentro del formulario (para errores que no mapean a ningún campo —
  el caso típico es un 409 de duplicado, que no trae `problema.errores`). `children` son los
  campos específicos de la entidad.
- **`lib/errores.ts`** (ya existía desde la fundación, ver §2.4) — cada campo usa
  `obtenerErrorDeCampo(error, "nombreDelCampo")` para su mensaje individual. Como el backend
  solo entrega los NOMBRES de los campos inválidos en un 400 (no un mensaje por campo), ese
  mensaje individual es el mismo `detail` general — reforzado, no inventado.

Los formularios de Cliente/TipoServicio combinan esto con validación **local** (antes de
llamar al backend): `erroresLocales` en el propio componente, mezclado con
`obtenerErrorDeCampo` vía una función `errorDeCampo(campo)` que prioriza el error local. El
RUT (`lib/rut.ts`, ver §3.3) es el caso que más lo necesita — se valida al perder el foco
y de nuevo al enviar.

### 3.3 `lib/rut.ts` — RUT chileno en el cliente

Puerto exacto del `RutChileno` del backend (mismo algoritmo módulo 11): `normalizarRut`
(a `NNNNNNNN-D` o `null` si no es válido), `esRutValido`, y `formatearRut` (con puntos de
miles, solo para mostrar — `12345678-9` → `12.345.678-9`). Se **normaliza antes de enviar**
y se **muestra formateado** en las tablas; el backend igual vuelve a validar y normalizar —
esto es solo feedback inmediato (UX), nunca la fuente de verdad.

### 3.4 Confirmación: `DialogoConfirmacion`

**`components/ui/DialogoConfirmacion.tsx`** — mensaje + botones Cancelar/Confirmar sobre
`Dialogo`, con `procesando` (deshabilita Cancelar, spinner en Confirmar) y `variante`
(`"peligro"` por defecto, para eliminar; puede pasarse `"primario"` para acciones menos
destructivas). Se usa igual para "Eliminar" que para cualquier acción irreversible futura.

### 3.5 Roles en Client Components: `lib/useRoles.ts`

`useRoles()` (roles de la sesión actual) y `useTieneAlgunRol(rolesPermitidos)` — el
equivalente client-side de `lib/roles.ts::tieneAlgunRol`, ya envuelto en `useSession()`. Vive
separado de `lib/roles.ts` porque ese archivo es puro y lo importa también `lib/auth.ts`
(que corre en Node/Edge, no en el navegador); mezclar `useSession` ahí forzaría `"use
client"` en un módulo que no debe tenerlo.

### 3.6 Discrepancia backend/tarea: `eliminar` nunca responde 409 en clientes/tipos-servicio

El plan original asumía "si el backend responde 409 por estar referenciado, ofrecer
desactivar". En los hechos, `ClienteServicio.eliminar` y `TipoServicioServicio.eliminar`
**nunca lanzan 409**: si la entidad está referenciada (por un proyecto), el propio backend
hace la baja lógica automáticamente (`activo = false`) y responde `204 No Content` — el
409 solo ocurre al **crear/editar** con un nombre/RUT duplicado (`CLIENTE_DUPLICADO`,
`TIPO_SERVICIO_DUPLICADO`). La UI sigue al backend: el diálogo de confirmación de "Eliminar"
ya avisa por adelantado que, si está en uso, quedará desactivado en vez de eliminarse — no
hay una rama de manejo de 409 específica para el flujo de eliminar porque nunca se dispara.

### 3.7 Dónde quedaron las pantallas

`GET /api/v1/tipos-servicio` no tenía un módulo de navegación propio (`lib/navegacion.ts`
sigue listando solo los 5 módulos de negocio + Inicio). Como `tipo_servicio` es un catálogo
auxiliar de `proyecto` — y en el backend vive en el mismo paquete `clientes/` — su pantalla
quedó anidada en `/clientes/tipos-servicio`, con un enlace de texto desde la cabecera del
listado de Clientes, en vez de sumar un ítem más al nav principal. Es una decisión de
arquitectura de información, no del backend; fácil de revisar si en la práctica se necesita
más visibilidad.

### 3.8 Mapeo de errores por campo — verificado, no necesitó cambios

Antes de construir Proyectos/Acuerdos (formularios notablemente más complejos), se verificó
que `obtenerErrorDeCampo` (`lib/errores.ts`) + `CampoFormulario` + `FormularioDialogo` ya
mapean correctamente un 400 `problema.errores: ["nombreDelCampo"]` al campo correspondiente,
además de mostrar el `detail` como banner y notificación — descrito en §3.2 y ya cubierto por
`FormularioCliente.test.tsx`. No hizo falta completar nada; los formularios de Proyecto y
Acuerdo lo reutilizan tal cual.

---

## 4. Proyectos y Acuerdos de precio

Segunda tanda de pantallas de negocio, sobre los patrones de §3. Dos decisiones nuevas y una
extensión a un componente compartido:

### 4.1 Acuerdos en una ruta propia, no en un modal anidado

`FormularioDialogo` (§3.2) asume un solo modal a la vez. La gestión de acuerdos de un
proyecto necesita su propio listado (`GET /proyectos/{id}/acuerdos`) MÁS su propio formulario
modal de creación/edición — anidar ese segundo modal dentro del modal de "editar proyecto"
habría sido, como mínimo, una mala experiencia de foco/z-index con `<dialog>` nativo. En vez
de eso:

- **Crear/editar proyecto** sigue siendo un modal (`FormularioDialogo`) disparado desde la
  fila del listado de `/proyectos`, igual que Cliente/TipoServicio.
- **Acuerdos** vive en su propia ruta, `/proyectos/{id}/acuerdos`
  (`components/proyectos/acuerdos/ListaAcuerdos.tsx`), enlazada con un link "Acuerdos" en
  cada fila del listado de proyectos. Ahí, el modal de creación/editar acuerdo
  (`FormularioAcuerdo`, también un `FormularioDialogo`) ya no compite con ningún otro modal
  abierto.

`GET /proyectos/{id}/acuerdos` devuelve una `List` plana (no paginada, a diferencia del resto
de los listados) — `ListaAcuerdos` no usa `useListadoPaginado` para eso; hace su propio
`useEffect` con `Promise.all([...])` (trae el proyecto, para el encabezado, y sus acuerdos a
la vez). Sí reutiliza `PanelListado` para los estados de carga/error/vacío, pasándole
`totalPaginas=1` — `Paginacion` ya no renderiza nada cuando `totalPaginas <= 1`, así que no
hizo falta ninguna variante del componente para el caso "sin paginar".

### 4.2 `AccionesFila` con Activar/Desactivar y Eliminar ahora opcionales

Proyectos, por decisión explícita del alcance de esta tarea, solo tiene
Editar/Activar-Desactivar (sin Eliminar); Acuerdos tiene Editar/Eliminar (sin
Activar/Desactivar — un acuerdo no tiene ese concepto). `components/listado/AccionesFila.tsx`
antes asumía los tres botones siempre presentes; ahora `onActivarDesactivar` y `onEliminar`
son opcionales — si no se pasan, ese botón simplemente no se renderiza. Cambio compatible
hacia atrás: Cliente y TipoServicio (que sí pasan los tres) siguen igual.

### 4.3 Selectores reutilizables: `SelectorCliente` / `SelectorTipoServicio`

`components/clientes/SelectorCliente.tsx` — búsqueda (reutiliza `GET /clientes?texto=...`,
solo activos) + un `<select>` con los resultados. Al editar, recibe `clienteInicial` (id +
razón social, que `ProyectoRespuestaDto` ya trae) para que la opción del cliente ASIGNADO
siga apareciendo aunque no matchee la búsqueda o el cliente se haya desactivado después —
sin pedir un fetch adicional. `components/tiposServicio/SelectorTipoServicio.tsx` es el
mismo patrón, más simple (sin búsqueda, el catálogo es chico). Ambos reutilizables por
cualquier formulario futuro que necesite elegir un cliente o un tipo de servicio.

### 4.4 `lib/numero.ts` — precio en es-CL, se envía crudo

`formatearNumeroEsCl`/`parsearNumeroEsCl`: el campo de precio muestra el valor formateado
es-CL (`Intl.NumberFormat`) al perder el foco, pero lo que se envía es el `number` crudo que
resulta de parsear — igual que el patrón ya establecido para el RUT (§3.3): formato en la UI,
nunca en lo que viaja al backend.

### 4.5 `lib/vigencia.ts` — vigente/futuro/pasado y solape orientativo

- `calcularEstadoVigencia(fechaInicio, fechaTermino)`: compara strings ISO contra hoy
  (comparación de texto, válida porque `AAAA-MM-DD` ordena igual que la fecha).
- `seSuperponen(...)`: solape de dos rangos, bordes inclusive — usado para el aviso NO
  bloqueante que se muestra en `FormularioAcuerdo` si las fechas elegidas chocan con algún
  acuerdo existente del proyecto (que también se listan en el propio formulario, como ayuda
  proactiva). Es solo orientativo: el backend es la única autoridad real sobre el solape
  (puede haber condiciones de carrera), así que el 409 `ACUERDO_TRASLAPADO` sigue pudiendo
  ocurrir aunque el aviso local no haya saltado.
- `calcularTerminoDesdeMeses(fechaInicio, meses)`: réplica exacta de
  `fechaInicio.plusMonths(meses).minusDays(1)` de `AcuerdoPrecioServicio` (backend), incluido
  el "clamp" de `LocalDate.plusMonths` al último día del mes cuando el día de inicio no existe
  en el mes resultante (31 de enero + 1 mes → 28/29 de febrero, no marzo). Se usa solo como
  vista previa del término cuando el modo de vigencia es "meses pactados" — lo que se envía
  es `mesesPactados`; el backend calcula la fecha real.

### 4.6 El 409 `ACUERDO_TRASLAPADO` no necesitó manejo especial

Igual que el 409 `CLIENTE_DUPLICADO` en Cliente (§3.2, §3.6): como `FormularioDialogo` ya
muestra el `detail` del error (banner + notificación) sea cual sea su código, el mensaje
específico del backend — "La vigencia se traslapa con el acuerdo N (fecha a fecha)." — se ve
tal cual, sin genérico "algo salió mal" y sin código adicional en `FormularioAcuerdo`. Mismo
mecanismo, cero líneas nuevas.

### 4.7 Discrepancia backend/tarea: `eliminar` de Proyecto SÍ puede responder 409

A diferencia de Cliente/TipoServicio (§3.6, que nunca 409 al eliminar — bajan lógicamente
solos), `ProyectoServicio.eliminar` SÍ lanza un 409 real (`PROYECTO_CON_REFERENCIAS`) si el
proyecto tiene acuerdos u otras referencias — ahí no hay baja lógica automática. No es una
discrepancia que haya afectado esta entrega: el alcance de la tarea para Proyectos fue
explícitamente "crear/editar/activar-desactivar" (sin "eliminar"), así que no se construyó
esa acción y este 409 no se ejercita todavía. Queda anotado para cuando se agregue "Eliminar"
a Proyectos: a diferencia de Cliente/TipoServicio, ahí sí hace falta manejar el 409
explícitamente (mostrar el mensaje y, probablemente, no ofrecer una alternativa automática de
"desactivar en su lugar", porque el backend no la hace por sí solo).

---

## 5. Ciclo de facturación y Propuestas

Tercera tanda: `GET/POST /api/v1/ciclos*` y `GET/PATCH /api/v1/propuestas*`. Propuestas es
mayormente lectura (no se crean a mano); la única escritura es "anular". Tres discrepancias
reales con lo que asumía la tarea — las tres se resolvieron siguiendo al backend.

### 5.1 Discrepancia: `GET /propuestas` no filtra por `origen`, y `estado` es de valor único

`PropuestaFacturacionControlador.listar` acepta `periodoAnio`, `periodoMes`, `clienteId` y
un `estado` **singular** (`EstadoPropuesta`, no una lista) — no existe ningún parámetro
`origen`, aunque `PropuestaFacturacionEspecificaciones.conOrigen` existe en el código del
backend (quedó de una tarea anterior, del informe) y `ServicioPropuestaFacturacion.listar`
simplemente no lo usa. La UI sigue al backend: el filtro de Estado es un `<select>` de un solo
valor (no multi-select), y **no hay control de filtro por Origen** — origen sigue siendo una
columna de la tabla (el dato sí viaja en la respuesta), solo no se puede filtrar por él desde
la API hoy. Si en el futuro se conecta `conOrigen` al servicio, agregar el filtro es sumar un
`Seleccion` más, el mismo patrón que los demás.

**Resuelto en el backend (patch posterior, sin migración):**
`PropuestaFacturacionControlador.listar` ahora acepta `origen` (`OrigenPropuesta`, opcional)
y lo combina con los demás filtros vía `PropuestaFacturacionEspecificaciones.conOrigen` + el
combinador null-safe `combinar(...)`. `estado` sigue siendo de valor único (no se tocó esa
parte).

**Resuelto en el frontend:** `ListaPropuestas` ya tiene el `Seleccion` de Origen (mismo
patrón que Estado), cableado al query param `origen`.

### 5.2 Discrepancia: `PropuestaFacturacionRespuestaDto` no trae el número de factura

La tarea pedía una columna de "número de factura (si FACTURADA)". La entidad
`PropuestaFacturacion` sí tiene la relación `factura` (con su `numeroFactura`), pero
`PropuestaFacturacionRespuestaDto` — y por lo tanto la respuesta de `GET /propuestas` — no
expone ni `numeroFactura` ni `facturaId`. No se armó ninguna solución alternativa (como pedir
la factura aparte por cada fila `FACTURADA`, un N+1 fuera de lugar para un dato que el propio
endpoint debería traer): la columna simplemente no se construyó. El estado `FACTURADA` del
badge ya comunica que se facturó; falta el número en sí. Anotado para cuando el DTO del
backend agregue el campo.

**Resuelto en el backend (patch posterior, sin migración):** `PropuestaFacturacionRespuestaDto`
ahora expone `numeroFactura` (String, nullable) y `fechaFactura` (LocalDate, nullable),
poblados desde `propuesta.getFactura()` — `null` cuando la propuesta no está `FACTURADA`. Sin
N+1: la relación `factura` sigue LAZY a nivel de entidad, pero
`PropuestaFacturacionEspecificaciones.conFacturaFetch()` agrega un `LEFT JOIN FETCH` a la
consulta de listado (con guard para no aplicarse a la consulta `COUNT` de la paginación).
Mismo campo agregado en `InformeFacturacionDetalleDto` (que ya traía `facturaId`/`numeroFactura`
de una tarea anterior; ahora también `fechaFactura`), con el mismo fetch join.

**Resuelto en el frontend:** `ListaPropuestas` ya tiene la columna "N° factura"
(`numeroFactura`, "—" si la propuesta no está `FACTURADA`).

### 5.3 Montos de `PENDIENTE_UF`: "— (sin UF)", nunca el 0 real

`lib/propuestas.ts`:

- `esMontoAusente(estado)` → `estado === "PENDIENTE_UF"`.
- `formatearMontoClpOAusente(monto, estado)` → `"— (sin UF)"` si `esMontoAusente`, si no
  `formatearClp(monto)`.

`neto_clp`/`iva_clp`/`total_clp` SÍ vienen en 0 desde el backend cuando la propuesta quedó
`PENDIENTE_UF` (arquitectura-tecnica.md §9: "nunca se inventan cifras" — no es que valga $0,
es que todavía no se sabe cuánto vale porque no había UF disponible para la fecha). Mostrar
ese 0 tal cual en una columna de "Neto"/"Total" se leería como una cifra real. Tanto
`ListaPropuestas` (columnas) como `DialogoDetallePropuesta` (snapshot completo) usan este
helper — ninguna de las dos formatea el monto "a mano".

### 5.4 Badge de `EstadoPropuesta` — semántica de color (reutilizable en el informe)

`components/facturacion/propuestas/BadgeEstadoPropuesta.tsx`:

| Estado | Color | Motivo |
|---|---|---|
| `PENDIENTE` | Azul (`sky`) | Neutro — falta facturarse, nada anómalo. |
| `PENDIENTE_UF` | Ámbar (`amber`) | Advertencia — sin monto real todavía (§5.3). |
| `FACTURADA` | Verde (`emerald`) | Éxito — ya se facturó. |
| `ANULADA` | Gris (`slate`) | Inactivo — no cuenta para nada. |

Mismo lenguaje de color en `components/facturacion/ciclo/BadgeEstadoEjecucionCiclo.tsx` para
`EstadoEjecucionCiclo` (`EXITOSA`=verde, `CON_ADVERTENCIAS`=ámbar, `ERROR`=rojo — el único caso
que usa rojo, reservado para errores duros de una ejecución, no para el estado de una
propuesta). El informe (10g) debería importar `BadgeEstadoPropuesta` directamente en vez de
reimplementar los colores.

### 5.5 Ejecutar ciclo: honestidad del resultado, no éxito silencioso

`components/facturacion/ciclo/EjecutarCiclo.tsx` — puntos que la tarea pedía explícitamente y
cómo quedaron:

- **Solo ADMINISTRADOR**: el botón usa `useTieneAlgunRol(["ADMINISTRADOR"])`; deshabilitado +
  `title` explicando el motivo para OPERADOR (mismo patrón que el resto de la app).
- **Confirmación previa**: `DialogoConfirmacion` explica que el proceso es idempotente antes
  de ejecutar.
- **Estado de carga**: `Boton` con `cargando={ejecutando}` (spinner + deshabilitado) y los
  campos de año/mes también se deshabilitan mientras corre.
- **`CON_ADVERTENCIAS` no se pinta como éxito pleno**: la notificación toast dice literalmente
  "terminó con advertencias" (no "correctamente"), y el resumen muestra el badge de estado
  real (§5.4) más un callout ámbar aparte con el conteo de `PENDIENTE_UF` — nunca se oculta
  ni se resume como si todo hubiera salido bien.
- **Enlace al período recién ejecutado**: `/facturacion?periodoAnio=X&periodoMes=Y`, con
  `&estado=PENDIENTE_UF` agregado solo si `cantidadPendientesUf > 0`. `ListaPropuestas` lee
  esos params con `useSearchParams()` como valor inicial de sus filtros (una sola vez, al
  montar) — por eso su página (`app/(protegido)/facturacion/page.tsx`) envuelve el componente
  en `<Suspense>`, que Next.js exige para todo lo que use `useSearchParams()`.
- **Error real (problem+json) del backend**: banner rojo con `obtenerMensajeError`, además de
  la notificación — la UI nunca queda como si la ejecución hubiera funcionado.
- **`ejecucionId: null`** (el backend omitió la corrida porque ya había otra en curso para el
  mismo período — el lock de Redis, arquitectura-tecnica.md §9): se muestra el mensaje tal
  cual en vez de un enlace a propuestas, porque no se generó nada nuevo que ver.

### 5.6 Dónde quedaron las pantallas

Ciclo y Propuestas viven en el mismo paquete del backend
(`cl.helpcom.facturacion.facturacion`), así que comparten el nav item "Facturación"
existente, con `/facturacion` como el listado de Propuestas (la pantalla del día a día) y el
ciclo anidado debajo — mismo criterio que Clientes→Tipos de servicio (§3.7) y
Proyectos→Acuerdos (§4.1):

- `/facturacion` — listado de propuestas (`ListaPropuestas`).
- `/facturacion/ciclo` — ejecutar el ciclo (`EjecutarCiclo`).
- `/facturacion/ciclo/historial` — historial de ejecuciones (`HistorialCiclos`, sin filtros —
  el backend ya ordena por fecha de ejecución descendente,
  `EjecucionCicloRepositorio.findByEmpresaIdOrderByEjecutadoEnDesc`, así que no hace falta
  ningún control de orden en la UI).

Enlaces cruzados en ambos sentidos (de Propuestas a "Ejecutar ciclo"/"Historial", y de
"Ejecutar ciclo" de vuelta a Propuestas) para que no haga falta pasar por el nav para moverse
entre las tres pantallas.

### 5.7 Detalle de propuesta: diálogo de solo lectura, no `FormularioDialogo`

`DialogoDetallePropuesta` usa `components/ui/Dialogo.tsx` directamente (no
`FormularioDialogo`): no hay nada que enviar, así que los botones Cancelar/Guardar y el
manejo de `enviando`/`error` de `FormularioDialogo` no aplican — solo el modal base con
contenido de solo lectura (el snapshot completo del cálculo, arquitectura-tecnica.md §9) y su
botón de cierre (×) ya incluido en `Dialogo`.

### 5.8 Botón "Reprocesar UF": conecta la UI al endpoint ya existente (deuda-tecnica.md ítem 8/9)

`PATCH /api/v1/propuestas/{id}/reprocesar-uf` ya existía y estaba probado (commit `1acbaa1`) —
esta tarea solo conectó la interfaz. Contrato verificado antes de construir: `200` + la
`PropuestaFacturacionRespuestaDto` completa en éxito (**incluido cuando el resultado sigue
siendo `PENDIENTE_UF`** — nunca falla por "seguir sin UF", eso es un resultado legítimo, no un
error, arquitectura-tecnica.md §9); `409 PROPUESTA_NO_REPROCESABLE` si el estado ya no es
`PENDIENTE_UF` (carrera con otra acción); `403` sin rol `ADMINISTRADOR`; `404` si el id no
existe. Reusa exactamente el patrón ya establecido por "Anular" (§ arriba, mismo componente
`ListaPropuestas`) — mismo `useTieneAlgunRol(["ADMINISTRADOR"])` (renombrado `esAdministrador`,
ya no `puedeAnular`, porque ahora gatea dos acciones), mismo `clienteApiCliente.actualizarParcial`,
mismo `recargar()` de `useListadoPaginado` tras la acción, mismos tokens de marca.

**El botón aparece solo en una fila `PENDIENTE_UF`** (`lib/propuestas.ts::esReprocesableUf`,
nuevo, mismo patrón que `esAnulable`/`esFacturable`) — el guard del backend rechazaría
cualquier otro estado con `409`, así que ofrecerlo ahí sería invitar a un clic que va a fallar
seguro.

**El punto delicado — los dos tipos de `PENDIENTE_UF` (arquitectura-tecnica.md §9/§8):** un
fallo transitorio de mindicador.cl (recuperable, ya resuelto en general por el reintento con
backoff del propio backend) y una fecha de facturación genuinamente futura sin UF publicada
todavía. El endpoint no distingue: para ambos devuelve `200` — si consiguió la UF, pasa a
`PENDIENTE`; si no, sigue en `PENDIENTE_UF`. La UI decide su mensaje mirando el `estado` de la
respuesta, no el éxito/fracaso HTTP:
- Sigue `PENDIENTE_UF` → toast **`"advertencia"`** (ámbar, `estado-sin-uf` — ni error rojo
  alarmante ni éxito verde engañoso): *"No se pudo obtener la UF de esta fecha (puede que aún
  no esté publicada). La propuesta sigue pendiente."*
- Pasa a `PENDIENTE` → toast `"exito"` + `recargar()` trae la fila con el neto ya calculado y
  el badge cambiado.

**Deshabilitar preventivamente si `fechaFacturacion` es futura — evaluado y descartado.** La UF
chilena se publica con antelación: el Banco Central anuncia el mes calendario completo por
adelantado, así que una `PENDIENTE_UF` de fecha nominalmente "futura" (como el proyecto
"crux - lalo", `docs/frontend.md` §8.7) puede tener su UF **ya disponible** — un chequeo de
fecha en el cliente adivinaría mal exactamente en ese caso, bloqueando un reproceso que sí
funcionaría. El botón se ofrece siempre que el estado sea `PENDIENTE_UF`; el resultado real de
la llamada es quien decide el mensaje, nunca una suposición de fecha hecha de antemano.

**Sin diálogo de confirmación** — a diferencia de "Anular" (irreversible, con
`DialogoConfirmacion`), reprocesar no es destructivo: es seguro reintentar cuantas veces haga
falta sin efecto acumulativo (mismo diseño del endpoint, arquitectura-tecnica.md §9), así que
pedir "¿estás seguro?" no aportaría nada — un clic directo alcanza.

**Estado de carga por fila, no global:** `reprocesandoId: number | null` (el id de la propuesta
cuyo `PATCH` está en vuelo) en vez de un booleano único — puede haber varias filas
`PENDIENTE_UF` visibles a la vez, y solo el botón de la fila en curso debe deshabilitarse
("Reprocesando…") mientras el resto sigue disponible.

**Pruebas** (`ListaPropuestas.test.tsx`, 7 nuevas): el botón solo aparece en `PENDIENTE_UF`, no
en `FACTURADA`; habilitado para `ADMINISTRADOR`/deshabilitado para `OPERADOR` en una fila
`PENDIENTE_UF`; el caso central — sigue `PENDIENTE_UF` tras reprocesar → aparece el mensaje
informativo, **nunca** el de éxito (verificado explícitamente que el texto de éxito NO está en
el documento); pasa a `PENDIENTE` → éxito + la lista se vuelve a pedir; un `409` se muestra
como error sin romper la fila; el botón se deshabilita mientras el `PATCH` está en vuelo, con
un `mockActualizarParcial` cuya promesa se resuelve a mano para inspeccionar ese estado
intermedio.

**Verificado contra el stack real, no solo unit tests:** imagen Docker del frontend
reconstruida (mismo checklist de `docs/despliegue.md` §6) y confirmado el texto "Reprocesar UF"
efectivamente presente en el bundle compilado dentro del contenedor
(`docker exec facturacion-frontend grep -rl "Reprocesar UF" /app/.next/static`) — no solo la
fecha de la imagen, el contenido real.

---

## 6. Facturas

Cuarta tanda: `POST/GET /api/v1/facturas*` y la subida/descarga del PDF de respaldo. A
diferencia de Ciclo/Propuestas (mayormente lectura), acá hay una escritura no trivial: asociar
propuestas `PENDIENTE` existentes a una factura nueva.

### 6.1 Flujo elegido para crear una factura: ruta propia, no checkboxes en `ListaPropuestas`

La tarea daba a elegir entre agregar checkboxes al listado general de Propuestas o una
pantalla dedicada. Se eligió una **ruta propia**, `/facturacion/facturas/nueva`
(`components/facturacion/facturas/NuevaFactura.tsx`), mismo criterio que Acuerdos (§4.1):

- La selección debe **sobrevivir** a cambios de filtro y de página — el usuario puede filtrar
  por cliente, cambiar de página, volver a filtrar por período, y las propuestas ya elegidas
  no deben desaparecer. Se modela como un `Map<id, PropuestaFacturacion>` en el propio
  componente, no como "las filas marcadas de la página actual" (que se perdería al re-listar).
- `ListaPropuestas` (`/facturacion`) tiene su propio propósito ya establecido — ver/anular —
  y agregarle un "modo selección" con estado de selección global habría complicado ese
  componente para un caso de uso que en los hechos necesita su propia página con su propio
  panel de "seleccionadas" + subtotal + formulario, que no encaja en `PanelListado` genérico.

**Elegibilidad en la UI** (`NuevaFactura.tsx`, la tabla de selección deliberadamente muestra
TODAS las propuestas del período/cliente filtrado, no solo las `PENDIENTE` — para que el
usuario entienda por qué algunas no se pueden elegir, en vez de que simplemente desaparezcan):

- El checkbox de una fila se deshabilita (con `title` explicando el motivo, mismo patrón que
  `AccionesFila`) si la propuesta no está `PENDIENTE`, o si ya hay una selección activa de
  **otro** cliente (`ServicioFactura.validarMismoCliente` en el backend — una factura no puede
  mezclar clientes). El cliente queda fijado por la **primera** propuesta elegida; un aviso
  (`role`-neutral, banner celeste) lo explica mientras hay selección activa.
- El subtotal (`Σ totalClp` de las seleccionadas, `formatearClp`) es solo informativo — el
  backend es la única fuente de verdad del cálculo real de la factura.

El backend es la autoridad final en ambos casos (`FACTURA_DUPLICADA`, `PROPUESTA_NO_FACTURABLE`,
`PROPUESTAS_DE_CLIENTES_DISTINTOS`): la UI solo evita ofrecer de entrada algo que sabe que va a
fallar; sigue pudiendo fallar por una condición de carrera (alguien más facturó la propuesta
entre que se listó y que se envió el formulario) — de ahí el botón "Refrescar listado" que
aparece junto al error `PROPUESTA_NO_FACTURABLE`.

### 6.2 Mapeo de errores de la creación: `FACTURA_DUPLICADA` no tiene `problema.errores`

Igual que `CLIENTE_DUPLICADO`/`ACUERDO_TRASLAPADO` en tareas anteriores (§3.6, §4.6):
`FACTURA_DUPLICADA` es un 409 (`ReglaNegocioException`) sin `problema.errores` — el backend no
lo reporta como error de validación de un campo específico. A diferencia de esos casos
anteriores (donde bastaba con el banner general), la tarea pedía explícitamente que este
mensaje aterrizara en el campo `numeroFactura`. Como `obtenerErrorDeCampo` (`lib/errores.ts`)
solo mira `problema.errores`, no alcanza — `NuevaFactura.tsx` agrega una función local,
`errorCampoNumeroFactura`, que primero chequea `problema.codigo === "FACTURA_DUPLICADA"` antes
de delegar en `obtenerErrorDeCampo`. No se generalizó a `lib/errores.ts` porque es el único
caso en la app donde un código de error (no un nombre de campo) necesita mapear a un campo
específico; si aparece un segundo caso, ahí sí vale la pena extraer el patrón.

`NuevaFactura` no usa `FormularioDialogo` (no es un modal), así que replica a mano su patrón de
mostrar el error general dos veces — notificación + banner (§3.2) — con el mismo `useRef` guard
para no repetir la notificación en cada render.

### 6.3 Descarga del PDF: fetch autenticado → blob, nunca un `<a href>` directo

`arquitectura-tecnica.md` §14 es explícito: el acceso al PDF pasa siempre por el backend, que
nunca expone la `clave_objeto` ni URLs directas del bucket. Un `<a href="…/facturas/{id}/pdf">`
plano no llevaría el header `Authorization` (el backend exige rol autenticado en ese endpoint
igual que en el resto de la API) y fallaría con 401. En vez de eso:

- `lib/clienteApi.ts` gana `procesarRespuestaBinaria` (no interpreta el cuerpo exitoso como
  JSON — a diferencia de `procesarRespuesta`, que asume JSON siempre) y `ejecutarFetch` ahora
  reconoce cuerpos `FormData` (para la subida) sin fijarles `Content-Type` a mano — el
  navegador arma el boundary multipart solo.
- `lib/clienteApiCliente.ts` gana `descargarArchivo` (mismo patrón de reintento ante 401 que el
  resto del cliente) y `subirArchivo` (multipart, un solo campo).
- `lib/archivos.ts::descargarArchivoEnNavegador` dispara la descarga del blob ya obtenido
  simulando el click de un `<a>` efímero con `URL.createObjectURL` — es la única forma de
  "guardar" un blob en el navegador sin backend adicional.
- El tamaño máximo del PDF vive en configuración de servidor
  (`PropiedadesAlmacenamiento.tamanoMaximo`), no expuesta a la API — `SeccionPdfFactura.tsx`
  valida en cliente solo el **tipo** (`application/pdf`) antes de subir; si el archivo excede
  el tamaño, el backend responde `ARCHIVO_DEMASIADO_GRANDE` y su mensaje se muestra tal cual.

### 6.4 Resuelto: `GET /facturas` filtra por RANGO de fecha, no por fecha exacta

**Discrepancia original (P4 y anteriores):** el backend (`FacturaControlador.listar` /
`FacturaEspecificaciones.conFecha`) solo aceptaba un único parámetro `fecha` (`LocalDate`),
comparado con igualdad estricta — sin `fechaDesde`/`fechaHasta`, a diferencia del informe de
facturación, que ya soportaba rango. `ListaFacturas` seguía al backend con un solo campo de
fecha exacta.

**Solución aplicada (P5, deuda-tecnica.md ítem 3):** se alineó el listado de facturas al rango,
consistente con el informe:
- Backend: `FacturaEspecificaciones.conFecha` (igualdad estricta) se reemplazó por
  `conFechaDesde`/`conFechaHasta` (ambos opcionales, inclusive), combinadas con el combinador
  null-safe. `FacturaControlador.listar` recibe `fechaDesde`/`fechaHasta` en vez de `fecha` —
  sin compatibilidad hacia atrás con el parámetro viejo (se priorizó la consistencia del
  contrato; sin consumidores externos en esta etapa).
- Frontend: `ListaFacturas` tiene dos campos `<input type="date">`, "Desde"/"Hasta", mismo
  patrón que `anioMesDesde`/`anioMesHasta` en el informe (§8.1) — envía ISO-8601, la tabla
  sigue mostrando la fecha en formato es-CL vía `formatearFecha`.

### 6.5 Dónde quedaron las pantallas

Facturas vive anidado bajo `/facturacion` (mismo criterio que Ciclo, §5.6):

- `/facturacion/facturas` — listado de facturas (`ListaFacturas`), con enlaces cruzados hacia
  y desde `/facturacion` (Propuestas).
- `/facturacion/facturas/nueva` — crear factura asociando propuestas (`NuevaFactura`, §6.1).
- `/facturacion/facturas/{id}` — detalle de factura + gestión del PDF (`DetalleFactura` +
  `SeccionPdfFactura`).

Todas las acciones de Facturas (`hasAnyRole('ADMINISTRADOR','OPERADOR')` en el backend, sin
excepción — crear, subir/reemplazar PDF, listar, descargar) están permitidas para **ambos**
roles por igual; a diferencia de Ciclo (§5.5, solo ADMINISTRADOR) o Proyectos/Acuerdos, ninguna
pantalla de Facturas usa `useTieneAlgunRol` para gatear un botón.

---

## 7. Importación CSV

Quinta tanda: `POST /api/v1/importaciones/previsualizar`, `POST /api/v1/importaciones/confirmar`
y `GET /api/v1/importaciones`. Único flujo de la app que es explícitamente de **dos fases**
sobre el mismo archivo, y el primero en subir un archivo cuyo contenido hay que revisar fila
por fila antes de decidir si se envía.

### 7.1 Dos fases sobre el mismo `File`: previsualizar → confirmar

`components/importacion/ImportarCsv.tsx` conserva el `File` elegido en estado de React durante
toda la sesión de la pantalla — no se sube a ningún lado hasta que el usuario pide
"Previsualizar" o "Confirmar" explícitamente. Esto refleja la estrategia (a) del backend
(`arquitectura-tecnica.md` §10, `ServicioImportacionCsv`): no hay un id temporal de
previsualización; `confirmar` reenvía el **mismo archivo completo** y el backend vuelve a
parsear y validar todo desde cero. Consecuencias para la UI:

- Elegir un archivo nuevo limpia cualquier `preview`/`resultado` previo — no tiene sentido
  mostrar la vista previa de un archivo que ya no es el seleccionado.
- El diálogo de confirmación deja explícito que **confirmar vuelve a validar** (§7 del
  objetivo de la tarea): si algo cambió del lado del servidor entre previsualizar y confirmar
  (un cliente se desactivó, la UF se cargó, etc.), el resultado real puede diferir de lo que
  mostró la previsualización. No es un caso a "corregir" en el frontend — es inherente a la
  estrategia elegida por el backend, y el mensaje se lo dice al usuario en vez de ocultarlo.
- Ambas llamadas (`previsualizar`/`confirmar`) usan
  `clienteApiCliente.subirArchivo<T>(ruta, "archivo", file)` — la misma infraestructura
  multipart de Facturas (§6.3), sin nada nuevo que agregar ahí: ambos endpoints son POST
  multipart con un único campo `archivo` que devuelven JSON.

### 7.2 `lib/useNotificarErrorUnaVez.ts` — extraído tras el tercer uso

`NuevaFactura` (§6.2) ya replicaba a mano el criterio de `FormularioDialogo` de notificar el
error general una sola vez por cambio (no en cada render). `ImportarCsv` necesita el mismo
efecto DOS veces (un error de previsualizar y uno de confirmar son estados independientes) —
con eso ya son 3 usos del mismo bloque de 6 líneas, así que se extrajo a
`lib/useNotificarErrorUnaVez.ts` y `NuevaFactura` se migró a usarlo también (sin cambiar su
comportamiento; sus pruebas existentes siguen pasando tal cual). Sigue sin integrarse a
`FormularioDialogo` porque su razón de ser es justamente cubrir pantallas que **no** son un
modal — sería un hook muerto en cualquier formulario que sí usa `FormularioDialogo`.

### 7.3 CSV grandes: paginación 100% en cliente sobre la respuesta ya cargada

`POST /importaciones/previsualizar` no pagina — devuelve TODAS las filas del archivo en una
sola respuesta (es el cálculo completo de un archivo ya subido, no un listado independiente
con su propio endpoint paginable). Para que un CSV de cientos de filas no renderice una tabla
gigante de una sola vez, `components/importacion/TablaPreviewImportacion.tsx` pagina
**en cliente**: corta el arreglo ya recibido con `.slice()` (50 filas por página) y reutiliza
`PanelListado`/`Paginacion` tal cual, pasándoles esa porción como si fuera "la página
actual" — a esos componentes no les importa si la página viene de una nueva consulta al
servidor o de datos que ya están en memoria (mismo truco que `ListaAcuerdos` con
`totalPaginas=1` para el caso "sin paginar", §4.1, solo que acá si hay más de una página).
Se descartó una librería de scroll virtual: hasta unos pocos miles de filas (el volumen
esperable de una carga mensual manual desde Excel) cortar en páginas de 50 alcanza y es
coherente con "simplicidad primero" (§2.3); si algún día se necesitan decenas de miles de
filas, ahí sí valdría la pena reevaluarlo.

### 7.4 Resuelto: el resultado de confirmar ahora expone el contador REAL de `PENDIENTE_UF`

**Discrepancia original (resuelta en P4, `docs/deuda-tecnica.md` punto 2):** la tarea que armó
esta pantalla pedía destacar "la cantidad que quedó en `PENDIENTE_UF`" en el resultado de
confirmar, pero `ImportacionCsvRespuestaDto` solo traía
`totalFilas`/`filasOk`/`filasError`/`estado` — a diferencia de
`ResultadoCicloDto.cantidadPendientesUf` en el ciclo (§5.5). Como parche temporal,
`ResultadoImportacion` **estimaba** el conteo desde la `preview` (contando las filas
`ADVERTENCIA` cuyo mensaje contenía el texto fijo del backend), un estimado que podía diferir
del resultado real si, por ejemplo, la UF se volvía disponible entre previsualizar y
confirmar.

**Estado actual:** el backend expone `ImportacionCsvRespuestaDto.cantidadPendienteUf`
(`Integer`, nullable — real en la respuesta de `confirmar`, `null` en las filas del historial,
que no lo persisten), contado en vivo por `ServicioImportacionCsv.confirmar` mientras arma las
propuestas de esa confirmación puntual. `ResultadoImportacion.tsx` ya NO estima nada: muestra
directamente `resultado.cantidadPendienteUf`, el valor real y definitivo.

La estimación (`lib/importaciones.ts::esSinUf`, sobre las filas `ADVERTENCIA` de la
previsualización) se mantiene, pero **solo** donde sigue siendo legítima: el diálogo de
confirmación PREVIO en `ImportarCsv.tsx`, antes de que la confirmación se haya ejecutado —
etiquetada explícitamente como estimado ("~N (estimado según esta previsualización)..., el
número real se confirma al procesar") para que nunca se lea como el dato definitivo.

El enlace a Propuestas con período (`lib/importaciones.ts::periodoUnicoImportable`) sigue
derivándose de la `preview` tal como antes: si todas las filas importables comparten un único
`AAAA-MM`, se agrega `periodoAnio`/`periodoMes` al enlace; si el CSV mezcla períodos, el
enlace queda solo con `origen=CSV` — esto no cambió, porque el resultado de `confirmar` no
trae el período (solo se resolvió el contador de `PENDIENTE_UF`).

### 7.5 Resuelto: el historial ya no necesita pedir `sort` explícito

**Discrepancia original (P4 y anteriores):** a diferencia de `EjecucionCicloRepositorio` (que
ya ordenaba en el nombre del método derivado, §5.6), `ImportacionCsvRepositorio.findByEmpresaId`
no tenía un orden por defecto — sin pedirlo, Spring Data devolvía las filas en el orden que le
resultara más cómodo a la consulta (no necesariamente por fecha). `HistorialImportaciones`
compensaba pidiendo el orden explícitamente vía el parámetro estándar de `Pageable`
(`sort=fechaImportacion,desc`).

**Solución aplicada (P5, deuda-tecnica.md ítem 3, estandares-de-codigo.md §3.8):** se definió
la convención de orden por defecto — los listados de eventos/registros temporales ordenan por
su fecha/timestamp descendente sin que el cliente tenga que pedirlo. `ImportacionCsvRepositorio`
gana el mismo patrón que `EjecucionCicloRepositorio`:
`findByEmpresaIdOrderByFechaImportacionDesc(...)`. `HistorialImportaciones` ya no envía
`sort=fechaImportacion,desc` — era puramente un workaround para compensar la falta de orden
por defecto, y con el backend ordenando ya no aporta nada.

### 7.6 Dónde quedaron las pantallas

Importación tiene su propio nav item (`/importacion`, ya existía como placeholder) y anida el
historial debajo, mismo criterio que Ciclo→Historial (§5.6) y Facturas (§6.5):

- `/importacion` — flujo de dos fases (`ImportarCsv`): ayuda de formato + plantilla
  descargable, selector de archivo, previsualización y confirmación.
- `/importacion/historial` — historial de importaciones (`HistorialImportaciones`).

Ambas pantallas están permitidas para **ambos** roles por igual
(`hasAnyRole('ADMINISTRADOR','OPERADOR')` en las tres operaciones del backend, sin excepción)
— igual que Facturas (§6.5), ninguna pantalla de Importación usa `useTieneAlgunRol`.

---

## 8. Informe de facturación

Sexta y última tanda de pantallas de negocio (ver nota de cierre en §9 sobre la numeración de
"Etapa"): `GET /api/v1/informes/facturacion` (resumen + detalle paginado juntos) y
`GET /api/v1/informes/facturacion/export` (CSV). Pantalla de solo lectura, ambos roles — sin
ninguna acción que escriba nada.

### 8.1 Filtros: período exacto Y rango, combinables (se intersectan)

`FiltroInformeFacturacion` (backend) acepta `periodoAnio`/`periodoMes` (período exacto) Y,
por separado, `anioMesDesde`/`anioMesHasta` (rango multi-período, codificado `AAAAMM`) — los
dos mecanismos pueden combinarse (se intersectan con AND), aunque lo usual es usar uno u
otro. `InformeFacturacion.tsx` expone ambos a la vez, sin ocultar ninguno tras un selector de
"modo": Año (número) + Mes (`Seleccion`) para el período exacto, mismo patrón que
Propuestas/Ciclo; Desde/Hasta con `<input type="month">` para el rango — el único filtro de
rango de períodos de la app hasta ahora, así que no había un patrón previo que replicar; el
`<input type="month">` nativo entrega directamente `"AAAA-MM"`, que se codifica a `AAAAMM`
numérico (`codificarAnioMes`) antes de mandarlo. Un texto en `CampoFormulario` aclara que se
intersectan si se usan ambos.

`estados` es una LISTA (`List<EstadoPropuesta>` en el backend) — a diferencia del `estado`
singular de Propuestas (§5.1). Se expone como un grupo de checkboxes (uno por estado, sin
selección = sin filtrar por estado) en vez de un `<select multiple>` nativo (peor UX, exige
ctrl+click). Esto obligó a extender `lib/query.ts::construirQueryString` para aceptar
arreglos, serializándolos como parámetros REPETIDOS (`estados=PENDIENTE&estados=FACTURADA`)
— la forma en que Spring MVC enlaza un `@RequestParam List<T>` sin depender de un converter
de "coma-separado" adicional.

`clienteId` (`SelectorCliente`), `origen` (`Seleccion`, mismo patrón que Propuestas §5.1) y
`facturada` (`Seleccion` de tres valores: Todas/Facturadas/No facturadas) completan el set —
los cuatro filtros que el backend ofrece y que la tarea pedía. Cambiar cualquier filtro
resetea la página a 0 y dispara un solo fetch que trae resumen + detalle juntos (mismos
filtros, misma respuesta — ver §8.3).

### 8.2 Resumen: la exclusión de totales a la vista, nunca en letra chica

`components/informes/ResumenInforme.tsx`, seteado exactamente como pide
`arquitectura-tecnica.md` §11 y lo pedía la tarea:

- **Totales** (`netoClp`/`ivaClp`/`totalClp`, tarjetas con `TarjetaEstadistica`, §8.5): un
  rótulo fijo junto al título — "Solo Pendiente + Facturada — excluye Pendiente UF y Anulada"
  — no un tooltip ni una nota al pie; es la primera cosa que se lee junto al total.
- **Desglose por estado**: los 4 estados con su cantidad (`cantidadPorEstado`, 0 si no hay),
  reutilizando `BadgeEstadoPropuesta` para el mismo lenguaje de color que Propuestas (§5.4) —
  ninguna paleta nueva.
- **`PENDIENTE_UF` destacado**: si `cantidadPendienteUf > 0`, un callout ámbar (mismo tono que
  el resto de advertencias de la app — Ciclo §5.5, Importación §7) con el conteo y un enlace a
  `/facturacion?estado=PENDIENTE_UF` que además propaga `periodoAnio`/`periodoMes`/
  `clienteId`/`origen` si el informe los tiene activos — el subconjunto de filtros que
  `ListaPropuestas` sabe interpretar (no tiene rango de períodos ni `facturada`). Si la
  cantidad es 0, el callout simplemente no se renderiza — nunca "0 pendientes" ocupando
  espacio para no decir nada.

`porCliente` (el desglose por cliente que también trae `InformeFacturacionResumenDto`) se
espeja en el tipo (`types/informeFacturacion.ts`) para que sea un espejo completo del backend,
pero NO se renderiza: la tarea no lo pidió y agregar una tabla más sin un pedido concreto
hubiera sido alcance no solicitado.

### 8.3 `useInformeFacturacion` — por qué no se reutilizó `useListadoPaginado` tal cual

`useListadoPaginado` (§3.1) asume que el `fetcher` retorna directamente una
`PaginaRespuesta<T>`. La respuesta del informe es `{ resumen, detalle }`, donde `detalle` SÍ
es una `PaginaRespuesta<T>` pero el resumen quedaría descartado si se le pasara tal cual.
`lib/useInformeFacturacion.ts` replica la misma forma (dependencias explícitas en el
`useEffect`, `fetcher` deliberadamente fuera del arreglo de dependencias) pero retiene ambas
partes de la respuesta — mismo criterio, sin poder compartir la implementación por la forma
distinta de la respuesta.

### 8.4 Resuelto: el nombre del CSV de export ahora lo arma el backend

**Discrepancia original (P4 y anteriores):** `GET /informes/facturacion/export` devolvía el CSV
con `Content-Disposition: attachment; filename="informe-facturacion.csv"` — siempre ese nombre
genérico, sin período ni filtros. `InformeFacturacion.tsx` compensaba armando su propio nombre
en el cliente (`nombreArchivoExportacion()`) y pasándoselo a `descargarArchivoEnNavegador` en
vez del que traía el header — un workaround aceptable, pero con el origen del nombre en el
lugar equivocado: el backend es quien conoce los filtros aplicados de forma autoritativa.

**Solución aplicada (P5, deuda-tecnica.md ítem 3):** el backend
(`InformeFacturacionControlador.exportar`) arma un nombre descriptivo según el filtro de
período/rango — `informe-facturacion-2026-02.csv` (período exacto),
`informe-facturacion-2026-01_2026-03.csv` (rango) o el genérico `informe-facturacion.csv` (sin
filtro de período) — con la misma construcción segura (`ContentDisposition.attachment()
.filename(nombre, StandardCharsets.UTF_8)`) que ya se usaba en la descarga de PDF de facturas
(§6.3), evitando inyección de encabezados aunque el nombre en este caso solo depende de
enteros ya validados. `InformeFacturacion.tsx` dejó de recalcular el nombre: usa
`descarga.nombreArchivo` (el que `procesarRespuestaBinaria` extrae del `Content-Disposition`)
directamente — `nombreArchivoExportacion()` se eliminó. Mismo mecanismo de descarga autenticada
que el PDF de Facturas (§6.3): fetch con token → blob → `<a>` efímero; nunca un `<a href>`
directo al endpoint (no llevaría el `Authorization`). El export sigue usando los MISMOS filtros
que el detalle en pantalla (sin `page`/`size`: el backend exporta el detalle filtrado completo,
sin paginar).

El CSV lo genera el backend con números crudos, sin formato de moneda
(`arquitectura-tecnica.md` §11) — el frontend no reprocesa el contenido del archivo descargado
en absoluto, solo lo nombra.

### 8.5 Extracciones DRY de esta tarea

Tres piezas que ya se repetían (o iban a repetirse por tercera vez) se promovieron a
compartidas, sin cambiar el comportamiento de las pantallas que ya las usaban (sus pruebas
existentes se corrieron de nuevo tal cual, sin modificar expectativas):

- `lib/etiquetas.ts::ETIQUETAS_ESTADO_PROPUESTA` — antes duplicada local en `ListaPropuestas`
  y `NuevaFactura`; el informe iba a ser la tercera copia.
- `components/ui/TarjetaEstadistica.tsx` — antes local a `TablaPreviewImportacion` (§7.3);
  el informe necesitaba la misma tarjeta etiqueta+valor para sus totales.
- `lib/query.ts::construirQueryString` — ganó soporte de arreglos (serializados como
  parámetros repetidos, ver §8.1); antes solo aceptaba valores escalares.

### 8.6 Dónde quedó la pantalla

`/informes` (el nav item ya existía como placeholder) — una sola pantalla, sin sub-rutas: a
diferencia de Ciclo/Facturas/Importación, el informe no tiene un flujo de creación ni un
historial propio que justifique anidar nada debajo.

**Con esto se completan las pantallas de los seis módulos de negocio de la etapa actual**
(Clientes/Tipos de servicio, Proyectos/Acuerdos, Ciclo/Propuestas, Facturas, Importación CSV,
Informe de facturación) sobre la fundación de §1-§2 — ver la nota de cierre al final del
documento sobre por qué esto NO es la "Etapa 2" que define CLAUDE.md (esa es
emisión electrónica + Crux ERP, y es trabajo de backend, no iniciado).

### 8.7 Snapshot de datos de dev (para pruebas de volumen)

No existe un documento de inventario de datos de dev separado en `docs/` — este es el único
lugar donde se deja registrado, porque es la pantalla cuya prueba visual depende del volumen.

**Snapshot vigente — conteo real contra el Postgres del stack Docker, `SELECT COUNT(*)` directo
(no de memoria), 2026-08-18, tras la pasada visual de R9 (el usuario siguió usando el sistema
entre medio — agregó un cliente y un proyecto nuevos por la UI):** **72 `propuesta_facturacion`
en total**, **5 `proyecto`**, **3 `cliente`**, **2 `tipo_servicio`**.

Por estado: `PENDIENTE` 63, `PENDIENTE_UF` 5, `FACTURADA` 3, `ANULADA` 1.
Por origen: `CSV` 58, `CICLO` 14.

**Qué cambió desde el cierre del sembrado de R9** (que había quedado en 67 propuestas / 4
proyectos / 2 clientes — narrativa completa más abajo, sigue siendo un relato fiel de ESE
momento):
- **Cliente nuevo:** id 3, "Lalo ltda" — sin ninguna propuesta todavía (`0` filas en
  `propuesta_facturacion` con `cliente_id = 3`). No aparece en la tarjeta "Por cliente" del
  dashboard por eso mismo (esa tarjeta solo lista clientes que SÍ tienen propuestas) — no es un
  error, es el comportamiento correcto ante un cliente sin actividad todavía.
- **Proyecto nuevo:** id 5, "crux - lalo" — cliente **2** (Helpcom Ltda, no el cliente nuevo
  pese al nombre), 1,9 UF, MENSUAL, día 15, inicio 2026-08-15, tipo "SaaS Crux ERP", con acuerdo
  `DESCUENTO_PORCENTAJE` 15% vigente 2026-08-15→2027-02-14 (6 meses). Su primera propuesta
  (período 2026-09, primer cobro el mes siguiente al de inicio, sin prorrateo) está en
  `PENDIENTE_UF` — la fecha de facturación (2026-09-15) es futura, sin valor UF publicado
  todavía; es un `PENDIENTE_UF` legítimo (arquitectura-tecnica.md §8/§9), no el defecto ya
  resuelto de deuda-tecnica.md ítem 8.
- **5 propuestas más** de ciclos corridos después del sembrado (proyectos 1-4 siguieron
  facturándose mes a mes) — de ahí que `FACTURADA` suba de 2 a 3 y `PENDIENTE_UF` de 2 a 5 (2
  preexistentes + la de "crux - lalo" + 2 más de meses siguientes de los proyectos ya
  conocidos).

Este es un snapshot puntual del ambiente de dev, no un dato del modelo — cambia con cada
ciclo/importación/alta que se haga después; no se referencia desde código ni pruebas.

**Narrativa histórica del sembrado de R9 (2026-08-18, más temprano el mismo día) — sigue siendo
un relato fiel de ese momento, aunque los números ya no sean los vigentes (arriba):**

**Sembrado dirigido de R9 (2026-08-18), vía dominio con `dev.qa`/ADMINISTRADOR — sin SQL directo,
sin código, sin migraciones:**

- **Tipo de servicio nuevo:** id 2, "Soporte y Mantención" (`POST /api/v1/tipos-servicio`). El
  único tipo previo era id 1, "SaaS Crux ERP".
- **Proyecto 3** — "Mantenimiento de Sistemas" (cliente 1, 8 UF, MENSUAL, día 10, inicio
  2026-05-01, tipo "Soporte y Mantención"), con acuerdo `DESCUENTO_MONTO` 50.000 CLP vigente
  2026-05-01→2026-12-31.
- **Proyecto 4** — "Consultoría TI Mensual" (cliente 2, 6 UF, MENSUAL, día 20, inicio
  2026-05-01, tipo "SaaS Crux ERP"), con acuerdo `PRECIO_PACTADO` 5 UF vigente
  2026-05-01→2026-12-31.
- **Proyecto 1 reclasificado** — vía `PUT /api/v1/proyectos/1` (reemplazo total, con GET previo
  para preservar el resto de los campos intactos): pasó de sin tipo de servicio a "Soporte y
  Mantención". Precio (12 UF), día (15), inicio (2026-01-01) y activo no cambiaron — verificado
  en la respuesta del PUT.
- **Ciclo de mayo y junio 2026 ejecutados** (`POST /api/v1/ciclos/ejecutar`), generando 4
  propuestas nuevas (ids 64-67) — **solo 1 de las 4 salió calculable**, ver el hallazgo abajo.

**Hallazgo real durante el sembrado — inestabilidad de red del contenedor hacia mindicador.cl
(no arreglado, documentado tal cual quedó):** de las 4 fechas UF que este sembrado necesitaba
(2026-05-15, 06-10, 06-15, 06-20), **solo una se pudo obtener** (`06-15`, UF 40.779,55,
persistida en `valor_uf`). Las otras tres fallaron con `Read timed out` en
`FuenteUfMindicador` (verificado en `docker logs facturacion-backend`) pese a que las 4 fechas
responden bien tanto desde el host como en pruebas puntuales de `wget` dentro del propio
contenedor — una medición de 5 intentos seguidos desde dentro de `facturacion-backend` dio
**1/5 éxito**, confirmando que no fue un hiccup aislado sino inestabilidad de red persistente en
el momento del sembrado. Como el ciclo es idempotente por diseño (`uq_prop_ciclo_periodo`,
`V009`, aplica a cualquier fila `CICLO` del período **sin importar su estado**, y
`ServicioCicloFacturacion.procesarProyecto` salta como `YA_EXISTIA` si ya existe una — código
verificado) y el snapshot de la propuesta es inmutable por regla de oro, **estas tres
propuestas quedaron en `PENDIENTE_UF` de forma permanente**: no hay, hoy, ningún camino de
dominio para recalcularlas (coincide con la decisión abierta de `CLAUDE.md` sobre recálculo del
ciclo — hoy simplemente no existe la funcionalidad). Corregirlas requeriría borrar esas filas
específicas por SQL directo o agregar recálculo por código — ambos fuera de alcance de esta
tarea por instrucción explícita.

**Detalle de las 4 propuestas nuevas** (`origen=CICLO`) al cierre del sembrado de R9, antes del
reproceso:

| id | proyecto | período | estado (post-sembrado) | netoClp |
|---|---|---|---|---|
| 64 | 1 — Soporte mensual | 2026-05 | `PENDIENTE_UF` | 0 |
| 65 | 1 — Soporte mensual | 2026-06 | `PENDIENTE` (calculable) | 440.419 |
| 66 | 3 — Mantenimiento de Sistemas | 2026-06 | `PENDIENTE_UF` | 0 |
| 67 | 4 — Consultoría TI Mensual | 2026-06 | `PENDIENTE_UF` | 0 |

**Reproceso de las 3 rotas (2026-08-18, mismo día, tras el fix de deuda-tecnica.md ítem 8 y la
feature de reproceso — ítem 9/`FlujoReprocesoUfE2ETest`):** con el reintento con backoff ya en
la imagen del backend (commit `f03901c`) y el endpoint `PATCH /api/v1/propuestas/{id}/
reprocesar-uf` (commit `1acbaa1`), se reprocesaron las 3, vía dominio con `dev.qa`/
ADMINISTRADOR. **Las 3 salieron exitosas al primer intento** (mindicador.cl respondió limpio
esta vez para las 3 fechas):

| id | proyecto | período | estado final | valorUf | netoClp |
|---|---|---|---|---|---|
| 64 | 1 — Soporte mensual | 2026-05 | `PENDIENTE` | 40.340,86 | **435.681** |
| 66 | 3 — Mantenimiento de Sistemas | 2026-06 | `PENDIENTE` | 40.765,97 | **276.128** |
| 67 | 4 — Consultoría TI Mensual | 2026-06 | `PENDIENTE` | 40.793,13 | **203.966** |

Los tres montos cuadran exactos con lo que anticipaba el plan original del sembrado. Con esto,
**las 4 propuestas de R9 (64-67) quedan todas calculables** — el hallazgo de inestabilidad de
mindicador.cl documentado arriba quedó resuelto operativamente para este ambiente, sin haber
necesitado SQL directo ni tocar código en esta operación de datos (el código ya estaba resuelto
y commiteado de antes).

**Tarjeta "por tipo de servicio" (Escenario B) — ahora con datos reales completos** (suma de
`netoClp` de propuestas `PENDIENTE`/`FACTURADA`, agrupadas por el tipo de servicio del proyecto
tras la reclasificación del proyecto 1):

| Tipo de servicio | Mayo 2026 | Junio 2026 | Julio 2026 |
|---|---|---|---|
| Soporte y Mantención (proyectos 1 y 3) | 435.681 | 716.547 (440.419 + 276.128) | 441.124 |
| SaaS Crux ERP (proyectos 2 y 4) | — | 203.966 | — |

**KPI de descuentos — ahora con los tres tipos calculables:**
- `DESCUENTO_PORCENTAJE` (proyecto 1, mayo+junio+julio): 48.409 + 48.935 + 49.013 = **146.357**
  (≈146.358, la diferencia de $1 es redondeo `HALF_UP` por mes, no un error).
- `DESCUENTO_MONTO` (proyecto 3, junio): **50.000** (monto fijo en CLP, se aplica íntegro una
  vez calculable).
- Total descuentos (% + monto): **196.357** (≈196.358).
- `PRECIO_PACTADO` (proyecto 4, junio, aparte, NO sumado a lo anterior): diferencia entre el
  precio base a la UF del día (6 × 40.793,13 = 244.758,78) y el precio pactado realizado
  (203.966) = **40.793**.

Todas las cifras verificadas contra la API real del stack Docker (`GET /api/v1/propuestas`), no
calculadas a mano sin contrastar.

### 8.8 [RESUELTO — 2026-08-17] `500` al exportar CSV — `produces` restrictivo vs `Accept` fijo del cliente

Bug funcional (no de piel) encontrado por el usuario en la pasada visual de R8: "Exportar CSV"
devolvía "Ocurrió un error inesperado. Contacte al administrador." Detalle completo del
diagnóstico y el fix en `docs/deuda-tecnica.md` ítem 6 — resumen: `lib/clienteApi.ts::
ejecutarFetch` fija `Accept: application/json` en toda solicitud (también las descargas
binarias), y `InformeFacturacionControlador.exportar` estaba mapeado con `produces =
"text/csv"`; la negociación de contenido de Spring rechazaba el request antes de llegar al
controlador (`HttpMediaTypeNotAcceptableException`), sin handler específico en
`ManejadorGlobalErrores`, cayendo al 500 genérico. Fix: se quitó `produces` del `@GetMapping`
(mismo patrón, sin `produces`, que ya usaba `FacturaControlador.descargarPdf`) — el
`Content-Type` real lo sigue fijando el `ResponseEntity` del controlador, el CSV no cambió ni
un byte. No era un problema del panel de filtros ni del re-piel de R8 (que no tocó ese código
en absoluto) ni de los datos (probado con las 63 propuestas reales, incluidas las 58 de origen
CSV con `proyecto_id` nulo). Único endpoint del backend con un `produces` restrictivo — no es
un patrón sistémico, a diferencia del `$NaN` del ítem 5 de deuda técnica.

---

## 9. Identidad visual Helpcom — R1 (docs/plan-rediseno.md)

Primera etapa del rediseño: fundación visual (tokens, tipografía, favicon, logo, navegación
nueva) sobre toda la app. **No** reconstruye el contenido interior de cada pantalla — eso
son R2 en adelante (`docs/plan-rediseno.md`); las tablas, botones y badges de las 14 pantallas
de negocio siguen con la paleta *default* de Tailwind hasta esa etapa.

### 9.1 Tokens (`tailwind.config.ts`)

Colores de marca, neutros y de estado, todos bajo `theme.extend.colors` (nunca hex sueltos en
el JSX):

| Token | Hex | Uso |
|---|---|---|
| `marca.azul` | `#066EE7` | Primario — sidebar, botones primarios, focos |
| `marca.azul-700` / `marca.azul-800` | `#0A57C2` / `#0A3AA0` | Hover/active del primario |
| `marca.celeste` | `#06BBFF` | Acento — borde del ítem de nav activo |
| `marca.azul-50` / `marca.azul-100` | `#EEF5FF` / `#DBE9FF` | Fondos sutiles sobre azul |
| `tinta` | `#0F1B2D` | Texto de máximo contraste |
| `texto` | `#28303C` | Texto base del body |
| `sutil` | `#5B6472` | Texto secundario |
| `tenue` | `#9AA3B2` | Texto terciario / placeholders |
| `linea` / `linea-2` | `#E6EAF1` / `#EEF1F6` | Bordes |
| `fondo` | `#F4F6F9` | Fondo de página |
| `estado.facturada` / `.pendiente` / `.sin-uf` / `.anulada` / `.error` | `#128A45` / `#066EE7` / `#C77700` / `#6B7280` / `#C62F42` | Reservados para R2 (badges de `PropuestaFacturacion`/`EjecucionCiclo`) — **no aplicados todavía**: `BadgeEstadoPropuesta`/`BadgeEstadoEjecucionCiclo` siguen con `sky`/`amber`/`emerald`/`slate` hasta esa etapa. |

`borderRadius`: `sm` 8px, `DEFAULT` 11px, `lg` 14px. `boxShadow`: `suave`/`tarjeta` (tinte
azulado sutil) y `modal` (para diálogos, aplicado recién en R2).

**Tipografía:** Montserrat vía `next/font/google` (`app/layout.tsx`), pesos 400/500/600/700/800,
variable `--font-montserrat`, mapeada como `fontFamily.sans` — es la fuente de toda la app sin
anotar `font-montserrat` en cada componente. Reemplaza los `localFont` de Geist (`app/fonts/`,
eliminado — sin uso). `app/globals.css` fija `--background`/`--foreground` a `fondo`/`texto` y
ya no fuerza `Arial, Helvetica` a mano. También agrega la regla `prefers-reduced-motion:
reduce` (transversal a toda animación/transición futura, no solo la navegación).

### 9.2 Logo — archivos y la decisión de la versión blanca

El logo oficial (`frontend/public/Logo_Helpcom.png`, provisto por Helpcom: wordmark azul
`#0069B4` + isotipo celeste `#21BBEF` con las letras "p"/"c" en negativo blanco) no traía una
variante blanca — necesaria para el sidebar, que es azul pleno y donde el logo a color sería
invisible. Se generó una **una sola vez**, por script (ya no versionado, ver más abajo), con
esta técnica:

- **`logo-helpcom-blanco.png`** (wordmark completo): cada píxel del color de marca (azul o
  celeste) se vuelve blanco sólido, conservando el canal alfa original; cada píxel **blanco**
  del logo original (los recortes en negativo de la "p"/"c") se vuelve **transparente** en vez
  de blanco — así, montado sobre el sidebar azul, el propio fondo se ve a través del hueco,
  reproduciendo el mismo efecto de "negativo" del isotipo original (que recortaba contra el
  celeste), ahora contra el azul de marca. La alternativa simple (aplanar todo a blanco, tipo
  filtro CSS `brightness(0) invert(1)`) se descartó porque el círculo y sus recortes internos
  quedan del mismo blanco y el detalle de la "p"/"c" desaparece — se ve un blob, no el isotipo.
- **`isotipo-helpcom-color.png`** / **`isotipo-helpcom-blanco.png`** (círculo solo, para el
  sidebar colapsado, la barra inferior y el favicon): en este lockup el círculo se **traslapa**
  con las letras "l"/"o" del wordmark (no es un elemento separable por posición/recorte) — un
  recorte por bounding box arrastraba fragmentos de esas letras. Se aisló **por color**: se
  conservan solo los píxeles celestes y blancos (el círculo + sus recortes), se descartan los
  azules (el wordmark), y se recorta al bounding box resultante, con 12% de resguardo.
- **`app/icon.png`** (favicon, convención de Next.js App Router — reemplaza `app/favicon.ico`,
  eliminado): el isotipo a color, sobre lienzo cuadrado 512×512 con resguardo, mismo mecanismo.

El script que generó estos cuatro archivos (`_procesar_logo.py`, Pillow) fue una herramienta de
preparación de assets de un solo uso — **no se versionó** (ver su propio docstring). Si Helpcom
provee más adelante una versión blanca oficial del wordmark, basta con reemplazar
`logo-helpcom-blanco.png` (mismo nombre, mismas proporciones) — nada en el código depende de
que el archivo sea generado en vez de provisto; `BarraLateral`/`app/login/page.tsx` solo
referencian la ruta.

**Dónde se usa cada variante** (docs/plan-rediseno.md §5):

| Componente | Archivo |
|---|---|
| `app/login/page.tsx` | `Logo_Helpcom.png` (color, sobre fondo blanco) |
| `components/shell/BarraLateral.tsx` (sidebar azul) | `logo-helpcom-blanco.png` |
| Favicon (`app/icon.png`) | Isotipo a color |
| Isotipo compacto (reservado — la barra inferior actual usa íconos de Lucide, no el isotipo; ver 9.3) | `isotipo-helpcom-{color,blanco}.png` |

### 9.3 Navegación: `BarraLateral` + `BarraInferior` reemplazan `Navegacion`

`components/shell/Navegacion.tsx` (una fila horizontal de enlaces, usada tanto en escritorio
como en móvil) se **eliminó** y se dividió en dos componentes reales, ambos alimentados por la
misma `lib/navegacion.ts::ENLACES_NAV` (que ahora incluye `icono: LucideIcon`, campo opcional
para no forzarlo en los fixtures de `navegacion.test.ts`):

- **`components/shell/BarraLateral.tsx`** — visible desde `md:` (`hidden md:flex`), columna
  fija de 256px, fondo `marca.azul` pleno: logo blanco arriba, los 6 enlaces (ícono Lucide +
  etiqueta) filtrados por rol igual que antes (`enlacesVisibles`), ítem activo con borde
  izquierdo `marca.celeste` + fondo blanco 10% de opacidad, y el bloque de usuario (nombre/email
  del token) + botón "Cerrar sesión" al final.
- **`components/shell/BarraInferior.tsx`** — visible solo bajo `md:` (`md:hidden`, `fixed
  bottom-0`), los primeros 4 enlaces (`CANTIDAD_PRINCIPAL`) como acceso directo (ícono + etiqueta
  corta, alto 56px ≥ 44px de toque), y los 2 restantes (Importación, Informes) agrupados en un
  menú "Más" (`role="menu"`/`"menuitem"`, con backdrop invisible que lo cierra al tocar fuera).
- **`app/(protegido)/layout.tsx`** compone `BarraLateral` + `Encabezado` + `<main>` +
  `BarraInferior` en un `flex` — cuál se muestra lo decide CSS (`hidden md:flex` /
  `flex md:hidden`), no JS; `<main>` lleva `pb-20` en móvil para no quedar tapado por la barra
  fija.
- **`components/shell/Encabezado.tsx`** se simplificó a una barra superior delgada (título +
  usuario + cerrar sesión), visible en todos los anchos — en escritorio complementa al sidebar
  (que repite el bloque de usuario en su pie); en móvil es la única vía de cerrar sesión, porque
  la barra inferior solo lleva navegación.
- **`components/shell/PlaceholderModulo.tsx`** se eliminó — código muerto detectado en la
  auditoría del plan (cero referencias fuera de sí mismo, quedó de antes de que los 6 módulos
  tuvieran pantalla real).

**Impacto en pruebas, previsto por el plan y no un efecto colateral:** `Navegacion.test.tsx` se
reemplazó por `BarraLateral.test.tsx` y `BarraInferior.test.tsx`, cada uno probando su propio
componente por separado — si ambos coexistieran en un solo test sobre `document`, los 6 enlaces
aparecerían duplicados (jsdom no aplica el CSS que oculta uno de los dos según el ancho de
pantalla) y `getByRole("link", { name })` fallaría por ambigüedad. Cada archivo nuevo cubre los
mismos tres casos que tenía el original (todos los enlaces para OPERADOR/ADMINISTRADOR, ninguno
sin rol reconocido) más 2 casos propios: `BarraLateral` verifica `aria-current` y el bloque de
usuario/logout; `BarraInferior` verifica `aria-current` en el acceso directo y la apertura/cierre
del menú "Más" (con `@testing-library/user-event`, ya presente en el proyecto).

### 9.4 Bug real encontrado y corregido: `middleware.ts` no excluía los assets de `public/`

El `matcher` de `middleware.ts` excluía `_next/static`, `_next/image`, `api/auth` y, como único
archivo suelto, `favicon.ico` — suficiente mientras `public/` no tenía ningún asset propio.
Al agregar el logo (usado, entre otros lugares, en `app/login/page.tsx` — **sin sesión**), el
guard de autenticación interceptaba la petición al archivo y la redirigía a `/login`; el
optimizador de imágenes de Next, al intentar procesar esa respuesta (HTML de la redirección, no
un PNG), fallaba con *"The requested resource isn't a valid image"* — encontrado al verificar
R1 con el frontend corriendo de verdad, no evidente en `next build` ni en las pruebas
(ninguna ejercita el middleware contra un archivo estático real). **Corrección:** el patrón
`.*\..*` se agregó a la exclusión — cualquier ruta con un punto (cualquier archivo con
extensión: `.png`, `.svg`, `.ico`, y los que se agreguen a futuro) queda fuera del guard de
sesión, sin tener que enumerar cada nombre de archivo a mano.

### 9.5 Nueva dependencia: `lucide-react`

Librería de íconos SVG *tree-shakeable* (cada ícono es su propio módulo — el bundle final solo
incluye los que se importan por nombre, `import { Users } from "lucide-react"`, nunca la
librería completa). Usada en `lib/navegacion.ts` (un ícono por módulo) y `BarraInferior.tsx`
(`MoreHorizontal` para "Más"). Única dependencia nueva de R1 — la de gráficas (Recharts u
otra) queda para R9 (`docs/plan-rediseno.md`).

---

## 10. Estructura agregada

```
frontend/
├── proxy.ts                         # protege todas las rutas salvo /login y /api/auth/* (Next 15: middleware.ts)
├── lib/
│   ├── auth.config.ts               # config Edge-safe + guard de rutas (estaAutorizado)
│   ├── auth.ts                      # config completa: provider Keycloak, jwt/session, refresh
│   ├── roles.ts                     # tipo Rol, validación de roles del token
│   ├── useRoles.ts                  # useRoles/useTieneAlgunRol (Client Components)
│   ├── jwt.ts                       # decodificación de payload JWT sin dependencias
│   ├── rutas.ts                     # esRutaPublica
│   ├── navegacion.ts                # ENLACES_NAV + enlacesVisibles (filtro por rol)
│   ├── rut.ts                       # normalizarRut/esRutValido/formatearRut (módulo 11)
│   ├── numero.ts                    # formatearNumeroEsCl/parsearNumeroEsCl (ver §4.4)
│   ├── vigencia.ts                  # estado vigente/futuro/pasado, solape, término desde meses (§4.5)
│   ├── etiquetas.ts                 # labels TipoAcuerdo/Periodicidad/OrigenPropuesta/DisparoCiclo, NOMBRES_MES
│   ├── propuestas.ts                # esMontoAusente/formatearMontoClpOAusente/esAnulable/esFacturable/esReprocesableUf (§5.3, §5.8, §6.1)
│   ├── archivos.ts                  # descargarArchivoEnNavegador (blob → descarga, ver §6.3)
│   ├── csv.ts                       # generarPlantillaCsv (plantilla descargable, ver §7)
│   ├── importaciones.ts             # esSinUf/formatearMontoFilaCsv/periodoUnicoImportable (§7.4)
│   ├── query.ts                     # construirQueryString (con soporte de arreglos, ver §8.1)
│   ├── useListadoPaginado.ts        # data hook del listado genérico (ver §3.1)
│   ├── useInformeFacturacion.ts     # data hook combinado resumen+detalle del informe (§8.3)
│   ├── useFormularioApi.ts          # estado de envío/error del formulario (ver §3.2)
│   ├── useNotificarErrorUnaVez.ts   # toast de error una vez por cambio, fuera de FormularioDialogo (§7.2)
│   ├── clienteApi.ts                # base compartida: ErrorApi, fetch, problem+json, FormData, blob (§6.3)
│   ├── clienteApiServidor.ts        # cliente API para Server Components / Route Handlers
│   ├── clienteApiCliente.ts         # cliente API para Client Components ("use client")
│   ├── errores.ts                   # helpers para mostrar ErrorApi en la UI
│   └── estilos.ts                   # combinarClases (utilidades de className)
├── components/
│   ├── proveedores/ProveedorSesion.tsx
│   ├── shell/{Encabezado,BarraLateral,BarraInferior}.tsx  # BarraLateral/BarraInferior: §9.3
│   ├── listado/{PanelListado,AccionesFila}.tsx    # AccionesFila: Activar/Eliminar opcionales (§4.2)
│   ├── formularios/FormularioDialogo.tsx
│   ├── ui/{Boton,CampoFormulario,Entrada,AreaTexto,Seleccion,Tabla,Paginacion,
│   │        Dialogo,DialogoConfirmacion,Notificaciones,TarjetaEstadistica}.tsx  # TarjetaEstadistica: §8.5
│   ├── clientes/{ListaClientes,FormularioCliente,SelectorCliente}.tsx
│   ├── tiposServicio/{ListaTiposServicio,FormularioTipoServicio,SelectorTipoServicio}.tsx
│   ├── proyectos/
│   │   ├── {ListaProyectos,FormularioProyecto}.tsx
│   │   └── acuerdos/{ListaAcuerdos,FormularioAcuerdo}.tsx
│   ├── facturacion/
│   │   ├── ciclo/{EjecutarCiclo,HistorialCiclos,BadgeEstadoEjecucionCiclo}.tsx
│   │   ├── propuestas/{ListaPropuestas,DialogoDetallePropuesta,BadgeEstadoPropuesta}.tsx
│   │   └── facturas/{ListaFacturas,NuevaFactura,DetalleFactura,SeccionPdfFactura}.tsx  # §6
│   ├── importacion/  # §7
│   │   ├── {ImportarCsv,AyudaFormatoCsv,TablaPreviewImportacion,ResultadoImportacion}.tsx
│   │   ├── HistorialImportaciones.tsx
│   │   └── {BadgeEstadoFilaCsv,BadgeEstadoImportacionCsv}.tsx
│   └── informes/{InformeFacturacion,ResumenInforme}.tsx  # §8
├── types/
│   ├── next-auth.d.ts               # augmentation: accessToken, roles, error en Session/JWT
│   ├── api.ts                       # PaginaRespuesta<T>
│   ├── cliente.ts                   # Cliente, ClienteSolicitud
│   ├── tipoServicio.ts              # TipoServicio, TipoServicioSolicitud
│   ├── proyecto.ts                  # Proyecto, ProyectoSolicitud
│   ├── acuerdoPrecio.ts             # AcuerdoPrecio, AcuerdoPrecioSolicitud
│   ├── propuestaFacturacion.ts      # PropuestaFacturacion (solo lectura, sin Solicitud; numeroFactura/fechaFactura §5.2)
│   ├── factura.ts                   # Factura, FacturaSolicitud, PropuestaResumenFactura (§6)
│   ├── importacionCsv.ts            # ImportacionPreview(Fila/Resumen), ImportacionCsv (§7)
│   ├── informeFacturacion.ts        # InformeFacturacionResumen/DetalleFila/Respuesta (§8)
│   └── ejecucionCiclo.ts            # EjecucionCiclo, ResultadoCiclo, EjecutarCicloSolicitud
├── public/
│   ├── Logo_Helpcom.png              # logo oficial a color (provisto por Helpcom)
│   ├── logo-helpcom-blanco.png       # wordmark blanco, generado — §9.2
│   └── isotipo-helpcom-{color,blanco}.png  # círculo aislado, generado — §9.2
└── app/
    ├── layout.tsx                   # ProveedorSesion + ProveedorNotificaciones; fuente Montserrat (§9.1)
    ├── icon.png                      # favicon (convención App Router) — §9.2
    ├── login/page.tsx                # server action -> signIn("keycloak"); logo a color (§9.2)
    ├── api/auth/[...nextauth]/route.ts
    └── (protegido)/                 # layout con BarraLateral+Encabezado+BarraInferior (§9.3); auth() de refuerzo
        ├── layout.tsx
        ├── page.tsx                 # "Panel" — placeholder del dashboard de R9 (docs/plan-rediseno.md)
        ├── clientes/
        │   ├── page.tsx             # listado de clientes
        │   └── tipos-servicio/page.tsx
        ├── proyectos/
        │   ├── page.tsx             # listado de proyectos
        │   └── [id]/acuerdos/page.tsx
        ├── facturacion/
        │   ├── page.tsx             # listado de propuestas (envuelto en <Suspense>, ver §5.5)
        │   ├── ciclo/
        │   │   ├── page.tsx         # ejecutar ciclo
        │   │   └── historial/page.tsx
        │   └── facturas/            # §6
        │       ├── page.tsx         # listado de facturas
        │       ├── nueva/page.tsx   # crear factura (selección de propuestas)
        │       └── [id]/page.tsx    # detalle de factura + PDF
        ├── importacion/              # §7
        │   ├── page.tsx             # flujo de dos fases
        │   └── historial/page.tsx
        └── informes/page.tsx        # §8
```

---

## 11. Modo desarrollo contra el stack Docker (iterar R1/R2+ con recarga en caliente)

Para iterar visualmente sobre el rediseño (`docs/plan-rediseno.md`) sin reconstruir imágenes de
Docker: `npm run dev` en un puerto libre, reutilizando el backend y el Keycloak **ya
dockerizados** (`docs/despliegue.md`) en vez de levantar otro backend aparte. Tres problemas
reales aparecen al hacer esto — ninguno es específico de R1, son de la fundación OIDC/CORS de
antes — documentados acá para no volver a redescubrirlos.

### 11.1 `.env.local` (nunca versionado)

```bash
cd frontend
npm run dev -- -p <puerto-libre>   # 3000/3001 pueden estar tomados por OTROS proyectos locales
```

```
NEXT_PUBLIC_API_BASE_URL=http://<host>:<puerto>/api/proxy   # ver §11.2, NO apunta directo al backend
BACKEND_PROXY_DESTINO=http://localhost:<puerto-host-backend>/api/v1
AUTH_KEYCLOAK_ISSUER=http://keycloak.localhost:<puerto-host-keycloak>/realms/helpcom  # ver §11.3
AUTH_KEYCLOAK_ID=facturacion-recurrente-frontend
AUTH_KEYCLOAK_SECRET=cambiar-este-secreto-cliente-keycloak   # el mismo placeholder del realm importado
AUTH_URL=http://<host>:<puerto>
AUTH_SECRET=<cualquier valor de desarrollo>
```

**El cliente OIDC del realm debe conocer el `redirect_uri` de este puerto** — el `realm-helpcom.json`
versionado solo trae `http://localhost:3000/api/auth/callback/keycloak` (§9.7 de
`docs/despliegue.md`). Como reimportar el realm no pisa un realm ya existente, agregar un
puerto de desarrollo se hace vía la API de administración de Keycloak, **solo en el contenedor
local, nunca editando el archivo versionado**:

```bash
TOKEN=$(curl -s -X POST http://localhost:<puerto-keycloak>/realms/master/protocol/openid-connect/token \
  -d "client_id=admin-cli" -d "grant_type=password" -d "username=admin" --data-urlencode "password=<KEYCLOAK_ADMIN_PASSWORD de deploy/.env>" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
CLIENT_UUID=$(curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:<puerto-keycloak>/admin/realms/helpcom/clients?clientId=facturacion-recurrente-frontend" \
  | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
# GET el cliente completo, agregar el nuevo redirect_uri/webOrigin a los arreglos existentes
# (sin borrar los que ya había), PUT de vuelta la representación completa.
```

### 11.2 CORS ausente en el backend — hallazgo real, no solo de "modo dev"

`SeguridadConfig` no tiene ningún `.cors(...)`/`CorsConfigurationSource` (verificado: un
preflight `OPTIONS` real contra `/api/v1/clientes` con `Origin` cruzado devuelve **401 sin
ningún header `Access-Control-Allow-*`**). Esto significa que **cualquier navegador real**
—no solo en modo desarrollo— bloquea un `fetch` desde un origen distinto al del backend en
cuanto la petición lleva el header `Authorization` (dispara *preflight*): eso incluye al
propio **frontend dockerizado real** (`docs/despliegue.md`), que sirve en un puerto distinto al
del backend. Ninguna verificación anterior lo detectó porque todas simulaban el navegador con
`curl`, que no aplica CORS. **No se corrigió acá** (arreglarlo de raíz es un cambio de
backend, fuera del alcance de "levantar el entorno de desarrollo" de esta tarea) — quedó como
hallazgo pendiente de una tarea de backend futura (agregar un `CorsConfigurationSource` con los
orígenes permitidos).

**Solución aplicada, solo para el entorno de desarrollo:** `next.config.mjs` agrega un
`rewrites()` que expone `/api/proxy/:ruta*` (mismo origen que la app en dev) y lo reenvía
server-a-server a `BACKEND_PROXY_DESTINO` — el navegador nunca cruza de origen, así que nunca
dispara un *preflight*. `proxy.ts` excluye `api/proxy` de su guard de sesión de páginas
(es un canal de infraestructura, no una página; el backend real ya exige su propio Bearer token).
Inerte en el build de Docker: ahí `NEXT_PUBLIC_API_BASE_URL` sigue apuntando directo al backend,
así que ninguna ruta construye una URL bajo `/api/proxy`.

### 11.3 `keycloak.localhost` — resuelve en navegadores y en `curl`, NO en Node en Windows

`docs/despliegue.md` §5.6 ya documentó por qué el backend valida el claim `iss` del token
contra `http://keycloak.localhost:<puerto>/realms/helpcom` (no `localhost` a secas) — necesario
para que el emisor que ve el navegador y el que ve el propio backend coincidan cuando Keycloak
corre con `KC_HOSTNAME` vacío. En **modo desarrollo, corriendo Next directo en el host (sin
contenedor)**, esto agrega una capa: `lib/auth.ts` necesita que **el propio proceso Node**
también pueda alcanzar esa misma URL para el descubrimiento OIDC y el intercambio de tokens
(`EMISOR_KEYCLOAK_INTERNO`, que sin `AUTH_KEYCLOAK_ISSUER_INTERNO` cae al mismo valor de
`AUTH_KEYCLOAK_ISSUER`) — y en Windows, `curl` resuelve `*.localhost` a loopback (igual que
cualquier navegador, RFC 6761), pero **el `fetch`/`undici` de Node.js NO**: falla con
`getaddrinfo ENOTFOUND keycloak.localhost`, un error que curl nunca muestra porque su propio
resolutor sí trata `.localhost` como caso especial. Verificado exactamente así en este entorno.

**No se puede evitar reescribiendo `AUTH_KEYCLOAK_ISSUER_INTERNO` a `localhost` a secas**: el
`iss` que de verdad viaja en la respuesta de autorización lo decide el navegador (que sí llega
por `keycloak.localhost`), así que si el `issuer` interno de Auth.js queda en un string distinto,
falla la validación `unexpected "iss" response parameter value` — el mismo bug ya documentado en
`docs/despliegue.md` §5.6, solo que ahora en el lado del proceso Node del propio desarrollador,
no de un contenedor.

**Arreglo:** una entrada en el archivo de hosts de Windows
(`C:\Windows\System32\drivers\etc\hosts`, requiere PowerShell/Notepad como Administrador):

```
127.0.0.1  keycloak.localhost
```

Con esa línea, `curl`/navegadores y el propio Node ven exactamente la misma resolución, y todo
el flujo (login, refresh de token, llamadas proxied a la API) funciona sin más cambios. **No se
aplicó en este entorno** — modificar el archivo de hosts del sistema requiere permisos de
administrador que este agente no tiene; queda pendiente de que quien opere la máquina lo agregue
una vez, después de lo cual el modo desarrollo funciona de punta a punta sin volver a tocarlo.

### 11.4 Nunca corras `npm run build` mientras `npm run dev` sigue vivo (mismo `.next`)

Bug real, no solo teórico — encontrado en este mismo entorno: con `next dev -p 3002` corriendo
en segundo plano, se ejecutó `npm run build` (verificación de un cambio de código) **en la misma
carpeta `frontend/`**. `next build` reescribe `.next/server/` con la estructura de *vendor
chunks* de producción, distinta a la que arma `next dev` — el proceso de dev, que seguía vivo
con su propio runtime de webpack ya cargado en memoria, quedó apuntando a archivos que el build
de producción ya había reemplazado. Toda petición a `/login` o `/api/auth/*` empezó a fallar con
`Error: Cannot find module './vendor-chunks/@auth.js'` (`MODULE_NOT_FOUND`) y una cascada de
warnings de resolución de `@panva`, `jose`, `preact` — Auth.js absorbe esa excepción interna y la
muestra en el navegador como el genérico *"Server error — There is a problem with the server
configuration"*, sin ninguna pista de la causa real (que sí queda en el log del proceso `next
dev`, nunca en la respuesta HTTP).

**No hay arreglo "en caliente":** ni reintentar la build ni recompilar alcanzan, porque el
propio proceso de dev ya tiene en memoria referencias rotas. La única salida es la que se aplicó
acá: **matar el proceso `next dev` primero** (mientras siga vivo, tiene archivos de `.next`
abiertos — un intento de borrar la carpeta a mano falla por "acceso denegado", que parece un
problema de permisos de la cuenta pero en realidad es el proceso reteniendo los archivos),
**borrar `frontend/.next` por completo**, y recién ahí volver a levantar `npm run dev`.

**Regla a partir de ahora:** `next dev` y `next build` **nunca** comparten `.next` al mismo
tiempo. Si hace falta verificar que el código compila para producción mientras hay un `next dev`
corriendo para iterar visualmente, hay que elegir uno:
- Detener el `next dev` antes de correr `npm run build`, y volver a levantarlo después (lo más
  simple si no se necesita ver ambos a la vez), o
- Correr la build de verificación en un **checkout/worktree separado** (otra copia del
  repositorio), para que cada una tenga su propia carpeta `.next` sin pisarse.

---

## 12. Revestir componentes compartidos — R2 (docs/plan-rediseno.md)

Segunda etapa del rediseño: los componentes de `components/ui/`, `components/listado/` y
`components/formularios/` (más los badges de estado) pasan a usar los tokens de R2 (§9). Es
**re-piel pura** — ninguna de estas piezas cambió sus props, su comportamiento ni su lógica; las
14 pantallas de negocio las heredan automáticamente sin haber tocado un solo archivo de pantalla
(excepto una demostración puntual, ver §12.3).

### 12.1 Componentes revestidos

| Componente | Qué cambió |
|---|---|
| `Boton` | Variantes re-coloreadas a tokens (`primario`=`marca.azul`, `secundario`=borde `marca.azul`, `peligro`=`estado.error`) — **mismos 3 nombres de variante que antes**, no se agregó "ghost" (no tenía ningún consumidor; se deja para cuando haga falta). Foco con `ring-2 ring-marca-azul` (antes `outline`). `min-h-11` (toque ≥44px). |
| `Entrada` / `AreaTexto` / `Seleccion` | Borde `linea`, foco `marca-azul`, estado inválido `estado-error` — mismo prop `invalida` de antes. `Entrada`/`Seleccion` con `min-h-11`. |
| `CampoFormulario` | Etiqueta/descripción a tokens; el mensaje de error gana el ícono `AlertCircle` (lucide) — el mecanismo de `obtenerErrorDeCampo`/`problema.errores` no se tocó, solo la presentación del mismo string. |
| `Dialogo` / `DialogoConfirmacion` | Cabecera y `backdrop` a tokens, `shadow-modal`, botón de cerrar con el ícono `X` de lucide (reemplaza el glifo `✕` de texto — único emoji/glifo suelto encontrado en toda la base). Mecánica de foco/ESC/cierre intacta (sigue siendo el `<dialog>` nativo). |
| `Paginacion` / `TarjetaEstadistica` | Bordes/texto a tokens. `TarjetaEstadistica` gana `tabular-nums`; **no** se le puso un color de texto por defecto porque dos pantallas (`TablaPreviewImportacion`, `ResumenInforme`) le pasan su propio `className` de color — un default habría competido con esos overrides de forma impredecible. |
| `PanelListado` | Título a `tinta`; filtros en columna en móvil (`flex-col sm:flex-row`); estado de carga con ícono `Loader2` girando; banner de error ahora usa `Alerta` (§12.2). |
| **`Tabla`** | Ver §12.3 — es el cambio más grande de la etapa. |
| `AccionesFila` | Colores a tokens (`sutil`/`marca-azul`/`estado-error`), ganan íconos lucide (`Pencil`/`Power`/`Trash2`) junto al texto — el texto se mantiene (nunca ícono solo) para no perder accesibilidad/`title`. Mismas props, mismo comportamiento deshabilitado/`title`. |
| `BadgeEstadoPropuesta` | `CLASES` retargeteado a `estado.pendiente`/`estado.sin-uf`/`estado.facturada`/`estado.anulada` — mismos 4 estados, misma etiqueta de texto, semántica de color idéntica a la que ya describía `docs/frontend.md` §5.4, ahora con los hex exactos de marca en vez de la paleta *default* de Tailwind. |
| `BadgeEstadoEjecucionCiclo`, `BadgeEstadoFilaCsv`, `BadgeEstadoImportacionCsv` | Mismo retargeteo (éxito→`estado.facturada`, advertencia→`estado.sin-uf`, error→`estado.error`) — reutilizan los tokens de *propuesta* para un lenguaje de color consistente en toda la app, tal como ya documentaba §5.4 antes del rediseño. |

### 12.2 Nuevos componentes compartidos: `Alerta` y `EstadoVacio`

- **`components/ui/Alerta.tsx`** — banner inline (`role="alert"`, variantes `error`/`advertencia`/`info` con su ícono lucide) que reemplaza el `<div role="alert" className="border-red-200 bg-red-50...">` que `PanelListado` y `FormularioDialogo` duplicaban cada uno por su cuenta. Mismo `role="alert"` y mismo texto (`obtenerMensajeError`), así que las pruebas que ya hacían `getByRole("alert")`/`findAllByText(...)` siguen pasando sin tocarlas.
- **`components/ui/EstadoVacio.tsx`** — ícono + título + descripción opcional + acción opcional, el patrón "invitación a actuar" que pedía el sistema de diseño en vez de una fila de texto plano. `Tabla` ya lo usa internamente (con `mensajeVacio` como título, sin descripción ni acción — es todo lo que `Tabla` sabe). Queda disponible para que una pantalla que lo necesite (p. ej. un listado recién estrenado sin ningún registro todavía) lo use directo con su propia acción ("+ Nuevo…") — no se retrofiteó en ninguna pantalla en esta etapa, es trabajo de R3+.

### 12.3 `Tabla` — mayúscula tenue, hover azul, y tarjetas en móvil SIN duplicar el DOM

Encabezado en mayúscula/tenue, filas con `hover:bg-marca-azul-50`, bordes a `linea` — la parte
simple. Lo importante es la responsividad (docs/plan-rediseno.md §7, "nunca scroll horizontal
con datos ocultos"):

**Decisión de arquitectura — un solo árbol DOM, no dos.** La primera versión de este cambio
renderizaba dos bloques JSX en paralelo (una tabla `hidden md:block` para escritorio y una lista
de tarjetas `flex md:hidden` para móvil), igual que el patrón ya usado para
`BarraLateral`/`BarraInferior` en R1. **Se revirtió**: a diferencia de la navegación (dos
componentes con contenido casi todo distinto), acá las dos versiones comparten exactamente las
mismas filas de datos —con sus mismos botones, checkboxes y badges por fila—, así que
duplicarlas en el DOM duplica también cada elemento interactivo. En un navegador real eso no se
nota (`display:none` los excluye), pero en las pruebas (jsdom, sin CSS aplicado) **ambas copias
quedan "visibles" a la vez** — confirmado empíricamente: la primera versión rompió 27 pruebas en
10 archivos distintos (`getByRole`/`findByRole` encontrando dos botones/checkboxes con el mismo
nombre en vez de uno).

**La solución que se aplicó — CSS puro, un solo `<table>`:** por debajo de `md`, `tr`/`td` pasan
a `display: block` (tarjeta apilada) y cada `<td>` gana una etiqueta generada por CSS
(`data-label={columna.encabezado}` + `before:content-[attr(data-label)]`) — nunca un segundo
JSX. Desde `md` en adelante, `md:table`/`md:table-row`/`md:table-cell` restauran la tabla real.
Con un único árbol DOM, las pruebas ven exactamente la misma estructura que antes de R2 (110/110
en verde, sin tocar ningún test de listado) y un navegador real nunca tiene dos copias de nada.

**Limitación conocida y aceptada:** el contenido de un pseudo-elemento CSS (`::before`) no está
garantizado de forma consistente en el árbol de accesibilidad entre lectores de pantalla — en la
vista de tarjeta (móvil), la ETIQUETA de cada campo es decorativa/visual; el VALOR en sí sigue
siendo texto real del DOM, siempre accesible. Es la misma limitación que acepta cualquier patrón
"responsive table" basado en CSS puro (la alternativa —duplicar el DOM— tiene el costo ya
descrito arriba); si en el futuro se vuelve un problema real de accesibilidad, la solución sería
un `<span className="sr-only">` con la etiqueta dentro de cada celda en lugar del `::before`.

**Nueva capacidad opcional, sin romper el contrato:** `ColumnaTabla<T>` gana
`alineacion?: "derecha"` (antes solo `encabezado`/`renderizar`) — sin este campo, una columna se
comporta exactamente igual que antes (alineada a la izquierda). Se aplicó como demostración a
las tres columnas monetarias de `ListaPropuestas` (Neto/IVA/Total) — el resto de las pantallas
con columnas numéricas (Facturas, Informe) pueden sumarlo cuando se revisiten en R3+, es un
cambio de una palabra por columna.

### 12.4 Notificaciones (toast): nueva variante `advertencia`

`TipoNotificacion` gana un cuarto valor, `"advertencia"` (ámbar, ícono `AlertTriangle`), además
de recolorear los tres existentes a tokens (`error`→`estado.error`, `exito`→`estado.facturada`,
`info`→`marca.azul`). Es un agregado puramente aditivo — ninguna pantalla existente llama
`notificar(msg, "advertencia")` todavía (p. ej. `EjecutarCiclo.tsx` sigue notificando su caso
`CON_ADVERTENCIAS` con `"info"`, `docs/frontend.md` §5.5); migrar esas llamadas puntuales es
trabajo de pantalla (R3+), no de este componente compartido.

### 12.5 Verificación

`npm run lint` y `npm run test` (110/110, ningún test tocado — la única pareja de archivos de
prueba que R2 hubiera podido afectar, los de `BarraLateral`/`BarraInferior` de R1, no dependen de
`Tabla` y no se vieron afectados) en verde. **No se corrió `npm run build`** en ningún momento de
esta etapa — el frontend siguió corriendo en modo desarrollo (§11) durante todo el trabajo,
verificado contra el dev server real (compilación sin errores, tokens nuevos presentes en el CSS
compilado — `tabular-nums`, `content: attr(data-label)`, `bg-marca-azul-50`, etc.).

**Revisión visual — RESUELTA (2026-08-18).** Verificada por el usuario en el navegador
(escritorio + móvil, stack Docker real, ambos roles): Clientes, Propuestas e Importación
conformes con la re-piel de marca. Sin hallazgos. Detalle en `docs/plan-rediseno.md` (sección
R2, §9).

---

## 13. Afinado de componentes compartidos — modal, interruptor, selects (R2, continuación)

Cuatro ajustes de presentación puntuales sobre los componentes ya revestidos en §12 — misma
regla: cero cambios de props/lógica/contrato.

### 13.1 Modal con más profundidad

`components/ui/Dialogo.tsx`: sombra `modal` reforzada en `tailwind.config.ts` (dos capas, más
grande — antes una sola capa de `0 10px 25px`), `backdrop:bg-tinta/60` (antes `/50`, un poco más
oscuro para separar mejor la modal del contenido de atrás), encabezado con fondo
`bg-marca-azul-50` + `border-b border-linea` (antes fondo blanco liso, mismo color que el
cuerpo), y una línea divisoria (`border-t border-linea`) sobre la fila de acciones — agregada en
`FormularioDialogo.tsx` y `DialogoConfirmacion.tsx` (cada uno arma su propia fila de botones;
`Dialogo` en sí no sabe nada de "acciones", solo de encabezado + cuerpo). Radios ya venían del
sistema (`rounded-lg`, R1); el cuerpo y las esquinas inferiores ahora se marcan explícitamente
`bg-white`/`rounded-b-lg` para que el contraste con el encabezado azulado quede limpio incluso en
los bordes. Mecánica del `<dialog>` nativo (foco atrapado, ESC, `showModal`/`close`) intacta —
nada de esto la toca.

### 13.2 Título de la modal en azul de marca

`<h2>` del encabezado: `text-tinta` → `text-marca-azul`, mismo peso (`font-semibold`). Contraste
verificado contra el nuevo fondo `marca.azul-50` (prácticamente blanco, mismo ~4.6:1 ya validado
en R1 para `marca.azul` sobre blanco — ver `docs/plan-rediseno.md` §3.1).

### 13.3 Campo "Activo" como interruptor — `components/ui/Interruptor.tsx` (nuevo)

Reemplaza el `<input type="checkbox">` a mano que se repetía igual en `FormularioCliente.tsx`,
`FormularioProyecto.tsx` y `FormularioTipoServicio.tsx` (los tres campos "Estado"/"Activo" de
esas entidades). Por dentro sigue siendo un `<input type="checkbox">` real — mismo `checked`/
`onChange`, mismo envío de formulario, mismo teclado (Espacio alterna, foco con Tab) — con dos
agregados puramente de accesibilidad/presentación:
- `role="switch"` para que un lector de pantalla lo anuncie como interruptor, no como casilla.
- El input queda `sr-only` (no `hidden`: sigue en el árbol de accesibilidad, sigue recibiendo el
  clic que le reenvía el `<label>` que lo envuelve, igual que cualquier checkbox con una etiqueta
  asociada) mientras dos `<span aria-hidden>` (pista + círculo) dibujan el interruptor con
  `peer-checked:`/`peer-focus-visible:` de Tailwind — pista `bg-tenue` apagado, `bg-marca-azul`
  encendido, círculo blanco que se desliza con `translate-x`, anillo de foco azul visible.

**Qué NO se convirtió a interruptor, a propósito:** los checkboxes de selección múltiple —
filtro de "Estados" en `InformeFacturacion.tsx` (varios a la vez, no es un on/off de una sola
entidad) y la selección de filas en `NuevaFactura.tsx` (elegir propuestas para una factura) —
siguen siendo casillas normales. Un interruptor comunica "una sola cosa encendida/apagada"; un
grupo de opciones o una selección de filas es un concepto distinto y un interruptor ahí
confundiría más de lo que aclara.

### 13.4 Selects con flecha propia

`components/ui/Seleccion.tsx`: sigue siendo un `<select>` nativo (teclado, buscar-al-escribir,
todo el comportamiento accesible de fábrica intacto) — se le oculta la flecha del sistema
operativo (`appearance-none`) y se dibuja una propia con `ChevronDown` de lucide, decorativa
(`aria-hidden`, `pointer-events-none`, no intercepta el clic). Cambio de estructura interno: el
`<select>` ahora vive dentro de un `<div className="relative">` para poder posicionar el ícono
encima; el `className` que antes recibía el `<select>` (usado por varias pantallas para
controlar el ancho, p. ej. `"w-full"`) ahora se aplica a ese contenedor — el `id`/`value`/
`onChange`/`disabled`/etc. siguen yendo directo al `<select>` sin cambios, así que cualquier
`<label htmlFor={id}>` externo (`CampoFormulario`) lo sigue asociando correctamente.

### 13.5 Casillas de selección múltiple — `components/ui/Casilla.tsx` (nuevo)

Ajuste posterior a los cuatro de arriba: el usuario notó que los checkboxes de selección
múltiple habían quedado con el aspecto nativo del sistema operativo tras §13.3 (que solo
convirtió los campos "Activo" a interruptor, dejando el resto de los `<input type="checkbox">`
sin tocar a propósito). `Casilla` reviste esos casos — **sigue siendo una casilla**, ni
semántica ni visualmente un interruptor: sin `role="switch"`, el `role="checkbox"` implícito del
`<input type="checkbox">` real que sigue por dentro. Mismo mecanismo que `Interruptor` (input
`peer sr-only` + hermanos `aria-hidden` que dibujan el control con `peer-checked:`): borde
`linea` sin marcar, relleno `marca.azul` + tick blanco (`Check` de lucide, opacidad 0→100 con
`peer-checked:opacity-100`) al marcar, anillo de foco azul, `min-h-11`/`min-w-11` de área de
toque en el `<label>` que lo envuelve.

**Un detalle de accesibilidad que no tiene equivalente en `Interruptor`:** `NuevaFactura.tsx` ya
usaba `title={motivo}` en el checkbox para explicar por qué una fila no es seleccionable (mismo
patrón que `AccionesFila`) — con el `<input>` real ahora `sr-only`, un `title` solo ahí nunca se
vería al pasar el mouse sobre el cuadro VISIBLE (el usuario nunca hace hover sobre el elemento
real, que quedó clippeado fuera de vista). `Casilla` extrae `title` explícitamente y lo aplica
también al `<label>` que envuelve el cuadro visible, para que el tooltip nativo siga apareciendo
donde el usuario de verdad posiciona el cursor.

**Aplicado en:**
- `components/informes/InformeFacturacion.tsx` — filtro de "Estado" (varios valores a la vez,
  `Casilla` con `etiqueta` visible por cada estado).
- `components/facturacion/facturas/NuevaFactura.tsx` — una casilla por fila para elegir qué
  propuestas van a la factura (`Casilla` sin `etiqueta`, con `aria-label` por fila — el mismo que
  ya traía; `disabled`/`title` intactos).

**Qué sigue siendo interruptor, para no confundir los dos:** los campos "Activo" de Cliente/
Proyecto/TipoServicio (§13.3) — la distinción no es solo estética: un interruptor comunica un
único estado on/off de UNA entidad; una casilla comunica una opción dentro de un conjunto (varios
estados a la vez en un filtro) o una selección entre varias filas — mezclar los dos patrones
visuales confundiría cuál es cuál.

### 13.6 Verificación

`npm run lint` + `npm run test` (110/110 en ambos ajustes de esta sección — ningún archivo de
prueba consultaba estos campos por una clase específica del `<select>`/checkbox, solo por
`role`/nombre accesible, que no cambiaron). Verificado además contra el dev server real: los
tokens/utilidades nuevos (`peer-checked:bg-marca-azul`, `peer-checked:opacity-100`,
`appearance-none`, `bg-marca-azul-50`, la sombra `modal` de dos capas) presentes en el CSS
servido. **No se corrió `npm run build`** en ningún momento (dev vivo en `:3002`, §11.4).

**Revisión visual — RESUELTA (2026-08-18).** Verificada por el usuario en el navegador
(escritorio + móvil, stack Docker real, ambos roles): los seis ajustes de esta sección
(§13.1-§13.5) — modal, interruptor, casilla — quedaron conformes. Sin hallazgos. Detalle en
`docs/plan-rediseno.md` (sección R2, §9).

---

## 14. Clientes y Tipos de servicio revisitadas — R3 (docs/plan-rediseno.md)

Primera pantalla de negocio revisitada del rediseño — sienta dos piezas que **R4 reutiliza**
para Proyectos: el badge de "activo" y, sobre todo, el patrón de detalle + subsecciones.

### 14.1 Composición pulida

- **`components/ui/BadgeActivo.tsx`** (nuevo) — reemplaza el texto de color plano
  (`text-emerald-700`/`text-slate-500`) que `ListaClientes` y `ListaTiposServicio` usaban para la
  columna "Estado": mismo lenguaje de badge que `BadgeEstadoPropuesta` (§12), tokens
  `estado.facturada`/`estado.anulada`. Mismo texto exacto ("Activo"/"Inactivo") que antes, así
  que ningún `getByText` de las pruebas existentes se vio afectado.
- **Acceso Clientes ↔ Tipos de servicio, en los dos sentidos:** el enlace
  "Tipos de servicio →" de `ListaClientes` (antes un texto subrayado gris, apenas visible) pasó a
  un botón secundario con ícono (`Settings2` de lucide, borde `marca.azul`) — mismo criterio que
  pedía la tarea ("hazlo claro y visible"). Se agregó el enlace de vuelta, "← Clientes", en
  `ListaTiposServicio` (no existía antes) para que la relación entre ambas pantallas se navegue
  en los dos sentidos, no solo de ida.
- **Verificado sin tocar:** validación de RUT (`lib/rut.ts`, al perder el foco y al enviar),
  mapeo de error por campo (`obtenerErrorDeCampo`), baja lógica/física (`docs/frontend.md` §3.6),
  filtrado por rol (`useTieneAlgunRol`), interruptor "Activo" (§13.3) — nada de esto cambió, la
  tarea era composición + piel.

### 14.2 El patrón "detalle de entidad + subsecciones" — para R4 (y más allá)

Clientes **no** necesita una pantalla de detalle propia: no tiene ninguna subentidad equivalente
a los Acuerdos de Proyecto, así que agregar una ruta `/clientes/{id}` hoy solo duplicaría el
modal de edición ya existente sin ganar nada — se evitó a propósito (`CLAUDE.md`: no diseñar
para hipotéticos). En su lugar, esta etapa deja **construidas y probadas** las dos piezas
reutilizables que sí hacen falta para R4 (`docs/plan-rediseno.md` §4.2, el acceso a
Descuentos/Acuerdos que hoy no es visible desde `/proyectos`):

- **`components/detalle/EncabezadoDetalle.tsx`** — enlace "Volver" al listado, título + un slot
  `subtitulo` (para un badge como `BadgeActivo`) y un slot `acciones` (para un botón "Editar").
- **`components/detalle/PestanasDetalle.tsx`** — barra de pestañas, activa según la ruta actual
  (`usePathname`, mismo mecanismo que `BarraLateral` para el nav principal — sin estado propio).
  Con prueba propia (`PestanasDetalle.test.tsx`): todas las pestañas se muestran, la que coincide
  con la ruta actual lleva `aria-current="page"`.

**Cómo R4 los va a usar** (ya especificado en `docs/plan-rediseno.md` §4.2, confirmado acá con
los componentes ya construidos): `app/(protegido)/proyectos/[id]/layout.tsx` (nuevo) renderiza
`<EncabezadoDetalle titulo={proyecto.nombre} subtitulo={<BadgeActivo activo={proyecto.activo} />}
acciones={<Boton>Editar</Boton>} volverA="/proyectos" />` seguido de
`<PestanasDetalle pestanas={[{ href: "/proyectos/{id}", etiqueta: "Datos" }, { href:
"/proyectos/{id}/acuerdos", etiqueta: "Descuentos" }]} />`, y `{children}` debajo. La pestaña
"Datos" (`page.tsx`, nuevo) muestra los campos del proyecto de solo lectura; la pestaña
"Descuentos" es la ruta `/proyectos/{id}/acuerdos` que **ya existe** (`ListaAcuerdos.tsx`, sin
tocar) — solo pasa a vivir bajo este layout compartido. El botón "Descuentos" de `AccionesFila`
en el listado de `/proyectos` (pendiente, R4) apunta directo a esa ruta.

### 14.3 Verificación

`npm run lint` + `npm run test` (112/112 — 110 + 2 pruebas nuevas de `PestanasDetalle`, ningún
test existente tocado). Verificado contra el dev server real: `/clientes`,
`/clientes/tipos-servicio` compilan y sirven sin error; tokens nuevos (`bg-estado-facturada/10`,
`border-marca-azul`) presentes en el CSS servido. **No se corrió `npm run build`** (dev vivo en
`:3002`, §11.4).

**Revisión visual — RESUELTA (2026-08-18).** Verificada por el usuario en el navegador
(escritorio + móvil, stack Docker real, ambos roles): `/clientes` (listado con el badge nuevo,
el botón "Tipos de servicio", el modal, la vista de tarjetas en móvil) y
`/clientes/tipos-servicio` (enlace de vuelta, mismo badge) quedaron conformes. Sin hallazgos.
Detalle en `docs/plan-rediseno.md` (sección R3, §9).

---

## 15. Proyectos y Descuentos — R4: el detalle de proyecto, y el acceso a Descuentos RESUELTO

La pieza central del rediseño hasta ahora: **el acceso a los acuerdos de precio de un proyecto,
que no existía de forma visible desde ningún lado, queda resuelto** con una pantalla de detalle
nueva, usando el patrón que R3 dejó construido (`EncabezadoDetalle` + `PestanasDetalle`,
`docs/frontend.md` §14.2).

### 15.1 Rutas nuevas

```
app/(protegido)/proyectos/
├── page.tsx                  # listado (sin cambios de ruta)
└── [id]/
    ├── layout.tsx             # NUEVO — cabecera + pestañas, envuelve a los dos de abajo
    ├── page.tsx                # NUEVO — pestaña "Datos"
    └── acuerdos/page.tsx      # YA EXISTÍA — pasa a ser la pestaña "Descuentos"
```

`app/(protegido)/proyectos/[id]/layout.tsx` es un envoltorio delgado (patrón ya establecido:
`page.tsx`/`layout.tsx` en `app/` solo pasan `params` y renderizan un componente de
`components/`) sobre **`components/proyectos/LayoutDetalleProyecto.tsx`** (nuevo, el que hace
todo el trabajo):

- Pide `GET /proyectos/{id}` una vez.
- Renderiza `EncabezadoDetalle` (volver a "Proyectos", título = nombre del proyecto,
  `subtitulo` = `BadgeActivo`, `acciones` = botón "Editar" solo para ADMINISTRADOR — mismo
  criterio de rol que ya usaba `ListaProyectos`) + una línea con cliente/precio/periodicidad, y
  debajo `PestanasDetalle` con exactamente las dos pestañas que pedía la tarea:

  ```ts
  [
    { href: `/proyectos/${id}`, etiqueta: "Datos" },
    { href: `/proyectos/${id}/acuerdos`, etiqueta: "Descuentos" },
  ]
  ```

- Envuelve `{children}` (la pestaña activa) en `ProveedorProyectoDetalle` (ver §15.2).
- El botón "Editar" abre el `FormularioProyecto` **ya existente, sin cambios** — mismo modal que
  usaba `ListaProyectos`, misma validación, mismo mapeo de error por campo.

**`components/proyectos/DatosProyecto.tsx`** (nuevo) es el contenido de la pestaña "Datos":
solo lectura (cliente, tipo de servicio, código, descripción, precio base + moneda,
periodicidad, día de facturación, fechas) — el botón "Editar" vive en la cabecera compartida
(visible también desde "Descuentos"), no duplicado acá.

### 15.2 Por qué un Context acá y no en ningún otro lado del rediseño

Único caso hasta ahora donde se introdujo algo más que tokens/composición: un problema de
sincronización real, no hipotético. Si `LayoutDetalleProyecto` (cabecera) y `DatosProyecto`
(pestaña) pidieran el proyecto CADA UNO por su cuenta (el patrón que sí usa el resto de la app,
p. ej. `ListaAcuerdos` antes de esta etapa), editar desde el botón "Editar" de la cabecera
refrescaría el `proyecto` de la cabecera pero **no** el de `DatosProyecto` — la pestaña activa
quedaría mostrando datos viejos hasta navegar fuera y volver. `components/proyectos/
ContextoProyectoDetalle.tsx` (`useProyectoDetalle()`) comparte el mismo `proyecto`/`recargar`
entre los dos, así que ambos quedan sincronizados con una sola fuente de verdad. Acotado a estos
dos componentes — `ListaAcuerdos` (la otra pestaña) no lo necesita, ver §15.3.

### 15.3 Descuentos (`ListaAcuerdos`) — simplificado, no rediseñado

Con la cabecera ahora en el layout compartido, `ListaAcuerdos.tsx` perdió su propio encabezado
duplicado (el "← Volver a proyectos" + "Acuerdos de precio — {nombre}" que traía antes) — y, con
eso, ya **no necesita pedir el proyecto** (antes lo hacía solo para ese encabezado; los
acuerdos en sí nunca dependieron de `proyecto`). Resultado: una llamada HTTP menos al entrar a
Descuentos, componente más simple. Todo lo demás intacto: paginación (`totalPaginas=1`, sin
paginar, como ya documentaba §4.1), roles, y el flujo completo de `FormularioAcuerdo`.

- **`components/proyectos/acuerdos/BadgeEstadoVigencia.tsx`** (nuevo) reemplaza el texto de
  color plano (`text-emerald-700`/`text-sky-700`/`text-slate-500`) de la columna "Estado" —
  mismo cálculo de `lib/vigencia.ts::calcularEstadoVigencia` (sin tocar), mismo lenguaje de
  badge que el resto de la app: vigente→`estado.facturada`, futuro→`estado.pendiente`,
  pasado→`estado.anulada`.
- Botón "+ Nuevo acuerdo" → **"+ Agregar descuento"** (pedido explícito de la tarea).
- `FormularioAcuerdo.tsx`: solo re-piel de sus tres bloques a mano (el hint de "vigencias ya
  ocupadas", la aclaración de que el precio pactado reemplaza al base, el aviso de solape) a
  tokens — **sin tocar `role="status"`** del aviso de solape a propósito: es una advertencia no
  bloqueante que aparece mientras se escribe, y `role="status"` (anuncio "polite") evita que un
  lector de pantalla interrumpa en cada tecleo, a diferencia de `role="alert"` (que sí usa el
  componente compartido `Alerta`) — reemplazarlo por `Alerta` habría cambiado ese comportamiento
  de accesibilidad, así que se mantuvo la implementación a mano. Campos condicionales por tipo,
  modo de vigencia (fecha/meses), y el 409 `ACUERDO_TRASLAPADO` — verificados intactos por los
  tests existentes de `FormularioAcuerdo.test.tsx`, sin tocar ninguno.

### 15.4 Listado de Proyectos

- **`BadgeActivo`** reemplaza el texto de color plano en la columna "Estado" (mismo patrón que
  R3 en Clientes/Tipos de servicio).
- El nombre del proyecto en la columna "Nombre / código" ahora es un enlace
  (`/proyectos/{id}`) — el punto de entrada al detalle. El link de texto "Acuerdos" que antes
  vivía en la columna de acciones **se eliminó**: el camino ahora es entrar al detalle y usar la
  pestaña "Descuentos", más descubrible y consistente con el resto de la app.

### 15.5 Verificación

`npm run lint` + `npm run test` (120/120 — 8 pruebas nuevas: `LayoutDetalleProyecto.test.tsx`
cubre la cabecera con datos reales, el badge, las dos pestañas con sus `href` correctos, cuál
queda `aria-current` según la ruta, el botón "Editar" gateado por rol y que abre el formulario, y
el estado de error; `DatosProyecto.test.tsx` cubre que renderiza los campos del proyecto
compartido por contexto, incluidos los guiones para los opcionales ausentes — ninguna prueba
existente se tocó, todas seguían pasando con la sola re-piel de `ListaAcuerdos`/
`FormularioAcuerdo`/`ListaProyectos`). Verificado además contra el dev server real: las tres
rutas nuevas/afectadas (`/proyectos`, `/proyectos/{id}`, `/proyectos/{id}/acuerdos`) compilan y
responden sin error 500 (redirigen a `/login` sin sesión, como corresponde). **No se corrió
`npm run build`** (dev vivo en `:3002`, §11.4).

**Revisión visual — RESUELTA (2026-08-17):** verificada en el navegador (escritorio + móvil,
stack Docker real en `:13000`/`:18080`) el flujo completo — listado de Proyectos → entrar a un
proyecto → pestaña Datos → pestaña Descuentos → "Agregar descuento" con vigencia solapada →
aviso `role="status"` → guardar. Sin hallazgos. Guion permanente en
`docs/guion-qa-manual.md`; detalle en `docs/plan-rediseno.md` §8 (R4).

---

## 16. Propuestas y Ciclo de facturación — R5: re-piel visual (docs/plan-rediseno.md)

Alcance acotado a propósito: **solo re-piel visual** sobre las pantallas que §5 de este mismo
documento ya describe funcionalmente — sin tocar lógica, contrato con el backend ni migraciones.

### 16.1 Los badges de estado ya estaban en tokens de marca — desde R2, no R5

Antes de tocar nada, se leyó el código: `BadgeEstadoPropuesta.tsx` y
`BadgeEstadoEjecucionCiclo.tsx` (ambos con la semántica de color de §5.4 arriba) **ya usaban**
`bg-estado-*/10 text-estado-*` — quedaron en la lista de archivos de R2 (§12.1), no en la de
R5. Se dejaron intactos; el trabajo real de R5 fue el resto de cada pantalla, que seguía en
paleta Tailwind default (`slate-*`/`amber-*`/`red-*`).

### 16.2 Qué se re-pieló, heredando en vez de reinventando

- **`EjecutarCiclo.tsx`:** la cabecera a mano (link "Volver" + `<h1>`) se reemplazó por
  `components/detalle/EncabezadoDetalle.tsx` (el mismo componente de R4) — mismo resultado
  visual, un componente menos duplicado. El banner de error y el callout de `PENDIENTE_UF` se
  reemplazaron por `components/ui/Alerta.tsx` (`variante="error"`/`"advertencia"`, R2) en vez de
  `<div role="alert" className="border-red-200 bg-red-50...">`/`<p className="border-amber-300
  bg-amber-50...">` a mano.
- **`HistorialCiclos.tsx`:** el link "← Volver a propuestas" (texto plano, `slate-600`) pasó al
  mismo patrón que `ListaTiposServicio.tsx` (R3) — ícono `ArrowLeft` de `lucide-react` +
  `text-sutil hover:text-marca-azul`.
- **`ListaPropuestas.tsx`:** "Ver detalle" al mismo tono neutro que "Editar"/"Activar" de
  `AccionesFila.tsx` (`text-sutil hover:text-marca-azul`, R2); "Anular" al mismo tono que
  "Eliminar" de ese mismo componente (`text-estado-error hover:text-estado-error/80`) — el
  `disabled:opacity-40` que ya traía no se tocó, sigue dando el atenuado correcto sobre el color
  nuevo. Los tres enlaces de cabecera (Facturas / Historial de ciclos / Ejecutar ciclo →)
  pasaron a `text-sutil hover:text-marca-azul`, con "Ejecutar ciclo →" en `text-marca-azul`
  (más énfasis, es la acción principal de esa fila).
- **`DialogoDetallePropuesta.tsx`:** las filas del snapshot (`slate-100`/`slate-500`/
  `slate-900`) al mismo lenguaje que `DatosProyecto.tsx` (R4): `border-linea-2`/`text-sutil`/
  `text-texto`.
- **`app/(protegido)/facturacion/page.tsx`:** el `<Suspense>` fallback ("Cargando…") a
  `text-sutil`, mismo patrón que `LayoutDetalleProyecto.tsx` (R4).

### 16.3 Verificación

`npm run lint` (limpio) + `npm run test` (**127/127** — 120 previos + 7 nuevos, §16.4 — incluye
`ListaPropuestas.test.tsx`, `EjecutarCiclo.test.tsx`, `HistorialCiclos.test.tsx` sin tocar, la
re-piel de color no cambió ningún texto/rol/atributo que las pruebas verificaran) + `npm run
build` (build de producción completo sin errores — a diferencia de R1-R4, en este entorno no
había un `npm run dev` vivo compartiendo `.next`, así que sí se pudo correr de punta a punta, no
solo contra el dev server).

### 16.4 El estado `ERROR` del badge de ciclo — cubierto por prueba unitaria, no por dato real

Al armar datos para la revisión visual (ANULADA/FACTURADA sí se generaron con datos reales, vía
los endpoints reales de anular/facturar) quedó claro que `ejecucion_ciclo.estado = ERROR` **no
tiene una vía de negocio real** que lo produzca: `ServicioCicloFacturacion` procesa cada
proyecto en su propia transacción con captura de `RuntimeException` (javadoc de la clase: "más
consistente con la filosofía de avanzar todo lo posible"), así que cualquier falla de negocio
por proyecto (UF no disponible, tasa IVA faltante, etc.) queda absorbida como
`CON_ADVERTENCIAS` — nunca escala a `ERROR` de la ejecución completa. Provocar un `ERROR` real
exigiría romper algo fuera del procesamiento por proyecto (la fila `empresa`, usada por
`ContextoEmpresa` en TODA la app, o la conexión a Postgres a mitad de una ejecución) — ninguna
opción segura sobre un Postgres compartido real.

Por eso ese render se cubre con `BadgeEstadoEjecucionCiclo.test.tsx` (nuevo — los tres estados:
`EXITOSA`→`estado-facturada`, `CON_ADVERTENCIAS`→`estado-sin-uf`, `ERROR`→`estado-error`) y,
de paso, `BadgeEstadoPropuesta.test.tsx` (nuevo — los cuatro estados de §5.4). Ambos badges ya
estaban correctamente implementados desde R2 (§16.1); estos tests solo cierran el hueco de
cobertura que dejaba a `ERROR` sin verificar de ninguna forma — no corrigieron ningún mapeo.

**Revisión visual — RESUELTA (2026-08-17):** verificada en el navegador (escritorio + móvil,
stack Docker real) con **ambos roles** — ADMINISTRADOR (`dev.qa`) para
listar/filtrar/detalle/anular/ejecutar ciclo/historial, y OPERADOR (`dev.qa.operador`) para
confirmar el botón "Ejecutar ciclo"/"Anular" deshabilitado con la paleta nueva, el punto de
verificación explícito del plan. Sin hallazgos. **R5 queda cerrada** — igual que R4 (§15).

---

## 17. Facturas — R6: re-piel visual (docs/plan-rediseno.md)

Mismo alcance acotado que R5: **solo re-piel visual**, sin lógica ni contrato. `BadgeEstadoPropuesta`
(reutilizado en `DetalleFactura.tsx`) ya estaba en tokens de marca desde R2 — verificado, no
tocado.

**Archivos tocados:** `ListaFacturas.tsx` (link de fila y "+ Nueva factura" a tokens de marca —
este último, al ser un `<Link>` de navegación y no un `<button>`, replica a mano las clases de
`Boton` variante `primario` en vez de reusar el componente, que exige un `<button>`);
`DetalleFactura.tsx` (cabecera → `EncabezadoDetalle`, R4 — cambio de estructura menor: el link
"Volver" deja de mostrarse durante carga/error, mismo patrón que `LayoutDetalleProyecto`, R4);
`SeccionPdfFactura.tsx`; `NuevaFactura.tsx` (banner de cliente restringido →
`Alerta variante="info"`, R2; botón "Refrescar listado" del error `PROPUESTA_NO_FACTURABLE`
movido fuera de `Alerta` para no anidar un `<button>` dentro de su `<span>` interno).

**Mapeo de estados de PDF** (atención especial pedida): "sin PDF" no tenía (y sigue sin tener)
ningún color propio — se muestra el formulario de subida en vez del bloque "con PDF", ya
neutro. "Con PDF": nombre de archivo a `text-texto` (cuerpo neutro). Errores reales de subida
(`ARCHIVO_DEMASIADO_GRANDE`, etc.) → `Alerta variante="error"` (rojo, correcto — son errores de
verdad, no un estado "sin PDF").

**Verificación:** lint + test (sin pruebas nuevas — pura re-piel) + build, todos verdes.

**Revisión visual — RESUELTA (2026-08-17):** verificada por el usuario en el navegador
(escritorio + móvil, stack Docker real) con ambos roles — listar, crear una factura
(selección de propuestas + subtotal, con la propuesta id 5), ver el detalle, y
subir/descargar PDF (con y sin PDF, usando la factura F-2026-0001 y una nueva). Datos de
prueba generados por la vía real para esta revisión: factura con PDF subido, una propuesta
PENDIENTE con monto real (ejecutando el ciclo para un período con UF real de mindicador.cl,
verificado en vivo — cierre de QA-3, `docs/qa.md` §6). Sin hallazgos. Guion permanente en
`docs/guion-qa-manual.md` (Caso P), que vigila explícitamente los dos cambios de
comportamiento de arriba (link "Volver" ausente durante carga/error; "Refrescar listado"
fuera del recuadro de error). **R6 queda cerrada.**

---

## 18. Importación CSV — R7: re-piel visual (docs/plan-rediseno.md)

Mismo alcance acotado. `BadgeEstadoFilaCsv.tsx`/`BadgeEstadoImportacionCsv.tsx` ya estaban en
tokens de marca desde R2 — verificado, no tocados. Tampoco se tocaron las comparaciones
`== null` de `TablaPreviewImportacion.tsx` (fix del `$NaN`, tarea anterior, `docs/deuda-tecnica.md`
ítem 5) ni ningún tipo TS — solo `className`.

**Archivos tocados:** `AyudaFormatoCsv.tsx`, `ImportarCsv.tsx` (cabecera; tarjeta "Archivo";
banners de error de previsualizar/confirmar → `Alerta variante="error"`),
`HistorialImportaciones.tsx` (link "Volver a importar" al patrón `ArrowLeft` + `text-sutil
hover:text-marca-azul` ya establecido en `HistorialCiclos.tsx`/`ListaTiposServicio.tsx`),
`ResultadoImportacion.tsx` (avisos ámbar → `Alerta variante="advertencia"`),
`TablaPreviewImportacion.tsx` (solo los mensajes de fila por estado y las 4 `TarjetaEstadistica`
del resumen — nada de su lógica de paginación en cliente ni sus comparaciones de monto).

**Mapeo estado de fila → token** (ya existía en `BadgeEstadoFilaCsv.tsx`, confirmado sin
tocar): `OK`→`estado-facturada` (verde), `ADVERTENCIA`→`estado-sin-uf` (ámbar),
`ERROR`→`estado-error` (rojo) — mismo lenguaje que el resto de la app.

**Hallazgo incidental, no tocado (fuera de alcance de esta tarea de re-piel):**
`ResultadoImportacion.tsx` tiene `resultado.cantidadPendienteUf !== null` — mismo patrón que el
del ítem 5 de deuda técnica, pero se verificó en `ServicioImportacionCsv.java:127` que en la
respuesta de `confirmar` (el único contexto donde se usa este componente) el valor es siempre
un `int` primitivo, nunca `null` — no es un bug vivo. Se deja anotado, sin tocar, por la misma
razón que `DialogoDetallePropuesta.tsx` en el ítem 5 no necesitaba el fix mecánico ahí.

**Verificación:** lint + test (**134/134**, sin tocar ninguna prueba existente, incluidas las
del fix del `$NaN`) + build, todos verdes.

**Revisión visual — RESUELTA (2026-08-17):** verificada por el usuario en el navegador
(escritorio + móvil, stack Docker real) con ambos roles (ADMINISTRADOR/OPERADOR): el flujo de
dos fases completo y el historial, incluida la paginación en cliente de
`TablaPreviewImportacion` (§7.3) cruzando de la página 1 a la 2, con los tres estados de fila
(OK/ADVERTENCIA/ERROR) mostrando la paleta nueva en ambas páginas. CSV usado para esa revisión:
`csv-prueba-r7-importacion.csv` (raíz del repo) — 60 filas (55 OK, 3 ADVERTENCIA por fecha
discordante con el período, 2 ERROR por RUT inexistente), formato exacto de
`modelo-de-datos.md` §6, verificado contra `POST /importaciones/previsualizar` real (mismo
desglose exacto: `filasOk:55, filasAdvertencia:3, filasError:2`). Con el tamaño de página de 50
(§7.3), cae en 2 páginas — caso de QA manual equivalente y reproducible por cualquiera:
**Caso Q** de `docs/guion-qa-manual.md`. **R7 queda cerrada.**

---

## 19. Informe de facturación — R8: re-piel visual (docs/plan-rediseno.md)

Mismo alcance acotado: solo `className`, nada del cálculo/armado del informe ni de la
exportación CSV. `BadgeEstadoPropuesta` (§8.2) ya estaba en tokens de marca desde R5 —
verificado, no tocado. `PanelListado`, `CampoFormulario`, `Entrada`, `Seleccion`, `Casilla` y
`SelectorCliente` (usados por el panel de filtros, §8.1) ya estaban en tokens desde R2/R3 —
verificados uno por uno antes de empezar, no tocados: el layout de filtros (contenedor
`flex flex-col ... sm:flex-row sm:flex-wrap sm:items-end` de `PanelListado`) tampoco se tocó,
solo los campos que ya venían tokenizados.

**Archivos tocados:**
- `InformeFacturacion.tsx` — un solo `className` (`text-slate-900` → `text-tinta` en el `<h1>`).
  Todo lo demás de la pantalla (filtros, tabla, botón de exportar) ya venía de componentes
  compartidos sin colores propios.
- `ResumenInforme.tsx` — el único archivo con re-piel real: los tres recuadros de "Totales"/
  "Cantidad de propuestas por estado" (`border-slate-200`/`text-slate-900`/`text-slate-500` →
  `border-linea`/`text-tinta`/`text-sutil`), la tarjeta "Total" (`text-emerald-700` →
  `text-estado-facturada`, mismo verde que `FACTURADA` en el badge — no un verde nuevo), y el
  callout de `PENDIENTE_UF` (`<div>` a mano con `border-amber-300 bg-amber-50 text-amber-800` →
  `<Alerta variante="advertencia">`, componente compartido de R2 que ya usa `estado-sin-uf` —
  mismo ámbar que el badge `PENDIENTE_UF` de Propuestas, R5).
- **No tocados** (ya en tokens, verificado antes de empezar): `app/(protegido)/informes/page.tsx`
  (envoltorio delgado), `BadgeEstadoPropuesta.tsx`, `TarjetaEstadistica.tsx`,
  `components/ui/{PanelListado,CampoFormulario,Entrada,Seleccion,Casilla}.tsx`,
  `components/clientes/SelectorCliente.tsx`.

**Callout PENDIENTE_UF:** confirma con `estado-sin-uf` (vía `Alerta variante="advertencia"`,
§2 de `Alerta.tsx`) — el mismo ámbar que el badge `PENDIENTE_UF` de `BadgeEstadoPropuesta`
(R5) y que las advertencias de Ciclo (§5.5) e Importación (§7, §18). Ningún color nuevo.

**Panel de filtros (el más cargado de la app — 8 controles: Año, Mes, Desde, Hasta, Cliente,
Origen, Facturación, checkboxes de Estado):** no requirió layout propio — hereda el contenedor
de `PanelListado` (§3 de este documento), que en escritorio los distribuye en fila con
`flex-wrap` (cada `CampoFormulario` ocupa su ancho natural y salta de línea cuando no cabe,
alineados por su base con `items-end`) y en móvil los apila a ancho completo
(`flex-col` por defecto, `sm:flex-row` recién desde el breakpoint). El grupo de checkboxes de
Estado usa su propio `flex flex-wrap gap-3` interno (ya existía, sin tocar) para no obligar a
una fila completa por cada estado.

**Volumen (63 propuestas reales tras la importación de R7, §8.7):** la tabla de detalle sigue
paginada por API (`TAMANO_PAGINA = 20`, igual que antes) — a diferencia de la previsualización
de Importación CSV (§7.3), el informe nunca trajo todas las filas de una vez, así que el
volumen nuevo no cambia su mecánica de paginación, solo la cantidad de páginas disponibles
para probarla (antes 1 página con los ~5 datos de CICLO, ahora hasta 4 con las 63 reales).

**Bug funcional encontrado en esta misma pasada visual, y ya cerrado por separado (no era de
piel):** "Exportar CSV" devolvía 500. No era del panel de filtros, el resumen ni el volumen —
detalle completo en §8.8 y `docs/deuda-tecnica.md` ítem 6.

**Verificación:** `npm run lint` (limpio) + `npm run test` (**134/134**, sin tocar ninguna
prueba existente — `InformeFacturacion.test.tsx` no hace ninguna aserción sobre clases
Tailwind) + `npm run build` (sin errores). Backend/E2E no se tocaron.

**Revisión visual — RESUELTA (2026-08-18).** Verificada por el usuario en el navegador
(escritorio + móvil, **ambos roles**) contra el stack Docker real: el panel de filtros completo
(los 8 controles), el resumen con el callout `PENDIENTE_UF`, la exportación CSV — ya con el fix
de §8.8/deuda-tecnica.md ítem 6 en firme, el `500` por `produces` restrictivo — y la tabla de
detalle paginada con volumen real. Sin hallazgos. **R8 queda CERRADA** — mismo estándar que
R4-R9.

---

## 20. Subida a Next 16 + React 19 — cierre de `docs/deuda-tecnica.md` ítem 1 (2026-08-17)

Sondeada primero en una copia aislada (sin git en ese momento), reportada, y aplicada en firme
recién con la confirmación del usuario — mismo criterio que el resto del rediseño: no se
mezcla con re-piel ni con lógica de negocio, es su propio commit. Detalle completo del
diagnóstico y las alternativas evaluadas en `docs/deuda-tecnica.md` ítem 1 (RESUELTO).

**Cambios:** `next` 14.2.35→16.3.1, `react`/`react-dom` ^18→^19.2.8, `eslint-config-next`
14→16.3.1, script `lint` de `next lint` a `eslint .` (Next 16 quitó el CLI integrado).
`next-auth@5.0.0-beta.32` no cambió — su propio `peerDependencies` ya declaraba soporte para
`next@^16.0.0`/`react@^19.0.0`.

**Codemod `next-async-request-api`** — `params`/`searchParams` de Server Components ahora son
`Promise`, con `await` en cada uno: `app/(protegido)/facturacion/facturas/[id]/page.tsx`,
`app/(protegido)/proyectos/[id]/acuerdos/page.tsx`, `app/(protegido)/proyectos/[id]/layout.tsx`,
`app/login/page.tsx`. Es el único cambio de código real que exigió la subida — el resto de los
codemods de la migración (`middleware-to-proxy`, `remove-experimental-ppr`, etc.) no tuvieron
nada que transformar en este código.

**`middleware.ts` → `proxy.ts`:** Next 16 deprecó la convención de archivo `middleware.ts`
(sigue funcionando, pero con warning de build) en favor de `proxy.ts` — mismo mecanismo, solo
el nombre. Contenido sin cambios de lógica: el `matcher` con la exclusión de assets estáticos
(`.*\..*`, el bug real de §9.4) sigue byte a byte igual, verificado.

**ESLint — de `next lint` a `eslint .` (flat config):** `eslint.config.mjs` nuevo, usando
`FlatCompat` para reexportar `eslint-config-next/core-web-vitals` y `/typescript`;
`.eslintrc.json` queda sin uso pero no se borró (ESLint 9+ con un flat config presente ignora
el legacy por completo). **`eslint` fijado en `^9.39.5`, NO en `^10`**: `eslint-plugin-react`
(dependencia anidada de `eslint-config-next@16`, v7.37.5, la última publicada al momento de
subir) usa `context.getFilename()`, removido en ESLint 10 — su propio `peerDependencies` topa
en `^9.7`. Con ESLint 10 el lint no reporta hallazgos: **crashea** (`TypeError:
contextOrFilename.getFilename is not a function`). `@eslint/js` y `@eslint/eslintrc` se
agregaron como devDependencies — el `eslint.config.mjs` los importa pero el codemod que lo
genera no los declara.

**Regla nueva `react-hooks/set-state-in-effect` (de `eslint-plugin-react-hooks@7`, que trae
`eslint-config-next@16`) — relajada a `warn`, a propósito:** marca el patrón `setCargando(true);
setError(null);` al inicio de un `useEffect` de fetching, usado en 6 sitios:
`lib/useListadoPaginado.ts`, `lib/useInformeFacturacion.ts`,
`components/proyectos/LayoutDetalleProyecto.tsx`, `components/proyectos/acuerdos/ListaAcuerdos.tsx`,
`components/facturacion/facturas/DetalleFactura.tsx`, `components/clientes/SelectorCliente.tsx`.
Comentario explícito en `eslint.config.mjs` señalando que el refactor de esos 6 sitios queda
como deuda técnica nueva y separada (`docs/deuda-tecnica.md` ítem 1) — deliberadamente no
mezclada con el bump de dependencias.

**`tsconfig.json`:** lo reescribió `next build` solo (no el codemod): `jsx: "preserve"` →
`"react-jsx"` (obligatorio), agregó `target: "ES2017"` e incluyó los tipos de Turbopack dev.
Mecánico, sin impacto funcional.

**Vulnerabilidades:** `npm audit fix` (sin `--force`, sin salto mayor) resolvió la 6ª
vulnerabilidad ALTA (`nanoid`, transitiva de `postcss`, apareció después del sondeo original) —
combinada con que next@16.3.1 + eslint-config-next@16.3.1 ya sacaban las 5 originales
(`next`, `postcss` empaquetado, `glob`, `@next/eslint-plugin-next`, `eslint-config-next`):
**`npm audit` → 0 vulnerabilidades.**

**Verificación:** `next build` limpio con Turbopack (sin flags, es el bundler por defecto en
Next 16), sin el warning de `middleware`; `output: "standalone"` sigue generando `server.js`
autocontenido; `npm run lint` → 0 errores, 6 warnings (los del punto anterior); suite frontend
**134/134**, sin tocar ningún test; `npm audit` → 0 vulnerabilidades. Imagen Docker real
reconstruida (`docker compose build frontend` + `up -d frontend`) y flujo OIDC completo
ejercido contra el Keycloak real del stack — login vivo con **ambos roles**:

```
[ADMIN dev.qa]           roles: [ADMINISTRADOR] | accessToken OK | GET /api/v1/clientes -> 200
[OPERADOR dev.qa.operador] roles: [OPERADOR]    | accessToken OK | GET /api/v1/clientes -> 200
```

`proxy.ts` verificado en el contenedor real: `/` sin sesión → 307 a `/login`; `/login` pública
→ 200; `/Logo_Helpcom.png` sin sesión → 200 (el patrón `.*\..*` de §9.4 sigue vivo). Backend/E2E
no se tocaron — cambio exclusivamente de `frontend/`.

**Deuda nueva, no cerrada acá:** el refactor de los 6 sitios `react-hooks/set-state-in-effect`
(`docs/deuda-tecnica.md` ítem 7) — tarea aparte, deliberadamente no mezclada con este commit.

---

## 21. Dashboard nuevo — R9 (docs/plan-rediseno.md)

A diferencia de R1-R8 (re-piel de pantallas existentes), R9 construye pantalla **nueva**:
`app/(protegido)/page.tsx` deja de ser un saludo con "el dashboard llega después" y pasa a
mostrar 6 tarjetas reales, calibradas al dato real del ambiente de dev tras el sembrado
dirigido y el reproceso de UF (§8.7): 67 propuestas, muy desiguales (58 de 67 sin proyecto,
concentradas en un solo mes de importación CSV histórica) — el diseño de cada tarjeta tuvo que
lidiar explícitamente con eso, no con un dataset parejo de ejemplo.

### 21.1 Por qué NO se usó `GET /informes/facturacion`

Antes de escribir una sola línea de UI se verificó qué exponía el backend hoy. El informe de
facturación (§8) da un `resumen` agregado — pero **sin** desglose por tipo de servicio, por
proyecto, ni por tipo de acuerdo (`InformeFacturacionResumenDto`: `cantidadPorEstado`,
`cantidadPendienteUf`, `netoClp`/`ivaClp`/`totalClp`, `porCliente` — nada más). El dashboard SÍ
necesita esos tres desgloses (KPI de descuentos, por tipo de servicio, por proyecto), así que no
alcanzaba. En vez de construir un endpoint de agregación nuevo (fuera de alcance de R9 — "NO
tocar backend salvo que falte un endpoint", y esto no calzaba con "falta", calzaba con "el
existente no alcanza pero uno más genérico sí"), el dashboard consume **dos listados que ya
existen** y agrega todo client-side:

- `GET /propuestas?size=500` — cada fila trae `acuerdoTipo`/`acuerdoValor`/`acuerdoMoneda`,
  `proyectoId`, `valorUf`, `netoClp` y `estado`: todo lo que hace falta para las 6 tarjetas.
- `GET /proyectos?size=500` — para el *join* `proyectoId` → `tipoServicioNombre` que la
  propuesta no trae directo (la propuesta no tiene un tipo de servicio propio; lo hereda de su
  proyecto).

Con 67 propuestas y 4 proyectos reales, una sola página de `size=500` trae todo (muy por debajo
del `max-page-size` de Spring Data, sin configurar en este backend). **Esto no escala
indefinidamente** — mismo caveat ya anotado en `plan-rediseno.md` §6.2 para "tendencia mensual":
si el volumen crece mucho, hace falta un endpoint de agregación real (`GET
/informes/facturacion` ampliado, o uno nuevo dedicado); no se construyó acá a propósito, es una
decisión consciente de fase, no una limitación no vista. `lib/useDashboardDatos.ts` documenta
esto en su propio comentario.

**Una sola fuente compartida** por las 6 tarjetas (a diferencia del §6.1 original, que
imaginaba 6+ *fetches* independientes: informe×2, ciclos, clientes, proyectos, importaciones,
facturas) — con el nuevo alcance, todas las tarjetas dependen genuinamente de las MISMAS dos
listas, así que compartir un solo `Promise.all` es la arquitectura correcta, no una
simplificación que sacrifique el aislamiento de fallos: si esa fuente falla, cada tarjeta
(`components/dashboard/TarjetaDashboard.tsx`, envoltorio compartido) lo muestra en su propio
lugar — la pantalla no se cae completa, cada `<Alerta variante="error">` queda en su tarjeta.

### 21.2 Las 6 tarjetas

| Tarjeta | Componente | Qué muestra |
|---|---|---|
| Descuentos realizados | `TarjetaKpiDescuentos.tsx` | `DESCUENTO_PORCENTAJE` + `DESCUENTO_MONTO` sumados en un total; `PRECIO_PACTADO` aparte, en ámbar (`estado-sin-uf`), NO sumado — decisión de negocio ya tomada. |
| Comparación de períodos | `TarjetaComparacionPeriodos.tsx` | MoM y YoY, ver §21.3. |
| Por tipo de servicio, por mes | `GraficoPorTipoServicio.tsx` (Recharts, barras apiladas) | Serie mensual de neto calculable por tipo de servicio, con "Sin clasificar" como categoría más. |
| Por proyecto | `GraficoPorProyecto.tsx` (Recharts, barras horizontales) | Neto calculable por proyecto, con "Sin proyecto" como barra más. |
| Por cliente | `TarjetaPorCliente.tsx` (lista, no gráfico) | Neto calculable + desglose de cantidad por estado — con solo 2 clientes reales, una lista con más detalle por fila lee mejor que un gráfico disperso. |
| Pendientes de UF | `CalloutPendienteUf.tsx` | Igual criterio que `ResumenInforme` (R5): cuenta aparte, nunca sumada; si es 0 no se renderiza nada. |

Todo el cálculo vive en `lib/dashboardCalculos.ts` (funciones puras, sin React — 18 pruebas
unitarias en `dashboardCalculos.test.ts`), separado de los componentes de presentación —
permite probar la lógica sin montar nada.

**"Calculable"** (`lib/propuestas.ts::esCalculable`, nueva, reutilizada de la misma política del
informe): solo `PENDIENTE` y `FACTURADA` aportan a cualquier suma o gráfico. `PENDIENTE_UF`
(snapshot incompleto) y `ANULADA` (decisión de negocio) quedan siempre afuera — verificado con
una prueba dedicada en cada función de `dashboardCalculos.ts` (`no suma nada de una propuesta
PENDIENTE_UF ni ANULADA aunque tenga acuerdo`), no solo asumido.

**KPI de descuentos — fórmula:** descuento realizado = precio de lista (precio base convertido a
CLP con la UF de la propia propuesta, o directo si ya está en CLP) menos `netoClp` — la misma
resta sirve para los 3 tipos de acuerdo, sin reimplementar las ramas de `CalculadoraFacturacion`
del backend. Verificado contra las 3 cifras reales del sembrado de R9 (`docs/frontend.md` §8.7):
`DESCUENTO_PORCENTAJE` = 146.358, `DESCUENTO_MONTO` = 50.000 (total 196.358),
`PRECIO_PACTADO` = 40.793 aparte — las 3 dieron exactas contra la prueba unitaria al primer
intento.

**"Sin clasificar"/"Sin proyecto" — honestidad deliberada:** ambos gráficos incluyen esa
categoría/barra en un tono neutro (`COLOR_TENUE`, `lib/coloresGrafica.ts`), nunca la omiten ni
la esconden, aunque sea — con datos reales — la porción más grande (58 de 67 propuestas, toda la
importación CSV histórica sin `codigo_proyecto`). Verificado con pruebas dedicadas
(`pone las propuestas sin proyecto ... en "Sin clasificar", visible, no omitida`).

### 21.3 MoM y YoY — el hueco real de enero, y el primer año sin 2025

Con datos reales, 58 de las 67 propuestas (toda la importación CSV histórica) caen en un solo
mes (enero 2026); el resto (proyectos reales del ciclo) recién tiene actividad desde mayo. Un
MoM ingenuo ("mes actual contra el período anterior en la lista") compararía mayo contra enero y
mostraría una caída de -100% — un hueco de 4 meses disfrazado de tendencia, indistinguible de un
bug real.

**Regla implementada** (`lib/dashboardCalculos.ts::calcularMoM`): comparar el último mes con
datos SOLO contra el mes CALENDARIO inmediatamente anterior, y solo si ese mes también tiene
datos. Si no, `disponible: false` — la tarjeta muestra "Sin dos meses consecutivos con datos
todavía para comparar", nunca un porcentaje inventado.

**YoY** (`calcularYoY`): mismo criterio, contra el mismo mes del año anterior. Con datos reales
(nada de 2025) nace `disponible: false` a propósito — comportamiento correcto también en
producción durante el primer año del sistema, no un caso sin manejar. Nunca se muestra un
"-100%" por la ausencia total de un período de comparación.

La tendencia por tipo de servicio (§21.2), en cambio, SÍ muestra el hueco real: el rango de
meses va del primero al último con datos, sin saltarse los intermedios — un mes sin propuestas
calculables queda en 0, visible en el eje, no se omite de la serie. Es la otra mitad de la
misma decisión: la tendencia se lee completa con sus huecos reales; el MoM/YoY, que resume un
solo número, nunca finge comparar algo que no es comparable.

### 21.4 Colores para Recharts

Recharts pinta SVG directo (`fill`/`stroke`) — no puede leer clases de Tailwind. `lib/
coloresGrafica.ts` centraliza los hex tomados 1:1 de `tailwind.config.ts` (`colors.marca`/
`colors.estado`), con un comentario explícito de que deben mantenerse sincronizados a mano si la
paleta de marca cambia — una sola fuente para los 2 gráficos, no duplicado por componente.

### 21.5 Verificación

`npm run lint` (limpio, 0 errores; 7 *warnings* de `react-hooks/set-state-in-effect` — 6
preexistentes, deuda-tecnica.md ítem 7 + 1 nuevo del mismo patrón ya aceptado,
`lib/useDashboardDatos.ts`) + `npm run test` (**156/156** — 134 previas intactas + 22 nuevas: 18
de `dashboardCalculos.test.ts`, 4 de `Dashboard.test.tsx` que montan el árbol completo con
`clienteApiCliente` mockeado y cubren carga/error/vacío/con-datos) + `npm run build` sin errores.

**Revisión visual — RESUELTA (2026-08-18).** Verificada por el usuario en el navegador
(escritorio + móvil, **ambos roles**, contra el stack Docker real — con la imagen del frontend
reconstruida sobre el commit `94e54da`, ver la nota de despliegue sobre este mismo patrón de
imagen desactualizada más abajo): las 5 tarjetas — descuentos realizados (sus 3 tipos, pactado
siempre aparte), por tipo de servicio y por proyecto (con "Sin clasificar"/"Sin proyecto"
visibles, sin disimular), comparación de períodos (MoM sin un "-100%" falso ante el hueco real
de datos, YoY con su estado vacío digno en vez de una caída inventada) y el callout de
`PENDIENTE_UF` — resolviendo sin *layout shift* perceptible, en ambos anchos. Sin hallazgos.
**R9 queda CERRADA** — mismo estándar de cierre que R4-R8.

---

*Fin del documento — Fundación de Frontend + Clientes/Tipos de Servicio + Proyectos/Acuerdos de
precio + Ciclo/Propuestas (+ botón "Reprocesar UF", §5.8) + Facturas + Importación CSV +
Informe de facturación + Identidad visual Helpcom R1 (§9, **cerrada**) + Componentes
compartidos revestidos R2 (§12, **cerrada** — revisión visual verificada por el usuario) +
Afinado de modal/interruptor/selects (§13, **cerrada** — revisión visual verificada por el
usuario) + Clientes/Tipos de servicio revisitadas y patrón de detalle R3 (§14, **cerrada** —
revisión visual verificada por el usuario) + Proyectos y Descuentos, detalle de proyecto R4
(§15) + Propuestas y Ciclo de facturación, re-piel visual R5 (§16, cerrada) + Facturas, re-piel
visual R6 (§17, cerrada) + Importación CSV, re-piel visual R7 (§18, cerrada) + Informe de
facturación, re-piel visual R8 (§19, **cerrada** — revisión visual verificada por el usuario) +
Subida a Next 16 + React 19 (§20, aplicada) + Dashboard nuevo R9 (§21, **cerrada** — revisión
visual verificada por el usuario, escritorio + móvil, ambos roles). Con esto, **los seis
módulos de negocio de la etapa actual (CLAUDE.md: "desarrollo, sobre la arquitectura ya
definida") tienen pantalla propia, y las nueve etapas del rediseño visual
(`docs/plan-rediseno.md` R1-R9) quedan IMPLEMENTADAS Y VERIFICADAS VISUALMENTE** — no queda
ninguna verificación visual pendiente (detalle completo, incluida la base de cada cierre, en
`docs/plan-rediseno.md` §9). Quedan únicamente **2 ítems bloqueados a la espera de material
oficial de Helpcom** (escala neutra §3.1 y sombras exactas §3.3 de `docs/plan-rediseno.md`) —
no son deuda de implementación ni de verificación, sino insumo pendiente del cliente. No
confundir con la "Etapa 2" de CLAUDE.md (emisión electrónica + integración Crux ERP), que es
trabajo de **backend** todavía
no iniciado.*
