# Plan de rediseño — Identidad visual Helpcom ("Confianza")

**Estado:** **R1-R8 — hechas** (ver detalle en sus secciones más abajo y en `docs/frontend.md`
§9, §12, §13, §14, §15, §16, §17, §18 y §8). **R6 y R7 — verificadas y cerradas. R8 queda
pendiente de revisión visual del usuario** antes de darse por cerrada (ver su sección). **El
acceso a Descuentos/Acuerdos de precio — pendiente desde la auditoría inicial del plan — quedó
RESUELTO en R4** (§4.2/§15: detalle de proyecto con pestañas "Datos"/"Descuentos"). R9 sigue
siendo plan, no implementación — se ejecuta y se verifica por separado, y se marca aquí como
hecha cuando corresponda.

**Alcance:** re-piel visual + navegación + un dashboard nuevo, sobre el frontend ya construido
(`docs/frontend.md`). **No** es un cambio de contrato con el backend, **no** toca lógica de
negocio, **no** crea migraciones (la próxima sigue siendo `V012`). Todo lo que ya está probado
(241 tests backend/E2E, 110 frontend tras R1, 112 tras R3, 120 tras R4, 127 tras R5, 134 tras
R7) debe seguir pasando al final de cada etapa — cuando una etapa obligue a tocar un test
existente, este documento lo dice explícitamente por adelantado (ver R1-R5).

---

## 1. Auditoría del frontend actual

### 1.1 Rutas/pantallas existentes

| Ruta | Componente | Módulo backend |
|---|---|---|
| `/login` | `app/login/page.tsx` | Auth.js / Keycloak |
| `/` | `app/(protegido)/page.tsx` | — (placeholder: saludo + roles, sin KPIs) |
| `/clientes` | `ListaClientes` + `FormularioCliente` (modal) | `clientes` |
| `/clientes/tipos-servicio` | `ListaTiposServicio` + `FormularioTipoServicio` (modal) | `clientes` (catálogo auxiliar) |
| `/proyectos` | `ListaProyectos` + `FormularioProyecto` (modal) | `proyectos` |
| `/proyectos/{id}/acuerdos` | `ListaAcuerdos` + `FormularioAcuerdo` (modal) | `proyectos` (acuerdos de precio = "descuentos") |
| `/facturacion` | `ListaPropuestas` | `facturacion` |
| `/facturacion/ciclo` | `EjecutarCiclo` | `facturacion` (ciclo) |
| `/facturacion/ciclo/historial` | `HistorialCiclos` | `facturacion` (ciclo) |
| `/facturacion/facturas` | `ListaFacturas` | `facturacion` |
| `/facturacion/facturas/nueva` | `NuevaFactura` | `facturacion` |
| `/facturacion/facturas/{id}` | `DetalleFactura` + `SeccionPdfFactura` | `facturacion` |
| `/importacion` | `ImportarCsv` | `importacion` |
| `/importacion/historial` | `HistorialImportaciones` | `importacion` |
| `/informes` | `InformeFacturacion` + `ResumenInforme` | `informes` |

14 pantallas reales + login + el placeholder de inicio. Árbol completo de archivos:
`docs/frontend.md` §10 (§9, agregado por R1, documenta la identidad visual en sí).

### 1.2 Archivos que definen hoy layout, navegación, tokens y componentes compartidos

| Qué | Archivo(s) | Estado actual |
|---|---|---|
| **Layout raíz** | `app/layout.tsx` | Fuente **Geist** (`next/font/local`, `app/fonts/GeistVF.woff`/`GeistMonoVF.woff`) — la plantilla por defecto de `create-next-app`, nunca reemplazada por Montserrat. |
| **Tokens de color** | `tailwind.config.ts` | Solo `background`/`foreground` (dos variables CSS). **Sin ninguna paleta de marca** — todo el resto de la app usa la paleta *default* de Tailwind (`slate`, `sky`, `amber`, `emerald`, `red`) directamente en cada componente. |
| **CSS global** | `app/globals.css` | `--background:#fff`, `--foreground:#171717`, `font-family: Arial, Helvetica, sans-serif` (ni siquiera usa la variable de Geist declarada en `layout.tsx`). |
| **Shell autenticado** | `app/(protegido)/layout.tsx` | `Encabezado` + `Navegacion` apiladas arriba, `<main>` debajo. Sin sidebar, sin barra inferior, sin lógica responsiva propia (delega en el navegador). |
| **Encabezado** | `components/shell/Encabezado.tsx` | Texto plano "Facturación Recurrente / Helpcom Ltda." — **sin logo** (no hay ningún archivo de imagen en `public/`, que hoy solo tiene `.gitkeep`). Nombre/correo de sesión + botón "Cerrar sesión". |
| **Navegación** | `components/shell/Navegacion.tsx` + `lib/navegacion.ts` | Una sola fila horizontal de enlaces de texto (`ENLACES_NAV`, 6 ítems: Inicio, Clientes, Proyectos, Facturación, Importación, Informes), activo = `bg-slate-900`. Ya filtra por rol (`enlacesVisibles`) — la regla provisional actual es "todos ven todo". |
| **Favicon** | `app/favicon.ico` | El ícono por defecto de Next.js, nunca reemplazado. |
| **Botón** | `components/ui/Boton.tsx` | 3 variantes (`primario`/`secundario`/`peligro`) ya con foco visible (`focus-visible:outline`) — la mecánica de accesibilidad ya existe, solo hay que retargetear colores. |
| **Campos** | `components/ui/{CampoFormulario,Entrada,AreaTexto,Seleccion}.tsx` | Estilo `slate` genérico. |
| **Tabla** | `components/ui/Tabla.tsx` | `<table>` HTML plano con `overflow-x-auto` — **sin ninguna variante para móvil** (no colapsa a tarjetas; en una pantalla angosta se lee con scroll horizontal). Es el componente compartido por **todos** los listados de la app (Clientes, Proyectos, Propuestas, Facturas, Importación, Informe) — un solo lugar que arreglar beneficia a las 6+ pantallas que lo usan. |
| **Paginación** | `components/ui/Paginacion.tsx` | Sin verificar tamaño de toque; a revisar en R2. |
| **Modal / confirmación** | `components/ui/{Dialogo,DialogoConfirmacion}.tsx` | Sobre `<dialog>` nativo — mecánica de foco/ESC ya resuelta, solo hay que retargetear radios/sombras/colores. |
| **Notificaciones (toast)** | `components/ui/Notificaciones.tsx` | A revisar tipos éxito/error/advertencia contra la nueva semántica de color. |
| **Estadística** | `components/ui/TarjetaEstadistica.tsx` | Ya existe (usado en el resumen del CSV y del informe) — candidato directo a reutilizarse como tarjeta de KPI del dashboard (R9). |
| **Badges de estado** | `components/facturacion/propuestas/BadgeEstadoPropuesta.tsx`, `components/facturacion/ciclo/BadgeEstadoEjecucionCiclo.tsx` | Semántica de color ya **conceptualmente** correcta (neutro/advertencia/éxito/inactivo) pero con la paleta *default* de Tailwind (`sky`/`amber`/`emerald`/`slate`), no con los hex de marca del sistema de diseño. |
| **Listado genérico** | `components/listado/{PanelListado,AccionesFila}.tsx` + `lib/useListadoPaginado.ts` | Patrón sólido, sin tokens de marca — se hereda automáticamente al revestir sus piezas internas (`Tabla`, `Boton`, etc.), sin tocar su lógica. |
| **Formulario genérico** | `components/formularios/FormularioDialogo.tsx` + `lib/useFormularioApi.ts` | Ídem — se hereda al revestir `Dialogo`/`Boton`/`CampoFormulario`. |
| **Login** | `app/login/page.tsx` | Tarjeta centrada genérica, botón `bg-slate-900` — sin logo, sin paleta de marca. |
| **Código muerto detectado** | `components/shell/PlaceholderModulo.tsx` | No lo importa ningún otro archivo (`grep` confirma cero referencias fuera de sí mismo) — quedó de la fundación, antes de que los 6 módulos tuvieran pantalla real. Se elimina en R1 al tocar el shell; no es parte del sistema de diseño, es limpieza incidental. |

**Conclusión de la auditoría:** no existe ningún token de marca hoy — todo color/tipografía es
la paleta *default* de Tailwind o CSS por defecto de `create-next-app`. Esto es bueno para el
rediseño: no hay que "deshacer" nada específico de Helpcom, solo introducir los tokens y
reasignar clases.

---

## 2. Mapeo del sistema de diseño sobre lo existente

| Categoría | Elementos | Tratamiento |
|---|---|---|
| **Se reviste** (mismo componente, mismo comportamiento, nuevas clases) | `Boton`, `CampoFormulario`/`Entrada`/`AreaTexto`/`Seleccion`, `Tabla` (+ variante móvil), `Paginacion`, `Dialogo`/`DialogoConfirmacion`/`FormularioDialogo`, `Notificaciones`, `TarjetaEstadistica`, `BadgeEstadoPropuesta`/`BadgeEstadoEjecucionCiclo`, `PanelListado`, `AccionesFila` | R2 — un cambio, 6+ pantallas heredan. |
| **Se rehace** (estructura nueva) | Shell autenticado (`app/(protegido)/layout.tsx`), `Encabezado`, `Navegacion` (se divide en `BarraLateral` + `BarraInferior`, ver §4), `app/login/page.tsx`, tipografía (`Geist` → Montserrat), `tailwind.config.ts`, `app/globals.css`, favicon | R1. |
| **Se crea nuevo** | Logo en `public/` (variantes color/blanco/isotipo), dashboard (`app/(protegido)/page.tsx` reescrito + componentes nuevos en `components/dashboard/`), página de detalle de proyecto con pestañas (`/proyectos/{id}`, ver §4.2) | R1 (logo), R9 (dashboard), R4 (detalle de proyecto). |
| **No se toca** | Toda la lógica de datos: `lib/useListadoPaginado.ts`, `lib/useFormularioApi.ts`, `lib/clienteApi*.ts`, `lib/errores.ts`, `lib/rut.ts`, `lib/numero.ts`, `lib/vigencia.ts`, `lib/propuestas.ts`, `lib/importaciones.ts`, `lib/query.ts`, todos los `types/*.ts`, toda la lógica de `auth.ts`/`auth.config.ts`/`roles.ts`/`useRoles.ts`, y el backend completo. | — |

---

## 3. Tokens de diseño para Tailwind

### 3.1 Colores (`tailwind.config.ts` → `theme.extend.colors`)

Confirmados por la marca (dados en esta tarea):

```ts
colors: {
  azul: {
    DEFAULT: "#066EE7",   // primario de marca — botones primarios, enlaces activos, sidebar
    oscuro: "#0552B5",    // hover/active del primario (derivado, a validar contra el manual)
  },
  celeste: {
    DEFAULT: "#06BBFF",   // acento/secundario — focos, resaltes, isotipo
  },
  estado: {
    facturada: "#128A45",   // verde — éxito (antes emerald-500/600 aprox.)
    pendiente: "#066EE7",   // azul — neutro (reutiliza el azul de marca, NO un azul distinto)
    "sin-uf": "#C77700",    // ámbar/naranja — advertencia (antes amber-600 aprox.)
    anulada: "#6B7280",     // gris — inactivo (antes slate-500/600 aprox.)
    error: "#C62F42",       // rojo — error duro (antes red-600)
  },
}
```

**Neutros (`tinta`/`sutil`/`linea`/`fondo`) — PROPUESTA, pendiente de confirmar contra el
manual de marca completo:** el encargo de esta tarea da los hex exactos de azul/celeste y de
los 5 estados semánticos, pero no los de la escala neutra. Se propone, hasta contar con esos
valores exactos, una escala neutra con un matiz frío ligeramente azulado (coherente con un azul
de marca tan saturado, en vez del gris puro de `slate` que ya usa la app):

```ts
tinta: {
  900: "#0B1F3A",  // texto principal (headings, texto de alto contraste)
  700: "#33465F",  // texto secundario
  500: "#647089",  // texto terciario / placeholders
},
sutil: "#8A93A6",   // iconografía inactiva, bordes de foco suaves
linea: "#E2E6ED",   // bordes de tarjetas/tablas/inputs
fondo: "#F5F7FA",   // fondo de página (reemplaza bg-slate-50)
```

**Regla de reemplazo:** cada `slate-*`/`sky-*`/`amber-*`/`emerald-*`/`red-*` que hoy aparece en
componentes compartidos (§1.2) se mapea 1:a-uno de los tokens de arriba durante R1/R2 — no se
inventa una paleta paralela; si un componente necesita un matiz que no está en esta lista, se
agrega **a la config**, nunca como hex suelto en el JSX (ya es la convención de
`estandares-de-codigo.md` §5.6: "utilidades de Tailwind, sin CSS en línea").

**Verificación de contraste (obligatoria antes de cerrar R1):** `azul` (#066EE7) sobre blanco da
~4.6:1 (pasa AA para texto normal, límite para texto pequeño en negrita) — usar texto blanco
sobre `azul` (no `azul` sobre blanco) para los botones primarios, que es más seguro (~4.6:1
tampoco cambia, pero es el sentido que ya usa `Boton.tsx`). Verificar con una herramienta de
contraste real (no asumir) los pares definitivos: texto `tinta-900` sobre `fondo`/blanco, blanco
sobre `azul`/`estado.*`, antes de dar R1 por cerrado.

### 3.2 Tipografía — Montserrat vía `next/font/google`

Reemplaza los `localFont` de Geist en `app/layout.tsx`:

```ts
import { Montserrat } from "next/font/google";

const montserrat = Montserrat({
  subsets: ["latin"],
  variable: "--font-montserrat",
  weight: ["400", "500", "600", "700"],
});
```

`next/font/google` **auto-hospeda** el archivo (lo descarga en build time y lo sirve desde el
propio dominio) — no hay llamada a Google Fonts en runtime, coherente con no depender de
terceros en producción. `tailwind.config.ts` gana `fontFamily.sans: ["var(--font-montserrat)", ...defaultsans]`
para que sea la fuente por defecto de toda la app sin tener que anotar `font-montserrat` en cada
elemento. `app/globals.css` pierde la línea `font-family: Arial, Helvetica, sans-serif` (queda
a cargo de Tailwind + la variable de fuente).

### 3.3 Radios y sombras

Sin especificación exacta entregada — se propone (ajustable en R1 contra el manual completo si
aparecen valores distintos):

```ts
borderRadius: {
  DEFAULT: "0.5rem",   // 8px — tarjetas, inputs, botones (reemplaza el rounded-md ad hoc actual)
  lg: "0.75rem",       // 12px — modales, tarjetas de KPI
},
boxShadow: {
  tarjeta: "0 1px 3px 0 rgb(11 31 58 / 0.08), 0 1px 2px -1px rgb(11 31 58 / 0.06)",
  modal: "0 10px 25px -5px rgb(11 31 58 / 0.15)",
},
```

Sombras con tinte azulado (`rgb(11 31 58 / …)`, el tono de `tinta-900`) en vez del negro puro
por defecto de Tailwind — detalle menor pero consistente con "Confianza" como sistema con
identidad de color propia hasta en los detalles neutros.

---

## 4. Navegación nueva

### 4.1 Estructura: sidebar (escritorio) + barra inferior (móvil)

**Decisión de arquitectura (afecta un test existente — ver abajo):** `components/shell/Navegacion.tsx`
se **divide** en dos componentes nuevos, ambos alimentados por la misma fuente de datos
(`lib/navegacion.ts::ENLACES_NAV`, que gana un campo `icono` — ver más abajo):

- **`components/shell/BarraLateral.tsx`** (nueva) — columna fija a la izquierda, fondo `azul`,
  visible desde el breakpoint `md:` (`hidden md:flex md:w-60 md:flex-col`). Contiene, de arriba
  a abajo: el logo blanco (§5), los 6 enlaces con ícono + etiqueta (apilados verticalmente,
  ítem activo con fondo `celeste`/10% de opacidad o un borde izquierdo `celeste`), y el
  bloque de usuario/cerrar-sesión al final (ver §4.3 sobre `Encabezado`).
- **`components/shell/BarraInferior.tsx`** (nueva) — barra fija al fondo de la pantalla,
  visible **solo** bajo `md:` (`flex md:hidden fixed bottom-0 inset-x-0`), fondo blanco con
  borde superior `linea`, los mismos 6 enlaces como ícono + etiqueta corta, en fila,
  distribuidos con `justify-around`. Alto mínimo 56px y cada ítem con área de toque ≥44×44px
  (regla de responsividad transversal, §7).
- `lib/navegacion.ts` — se **mantiene como única fuente de verdad** de qué enlaces existen y
  para qué rol (`ENLACES_NAV`, `enlacesVisibles`); solo se le agrega `icono: LucideIcon` (o el
  tipo que corresponda, ver §4.4) a `EnlaceNav`. Ninguna lógica de rol se duplica entre los dos
  componentes nuevos — ambos llaman a `enlacesVisibles(ENLACES_NAV, sesion?.roles ?? [])`.

**Por qué dos componentes y no uno con clases responsivas condicionales:** un sidebar vertical
con logo y footer de usuario, y una barra inferior horizontal solo-ícono+etiqueta, son
suficientemente distintos en estructura (no solo en `flex-direction`) que forzarlos a un único
JSX con clases condicionales habría sido más difícil de leer que dos componentes chicos y
explícitos — y evita el "componente todoterreno" que la app ya evita en otros lados (p. ej.
`NuevaFactura` como ruta propia en vez de un modo especial de `ListaPropuestas`,
`docs/frontend.md` §6.1).

**Impacto en pruebas — explícito, no un efecto secundario a descubrir en R1:**
`Navegacion.test.tsx` renderiza `<Navegacion />` y busca `getByRole("link", { name })` en todo
el documento. Si los 6 enlaces existieran simultáneamente en `BarraLateral` y `BarraInferior`
(ambos montados en el DOM real; el navegador solo oculta uno vía `display:none` según el ancho
de pantalla — jsdom no aplica CSS real, así que en un test ambos aparecerían "visibles" a la
vez), `getByRole` fallaría por encontrar **dos** elementos con el mismo nombre accesible. La
solución **no** es un truco de test: es que cada componente se prueba por separado, como
corresponde a dos componentes reales distintos:
- `Navegacion.test.tsx` se **elimina**.
- `BarraLateral.test.tsx` y `BarraInferior.test.tsx` (nuevos) replican exactamente los mismos
  tres casos que tenía `Navegacion.test.tsx` (todos los enlaces para OPERADOR, todos para
  ADMINISTRADOR, ninguno sin rol reconocido), cada uno renderizando su propio componente.

Este es el único ajuste a un test **existente** que exige la etapa de navegación — se declara
acá para que no se descubra a mitad de R1 como si fuera un efecto colateral no previsto.

### 4.2 Acceso a Descuentos/Acuerdos — punto de entrada nuevo

**Problema actual:** `/proyectos/{id}/acuerdos` existe y funciona, pero el único acceso es un
link de texto discreto ("Acuerdos") en cada fila del listado de `/proyectos` — fácil de no ver.

**Decisión:** introducir una página de **detalle de proyecto real** (hoy no existe — editar un
proyecto es un modal, y "ver" un proyecto no tiene pantalla propia) con dos pestañas, en vez de
un botón simplemente más grande sobre el listado:

- **`app/(protegido)/proyectos/[id]/layout.tsx`** (nuevo) — layout con cabecera (nombre del
  proyecto, cliente, estado activo/inactivo) y una barra de pestañas: **"Datos"** | **"Descuentos"**.
- **`app/(protegido)/proyectos/[id]/page.tsx`** (nuevo, pestaña "Datos") — vista de solo lectura
  de los campos del proyecto (los mismos que hoy solo se ven al abrir el modal de editar) +
  botón "Editar" que abre el `FormularioDialogo` ya existente (`FormularioProyecto`, sin
  cambios). Dato derivado de `GET /proyectos/{id}`, ya usado hoy por el modal de edición.
- **`app/(protegido)/proyectos/[id]/acuerdos/page.tsx`** (ya existe) pasa a ser la pestaña
  **"Descuentos"** — **sin tocar `ListaAcuerdos.tsx`**: la pestaña activa se resuelve por la ruta
  actual (`usePathname()`, mismo patrón que ya usa `Navegacion.tsx` para "activo"), el
  componente de contenido es exactamente el que ya existe.
- El listado de `/proyectos` cambia el link de texto "Acuerdos" por un botón visible con ícono
  (p. ej. ícono de porcentaje/etiqueta + "Descuentos") en `AccionesFila`, apuntando a
  `/proyectos/{id}/acuerdos` (que ahora abre directo en la pestaña correcta) — visibilidad
  inmediata desde el listado, sin pasar primero por "Datos".

**Por qué pestañas y no solo un botón más grande:** un botón más grande resuelve *encontrar* el
acceso una vez, pero no resuelve que hoy no existe ningún lugar para *ver* un proyecto (solo
editarlo vía modal) — la pestaña "Datos" llena ese hueco al mismo tiempo, con el mismo costo de
implementación (un layout + una página nueva, cero cambios a componentes existentes). Es
además el patrón que años de convención de UI corporativa esperan para "el detalle de una
entidad con secciones" (coincide con el lenguaje "pestaña Descuentos" que se pidió resolver).

**Alcance de negocio: cero.** `ListaAcuerdos`/`FormularioAcuerdo`, sus reglas de solape/vigencia
(`lib/vigencia.ts`) y sus pruebas siguen exactamente iguales — cambia únicamente dónde vive
visualmente la entrada a esa pantalla.

### 4.3 `Encabezado` — qué le queda

Con el logo y la marca ahora en la `BarraLateral`, `Encabezado.tsx` se simplifica a una barra
superior delgada (breadcrumb o título de la sección actual + nombre de usuario + cerrar sesión)
— visible en escritorio junto al contenido (a la derecha del sidebar) y en móvil arriba de todo
(sobre la barra inferior). No es una pantalla nueva, es un ajuste de layout dentro de R1.

### 4.4 Íconos — decisión de dependencia nueva (a confirmar contigo antes de R1)

Hoy el frontend no tiene ninguna librería de íconos. El sidebar/barra inferior y el dashboard
(R9) los necesitan. Dos caminos, ambos legítimos:

1. **`lucide-react`** (recomendado): librería de íconos SVG, *tree-shakeable* (cada ícono es su
   propio módulo, el bundle final solo incluye los que se importan), sin dependencias propias,
   muy usada con Tailwind. Agrega una dependencia de producción nueva — pequeña, pero es una
   decisión que corresponde declarar explícitamente (regla de "no asumir", `CLAUDE.md`).
2. **SVG a mano**: 6-8 íconos inline (uno por módulo + los que necesite el dashboard) como
   componentes React propios en `components/ui/iconos/`, sin ninguna dependencia nueva —
   coherente con la filosofía "simplicidad primero" que ya evitó sumar shadcn/ui
   (`docs/frontend.md` §2.3), al costo de mantener los SVG a mano si se necesitan más íconos
   después.

Se deja como **decisión abierta** para el inicio de R1 (ver "Por dónde empezar" al final) —
ninguna de las dos bloquea el resto del plan.

---

## 5. Logo e identidad

- **Archivo entregado por el usuario** → `frontend/public/logo-helpcom-color.png` (o `.svg` si
  se entrega vectorial — preferible por escalado nítido en pantallas de alta densidad). **Paso
  previo a R1:** copiar el archivo ahí; si no está disponible al iniciar R1, esa etapa arranca
  igual con un placeholder de texto ("helpcom") en el lugar del logo y se reemplaza en cuanto
  llegue el archivo, sin bloquear el resto de R1 (tokens, layout, Montserrat).
- **Versión blanca para el sidebar azul** (`frontend/public/logo-helpcom-blanco.png`/`.svg`):
  necesaria porque el logo a color trae el texto "helpcom" en azul de marca — invisible sobre un
  fondo del mismo azul. Si el usuario **no** cuenta con un archivo blanco ya preparado, la
  resolución dentro de R1 es, en orden de preferencia:
  1. Pedir la variante blanca (la más simple y fiel, si existe en el manual de marca completo).
  2. Si el logo es SVG, generar la variante blanca aplicando `fill: white` a los paths de texto
     conservando el círculo/isotipo celeste tal cual (el manual ya usa esa combinación —
     "círculo celeste con las 'c' en negativo blanco" — sobre superficies oscuras, según el
     ejemplo del uniforme/vehículo citado en el encargo).
  3. Si solo existe el PNG a color y no es viable reprocesarlo, usar el **isotipo/círculo solo**
     (sin el texto "helpcom") como marca compacta en el sidebar — sigue siendo reconocible y
     evita un logo con texto ilegible.
- **Sidebar colapsado / `BarraInferior` (móvil):** en cualquier caso, el espacio es angosto — se
  usa el isotipo/círculo solo (sin el texto "helpcom"), como favicon ampliado, no el logotipo
  completo.
- **Favicon:** `app/icon.png` (convención de archivo de Next.js 14 App Router — reemplaza
  `app/favicon.ico`) generado a partir del isotipo/círculo celeste con la "c" en negativo
  blanco, recortado a cuadrado. Si el archivo fuente no trae el isotipo aislado, se recorta del
  logo completo en la primera pasada de R1 (herramienta de edición de imagen simple, sin
  necesidad de diseño nuevo).
- **Dónde se usa cada variante:**

| Contexto | Fondo | Variante |
|---|---|---|
| `BarraLateral` (sidebar azul) | `azul` | Blanca (o isotipo solo, ver arriba) |
| `app/login/page.tsx` | blanco/claro | Color completo |
| `Encabezado` (si se decide mostrar logo ahí también) | blanco | Color completo |
| `BarraInferior` / pestaña del navegador | — | Isotipo/círculo solo |
| Favicon / ícono de PWA (si se agrega a futuro) | — | Isotipo/círculo solo |

---

## 6. Dashboard nuevo (`/`) — alcance y origen de cada dato

Reemplaza el placeholder actual (`app/(protegido)/page.tsx`, hoy solo un saludo). **Todos los
KPIs de la fase base salen de endpoints que el backend ya expone hoy** — se llama al mismo
endpoint `GET /api/v1/informes/facturacion` más de una vez con filtros distintos cuando hace
falta separar un total "solo facturado" de uno "pendiente+facturado" (ver nota en la fila
correspondiente), no se inventa ninguna agregación nueva.

### 6.1 KPIs y gráficas — fase base (sin backend nuevo)

| KPI / gráfica | Origen del dato | Detalle |
|---|---|---|
| **Por facturar este mes** (tarjeta, neto+IVA+total) | `GET /informes/facturacion?periodoAnio=X&periodoMes=Y` (mes actual, zona `America/Santiago`) → `resumen.totalClp`/`netoClp`/`ivaClp` | Mismo campo que ya usa `ResumenInforme.tsx` — se reutiliza `TarjetaEstadistica`. Recordar el mismo rótulo de exclusión que ya usa el informe ("excluye Pendiente UF y Anulada", `docs/frontend.md` §8.2) para no repetir la ambigüedad en un contexto nuevo. |
| **Ya facturado este mes** (tarjeta) | `GET /informes/facturacion?periodoAnio=X&periodoMes=Y&estados=FACTURADA` → `resumen.totalClp` | Llamada separada de la anterior (mismo endpoint, filtro de estado distinto) porque el total combinado del informe mezcla `PENDIENTE`+`FACTURADA` (`InformeFacturacionResumenDto`, política de totales documentada en el propio DTO) — no hay forma de separar "ya facturado" del total combinado sin este segundo filtro. |
| **Propuestas por estado** (gráfica de barras/dona, 4 categorías) | `GET /informes/facturacion` **sin** filtro de período (vista global) → `resumen.cantidadPorEstado` (`PENDIENTE`/`PENDIENTE_UF`/`FACTURADA`/`ANULADA`) | Reutiliza `BadgeEstadoPropuesta` para el color de cada categoría — mismo lenguaje visual que Propuestas/Informe, cero paleta nueva. |
| **Pendientes de UF** (callout de advertencia, con enlace) | Mismo llamado anterior → `resumen.cantidadPendienteUf` | Si es 0, no se renderiza — mismo criterio que `ResumenInforme.tsx` (`docs/frontend.md` §8.2). Enlaza a `/facturacion?estado=PENDIENTE_UF`. |
| **Top clientes por facturación** (lista/barras horizontales, top 5) | Mismo llamado que "Por facturar este mes" → `resumen.porCliente` (ya lo devuelve el backend; hoy el frontend lo tipa pero **nunca lo renderiza**, `docs/frontend.md` §8.2) | Dato ya disponible, hoy sin usar en ninguna pantalla — el dashboard es su primer consumidor real. |
| **Últimas ejecuciones del ciclo** (mini-tabla o timeline, 5 filas) | `GET /ciclos?page=0&size=5` (ya ordenado desc por `ejecutadoEn`, `EjecucionCicloRepositorio.findByEmpresaIdOrderByEjecutadoEnDesc`) | Reutiliza `BadgeEstadoEjecucionCiclo`. Enlaza a `/facturacion/ciclo/historial`. |
| **Clientes activos** (tarjeta numérica) | `GET /clientes?activo=true&size=1` → `total` | Se pide `size=1` porque solo interesa el conteo (`PaginaRespuestaDto.total`), no el contenido — evita traer una página completa de datos que no se usan. |
| **Proyectos activos** (tarjeta numérica) | `GET /proyectos?activo=true&size=1` → `total` | Mismo patrón que clientes activos. |
| **Importaciones recientes** (mini-lista, 5 filas) | `GET /importaciones?page=0&size=5` (ya ordenado desc por `fechaImportacion`, `docs/frontend.md` §7.5) | Reutiliza `BadgeEstadoImportacionCsv`. Enlaza a `/importacion/historial`. |
| **Facturas recientes** (mini-lista, 5 filas) | `GET /facturas?page=0&size=5&sort=fechaFactura,desc` | A diferencia de Ciclo/Importación, `FacturaRepositorio` **no** tiene un orden por defecto (`docs/frontend.md` no documenta uno, y el repositorio no declara `OrderBy`) — hay que pasar `sort` explícito como parámetro estándar de `Pageable` (Spring Data ya lo soporta sin cambios de backend), igual que hacía `HistorialImportaciones` antes de que se le agregara el orden por defecto (§7.5). |

### 6.2 Gráfica de tendencia mensual — factible hoy, con una salvedad de rendimiento

**"Facturación de los últimos 6 meses"** (gráfica de línea o barras): es factible **sin
endpoint nuevo**, llamando a `GET /informes/facturacion?periodoAnio=X&periodoMes=Y` una vez por
cada uno de los últimos 6 períodos (6 llamadas en paralelo, `Promise.all`, cada una trayendo
solo `resumen.totalClp` — sin pedir `detalle`, aunque el endpoint hoy siempre lo incluye en la
respuesta). Se incluye en el alcance de R9 con esta implementación. **Si en la práctica esto
resulta pesado** (6 llamadas HTTP en cada carga del dashboard) queda anotado como candidato a
"requiere endpoint nuevo" (un endpoint de serie agregada por mes, p. ej.
`GET /informes/facturacion/serie-mensual`) — **no se construye ese endpoint en esta tarea**, es
una optimización de fase posterior si el costo real lo justifica.

### 6.3 Marcado explícitamente como "requiere endpoint nuevo" — NO se construye ahora

- **Comparación interanual** (este mes vs. el mismo mes del año anterior): factible con el mismo
  mecanismo de §6.2 (una llamada más), así que en rigor tampoco requiere backend nuevo — se deja
  fuera de la fase base solo por acotar el alcance de R9, no por una limitación real.
- **Tasa de cobro / mora** (cuánto de lo facturado ya fue pagado): el dominio actual
  (`PropuestaFacturacion`/`Factura`) no modela pagos ni cobranza — no hay ningún campo del que
  derivar esto. Requiere modelo de datos nuevo, no solo un endpoint — claramente fuera de
  alcance de esta tarea y de la etapa actual del proyecto (`CLAUDE.md`: "emisión electrónica e
  integración Crux ERP" es Etapa 2, no iniciada).
- **Alertas de acuerdos por vencer** (acuerdos de precio cuya vigencia termina pronto): factible
  en teoría (`AcuerdoPrecioRepositorio` ya tiene los datos de vigencia), pero **no existe hoy un
  endpoint que liste acuerdos a través de todos los proyectos** — `GET /proyectos/{id}/acuerdos`
  es por proyecto. Requiere un endpoint nuevo (`GET /acuerdos?venceAntesDe=...` o similar). Se
  marca como fase opcional posterior, explícitamente fuera de esta tarea.

---

## 7. Responsividad — criterio transversal (no una fase aparte)

Aplicado en **cada** etapa que toque una pantalla, verificado en cada una con el navegador en al
menos dos anchos (~375px móvil, ~1280px escritorio):

- **`Tabla` (R2) gana una variante de tarjetas en móvil**: bajo un breakpoint (`sm:` o el que
  se confirme visualmente en R2), cada fila se renderiza como una tarjeta apilada
  (etiqueta+valor por campo) en vez de una fila de tabla con scroll horizontal — un solo cambio
  en el componente compartido, heredado por Clientes/Proyectos/Propuestas/Facturas/Importación/
  Informe sin tocar esas pantallas una por una. La API de `Tabla` (`columnas`/`filas`/
  `obtenerClave`) no cambia — la variante de tarjeta reutiliza la misma definición de columnas,
  solo cambia cómo se disponen visualmente.
- **Sidebar → barra inferior**: resuelto estructuralmente en R1 (§4.1), no una utilidad CSS
  aislada — son dos componentes reales, cada uno probado por separado.
- **Área de toque ≥44×44px**: todo elemento interactivo (ítems de `BarraInferior`, botones de
  `AccionesFila`, controles de `Paginacion`, checkboxes de `NuevaFactura`/`TablaPreviewImportacion`)
  se revisa contra este mínimo en la etapa que lo toca — no se re-audita todo de una vez al
  final.
- **Formularios largos en modal** (`FormularioProyecto`, `FormularioAcuerdo`) en pantallas
  angostas: verificar que `Dialogo` permita scroll interno del contenido sin que los botones
  Cancelar/Guardar queden fuera de vista — ajuste de CSS si hace falta, dentro de R2 (es el
  propio `Dialogo`/`FormularioDialogo` el que se ajusta, una vez, para todos los formularios).
- **Filtros de listados** (`PanelListado`, hoy un `ReactNode` libre por pantalla): en escritorio
  en fila, en móvil apilados — se resuelve en R2 dentro de `PanelListado` con clases responsivas
  (`flex-col sm:flex-row`), sin que cada pantalla tenga que repetirlo.

---

## 8. Secuencia de etapas

Cada etapa se da por **terminada** cuando cumple sus tres criterios de verificación:
**(a)** `npm run lint` y `npm run test` en verde, **(b)** `npm run build` sin errores,
**(c)** revisión visual manual en el navegador (`npm run dev`) en ancho móvil y de escritorio.
Ninguna etapa toca el backend ni crea migraciones. El orden respeta dependencias reales: R2
necesita los tokens de R1; R3+ necesitan los componentes ya revestidos de R2; el dashboard (R9)
necesita que las pantallas a las que enlaza (Facturación, Ciclo, Importación) ya existan con la
piel nueva para que los enlaces salientes no aterricen en una pantalla con la piel vieja.

### R1 — Fundación visual — **HECHA**

Implementada y verificada (lint/test/build en verde, revisión visual del login y del favicon
contra el frontend corriendo de verdad — ver `docs/frontend.md` §9 para el detalle completo,
incluida la técnica de la versión blanca del logo y un bug real de `middleware.ts` encontrado y
corregido en el camino). Se agregó `lucide-react` (única dependencia nueva de esta etapa). Como
el sidebar/barra inferior requerían representar una sesión autenticada para verse completos y
este entorno no tenía Keycloak configurado, la revisión visual en navegador cubrió `/login`
(logo a color, botón con la paleta nueva) y la generación real de `app/icon.png`; el shell
autenticado (`BarraLateral`/`BarraInferior`/`Encabezado`) quedó verificado por sus pruebas
(`BarraLateral.test.tsx`, `BarraInferior.test.tsx`) más inspección manual del HTML/CSS
compilado — pendiente una revisión visual del shell completo con sesión real antes de dar por
buena la experiencia entera (recomendado como primer paso de R2, cuando de todos modos hay que
levantar el stack para verificar el contenido interior revestido).

**Archivos (los que finalmente se tocaron):**
- `tailwind.config.ts` (colores §3.1, `fontFamily` §3.2, `borderRadius`/`boxShadow` §3.3)
- `app/layout.tsx` (Montserrat reemplaza Geist), `app/globals.css` (tokens de fondo/texto,
  quita el `font-family` manual), elimina `app/fonts/Geist*.woff` (ya sin uso)
- `app/icon.png` (favicon nuevo, reemplaza `app/favicon.ico`, eliminado)
- `frontend/public/Logo_Helpcom.png` (logo oficial, provisto por Helpcom — ya estaba en el
  repo), `logo-helpcom-blanco.png` + `isotipo-helpcom-{color,blanco}.png` (generados —
  `docs/frontend.md` §9.2 documenta la técnica y por qué)
- `components/shell/BarraLateral.tsx` (nuevo), `components/shell/BarraInferior.tsx` (nuevo),
  `components/shell/Navegacion.tsx` + `PlaceholderModulo.tsx` (eliminados),
  `lib/navegacion.ts` (+ campo `icono`, opcional)
- `app/(protegido)/layout.tsx` (reescrito: compone `BarraLateral` + `Encabezado` + contenido +
  `BarraInferior`)
- `components/shell/Encabezado.tsx` (simplificado, §4.3)
- `app/login/page.tsx` (logo + paleta nueva)
- `components/shell/Navegacion.test.tsx` (eliminado) → `BarraLateral.test.tsx` +
  `BarraInferior.test.tsx` (nuevos, mismos 3 casos de rol + 2 casos propios cada uno — §4.1)
- `middleware.ts` — **ajuste no previsto en el plan original**, encontrado al verificar: su
  `matcher` no excluía archivos estáticos de `public/` más allá de `favicon.ico`, así que el
  logo del propio `/login` (sin sesión) quedaba atrapado por el guard de autenticación
  (`docs/frontend.md` §9.4).
- Dependencia agregada: `lucide-react` (decisión ya tomada antes de iniciar esta etapa).

**Terminado cuando:** toda la app (todas las pantallas, sin excepción, porque el shell es
compartido) se ve con la tipografía Montserrat, la paleta azul/celeste en sidebar/barra
inferior/login, y el logo correcto en cada superficie de la tabla de §5 — aunque el contenido
interno de cada pantalla (tablas, botones, badges) siga con los colores viejos hasta R2.

**Verificación:** lint + test (con los archivos de test reemplazados arriba) + build; visual en
`/`, `/login`, y una pantalla cualquiera del resto (para confirmar que el shell nuevo no rompe
el contenido) en ambos anchos.

### R2 — Revestir componentes compartidos — **HECHA**

Implementada y verificada (lint/test en verde, 110/110, sin tocar ningún test — la predicción de
§1.2 se cumplió: ninguna prueba asociaba una clase Tailwind específica). **No se corrió `npm run
build`** en esta etapa (el dev seguía vivo en `:3002`, §11.4 de `docs/frontend.md`) — la
verificación de compilación fue contra el dev server real (sin errores, tokens nuevos presentes
en el CSS servido). Detalle completo, incluida una decisión de arquitectura que se revirtió a
mitad de camino, en `docs/frontend.md` §12.

**Archivos (los que finalmente se tocaron) —** igual que lo planeado, más dos componentes nuevos
y un ajuste puntual de pantalla:
`components/ui/{Boton,CampoFormulario,Entrada,AreaTexto,Seleccion,Tabla,Paginacion,Dialogo,
DialogoConfirmacion,Notificaciones,TarjetaEstadistica}.tsx` (re-piel),
`components/ui/{Alerta,EstadoVacio}.tsx` (**nuevos** — §12.2 de `docs/frontend.md`),
`components/formularios/FormularioDialogo.tsx`, `components/listado/{PanelListado,AccionesFila}.tsx`,
`components/facturacion/propuestas/BadgeEstadoPropuesta.tsx`,
`components/facturacion/ciclo/BadgeEstadoEjecucionCiclo.tsx`,
`components/importacion/{BadgeEstadoFilaCsv,BadgeEstadoImportacionCsv}.tsx`,
`components/facturacion/propuestas/ListaPropuestas.tsx` (**único archivo de pantalla tocado** —
tres columnas monetarias marcadas con la nueva propiedad opcional `alineacion: "derecha"` de
`ColumnaTabla`, como demostración visible de esa capacidad; el resto de las pantallas la suman
cuando se revisiten en R3+).

**Decisión de arquitectura revertida a mitad de camino — vale la pena que quede acá, no solo en
`docs/frontend.md`:** la primera versión de la vista de tarjetas de `Tabla` en móvil usaba dos
bloques JSX en paralelo (tabla + tarjetas, alternados con `hidden md:block`/`flex md:hidden`),
el mismo patrón que ya había funcionado para `BarraLateral`/`BarraInferior` en R1. Acá **no
funcionó**: a diferencia de la navegación, las dos versiones de una fila de tabla comparten los
mismos botones/checkboxes/badges — duplicarlas en el DOM (aunque invisible en un navegador real)
rompió 27 pruebas en 10 archivos de listado distintos apenas se corrió `npm run test`. Se
resolvió con un único árbol DOM que cambia de `table`/`table-row`/`table-cell` a `block` por CSS
según el breakpoint, con la etiqueta de cada campo generada vía `::before { content:
attr(data-label) }` en vez de un segundo JSX — cero pruebas tocadas, mismo resultado visual.
Moraleja para etapas futuras: el patrón "dos JSX, uno oculto por breakpoint" es seguro solo
cuando las dos versiones NO comparten los mismos elementos interactivos fila por fila.

**Terminado:** las pantallas existentes (verificado con el compilado del dev server, no con
captura de navegador — ver la limitación de entorno en el reporte de esta etapa) heredan
colores/radios/sombras/foco de marca sin haber tocado su archivo — con la única excepción
deliberada de `ListaPropuestas.tsx` arriba.

**Pendiente, explícito:** verificación VISUAL en el navegador (escritorio + móvil) de al menos
Clientes, Propuestas e Importación, antes de arrancar R3 — el entorno donde se implementó R2 no
tenía el navegador con extensión de Claude conectado; la verificación de esta etapa fue por
lint/test/inspección del CSS compilado, no por captura de pantalla.

### R3 — Clientes y Tipos de servicio — **HECHA**

Implementada y verificada (lint/test en verde, 112/112 — 2 pruebas nuevas, ninguna existente
tocada). **No se corrió `npm run build`** (dev vivo en `:3002`, §11.4) — verificación contra el
dev server real. Detalle completo en `docs/frontend.md` §14.

**Archivos:** `components/clientes/ListaClientes.tsx` (badge `BadgeActivo`, enlace a "Tipos de
servicio" como botón visible con ícono), `components/tiposServicio/ListaTiposServicio.tsx`
(mismo badge, + enlace de vuelta "← Clientes" que no existía), `components/ui/BadgeActivo.tsx`
(**nuevo** — reemplaza el texto de color plano en ambas listas). Sin cambios de lógica —
validación de RUT, mapeo de error por campo, filtrado por rol y baja lógica/física quedaron
intactos.

**El patrón de detalle + subsecciones (§4.2), construido y probado, no todavía usado:**
`components/detalle/{EncabezadoDetalle,PestanasDetalle}.tsx` (**nuevos**, con prueba propia para
`PestanasDetalle` — el mecanismo de pestaña activa vía `usePathname`). Clientes no necesita una
pantalla de detalle propia (no tiene una subentidad equivalente a Acuerdos de Proyecto —
construir `/clientes/{id}` habría solo duplicado el modal de edición ya existente, así que se
evitó a propósito). Quedan listos para que R4 los use tal cual especifica §4.2 — ver
`docs/frontend.md` §14.2 para el detalle exacto de cómo se conectan a `/proyectos/{id}`.

**Terminado:** verificado contra el dev server real (`/clientes`, `/clientes/tipos-servicio`
compilan y sirven sin error, tokens nuevos presentes en el CSS servido) — sin revisión visual con
captura de pantalla (sin la extensión de Claude en Chrome conectada en este entorno).

**Pendiente, explícito:** revisión visual en el navegador (escritorio + móvil) de ambas pantallas
antes de arrancar R4.

### R4 — Proyectos y Acuerdos (incluye el nuevo acceso a Descuentos) — **HECHA**

**El acceso a Descuentos/Acuerdos de precio, pendiente desde la auditoría inicial de este plan,
quedó RESUELTO.** Implementada y verificada (lint/test en verde, 120/120 — 8 pruebas nuevas,
ninguna existente tocada). **No se corrió `npm run build`** (dev vivo en `:3002`, §11.4) —
verificación contra el dev server real (las tres rutas de Proyectos responden sin error 500).
Detalle completo en `docs/frontend.md` §15.

**Archivos:** `app/(protegido)/proyectos/[id]/{layout,page}.tsx` (**nuevos**),
`components/proyectos/{LayoutDetalleProyecto,DatosProyecto,ContextoProyectoDetalle}.tsx`
(**nuevos** — el contexto no estaba planeado en el plan original; resultó necesario para que
editar desde la cabecera no dejara la pestaña "Datos" con el proyecto desactualizado, ver
`docs/frontend.md` §15.2), `components/proyectos/acuerdos/BadgeEstadoVigencia.tsx` (**nuevo**),
`components/proyectos/ListaProyectos.tsx` (`BadgeActivo`, nombre como enlace al detalle, se quitó
el link "Acuerdos" de la fila), `components/proyectos/acuerdos/ListaAcuerdos.tsx` (se le quitó su
propio encabezado duplicado — ahora lo da el layout — y de paso el fetch de `proyecto` que solo
existía para eso), `components/proyectos/acuerdos/FormularioAcuerdo.tsx` (re-piel de sus tres
avisos a mano, sin tocar `role="status"` del aviso de solape). **No** se tocó
`components/listado/AccionesFila.tsx` como anticipaba el plan original — se optó por un enlace en
el nombre del proyecto (más descubrible que un botón más en la columna de acciones, y evita
sumarle una prop más a un componente ya compartido por 6 pantallas).

**Terminado:** desde `/proyectos`, el nombre de cualquier fila enlaza a `/proyectos/{id}`;
ahí, `EncabezadoDetalle` + `PestanasDetalle` (R3) muestran "Datos" (solo lectura, con "Editar" en
la cabecera) y "Descuentos" (el `ListaAcuerdos` de siempre, sin cambios de lógica) — exactamente
el flujo que pedía el objetivo original: clic en el proyecto → pestaña Descuentos.

**Revisión visual — RESUELTA (2026-08-17).** Verificada en el navegador contra el stack Docker
(`localhost:13000` → `18080`, ya con el fix de CORS y la imagen de frontend reconstruida) en
ambos anchos, escritorio y móvil: listado de Proyectos → detalle → pestaña Datos → pestaña
Descuentos → "Agregar descuento" con una vigencia que se pisa con la ya existente → aviso de
solape (`role="status"`, §15.3 de `docs/frontend.md`) visible correctamente. Sin hallazgos.
R4 queda completamente cerrada; guion permanente en `docs/guion-qa-manual.md`.

### R5 — Propuestas y Ciclo de facturación — **HECHA Y VERIFICADA (2026-08-17)**

Implementada y verificada (lint en verde, test **120/120** — ninguna prueba existente tocada —
y `npm run build` completo, ambos sin errores). Alcance real más acotado de lo que el plan
original anticipaba: `BadgeEstadoPropuesta`/`BadgeEstadoEjecucionCiclo` **ya estaban** en los
tokens `estado.*` desde R2 (§12, arriba — quedaron en la lista de archivos tocados de esa etapa;
verificado leyendo el código antes de tocar nada, sin reskinearlos de nuevo). Lo que sí seguía en
paleta Tailwind default (`slate-*`/`amber-*`/`red-*`) eran las pantallas alrededor de esos badges.

**Archivos tocados:**
- `app/(protegido)/facturacion/page.tsx` — `text-slate-500` → `text-sutil` (fallback de
  `<Suspense>`).
- `components/facturacion/ciclo/EjecutarCiclo.tsx` — cabecera (link "Volver" + `<h1>`)
  reemplazada por `components/detalle/EncabezadoDetalle.tsx` (R4, heredado en vez de
  reinventado); banner de error rojo y callout ámbar de `PENDIENTE_UF` reemplazados por
  `components/ui/Alerta.tsx` (`variante="error"`/`"advertencia"`, R2); resto de textos
  (`slate-500/700/900`) → `sutil`/`texto`; enlace "Ver propuestas de este período →" →
  `text-marca-azul hover:text-marca-azul-700`.
- `components/facturacion/ciclo/HistorialCiclos.tsx` — link "Volver a propuestas" al mismo
  patrón que `ListaTiposServicio.tsx` (R3): ícono `ArrowLeft` de `lucide-react` + `text-sutil
  hover:text-marca-azul`, en vez del texto plano `"← Volver..."` en `slate-600`.
- `components/facturacion/propuestas/DialogoDetallePropuesta.tsx` — filas del snapshot
  (`border-slate-100`/`text-slate-500`/`text-slate-900`) → `border-linea-2`/`text-sutil`/
  `text-texto`, mismo lenguaje que `DatosProyecto.tsx` (R4).
- `components/facturacion/propuestas/ListaPropuestas.tsx` — botones de fila "Ver detalle"
  (`text-sutil hover:text-marca-azul`, mismo tono neutro que "Editar"/"Activar" de
  `AccionesFila.tsx`, R2) y "Anular" (`text-estado-error hover:text-estado-error/80`, mismo
  tono que "Eliminar" de `AccionesFila.tsx`); enlaces de cabecera "Facturas"/"Historial de
  ciclos" (`text-sutil hover:text-marca-azul`) y "Ejecutar ciclo →" (`text-marca-azul
  hover:text-marca-azul-700`, con más énfasis por ser la acción principal de la fila).
- **No tocados** (ya en tokens de marca, verificado antes de empezar):
  `components/facturacion/propuestas/BadgeEstadoPropuesta.tsx`,
  `components/facturacion/ciclo/BadgeEstadoEjecucionCiclo.tsx`,
  `app/(protegido)/facturacion/ciclo/page.tsx`, `app/(protegido)/facturacion/ciclo/historial/page.tsx`
  (envoltorios delgados, sin clases de color).

**Botón deshabilitado de OPERADOR:** el botón "Ejecutar ciclo" y el "Anular" de fila usan el
mismo mecanismo ya existente (`disabled` + `title` + `disabled:opacity-40`), sin tocarlo — el
`Boton.tsx`/`AccionesFila.tsx` de R2 ya lo resolvían para la paleta de marca; este re-piel solo
cambió el color BASE de "Anular" (antes `red-600` de Tailwind, ahora `estado-error`), y
`opacity-40` sobre `estado-error` da el mismo atenuado esperado. Pendiente de **confirmación
visual** por el usuario (ver abajo).

**Verificación:** `npm run lint` (limpio) + `npm run test` (**127/127** — 120 previos + 7 nuevos
de badges, ver abajo — `ListaPropuestas.test.tsx`, `EjecutarCiclo.test.tsx`,
`HistorialCiclos.test.tsx` sin tocar) + `npm run build` (build de producción completo, sin
errores — a diferencia de R1-R4, esta vez no había un `npm run dev` vivo compartiendo `.next`,
así que sí se pudo correr).

**El estado `ERROR` del badge de ciclo se cubre por prueba unitaria, no por dato real:**
`ejecucion_ciclo.estado = ERROR` no es provocable con datos reales sin romper el stack — por
diseño de `ServicioCicloFacturacion` (javadoc de la clase: "más consistente con la filosofía de
avanzar todo lo posible"), cada proyecto corre en su propia transacción con captura de
`RuntimeException`, así que cualquier falla de negocio (UF, tasa IVA, datos del proyecto) queda
absorbida como `CON_ADVERTENCIAS` y nunca escala a `ERROR` a nivel de ejecución. Para llegar a
`ERROR` de verdad haría falta romper algo fuera del procesamiento por proyecto — la fila
`empresa` que usa TODA la app, o la conexión a Postgres a mitad de una ejecución — ninguna
opción segura sobre el Postgres real compartido. Se agregaron
`BadgeEstadoEjecucionCiclo.test.tsx` (los tres estados, incluido `ERROR` → `estado-error`) y
`BadgeEstadoPropuesta.test.tsx` (los cuatro estados) — el mapeo estado→token queda verificado
completo en aislamiento; ambos badges ya estaban correctamente implementados (R2), estos tests
solo cierran el hueco de cobertura, no corrigieron nada.

**Revisión visual — RESUELTA (2026-08-17).** Verificada en el navegador (escritorio + móvil,
stack Docker real) con **ambos roles** (ADMINISTRADOR vía `dev.qa`, OPERADOR vía
`dev.qa.operador` — usuarios de prueba dedicados en Keycloak, permanentes, sin reutilizar
cuentas reales): listar/filtrar propuestas, ver detalle, anular una propuesta y ejecutar el
ciclo/ver historial con ADMINISTRADOR, y con OPERADOR el botón "Ejecutar ciclo"/"Anular"
correctamente deshabilitado con la paleta nueva. Sin hallazgos. **R5 queda CERRADA.**

### R6 — Facturas — **HECHA**

Implementada y verificada (lint en verde, test y `npm run build` en verde, sin pruebas nuevas —
pura re-piel, sin lógica nueva que cubrir). `BadgeEstadoPropuesta` (reutilizado en
`DetalleFactura`) ya estaba en tokens desde R2, no se tocó. Archivos re-pielados:
`ListaFacturas.tsx`, `DetalleFactura.tsx` (cabecera → `EncabezadoDetalle`, R4),
`SeccionPdfFactura.tsx` (estado "sin PDF" ya era neutro, sin color de error; "con PDF" a
`texto`) y `NuevaFactura.tsx` (banner de cliente restringido → `Alerta variante="info"`).

**Archivos:** `components/facturacion/facturas/*`, `app/(protegido)/facturacion/facturas/**`.

**Revisión visual — RESUELTA (2026-08-17).** Verificada por el usuario en el navegador
(escritorio + móvil, stack Docker real) con **ambos roles** (ADMINISTRADOR vía `dev.qa`,
OPERADOR vía `dev.qa.operador`): listar facturas, crear una nueva (selección de propuestas +
subtotal, con la propuesta id 5), ver el detalle, descargar el PDF de la factura F-2026-0001
y subir uno nuevo (cubriendo tanto el estado "con PDF" como "sin PDF"). Sin hallazgos. Guion
permanente en `docs/guion-qa-manual.md` (Caso P), que además deja vigilados explícitamente los
dos cambios de comportamiento declarados en `docs/frontend.md` §17 (el link "Volver" del
detalle de factura ya no se muestra durante carga/error, y el botón "Refrescar listado" del
error `PROPUESTA_NO_FACTURABLE` quedó fuera del recuadro de error). **R6 queda CERRADA.**

### R7 — Importación CSV — **HECHA**

Implementada y verificada (lint en verde, test **134/134** — ninguna prueba existente tocada, ni
las del fix del `$NaN` de `TablaPreviewImportacion` de la tarea anterior — y `npm run build`
completo, sin errores). Igual que en R5/R6: `BadgeEstadoFilaCsv.tsx` y
`BadgeEstadoImportacionCsv.tsx` **ya estaban** en tokens `estado.*` desde R2 — verificado
leyendo el código antes de tocar nada, no re-pieleados de nuevo.

**Archivos tocados:**
- `components/importacion/AyudaFormatoCsv.tsx` — tarjeta de ayuda de formato (`slate-200/50` →
  `linea`/`fondo`, textos a `tinta`/`sutil`/`texto`).
- `components/importacion/ImportarCsv.tsx` — encabezado (`slate-900` → `tinta`; enlace "Ver
  historial de importaciones →" a `text-marca-azul`, mismo énfasis que "Ejecutar ciclo →" de
  R5); tarjeta "Archivo" (`slate-200` → `linea`); banners de error de previsualizar/confirmar
  → `components/ui/Alerta.tsx` (`variante="error"`, R2) en vez de `<div role="alert"
  className="border-red-200...">` a mano.
- `components/importacion/HistorialImportaciones.tsx` — link "Volver a importar" al mismo
  patrón que `HistorialCiclos.tsx` (R5)/`ListaTiposServicio.tsx` (R3): ícono `ArrowLeft` +
  `text-sutil hover:text-marca-azul`.
- `components/importacion/ResultadoImportacion.tsx` — tarjeta de resultado (`slate-200` →
  `linea`); los dos avisos ámbar (estado ≠ PROCESADA, y "quedaron en Pendiente UF") →
  `Alerta variante="advertencia"`; enlace final a `text-marca-azul`.
- `components/importacion/TablaPreviewImportacion.tsx` — **solo colores**: mensajes de fila
  por estado (`text-red-700`/`text-amber-700` → `text-estado-error`/`text-estado-sin-uf`) y
  las 4 `TarjetaEstadistica` del resumen (`slate-900`/`emerald-700`/`amber-700`/`red-700` →
  `tinta`/`estado-facturada`/`estado-sin-uf`/`estado-error`). Las comparaciones `== null` del
  fix del `$NaN` (tarea anterior) **no se tocaron** — verificado que siguen igual y sus
  pruebas (`TablaPreviewImportacion.test.tsx`) siguen pasando tal cual.
- **No tocados** (ya en tokens de marca, verificado antes de empezar):
  `components/importacion/{BadgeEstadoFilaCsv,BadgeEstadoImportacionCsv}.tsx`,
  `app/(protegido)/importacion/{page.tsx,historial/page.tsx}` (envoltorios delgados).

**Mapeo estado de fila → token** (ya existía, confirmado sin tocar):
`OK`→`estado-facturada` (verde), `ADVERTENCIA`→`estado-sin-uf` (ámbar), `ERROR`→`estado-error`
(rojo) — mismo lenguaje que `BadgeEstadoPropuesta`/`BadgeEstadoEjecucionCiclo`.

**Verificación:** `npm run lint` (limpio) + `npm run test` (134/134) + `npm run build` (sin
errores). Backend/E2E no se tocaron.

**Revisión visual — RESUELTA (2026-08-17).** Verificada por el usuario en el navegador
(escritorio + móvil, stack Docker real) con **ambos roles** (ADMINISTRADOR/OPERADOR): el flujo
de dos fases completo (elegir archivo → previsualizar → confirmar); la paginación **en
cliente** de `TablaPreviewImportacion` (§7.3 de `docs/frontend.md`), cruzando explícitamente de
la página 1 a la página 2; los tres estados de fila (OK/ADVERTENCIA/ERROR) con la paleta nueva
en ambas páginas; el diálogo de confirmación (que avisa que confirmar re-valida el archivo
completo); y el resumen de resultado. Caso de QA manual correspondiente: **Caso Q** de
`docs/guion-qa-manual.md`. **R7 queda CERRADA.**

### R8 — Informe de facturación — **HECHA**

Implementada y verificada (lint en verde, test **134/134** — ninguna prueba existente tocada —
y `npm run build` completo, sin errores). Igual que en R5/R6/R7: `BadgeEstadoPropuesta` (R5) y
los componentes compartidos del panel de filtros (`PanelListado`, `CampoFormulario`, `Entrada`,
`Seleccion`, `Casilla`, `SelectorCliente`, R2/R3) **ya estaban** en tokens `estado.*`/marca —
verificado leyendo el código antes de tocar nada, no re-pieleados de nuevo.

**PASO 0 (dato, no código):** el ambiente de dev cambió tras la importación CSV confirmada
durante la revisión de R7 — conteo real contra el Postgres del stack Docker (2026-08-17): **63
`propuesta_facturacion`** en total (`PENDIENTE` 58, `PENDIENTE_UF` 2, `FACTURADA` 2, `ANULADA`
1; por origen: `CSV` 58, `CICLO` 5). Registrado en `docs/frontend.md` §8.7 (no había un
documento de inventario de datos de dev separado).

**Archivos tocados:**
- `components/informes/InformeFacturacion.tsx` — un solo `className` (`text-slate-900` →
  `text-tinta` en el título). El resto (filtros, tabla, botón de exportar) ya venía de
  componentes compartidos sin colores propios.
- `components/informes/ResumenInforme.tsx` — recuadros de "Totales" y "Cantidad de propuestas
  por estado" (`slate-200`/`slate-900`/`slate-500` → `linea`/`tinta`/`sutil`); tarjeta "Total"
  (`emerald-700` → `estado-facturada`, mismo verde que el badge `FACTURADA`); callout de
  `PENDIENTE_UF` (`<div>` a mano en ámbar → `Alerta variante="advertencia"`, que ya usa
  `estado-sin-uf` — mismo ámbar que el badge `PENDIENTE_UF` de Propuestas).
- **No tocados** (ya en tokens, verificado antes de empezar): `app/(protegido)/informes/page.tsx`
  (envoltorio delgado), `BadgeEstadoPropuesta.tsx`, `TarjetaEstadistica.tsx`, y los componentes
  compartidos del panel de filtros listados arriba.

**Callout `PENDIENTE_UF`:** confirmado con el token `estado-sin-uf` (vía `Alerta
variante="advertencia"`) — el mismo ámbar que el badge `PENDIENTE_UF` en el resto de la app,
sin color nuevo.

**Panel de filtros (el más cargado de la app, 8 controles):** sin layout propio — hereda el
contenedor de `PanelListado`, que en escritorio distribuye los campos en fila con `flex-wrap`
(cada uno a su ancho natural, saltando de línea cuando no cabe, alineados por la base) y en
móvil los apila a ancho completo. Detalle completo en `docs/frontend.md` §19.

**Verificación:** `npm run lint` (limpio) + `npm run test` (134/134) + `npm run build` (sin
errores). Backend/E2E no se tocaron.

**Pendiente, explícito:** revisión visual (escritorio + móvil, ambos roles) de los filtros
combinados (período exacto + rango + estados + cliente + origen + facturada), el resumen con
el callout de `PENDIENTE_UF`, la exportación CSV, y la tabla de detalle paginada con el volumen
real (63 propuestas) — la hace el usuario contra el stack Docker, no se pudo hacer en este
entorno. **R8 no se da por cerrada hasta esa revisión.**

### R9 (Rúltimo) — Dashboard nuevo

**Archivos:** `app/(protegido)/page.tsx` (reescrito por completo), `components/dashboard/`
(nuevo: `TarjetasResumen.tsx`, `GraficoEstadoPropuestas.tsx`, `GraficoTendenciaMensual.tsx`,
`TopClientes.tsx`, `UltimasEjecucionesCiclo.tsx`, `ActividadReciente.tsx` o la agrupación que
resulte más simple al implementar — nombres orientativos, no definitivos), posiblemente
`lib/useDashboard.ts` (hook de obtención de datos, mismo patrón que `useInformeFacturacion.ts`
si conviene agrupar las llamadas).

**Decisión de dependencia nueva (a confirmar antes de iniciar R9):** no hay ninguna librería de
gráficas en el proyecto hoy. Para "Propuestas por estado" y "Tendencia mensual" (§6.1, §6.2) se
necesita alguna. Opciones: una librería liviana (p. ej. Recharts, la más común en proyectos
React+Tailwind) vs. SVG a mano para las 2-3 gráficas simples que pide este dashboard (barras
horizontales para "top clientes", barras/dona para "por estado", lineal para "tendencia") —
misma disyuntiva que los íconos (§4.4), incluso más justificable resolverla a mano porque son
pocas gráficas y simples. Se deja abierta para el inicio de R9.

**Terminado cuando:** `/` muestra las tarjetas y gráficas de §6.1/§6.2 con datos reales del
backend (no mock), cada una enlazando a su pantalla de detalle correspondiente
(Facturación/Ciclo/Importación filtrados), y ningún elemento del dashboard bloquea la carga de
otro si un endpoint individual falla (cada tarjeta/gráfica maneja su propio estado de
error/carga — ninguna pantalla completa se cae por un solo fetch fallido).

**Verificación:** lint + test (pruebas nuevas para cada componente de `components/dashboard/`
con datos mockeados, mismo patrón que el resto de la app — carga/error/vacío/con-datos) + build;
visual con datos reales contra el stack Docker (`docs/despliegue.md`) o contra un backend local,
ambos anchos, confirmando que los 6 (o más) fetches en paralelo no generan un salto de layout
(*layout shift*) perceptible al ir resolviendo.

---

## 9. Riesgos y decisiones abiertas (no asumidas — confirmar antes o durante la etapa que corresponda)

**Resueltas por R1:**
- **Hex de color, tipografía y radios:** R1 los aplicó tal como los entregó la marca en esa
  tarea (azul/celeste/neutros/estados + Montserrat + radios `sm`/`DEFAULT`/`lg` 8/11/14px) — ya
  no son una propuesta de este documento, son los valores reales en `tailwind.config.ts`.
- **Archivo del logo y su variante blanca:** resueltos — `Logo_Helpcom.png` (provisto) +
  `logo-helpcom-blanco.png`/`isotipo-helpcom-{color,blanco}.png` (derivados por color, no por
  filtro CSS — `docs/frontend.md` §9.2 tiene la técnica y el porqué).
- **Librería de íconos:** resuelta — `lucide-react`.

**Siguen abiertas:**
- **Verificación visual del shell autenticado con sesión real:** R1 verificó `/login` (pública)
  y las pruebas automatizadas de `BarraLateral`/`BarraInferior`, pero no una revisión ocular del
  sidebar/barra inferior ya logueado (este entorno no tenía Keycloak configurado) — hacerlo
  antes o al iniciar R2, cuando de todos modos hace falta el stack completo arriba para revisar
  el contenido interior revestido.
- **Librería de gráficas** (R9, §9 de esa sección): sin decidir todavía, no bloquea nada previo.
- **Sombras exactas** (§3.3): los valores de R1 son una propuesta razonable, no confirmados
  contra mockups pixel-perfect — ajustar en R2 si hace falta.
- **Regla de visibilidad de nav por rol** (`docs/frontend.md`: "todos ven todo" es provisional):
  no cambia con este rediseño — sigue siendo una decisión de negocio pendiente, no de diseño.

---

*Fin del plan. Ninguna etapa (R1..R9) está implementada. Próximo paso: confirmar por dónde
empezar (ver mensaje de cierre fuera de este documento) y las dos decisiones de dependencia
abiertas que correspondan a esa etapa.*
