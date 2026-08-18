# CLAUDE.md

Guía para Claude Code. **Léela al inicio de cada sesión**, junto con los documentos de `docs/` relevantes a la tarea. Ante conflicto entre memoria y estos documentos, **mandan los documentos**.

---

## Qué es este proyecto

Sistema de **facturación recurrente de proyectos** para PyMEs chilenas, comenzando con **Helpcom Ltda.** Registra proyectos que se facturan por período (mensual o anual) en UF o CLP, calcula el ciclo el **día 1 de cada mes**, y genera el informe de lo que se va a facturar (neto + IVA 19% + total). La emisión electrónica y la integración con **Crux ERP** son de la Etapa 2.

**Etapa actual:** desarrollo, sobre la arquitectura ya definida en `docs/`.

---

## Stack

- **Backend:** Java 21 · Spring Boot 4.0.6 · PostgreSQL 16 · Redis 7 · Flyway
- **Frontend:** Next.js 14 · TypeScript · Tailwind CSS
- **Auth:** Keycloak (OAuth2 / OIDC) · **Despliegue:** servidor local en la oficina de Helpcom, con acceso remoto, todo en Docker Compose (ver `docs/despliegue.md`)

---

## Documentos de referencia (`docs/`)

- **`arquitectura-tecnica.md`** — visión general, módulos, ciclo del día 1, seguridad, integración UF, multi-tenant preparado.
- **`modelo-de-datos.md`** — tablas y DDL, reglas de cálculo (§5), especificación del CSV (§6), plan de migraciones Flyway (§7).
- **`estandares-de-codigo.md`** — convenciones de backend y frontend, dinero, fechas, Flyway, Git.
- **`frontend.md`** — fundación del frontend: autenticación OIDC/Keycloak (Auth.js v5), shell, cliente API, componentes base, cómo levantarlo en local.
- **`deuda-tecnica.md`** — lista viva de deuda técnica consciente (vulnerabilidades aceptadas temporalmente, contadores/discrepancias de contrato pendientes). Revisar antes de cualquier despliegue a producción.
- **`qa.md`** — suite E2E de integración determinística (`backend/src/test/java/.../e2e/`): qué flujos cubre, cómo correrla (`mvn test -Dgroups=e2e`), y la estrategia que la hace determinística (UF sembrada, sin red, sin Keycloak real, contenedores Postgres/Redis compartidos). Base del guion de QA manual.
- **`guion-qa-manual.md`** — guion de QA manual en español para una persona de Helpcom no desarrolladora (perfil administrativo/contable): los mismos 9 flujos de `qa.md` traducidos a pasos clic a clic, con datos de ejemplo y resultados esperados en lenguaje de negocio. Contraparte manual de `qa.md`.
- **`despliegue.md`** — despliegue en Docker Compose sobre un servidor local de Helpcom con acceso remoto, por etapas (D1: base de datos e identidad — Postgres, Redis, Keycloak; D2: backend y frontend contenerizados, migraciones aplicadas; D3: realm de Keycloak como código, login/autorización verificados de punta a punta. D4-D5 planificadas). Detalle de cada servicio, variables de `deploy/.env`, cómo levantar y verificar.
- **`plan-rediseno.md`** — hoja de ruta del rediseño visual del frontend con la identidad Helpcom ("Confianza": paleta azul/celeste, Montserrat, sidebar/barra inferior, dashboard nuevo). Auditoría del frontend actual, tokens de Tailwind propuestos, navegación y acceso a Descuentos, alcance del dashboard con el origen de cada dato, y la secuencia de etapas R1..R9 con archivos/criterios/verificación. **Plan, no implementación** — revisar el estado de cada etapa ahí antes de tocar código de presentación.

Lee el documento del área antes de tocar su código.

---

## Reglas de oro (no negociables)

1. **Dominio en español**: clases, métodos, variables, paquetes de dominio, tablas, columnas y comentarios. Las palabras clave del lenguaje y las anotaciones de framework quedan en su idioma.
2. **Dinero con `BigDecimal`** siempre. UF con escala 4; **CLP con escala 0 (entero)**. Redondeo `HALF_UP`. Nunca `double` ni `float`.
3. **Fechas con `java.time`**. Zona de negocio **`America/Santiago`** (ciclo del día 1 y día de facturación). Los `TIMESTAMPTZ` se guardan en UTC.
4. **Migraciones Flyway correlativas**: `V###__descripcion_en_espanol.sql`. Una migración = un cambio lógico. Las ya fusionadas son **inmutables** (se crea una nueva, no se edita).
5. **`empresa_id` en toda tabla de negocio y en toda consulta.** Hoy es fijo en Helpcom vía `ContextoEmpresa`; jamás nulo. Única excepción: `valor_uf` (es global, nacional).
6. **Snapshot inmutable** en `propuesta_facturacion`: los valores del cálculo (precio base, acuerdo, UF, neto, IVA, total) no cambian en recálculos posteriores.
7. **Idempotencia del ciclo**: respeta el índice único parcial `(proyecto_id, periodo_anio, periodo_mes)` para propuestas de `origen = 'CICLO'`. El proceso puede correr más de una vez sin duplicar.
8. **Un solo acuerdo de precio vigente** por proyecto (lo fuerza la *exclusion constraint* `ex_acuerdo_no_traslape`). No traslapar vigencias.
9. **Sin secretos en el repositorio.** Configuración por variables de entorno. Los parámetros de negocio (p. ej. `tasa_iva`) viven en `parametro_sistema`, no en configuración de aplicación.
10. **No exponer entidades JPA** en los controladores; siempre DTOs (`record`). Inyección por **constructor**.

---

## Estructura

**Backend** (paquete raíz `cl.helpcom.facturacion`), por módulo de negocio y capa:
```
<modulo>/ { controlador/ servicio/ repositorio/ dominio/ dto/ }   +   comun/
```
Módulos: `empresa · seguridad · clientes · proyectos · uf · facturacion · importacion · informes`.

**Frontend** (Next.js App Router): `app/ · components/ · lib/ · types/`.

---

## Migraciones — estado

- Ubicación: `src/main/resources/db/migration`.
- **Próxima migración: `V012`**. **Mantén este número actualizado** a medida que agregues migraciones.
- `V001`→`V011` ya están creadas, según el plan de `modelo-de-datos.md` §7: `V001` habilita `btree_gist` y crea `empresa`/`usuario`/`parametro_sistema`; `V002`→`V010` crean el resto del esquema (`tipo_servicio`, `cliente`, `proyecto`, `acuerdo_precio`, `valor_uf`, `archivo`+`factura`, `importacion_csv`, `propuesta_facturacion`, `ejecucion_ciclo`); `V011` inserta los datos semilla (empresa Helpcom con RUT placeholder, `tasa_iva`).
- Entidades JPA y repositorios base ya existen para las 13 tablas (sin lógica de negocio ni endpoints todavía). `Flyway` corre automáticamente al arrancar la app; Hibernate usa `ddl-auto=validate` (nunca genera DDL).

---

## Reglas de negocio (resumen — detalle en `modelo-de-datos.md` §5)

- Precio base **neto**, normalmente en **UF**. Conversión a CLP con la UF del **día de facturación** de cada proyecto. Fuente UF: **mindicador.cl**, cacheada en Redis y persistida en `valor_uf`.
- **Mensual:** factura cada mes en su día; el **primer cobro es el mes siguiente** al de inicio (sin prorrateo). **Anual:** en el mes y día del contrato.
- **Día inexistente** en el mes (p. ej. 31 en febrero) → **último día del mes**.
- **Acuerdos de precio:** descuento %, descuento monto (UF/CLP) o precio pactado (UF/CLP), con vigencia. Ajustes en **UF antes** de convertir, en **CLP después**; todo sobre el **neto**; el IVA se aplica al neto **ya rebajado**.
- **CSV de importación** → propuestas con `origen = 'CSV'`, con las **mismas reglas de cálculo** del ciclo. Columnas en `modelo-de-datos.md` §6.

---

## Flujo de trabajo para cada tarea

1. Lee este `CLAUDE.md` y el/los documento(s) de `docs/` que apliquen.
2. Aplica las reglas de oro y los estándares de código.
3. Si el cambio toca la base de datos: crea la migración Flyway con el **número siguiente** e indícalo en tu respuesta.
4. Escribe pruebas (obligatorio para cálculo y ciclo).
5. Al terminar: **actualiza el documento de `docs/` correspondiente** si el cambio lo afecta.

---

## Definición de "terminado"

Código conforme a estándares (dominio en español, capas) · dinero y fechas correctos · migración Flyway si cambió el esquema · pruebas de la lógica nueva · linter/formateador en verde · documento de `docs/` actualizado.

---

## Comandos

Todos los comandos se ejecutan desde la carpeta indicada (`backend/` o `frontend/`).

### Backend (Maven)

| Acción | Comando |
|---|---|
| Compilar | `mvn compile` |
| Probar | `mvn test` (solo la suite E2E: `mvn test -Dgroups=e2e`; todo menos la E2E: `mvn test -DexcludedGroups=e2e` — ver `docs/qa.md`) |
| Empaquetar | `mvn package` |
| Levantar en local (perfil `local`) | `mvn spring-boot:run` |
| Migraciones Flyway | Se aplican automáticamente al arrancar la app (`spring.flyway.enabled=true`). Manual: `mvn flyway:migrate` |

El perfil activo por defecto es `local` (`SPRING_PROFILES_ACTIVE`). Requiere PostgreSQL, Redis y Keycloak accesibles según las variables de entorno de `application-local.yml` (con valores por defecto para `localhost`).

Las pruebas de esquema/migraciones (`EsquemaBaseDatosTest`) y la suite E2E (`docs/qa.md`, paquete `e2e/`) usan Testcontainers con PostgreSQL 16 real (la E2E además con Redis real, ambos en un único contenedor compartido por toda la suite). **Verificación contra infraestructura real: en verde** (última corrida: 5/5 en `EsquemaBaseDatosTest`, 27/27 en la suite E2E, 235/235 en la suite completa, 0 fallos). La limitación histórica de "Docker no alcanzable desde la JVM en Windows" quedó resuelta con configuración de **máquina** (no del repo, para no afectar CI ni otros entornos) — si `mvn test` vuelve a fallar solo en esas clases con "Could not find a valid Docker environment" en una máquina Windows con Docker Desktop reciente, revisar/crear estos dos archivos en el `HOME` del usuario:
- `~/.testcontainers.properties` → `docker.host=npipe:////./pipe/docker_engine_linux` (el pipe por defecto, `docker_engine`, puede responder con un JSON vacío que redirige a otro pipe interno — `docker_cli` — en vez de servir la API de Docker).
- `~/.docker-java.properties` → `api.version=1.41` (docker-java, el cliente que usa Testcontainers, negocia por defecto con la versión de API 1.32; Docker Desktop reciente la rechaza por antigua, exige mínimo 1.40).

Detalle completo del diagnóstico en el javadoc de la clase.

### Frontend (npm)

| Acción | Comando |
|---|---|
| Instalar dependencias | `npm install` |
| Levantar en local | `npm run dev` |
| Compilar (build de producción) | `npm run build` |
| Iniciar build de producción | `npm run start` |
| Linter | `npm run lint` |
| Formatear con Prettier | `npm run format` (`npm run format:check` solo verifica) |
| Pruebas | `npm run test` (`npm run test:watch` en modo watch) |

Variables de entorno: copiar `.env.example` a `.env.local` y completar (`NEXT_PUBLIC_API_BASE_URL`, `AUTH_KEYCLOAK_ISSUER`/`AUTH_KEYCLOAK_ID`/`AUTH_KEYCLOAK_SECRET`, `AUTH_SECRET`). Cómo levantar un Keycloak local y el detalle de cada variable: `docs/frontend.md` §1.

---

## Spring Boot 4 — cambios que muerden

Durante la Etapa 2 aparecieron **cuatro defectos con la misma causa raíz**: Spring Boot 4 /
Spring 7 modularizaron autoconfiguraciones que antes venían "gratis" con un starter más
genérico, y endurecieron contratos que antes eran permisivos. Los cuatro son **silenciosos con
mocks o H2** — solo se manifiestan contra infraestructura real (Postgres, HTTP real, `save()`
real).

1. **Flyway no corre, sin error visible.** Con solo `flyway-core` en el classpath, Boot 4 ya no
   autoconfigura el `FlywayMigrationInitializer` (la autoconfiguración se movió a un starter
   aparte). El síntoma es engañoso: Hibernate falla al arrancar con "missing table", como si el
   problema fuera el modelo, no la migración que nunca corrió. **Solución:** agregar el starter
   dedicado `spring-boot-flyway` (ya está en `pom.xml`, junto a `flyway-core` y
   `flyway-database-postgresql`).
2. **`RestClient.Builder` no se inyecta, y un test con mock golpea la API real en silencio.**
   `spring-boot-starter-web` ya no autoconfigura `RestClient.Builder` — se movió a
   `spring-boot-restclient`. Sin ese módulo, `MockRestServiceServer` no intercepta nada: la
   llamada sale de verdad hacia `mindicador.cl` mientras el test parece (o no) seguir
   funcionando. Visto en `uf/fuente/FuenteUfMindicador.java` y su test. **Solución:** agregar
   el módulo `spring-boot-restclient` (ya está en `pom.xml`).
3. **`@CreatedDate`/`@LastModifiedDate` fallan en todo insert real.** El `DateTimeProvider` por
   defecto de Spring Data no sabe convertir a `OffsetDateTime` (el tipo que usa
   `EntidadAuditable` para `creado_en`/`modificado_en`, coherente con `TIMESTAMPTZ`).
   **Solución:** un bean `DateTimeProvider` explícito en `AuditoriaConfig`
   (`comun/config/AuditoriaConfig.java`).
4. **`Specification.and()`/`allOf()` ya no toleran `null`.** Antes ignoraban en silencio una
   especificación `null` (el patrón típico para "este filtro es opcional" en un listado
   dinámico); Spring Data JPA 4 lanza `IllegalArgumentException` en cuanto se omite un filtro
   opcional — revienta cada listado con filtros dinámicos apenas alguno se deja sin usar.
   **Solución:** un combinador null-safe (`combinar(...)`, que filtra los `null` antes de
   llamar a `Specification.allOf`), repetido en las cuatro clases `*Especificaciones`
   (`ClienteEspecificaciones`, `ProyectoEspecificaciones`, `FacturaEspecificaciones`,
   `PropuestaFacturacionEspecificaciones`) — nunca encadenar `.and(...)` a mano fuera de ese
   combinador. Detalle completo en el javadoc de cada clase.

**Moraleja:** estos cuatro defectos son invisibles con mocks/H2 y solo aparecen contra
infraestructura real. Ante una biblioteca que "no hace nada" sin ningún error visible en
Boot 4, sospecha primero que **falta su starter dedicado**, y **verifica arrancando o
probando contra infraestructura real**, no solo contra un mock o H2. La propia
`EsquemaBaseDatosTest` (arriba, en Comandos) y su configuración de Docker-desde-Java es un
ejemplo del mismo principio: lo que pasa en H2 o con mocks no prueba nada sobre lo que pasará
contra la infraestructura real.

---

## Decisiones abiertas (no asumir; preguntar o dejar `TODO`)

- ¿El CSV **crea** clientes inexistentes o **exige** que existan? (default asumido: exige que existan)
- Matriz fina de permisos **ADMINISTRADOR** vs **OPERADOR**.
- Recálculo del ciclo sobre períodos con propuestas ya `FACTURADA` (default: no tocarlas).

**Resuelto:** si la API de la UF no responde el día del cálculo, la propuesta se genera igual
con `neto_clp`/`iva_clp`/`total_clp` en 0 y estado `PENDIENTE_UF` (nunca se inventan cifras);
el ciclo continúa con el resto de los proyectos. Ver `arquitectura-tecnica.md` §9.

**Deuda técnica pendiente de decisión** (vulnerabilidades de Next.js aceptadas temporalmente,
contadores/discrepancias de contrato menores): ver `docs/deuda-tecnica.md` — **revisar antes
de cualquier despliegue a producción**.
