# Arquitectura Técnica

**Proyecto:** Sistema de Facturación Recurrente de Proyectos
**Cliente inicial:** Helpcom Ltda.
**Etapa:** 1 — Arquitectura
**Estado del documento:** Borrador para revisión
**Última actualización:** 2026-08-06

---

## 1. Propósito y alcance

Este documento define la arquitectura técnica del sistema en su **Etapa 1**. El objetivo de la Etapa 1 es **registrar proyectos de clientes que se facturan de forma recurrente** (mensual o anual, en UF o CLP) y **generar el informe de lo que se va a facturar** en cada ciclo mensual, dejando constancia del número de factura emitida y su respaldo en PDF.

**Dentro del alcance de la Etapa 1:**

- Registro de clientes y proyectos.
- Cálculo del ciclo de facturación el primer día de cada mes.
- Conversión UF → CLP con el valor del día de facturación de cada proyecto.
- Informe de facturación (neto + IVA 19% + total).
- Asociación manual del número de factura emitida y carga del PDF de respaldo.
- Importación masiva vía CSV que genera ítems del ciclo de facturación.
- Autenticación OAuth/OIDC con roles `ADMINISTRADOR` y `OPERADOR`.

**Fuera del alcance de la Etapa 1 (previsto para Etapa 2):**

- Emisión de facturación electrónica (DTE).
- Integración con **Crux ERP**.
- Habilitación como SaaS multi-tenant real (la Etapa 1 se diseña *preparada* para ello, ver §4).

---

## 2. Principios de arquitectura

Estos principios guían todas las decisiones del documento:

1. **Simplicidad primero.** El sistema arranca para una sola empresa (Helpcom). Se prioriza una solución simple y mantenible por sobre la complejidad prematura.
2. **Preparado para crecer, sin sobrecosto hoy.** Las decisiones que son caras de revertir (multi-tenancy, separación factura/borrador) se dejan preparadas; el resto se implementa solo cuando se necesite.
3. **Cálculos auditables e inmutables.** Todo monto facturado guarda un *snapshot* del cálculo (valor UF, fecha, tasa IVA, monto CLP). Un informe pasado nunca debe cambiar de valor.
4. **Idempotencia en los procesos automáticos.** El proceso del día 1 puede ejecutarse más de una vez sin generar duplicados.
5. **Código y dominio en español.** Variables, métodos, tablas, columnas y comentarios en español (ver documento de *Estándares de código*).

---

## 3. Vista general del sistema

El sistema es una aplicación web compuesta por un **frontend** (Next.js) y un **backend** (Spring Boot) que expone una API REST, respaldado por **PostgreSQL**, **Redis** y **almacenamiento de objetos** para los PDF.

```
        ┌─────────────────────────────────────────────────────────┐
        │                        Usuario                          │
        │              (Administrador / Operador)                 │
        └───────────────────────────┬─────────────────────────────┘
                                     │ HTTPS
                        ┌────────────▼────────────┐
                        │        Frontend         │
                        │   Next.js 14 + TS +     │
                        │       Tailwind CSS      │
                        └────────────┬────────────┘
                                     │ REST (JSON, JWT)
                        ┌────────────▼────────────┐        ┌──────────────────┐
                        │        Backend          │◄──────►│    Keycloak      │
                        │  Java 21 + Spring Boot  │  OIDC  │  (OAuth/OIDC)    │
                        │      (monolito          │        └──────────────────┘
                        │       modular)          │
                        └───┬──────┬──────┬───────┘
                            │      │      │
            ┌───────────────┘      │      └────────────────┐
            │                      │                       │
    ┌───────▼───────┐     ┌────────▼────────┐     ┌────────▼─────────┐
    │  PostgreSQL   │     │     Redis 7     │     │  Object Storage  │
    │   (datos)     │     │ (caché UF,      │     │   (PDF facturas) │
    │               │     │  sesiones/lock) │     │                  │
    └───────────────┘     └─────────────────┘     └──────────────────┘
                                     │
                        ┌────────────▼────────────┐
                        │   API pública UF        │
                        │   (mindicador.cl)       │
                        └─────────────────────────┘
```

---

## 4. Decisión clave: multi-tenancy preparado (single-tenant hoy)

La Etapa 1 **no** implementa multi-tenancy funcional, pero deja la costura lista para activarlo sin migraciones dolorosas.

**Qué se hace hoy:**

- Todas las tablas de negocio incluyen una columna `empresa_id`.
- Existe una única empresa sembrada por defecto: **Helpcom** (`empresa_id` fijo).
- La "empresa actual" se resuelve en **un solo punto** del backend (un componente `ContextoEmpresa`), que hoy devuelve siempre Helpcom.

**Qué se activa en el futuro (Etapa SaaS):**

- El `ContextoEmpresa` pasa a resolver la empresa desde el token OIDC / subdominio.
- Se activa un filtro automático por `empresa_id` (por ejemplo, filtro a nivel de repositorio o *filter* de Hibernate).
- Keycloak evoluciona a un **realm por empresa**.

> **Regla para el desarrollo:** ninguna consulta ni inserción de negocio debe omitir `empresa_id`. Aunque hoy no filtre nada, la columna debe estar presente y poblada siempre.

---

## 5. Estilo arquitectónico: monolito modular

Para el tamaño y la etapa del proyecto, se adopta un **monolito modular** desplegable como una sola unidad, organizado internamente por **módulos de negocio** (vertical slices), no por capas técnicas globales.

**Módulos previstos (Etapa 1):**

| Módulo | Responsabilidad |
|---|---|
| `empresa` | Contexto de empresa y preparación multi-tenant. |
| `seguridad` | Integración OIDC, roles y autorización. |
| `clientes` | ABM de clientes. |
| `proyectos` | ABM de proyectos y su configuración de facturación (servicio único, precio, moneda, periodicidad, día). |
| `uf` | Consulta y caché del valor UF por fecha. |
| `facturacion` | Ciclo mensual, generación de propuestas, snapshot de cálculo, asociación de factura y PDF. |
| `importacion` | Carga masiva CSV → ítems del ciclo. |
| `informes` | Informe de lo que se va a facturar (neto + IVA + total), solo lectura sobre `propuesta_facturacion` (ver §11). |

Cada módulo se organiza internamente en capas:

```
controlador  → API REST (DTOs de entrada/salida)
servicio     → lógica de negocio y orquestación
repositorio  → acceso a datos (Spring Data JPA)
entidad      → mapeo del modelo de datos
```

**Motivación:** el monolito modular permite límites claros entre dominios (facilitando una eventual extracción a servicios en el futuro) manteniendo la simplicidad operativa de un único despliegue.

---

## 6. Stack tecnológico

### Backend
- **Java 21** (LTS).
- **Spring Boot 4.0.6**, con:
  - Spring Web (API REST).
  - Spring Data JPA (persistencia).
  - Spring Security como *OAuth2 Resource Server* (validación de JWT).
  - Spring Scheduling / o Quartz para el proceso del día 1 (ver §9).
  - Spring Data Redis (caché).
- **Flyway** para migraciones de base de datos.
- **PostgreSQL 16** como base de datos relacional.
- **Redis 7** como caché.

### Frontend
- **Next.js 14** (App Router) + **TypeScript**.
- **Tailwind CSS** para estilos.
- Autenticación OIDC contra Keycloak (biblioteca de sesión OIDC del lado del frontend).
- Consumo de la API REST del backend con el JWT del usuario.

### Infraestructura (referencia; el detalle vive en `docs/despliegue.md`)
- **Servidor:** local, en la oficina de Helpcom, con acceso remoto — no una nube administrada.
- **Orquestación:** Docker Compose, por etapas (D1: base de datos e identidad; D2: backend/frontend; D3: realm de Keycloak; D4: reverse proxy HTTPS; D5: respaldos/observabilidad).
- **Almacenamiento de PDF:** a definir en una etapa de Despliegue posterior a D1 — el componente ya soporta ambos adaptadores por configuración (`AlmacenArchivosLocal`, sistema de archivos, o `AlmacenArchivosOci`, §14), así que la elección no requiere cambios de código.
- **Identidad:** Keycloak autoalojado, en el mismo Docker Compose.

> La topología concreta (qué contenedor corre cada pieza, variables, healthchecks, cómo levantar y verificar) se define en la etapa de **Despliegue** — ver `docs/despliegue.md`. Este documento fija el diseño lógico, no la topología final de infraestructura.

---

## 7. Autenticación y autorización

**Proveedor:** Keycloak autoalojado, hablando **OAuth 2.0 / OIDC**.

**Motivación de la elección:**
- Open source y sin costo de licencia.
- Integración directa con Spring Security como *Resource Server*.
- Soporte de *realms*, que es el camino natural hacia multi-tenant (un realm por empresa en el futuro).

**Flujo:**
1. El usuario se autentica en Keycloak desde el frontend (Authorization Code + PKCE).
2. El frontend obtiene un **JWT** y lo envía al backend en cada petición (`Authorization: Bearer`).
3. El backend valida el token como *Resource Server* y extrae los roles.

**Extracción de roles:** el backend lee los roles de realm del claim estándar de Keycloak `realm_access.roles` (no del claim `scope`, que es lo que usa el conversor por defecto de Spring Security) y los mapea a autoridades `ROLE_ADMINISTRADOR` / `ROLE_OPERADOR` mediante un `JwtAuthenticationConverter` propio. Los endpoints se protegen con `@PreAuthorize` a nivel de controlador usando esos roles.

**Roles (Etapa 1):**

| Rol | Descripción | Permisos (referenciales) |
|---|---|---|
| `ADMINISTRADOR` | Gestión completa. | Todo: clientes, proyectos, ciclo, informes, importación, asociación de facturas. |
| `OPERADOR` | Operación diaria. | Registro/consulta de clientes y proyectos, ejecución/consulta de informes, asociación de facturas. Sin operaciones administrativas sensibles (a definir en detalle). |

La matriz de permisos fina se especifica junto al modelo de datos y los endpoints.

---

## 8. Integración con el valor UF

El valor UF se obtiene de una **API pública** y se cachea en Redis.

**Fuente principal:** `mindicador.cl` — gratuita, sin API key, formato JSON, con consulta **por fecha específica**:

```
GET https://www.mindicador.cl/api/uf/{dd-mm-yyyy}
```

Es un espejo de la publicación oficial del Banco Central de Chile.

**Fuente oficial opcional (contraste/auditoría):** API de la **CMF** (`api.cmfchile.cl`), que requiere API key.

**Diseño del componente `uf`:**
- Un servicio `ServicioUf` con un método tipo `obtenerValorUf(LocalDate fecha)`.
- Detrás, un adaptador HTTP hacia mindicador.cl (patrón puerto/adaptador, para poder cambiar de fuente sin tocar la lógica de negocio).
- **Caché en Redis** con clave por fecha (`uf:{yyyy-MM-dd}`).
  - El valor UF de una fecha ya publicada **no cambia nunca**, por lo que el caché de fechas pasadas es permanente.
  - Se recomienda además persistir en base de datos las UF utilizadas en cálculos, para garantizar auditoría aunque el caché o la API externa fallen.

**Manejo de indisponibilidad:** si la API externa no responde, el sistema debe (a) usar el valor cacheado/persistido si existe, o (b) reportar el ítem como "pendiente de valor UF" en lugar de fallar el ciclo completo. El comportamiento exacto se detalla en el modelo de datos.

---

## 9. Proceso del ciclo de facturación (día 1)

Es el corazón del sistema.

**Disparo:** una tarea programada (`@Scheduled` con expresión cron, zona `America/Santiago`) se ejecuta el **día 1 de cada mes**. Se eligió `@Scheduled` en vez de Quartz porque el ciclo es un único disparo mensual sin necesidad de persistencia de triggers ni reprogramación dinámica; Quartz habría agregado complejidad operacional (esquema propio, gestión de jobs) sin resolver nada que `@Scheduled` no resolviera ya. Se puede deshabilitar por configuración (`app.ciclo.programado.habilitado=false`) para entornos de prueba. También debe poder ejecutarse manualmente (re-ejecución) por un administrador.

**Qué hace, para el mes en curso:**

1. Recorre todos los proyectos activos de la empresa.
2. Determina, según periodicidad y día de facturación, **si el proyecto corresponde facturarse este mes** y **en qué día**:
   - **Mensual:** corresponde todos los meses, en su día de facturación. El **primer** cobro es el mes siguiente al de inicio del proyecto (sin prorrateo del período parcial inicial).
   - **Anual:** corresponde solo en el mes/día del contrato (aniversario), partiendo desde el mes del contrato.
   - **Días inexistentes:** si el día de facturación no existe en el mes (p. ej. 31 en febrero), se usa el **último día del mes**.
3. Determina el **precio aplicable** del proyecto: si existe un **acuerdo de precio** vigente en la fecha de facturación (ver §9.1), aplica ese acuerdo; si no, usa el precio base.
4. Obtiene el **valor UF del día de facturación** (si corresponde) y calcula el monto neto en CLP. **Si la UF no está disponible** (`ValorUfNoDisponibleException`), la propuesta se genera igual, pero con `neto_clp`/`iva_clp`/`total_clp` en **0** y estado `PENDIENTE_UF` — nunca se inventan cifras — y el ciclo **continúa** con el resto de los proyectos (no aborta el período completo por un proyecto). La ejecución queda con estado `CON_ADVERTENCIAS`.
5. Genera una **propuesta de facturación** (borrador) con el *snapshot* inmutable: precio base, acuerdo aplicado (tipo y valor), neto final, moneda origen, valor UF usado, fecha de conversión, tasa IVA aplicada y monto CLP resultante.

**Idempotencia:** cada propuesta se identifica de forma única por `(proyecto, período año-mes)`. Si el proceso corre dos veces, no se duplican ítems: los ya existentes se respetan y no se recalculan. Esta unicidad se refuerza con una restricción a nivel de base de datos (índice único parcial sobre `origen = 'CICLO'`), que además actúa como red de seguridad ante condiciones de carrera entre ejecuciones concurrentes.

**Transaccionalidad:** cada proyecto se procesa en su propia transacción. Si uno falla al persistir, esa transacción se revierte sola — no arrastra ni pierde las propuestas ya generadas por otros proyectos ni el registro de `ejecucion_ciclo`. El ciclo avanza todo lo posible y reporta lo que no pudo procesar, en vez de abortar el período completo por un solo proyecto problemático.

**Componente compartido con la importación CSV (`ArmadorPropuesta`):** los pasos 3 y 4 —resolver el acuerdo vigente, obtener el valor UF (con su fallback a `PENDIENTE_UF`), obtener la tasa de IVA vigente y llamar a `CalculadoraFacturacion`— viven en `facturacion.armado.ArmadorPropuesta`, no en `ServicioCicloFacturacion`. Este componente recibe un `EntradaArmadoPropuesta` (empresa, cliente, proyecto opcional, período, fecha, descripción, precio neto y moneda) y retorna una `PropuestaFacturacion` con el snapshot completo, sin persistirla. El ciclo lo usa con los datos del proyecto; la importación CSV (§10) lo usa con los datos de cada fila. Es la única implementación de "armar una propuesta a partir de una fuente de datos más una fecha", para que ambos caminos calculen siempre igual.

**Separación borrador / factura:** la propuesta de facturación es una entidad **distinta** de la futura factura. En Etapa 1 el usuario asocia manualmente el número de factura y sube el PDF sobre la propuesta. En Etapa 2, la integración con Crux ERP se "enchufa" sobre esta misma estructura sin rehacerla.

### 9.1 Acuerdos de precio (descuentos y precios pactados)

Un proyecto tiene un **precio base** fijo (normalmente en UF). Opcionalmente puede tener **acuerdos de precio** temporales con el cliente. Reglas:

- **Un solo acuerdo vigente a la vez** por proyecto. Los acuerdos de un mismo proyecto **no pueden traslaparse** en el tiempo (validación obligatoria).
- **Vigencia:** definida por fecha de inicio y fecha de término. La fecha de término puede indicarse directamente o derivarse de una cantidad de meses pactada (ambas formas soportadas).
- **Tipos de acuerdo:**
  - *Descuento porcentual* — un porcentaje sobre el neto.
  - *Descuento por monto fijo* — un monto en UF o en CLP.
  - *Precio pactado* — reemplaza el precio base, en UF o CLP.
- **Aplicación del cálculo:**
  - Todo se calcula sobre el **neto**; el IVA se aplica sobre el neto **ya rebajado**.
  - Los ajustes en **UF** (porcentaje o monto) se aplican sobre el precio en UF **antes** de convertir a CLP; los montos en **CLP** se aplican **después** de convertir a CLP.
  - Un *precio pactado* **reemplaza** al precio base durante su ventana de vigencia.
  - Los montos finales se redondean a **pesos enteros**.
- Al **finalizar** el acuerdo, el proyecto vuelve automáticamente a su **precio base**.
- El acuerdo aplicado (tipo y valor) queda registrado en el **snapshot** del ítem para auditoría.

El modelado de la tabla `acuerdo_precio` y sus restricciones se detallan en el documento de *Modelo de datos*.

---

## 10. Importación masiva CSV

Para proyectos que hoy se calculan en Excel, se habilita una **importación CSV** (módulo `importacion/`) cuyas líneas se convierten en propuestas de facturación (`propuesta_facturacion`, `origen = 'CSV'`), calculadas con el mismo `ArmadorPropuesta` y `CalculadoraFacturacion` que usa el ciclo (§9) — no una implementación paralela.

**Flujo en dos fases:**
1. **Previsualizar** (`POST /api/v1/importaciones/previsualizar`, multipart): parsea el CSV (Apache Commons CSV; separador `;`, UTF-8 tolerante a BOM y a espacios en los encabezados) y valida cada fila (`ValidadorFilaCsv`): formato y existencia del cliente por RUT, formato de período/fecha y su consistencia, moneda, monto neto, y — si viene `codigo_proyecto` — que el proyecto exista y pertenezca al mismo cliente. No persiste nada. Para las filas sin error, calcula el resultado (`ArmadorPropuesta`) y lo devuelve junto al estado de la fila, para que el usuario vea qué se va a facturar antes de confirmar.
2. **Confirmar** (`POST /api/v1/importaciones/confirmar`, multipart): el cliente reenvía el **mismo archivo**; el servicio vuelve a parsear y validar (no hay estado guardado entre las dos llamadas) y persiste todo en una única transacción: crea `importacion_csv` (contadores y estado `PROCESADA`/`PARCIAL`/`RECHAZADA`), guarda el CSV original vía `AlmacenArchivos` (§14) enlazando `archivo_id`, y crea una `propuesta_facturacion` por cada fila válida.

   **Decisión de diseño — por qué reenviar el archivo en vez de un id temporal:** es más simple, no exige limpiar archivos temporales de previsualizaciones nunca confirmadas, y re-validar es barato comparado con la complejidad de sostener estado entre dos peticiones HTTP.

**Estados de fila:** `OK`, `ADVERTENCIA` (se importa igual — p. ej. la fecha no coincide con el período, o la UF no está disponible para la fecha, quedando la propuesta en `PENDIENTE_UF` con montos en 0, igual que el ciclo) y `ERROR` (no se importa).

**Idempotencia CSV vs. CICLO:** si una fila referencia un proyecto que ya tiene una propuesta `origen='CICLO'` para el mismo (proyecto, período), la fila se marca **`ERROR`** y no se importa — no `ADVERTENCIA`, porque en este sistema las filas `ADVERTENCIA` sí se importan, y permitirlo duplicaría la propuesta. No existe restricción de base de datos para este caso (el índice único parcial de §9 solo cubre `origen='CICLO'`); es una regla de negocio evaluada en `ValidadorFilaCsv`, la misma en ambas fases.

**Reglas de cálculo:** los montos vienen **netos** en la fila (columna `monto_neto`), no del precio configurado del proyecto — incluso cuando la fila referencia un proyecto existente, ese proyecto solo se usa para resolver su acuerdo de precio vigente (si tiene uno) y para el enlace de trazabilidad; si la fila no trae `codigo_proyecto`, no se aplica ningún acuerdo. La conversión UF → CLP (si aplica) usa el valor UF de la fecha de facturación indicada en la fila.

Las **columnas exactas del CSV** se definen en el documento de *Modelo de datos* §6.

---

## 11. Informe de facturación

El módulo `informes/` expone "lo que se va a facturar" (neto + IVA 19% + total): **solo lectura y agregación** sobre `propuesta_facturacion` (§9, §10) — no crea ni modifica datos. Un único endpoint, `GET /api/v1/informes/facturacion`, sirve **resumen** (totales y desgloses) y **detalle paginado** juntos, porque ambas partes comparten los mismos filtros y se consumen en la misma pantalla; separar en `/resumen` y `/detalle` solo habría duplicado la lista de filtros sin un caso de uso real que lo justificara hoy.

**Filtros** (todos opcionales, se combinan con AND): período exacto (`periodoAnio`/`periodoMes`) o rango multi-período (`anioMesDesde`/`anioMesHasta`, codificados `AAAAMM`), `clienteId`, uno o varios `estados`, `origen` (`CICLO`/`CSV`), y `facturada` (sí/no). Todo acotado por `empresa_id` vía `ContextoEmpresa`.

**Política de totales (decisión de diseño):** los totales (`netoClp`/`ivaClp`/`totalClp`) suman **solo** las propuestas en estado `PENDIENTE` o `FACTURADA` — "lo que efectivamente se va a facturar o ya se facturó" — y **excluyen por completo**:
- `PENDIENTE_UF`: no tiene un monto real (queda en 0 hasta poder recalcularse); se excluye para que el total no se lea como "ya contempla esa propuesta". Su cantidad se reporta **aparte y explícita** (`cantidadPendienteUf`), nunca se pierde de vista.
- `ANULADA`: una propuesta anulada no se factura, así que no es "lo que se va a facturar" bajo ninguna definición razonable — se excluye incluso si el usuario filtra explícitamente por `estados=ANULADA` (los totales igual dan 0; la cantidad y el detalle sí la muestran).

El desglose por estado (`cantidadPorEstado`) sí incluye los 4 estados con su cantidad real (0 si no hay), para que sea transparente aunque los totales no sumen todos. El desglose opcional por cliente (`porCliente`) usa la misma exclusión: solo subtotaliza clientes con propuestas facturables dentro del período filtrado.

**Implementación:** los totales y desgloses se calculan con **una consulta de agregación SQL por desglose** (`SUM`/`COUNT` con `GROUP BY`, vía `CriteriaBuilder` reutilizando las mismas `Specification` del detalle — así ambos responden siempre a los mismos filtros), nunca cargando las propuestas en memoria para sumarlas en Java; solo se suman en Java los 2 a 4 subtotales ya agregados por estado que corresponden a los estados facturables. El detalle usa paginación estándar (`Pageable`).

**Exportación CSV** (nice-to-have, implementado): `GET /api/v1/informes/facturacion/export` exporta el detalle filtrado completo (sin paginar) con el mismo separador `;` del resto del sistema; los montos van como números crudos, sin formatear a moneda — ese formato es responsabilidad del frontend.

---

## 12. Persistencia y migraciones

- **Motor:** PostgreSQL 16.
- **ORM:** Spring Data JPA / Hibernate.
- **Migraciones:** **Flyway**, versionadas y en español.
  - Convención de nombre: `V001__descripcion_en_espanol.sql`, `V002__...`, correlativas.
  - La **primera migración** del proyecto es `V001`.
  - Toda modificación de esquema se hace vía migración Flyway; nunca a mano sobre la base.
- **Montos:** se almacenan en tipos exactos (`NUMERIC`), nunca en punto flotante, para evitar errores de redondeo en dinero.
- **Auditoría de registros:** las tablas de negocio incluyen columnas de auditoría (creado/modificado por, fechas). El detalle está en el *Modelo de datos*.

---

## 13. Caché (Redis)

Usos previstos de Redis en la Etapa 1:

- **Valor UF por fecha** (`uf:{yyyy-MM-dd}`), ver §8.
- **Lock distribuido** para el proceso del día 1, evitando ejecuciones concurrentes que compitan (complementario a la idempotencia de base de datos).
- Espacio para caché de consultas de lectura frecuentes (informes) si se requiere, como optimización posterior.

---

## 14. Almacenamiento de archivos (PDF de facturas)

- Los **PDF de facturas** emitidas se guardan en **almacenamiento de objetos** (OCI Object Storage), **no** en la base de datos.
- En la base se persiste solo la **referencia** al objeto (`clave_objeto`), junto al número de factura y metadatos.
- **Diseño del componente:** igual patrón puerto/adaptador que el módulo `uf` (ver §8). Un
  puerto `AlmacenArchivos` (guardar/obtener/eliminar bytes) con dos adaptadores:
  `AlmacenArchivosLocal` (sistema de archivos, perfiles local/dev/test, por defecto) y
  `AlmacenArchivosOci` (OCI Object Storage vía su API S3-compatible, usando el SDK de AWS en
  vez del SDK completo de OCI, por ser más liviano). Se elige por configuración
  (`app.almacenamiento.tipo=local|oci`). El esquema de claves es `<uuid-v4><extensión
  original>` (p. ej. `550e8400-…-446655440000.pdf`), sin jerarquía de carpetas por fecha —
  el volumen esperado en Etapa 1 no la justifica.
- **Descarga sin URLs públicas:** el acceso a los PDF **pasa siempre por el backend**
  (`GET /api/v1/facturas/{id}/pdf`, con control de rol), que hace streaming del contenido.
  El backend **nunca** expone la `clave_objeto` ni URLs directas del bucket al cliente. Si
  más adelante se necesitan URLs pre-firmadas de OCI (para descargas masivas o integraciones
  externas), es una decisión a evaluar aparte, no la de esta etapa.
- **Reemplazo de PDF:** subir un PDF nuevo sobre una factura que ya tenía uno reemplaza el
  enlace y **elimina** el objeto y la fila `archivo` anteriores (no se acumulan huérfanos).

---

## 15. Seguridad

- **Transporte:** HTTPS en todo el tráfico.
- **Autenticación/Autorización:** OIDC + JWT validado en el backend; autorización por rol.
- **Aislamiento por empresa:** aunque hoy es single-tenant, `empresa_id` es obligatorio en las tablas de negocio para no filtrar datos cuando se active multi-tenant.
- **Datos sensibles y secretos:** credenciales de base de datos, claves de la API CMF (si se usa) y secretos de Keycloak **no** se versionan; se gestionan como configuración/secretos del entorno.
- **Validación de entrada:** validación en el backend de todo dato de entrada (incluidas filas de CSV).
- **Auditoría:** registro de quién ejecuta operaciones sensibles (ejecución de ciclo, asociación de facturas, importaciones).

---

## 16. Observabilidad

- **Logs estructurados** en el backend, con identificación de la operación y (a futuro) de la empresa.
- **Trazabilidad del ciclo:** cada ejecución del proceso del día 1 deja registro de cuándo corrió, cuántos ítems generó y qué proyectos quedaron pendientes (p. ej. por falta de valor UF).
- Métricas y monitoreo de infraestructura se detallan en la etapa de Despliegue.

---

## 17. Estructura de proyecto (referencia)

**Backend (paquete raíz orientativo `cl.helpcom.facturacion`):**

```
cl.helpcom.facturacion
├── empresa/
├── seguridad/
├── clientes/
├── proyectos/
├── uf/
├── facturacion/
├── importacion/
├── informes/
└── comun/          (utilidades, manejo de errores, configuración transversal)
```

**Frontend (Next.js App Router):**

```
app/
├── (auth)/         (login / callback OIDC)
├── clientes/
├── proyectos/
├── facturacion/
├── importacion/
├── informes/
└── ...
components/
lib/                (cliente API, auth, utilidades)
```

La estructura definitiva y las convenciones de nombres se fijan en el documento de *Estándares de código*.

---

## 18. Preparación para la Etapa 2

Decisiones tomadas hoy que habilitan la Etapa 2 sin rehacer:

- **Propuesta de facturación separada de la factura:** la integración con **Crux ERP** y la emisión de DTE se conectan sobre las propuestas ya existentes.
- **Snapshot inmutable del cálculo:** los montos ya calculados no dependen de la fuente UF externa al momento de emitir.
- **`empresa_id` en todas las tablas:** habilita el paso a SaaS multi-tenant.
- **Adaptador de fuente UF desacoplado:** permite cambiar o complementar la fuente (p. ej. CMF) sin tocar la lógica de facturación.

---

## 19. Decisiones abiertas / a confirmar en etapas siguientes

- Matriz fina de permisos `ADMINISTRADOR` vs `OPERADOR`.
- Regla de recálculo cuando el ciclo se re-ejecuta sobre un período con ítems ya asociados a factura.
- **Resuelto (D1):** topología de infraestructura — servidor local en la oficina de Helpcom con acceso remoto, Docker Compose (`docs/despliegue.md`), no OCI. Quedan abiertas las etapas D2-D5 de esa misma hoja de ruta (backend/frontend contenerizados, realm de Keycloak, reverse proxy HTTPS, respaldos/observabilidad).

---

## 20. Glosario

| Término | Definición |
|---|---|
| **UF** | Unidad de Fomento. Unidad de cuenta reajustable a diario; su valor mensual se conoce desde el día 10 del mes anterior. |
| **CLP** | Peso chileno. Moneda de expresión final de todos los montos. |
| **Neto** | Valor sin IVA. Todos los precios de proyectos y servicios se registran netos. |
| **IVA** | Impuesto al Valor Agregado (19% a la fecha; parametrizable). |
| **Propuesta de facturación** | Borrador de lo que se va a facturar en un período, generado por el ciclo. Base de la futura factura. |
| **Acuerdo de precio** | Descuento (porcentaje, monto en UF/CLP) o precio pactado temporal aplicado a un proyecto durante una vigencia. Solo uno vigente a la vez; al terminar vuelve el precio base. |
| **Ciclo de facturación** | Proceso que el día 1 de cada mes calcula las propuestas del mes. |
| **Snapshot de cálculo** | Copia inmutable de los valores usados en un cálculo (UF, fecha, tasa IVA, monto CLP). |
| **Single-tenant preparado** | Diseño para una empresa, con la costura lista para activar multi-tenant. |
| **DTE** | Documento Tributario Electrónico (facturación electrónica). Etapa 2. |

---

*Fin del documento — Arquitectura Técnica (Etapa 1).*
