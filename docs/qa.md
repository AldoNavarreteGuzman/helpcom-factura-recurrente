# QA — Suite E2E de integración determinística

**Etapa:** desarrollo. Este documento describe la suite de pruebas **E2E de integración**
(`backend/src/test/java/cl/helpcom/facturacion/e2e/`), qué cubre, cómo correrla y la
estrategia que la hace determinística. Es la base sobre la que se construirá el guion de QA
manual (QA-2).

---

## 1. Propósito

Las pruebas unitarias, de módulo y `@WebMvcTest` (con mocks) de este proyecto verifican piezas
aisladas. Ninguna de ellas ejercita el sistema **completo**: HTTP real → filtro de seguridad →
`@PreAuthorize` → controlador → servicio → cálculo → persistencia real en PostgreSQL. La suite
E2E llena ese hueco: es la red que atrapa bugs de **integración entre módulos** — el tipo de
bug que solo aparece cuando dos piezas correctas por separado interactúan mal juntas (ver §5,
donde esta misma suite encontró uno).

No reemplaza las pruebas existentes: las complementa. Sigue corriendo todo lo demás
(`ServicioXTest`, `XControladorTest`, `XRepositorioTest`, `EsquemaBaseDatosTest`, etc.) sin
cambios.

---

## 2. Cómo correrla

Todo pasa por `mvn test` normal — la suite E2E está integrada, no es un paso aparte del build.
Requiere **Docker** accesible desde la JVM (mismo requisito que `EsquemaBaseDatosTest`; ver
CLAUDE.md § "Verificación de esquema real" si `mvn test` falla solo con
"Could not find a valid Docker environment" en Windows).

| Objetivo | Comando |
|---|---|
| Todo el backend (unitarias + integración + E2E) | `mvn test` |
| **Solo** la suite E2E | `mvn test -Dgroups=e2e` |
| Todo **menos** la suite E2E (iteración rápida) | `mvn test -DexcludedGroups=e2e` |
| Solo un flujo E2E | `mvn test -Dtest=FlujoCaminoFelizE2ETest` |

Las nueve clases de flujo están marcadas con `@Tag("e2e")`, lo que habilita el filtrado por
grupo de JUnit 5 (`-Dgroups`/`-DexcludedGroups`, soportado nativamente por el provider
JUnit Platform de `maven-surefire-plugin`, sin configuración adicional en el `pom.xml`). Sus
nombres de clase siguen además el patrón `Flujo*E2ETest`, así que un simple
`-Dtest=Flujo*E2ETest` también sirve para aislarlas.

**Rendimiento:** toda la suite corre en **un solo contenedor Postgres y un solo Redis**,
reutilizados por las nueve clases (patrón *singleton container*, ver §3). Spring además
reutiliza el **mismo `ApplicationContext`** en las nueve clases (mismos `@MockitoBean`, mismas
propiedades dinámicas) — solo la primera clase que corre paga el arranque completo
(~20-30 s: Postgres + Redis + Flyway + Spring); el resto corre en menos de un segundo cada
una. La suite completa toma el tiempo del arranque una vez, más ~30 s de
`FlujoAcuerdosPrecioE2ETest` (ocho sub-flujos con HTTP real, deliberadamente exhaustivo) y
fracciones de segundo para las demás.

---

## 3. Estrategia determinística

**Nunca golpea la red.** `FuenteUf` (el cliente HTTP hacia mindicador.cl) se reemplaza en toda
la suite por un `@MockitoBean` que siempre retorna `Optional.empty()`. La UF que necesita un
flujo se **siembra directo en la tabla `valor_uf`** (vía el repositorio, antes de ejercitar el
flujo) — nunca vía la fuente externa. Esto hace que:
- Cada monto esperado sea **calculable a mano** con la tabla de modelo-de-datos.md §5 y sea
  **estable** entre corridas (no depende de qué valor tenga la UF hoy).
- El caso `PENDIENTE_UF` sea 100% reproducible: basta con NO sembrar la fecha.
- La verificación contra la API real de mindicador.cl queda para una fase aparte (QA-3), fuera
  del alcance de esta suite.

**Sin Keycloak real.** El filtro de seguridad (`SeguridadConfig`) sigue activo tal cual —
`@PreAuthorize` real, conversor real de `realm_access.roles` → `ROLE_*` — pero el `JwtDecoder`
se reemplaza por un `@MockitoBean` sin configurar. El post-processor `jwt()` de
spring-security-test arma la autenticación directamente en el contexto de seguridad sin pasar
por el decoder, así que basta con que el bean exista para que el contexto arranque (el mismo
patrón que ya usaba `EsquemaBaseDatosTest`). Cada request de la suite se firma con
`administrador()`/`operador()`/`sinRolesReconocidos()` (helpers de `SoporteE2E`) para ejercitar
la autorización real, no solo simularla.

**Almacenamiento local, no OCI.** `app.almacenamiento.local.ruta` apunta a un directorio
temporal del sistema de archivos (`AlmacenArchivosLocal`, el adaptador por defecto), creado una
vez por JVM.

**Un Postgres y un Redis reales, compartidos (patrón *singleton container*).**
`SoporteE2E` (la clase base de las nueve clases de flujo) declara los contenedores como campos
`static`, arrancados una única vez en un bloque `static { }` — nunca detenidos explícitamente
(Testcontainers/Ryuk los limpia al terminar la JVM). `@ServiceConnection` (Postgres) y
`@DynamicPropertySource` (Redis, tipo de almacenamiento, `app.ciclo.programado.habilitado=false`
para que el `@Scheduled` real no dispare durante los tests) conectan Spring a esos mismos
contenedores. Flyway aplica el esquema real; Hibernate valida con `ddl-auto=validate` — igual
que en producción.

**Aislamiento entre tests — sin `@Transactional` de rollback.** Se consideró envolver cada test
en una transacción que se revierte al final (el patrón más común en pruebas Spring), pero
`ServicioCicloFacturacion` procesa cada proyecto en su **propia transacción `REQUIRES_NEW`**
(arquitectura-tecnica.md §9, para que un proyecto problemático no arrastre a los demás) — esa
transacción interna haría *commit* igual aunque el test envolvente revirtiera, dejando datos
"fantasma" solo parcialmente limpiados. En su lugar, cada test arranca desde un estado
**conocido y limpio**:
- Un `@BeforeEach` en `SoporteE2E` ejecuta `TRUNCATE ... RESTART IDENTITY CASCADE` sobre todas
  las tablas de negocio (`propuesta_facturacion`, `ejecucion_ciclo`, `importacion_csv`,
  `factura`, `archivo`, `acuerdo_precio`, `proyecto`, `cliente`, `tipo_servicio`, `valor_uf`),
  en su propia transacción `REQUIRES_NEW` explícita (vía `TransactionTemplate`, para que
  corra siempre en una transacción propia y comprometida, sin depender de una envolvente).
  `empresa`/`parametro_sistema` NO se tocan: son la semilla de Flyway `V011` (Helpcom,
  `tasa_iva=0.19`), fija y compartida por toda la suite — `ContextoEmpresa` siempre resuelve
  `empresa_id=1`.
- Ese mismo `@BeforeEach` hace `FLUSHALL` sobre Redis, para que un valor UF cacheado en un test
  no contamine a otro que reutilice la misma fecha (el caché de UF es permanente por diseño,
  arquitectura-tecnica.md §8).

**Todo pasa por HTTP real (MockMvc).** Los helpers de `SoporteE2E` (`crearCliente`,
`crearProyecto`, `crearAcuerdo`, `ejecutarCiclo`, `crearFactura`, `subirPdf`,
`previsualizarCsv`, `confirmarCsv`, `listarPropuestas`, `obtenerInforme`, ...) arman la
petición HTTP y la envían vía `MockMvc` contra el contexto Spring completo — nunca invocan un
`Servicio*` directamente. La única excepción deliberada es `sembrarUf`, que escribe
`valor_uf` directo por el repositorio: no existe (ni debe existir) un endpoint para cargar UF
manualmente: es la siembra determinística del entorno de prueba, no una acción de negocio.

---

## 4. Qué cubre cada flujo

| Clase | Qué prueba |
|---|---|
| `FlujoCaminoFelizE2ETest` | Cliente → proyecto (UF, mensual, sin acuerdo) → ciclo → propuesta con snapshot exacto → factura → PDF (subir/descargar, `AlmacenArchivosLocal`) → informe con la propuesta `FACTURADA` sumando en los totales. |
| `FlujoAcuerdosPrecioE2ETest` | Un caso end-to-end (vía ciclo real) por cada una de las 8 ramas de acuerdo de modelo-de-datos.md §5 (filas 3-10 de la tabla: descuento % en base UF/CLP, descuento monto UF/CLP cruzado con base UF/CLP, precio pactado UF/CLP). |
| `FlujoPendienteUfE2ETest` | Proyecto en UF sin UF sembrada para su fecha → propuesta `PENDIENTE_UF` en 0, ciclo `CON_ADVERTENCIAS` (no aborta), informe la cuenta aparte sin sumarla, y no se puede facturar (`PROPUESTA_NO_FACTURABLE`). |
| `FlujoIdempotenciaCicloE2ETest` | El mismo período ejecutado dos veces no duplica propuestas (índice único parcial real de Postgres). |
| `FlujoPeriodicidadE2ETest` | Mensual no factura el mes de inicio; anual factura cada año en su mes de aniversario; día de facturación inexistente en el mes (31) usa el último día. |
| `FlujoImportacionCsvE2ETest` | Previsualizar → confirmar (mismo archivo) con filas OK sin proyecto, OK con proyecto+acuerdo vigente (mismo `ArmadorPropuesta` que el ciclo), cliente inexistente (`ERROR`) y sin UF sembrada (`ADVERTENCIA` → `PENDIENTE_UF`); estado `PARCIAL`, contador real de `PENDIENTE_UF`. |
| `FlujoAcuerdoTraslapadoE2ETest` | Un segundo acuerdo que se traslapa con el vigente → `409 ACUERDO_TRASLAPADO`. |
| `FlujoAutorizacionE2ETest` | Al menos un endpoint por módulo: sin autenticar → 401; rol no reconocido → 403; endpoints solo-ADMINISTRADOR (crear cliente/tipo de servicio/proyecto/acuerdo, ejecutar ciclo, anular propuesta) rechazan a OPERADOR; endpoints compartidos (crear factura, subir PDF, importar CSV) sí los acepta. |
| `FlujoInformeE2ETest` | Mezcla `PENDIENTE`/`FACTURADA`/`PENDIENTE_UF`/`ANULADA` en un período: los totales solo suman lo facturable, el desglose por estado y por cliente es exacto, y los filtros de cliente/estado/origen/rango de períodos acotan bien. |

**Calidad de las aserciones:** cada flujo verifica **montos exactos** (neto/IVA/total
calculados a mano con la UF sembrada, comparados con `isEqualByComparingTo`, no solo "existe
una propuesta") y el **snapshot persistido completo** (`valorUf`, `fechaValorUf`, `tasaIva`,
`acuerdoTipo`/`acuerdoValor`/`acuerdoMoneda`), no solo los totales derivados.

---

## 5. Bug real encontrado y corregido por esta suite

`FlujoPendienteUfE2ETest` reventó la primera vez que corrió contra Postgres real — señal de que
había un bug de integración genuino, no un problema del test. Diagnóstico:

- `ServicioUfImpl.obtenerValorUf` está `@Transactional`. Corre **dentro de la misma transacción
  `REQUIRES_NEW` por proyecto** que abre `ServicioCicloFacturacion` (propagación por defecto:
  se une a la del llamador).
- Cuando no hay UF disponible, lanza `ValorUfNoDisponibleException` — documentada como un
  resultado **esperado y manejado**: `ArmadorPropuesta` la captura y degrada la propuesta a
  `PENDIENTE_UF` en vez de propagarla (arquitectura-tecnica.md §9).
- Pero el aspecto transaccional de Spring alrededor de `obtenerValorUf` marca la transacción
  **compartida** como `rollback-only` en cuanto la excepción la atraviesa — **antes** de que
  `ArmadorPropuesta` la capture. El código sigue normalmente (parece "manejado"), pero al
  llegar al *commit* de esa transacción `REQUIRES_NEW`, Spring revienta con
  `UnexpectedRollbackException` — no capturado en ningún punto del camino feliz — y
  `ServicioCicloFacturacion` termina contando el proyecto como `ERROR` en vez de generar la
  propuesta `PENDIENTE_UF`.
- **Invisible con mocks o H2** por la misma razón que los cuatro defectos ya documentados en
  `CLAUDE.md` ("Spring Boot 4 — cambios que muerden"): mockear `ServicioUf`/`ArmadorPropuesta`
  evita por completo el aspecto transaccional real de Spring; solo aparece contra una
  transacción/base de datos real ejecutando el commit de verdad.

**Corrección aplicada** (`ServicioUfImpl.java`): `@Transactional(noRollbackFor =
ValorUfNoDisponibleException.class)` en `obtenerValorUf`. Le dice a Spring explícitamente que
esa excepción es un resultado de negocio esperado, no un error — no debe marcar la transacción
como perdida. Sin migraciones ni cambios de contrato; un atributo de una anotación existente.

---

## 6. Próximos pasos (fuera de esta suite)

- **QA-2** — **resuelto**: `docs/guion-qa-manual.md` es el guion de QA manual, construido sobre
  la cobertura de §4 de este documento — traduce los mismos 9 flujos a pasos clic a clic en
  español llano, con datos de ejemplo y montos calculados a mano, para que cualquier persona de
  Helpcom (sin necesidad de saber programar) pueda ejecutarlo como regresión manual antes de un
  release.
- **QA-3** — **ambas porciones, resueltas**: esta suite (§3, "Sin Keycloak real"/"Nunca golpea
  la red") simula la autenticación y siembra la UF directo en `valor_uf` — nunca prueba un
  Keycloak ni un mindicador.cl reales. Ambas porciones quedaron verificadas de punta a punta:
  - **Keycloak real** en `docs/despliegue.md` §7.2 (D3): realm `helpcom` importado como código,
    login Authorization Code + PKCE completo para `admin.prueba` (`ADMINISTRADOR`) y
    `operador.prueba` (`OPERADOR`) contra el Keycloak real del stack, JWT reales con
    `realm_access.roles` correcto, autorización por rol confirmada contra el backend real
    (`200`/`403` en `POST /api/v1/ciclos/ejecutar` según el rol) y renovación silenciosa del
    access token sin caída de sesión.
  - **mindicador.cl real (2026-08-17):** ejecutado el ciclo real (`POST /ciclos/ejecutar`) para
    un período sin UF cacheada (ni en Redis ni en `valor_uf`), forzando a `ServicioUfImpl` a
    llegar hasta `FuenteUfMindicador.consultarUf` (RestClient real, arquitectura-tecnica.md §8).
    La API respondió `200` con un valor real; verificado además por fuera del backend, con
    `curl` directo a `https://www.mindicador.cl/api/uf/{fecha}`, que el valor coincide
    exactamente. Quedó persistido en `valor_uf` con `fuente = 'mindicador.cl'` — la única forma
    de llegar a esa columna con ese valor es por esta vía real (`ServicioUfImpl.persistir`), no
    hay ningún camino de siembra que la escriba con ese `fuente`. La propuesta resultante quedó
    `PENDIENTE` con monto calculado a partir del valor real. Sin hallazgos — la integración
    funciona igual que simulada.
