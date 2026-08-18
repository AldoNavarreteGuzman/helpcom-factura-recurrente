# Estándares de Código

**Proyecto:** Sistema de Facturación Recurrente de Proyectos
**Cliente inicial:** Helpcom Ltda.
**Etapa:** 1 — Arquitectura
**Estado del documento:** Borrador para revisión
**Última actualización:** 2026-08-05

---

## 1. Propósito

Este documento fija las convenciones de código para el backend y el frontend, de modo que todo el equipo (y Claude Code) produzca código consistente, mantenible y alineado con las decisiones de *Arquitectura Técnica* y *Modelo de Datos*.

---

## 2. Principios generales

1. **Dominio en español.** Clases, métodos, variables, paquetes de dominio, tablas, columnas y comentarios se escriben en español. Las palabras clave del lenguaje, anotaciones de framework y APIs de terceros permanecen en su idioma original.
2. **Consistencia sobre preferencia personal.** Ante la duda, se sigue lo que ya existe en el código.
3. **Claridad sobre astucia.** Se prefiere código explícito y legible antes que soluciones ingeniosas difíciles de mantener.
4. **El dinero y las fechas se tratan con reglas estrictas** (§3.5 y §3.6). Es la parte más sensible del sistema.
5. **Nada de secretos en el repositorio.** Credenciales y claves van por variables de entorno.

---

## 3. Backend — Java 21 + Spring Boot 4

### 3.1 Estructura de paquetes
Paquete raíz `cl.helpcom.facturacion`. Organización **por módulo de negocio** y, dentro de cada uno, por capa:

```
cl.helpcom.facturacion
├── <modulo>/
│   ├── controlador/      // API REST (recibe/retorna DTOs)
│   ├── servicio/         // lógica de negocio, @Transactional
│   ├── repositorio/      // Spring Data JPA
│   ├── dominio/          // entidades JPA y enumerados
│   └── dto/              // records de entrada y salida
└── comun/                // configuración, manejo de errores, utilidades transversales
```
Módulos: `empresa`, `seguridad`, `clientes`, `proyectos`, `uf`, `facturacion`, `importacion`, `informes`.

### 3.2 Convenciones de nombres
- **Clases:** `PascalCase`. Sufijos por capa: `Controlador`, `Servicio`, `Repositorio`, `Dto`. Ejemplos: `ClienteControlador`, `ProyectoServicio`, `PropuestaFacturacionRepositorio`.
- **Entidades:** nombre del concepto sin sufijo (`Cliente`, `Proyecto`, `AcuerdoPrecio`, `PropuestaFacturacion`).
- **Métodos y variables:** `camelCase`, en español: `obtenerCliente`, `calcularCicloMensual`, `netoClp`.
- **Constantes:** `UPPER_SNAKE_CASE`: `TASA_IVA_POR_DEFECTO`.
- **Paquetes:** minúsculas, sin guiones.
- **Interfaces:** sin prefijo `I`. La implementación, si hay una sola, puede llamarse `<Nombre>Impl` solo cuando aporte claridad.

### 3.3 Entidades JPA y mapeo
- Una entidad por tabla, anotada con `@Entity` y `@Table(name = "...")` en `snake_case`.
- Columnas mapeadas explícitamente con `@Column(name = "...")` cuando el nombre difiera del atributo.
- Clave primaria: `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`.
- **No** exponer entidades en los controladores; siempre convertir a DTO.
- Inyección por **constructor**; nunca `@Autowired` en campos.
- Evitar Lombok `@Data` en entidades (rompe `equals`/`hashCode` y la mutabilidad controlada). Se permiten `@Getter`/`@Setter` puntuales.

```java
@Entity
@Table(name = "proyecto")
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "precio_base_neto", nullable = false)
    private BigDecimal precioBaseNeto;

    @Enumerated(EnumType.STRING)
    @Column(name = "moneda_precio", nullable = false)
    private Moneda monedaPrecio;

    // ...
}
```

### 3.4 Enumerados
Los enumerados de negocio se modelan como `enum` de Java **con nombres idénticos a los valores en base de datos** y se persisten con `@Enumerated(EnumType.STRING)`:

```java
public enum Moneda { UF, CLP }
public enum Periodicidad { MENSUAL, ANUAL }
public enum TipoAcuerdo { DESCUENTO_PORCENTAJE, DESCUENTO_MONTO, PRECIO_PACTADO }
public enum OrigenPropuesta { CICLO, CSV }
public enum EstadoPropuesta { PENDIENTE, PENDIENTE_UF, FACTURADA, ANULADA }
```

### 3.5 Manejo de dinero (crítico)
- **Siempre `BigDecimal`.** Prohibido `double`/`float` para montos.
- **Escalas:** UF con 4 decimales; CLP con **0 decimales** (entero).
- **Redondeo:** `RoundingMode.HALF_UP` en toda conversión/aplicación de descuento.
- El monto en CLP se redondea a entero al momento de convertir desde UF, según la tabla de cálculo del *Modelo de Datos* §5.
- Comparaciones con `compareTo`, nunca con `equals` (por diferencias de escala).
- Un único componente utilitario centraliza el cálculo (p. ej. `CalculadoraFacturacion`) para no duplicar reglas.

```java
BigDecimal netoClp = precioUf
    .multiply(valorUf)
    .setScale(0, RoundingMode.HALF_UP);
```

### 3.6 Manejo de fechas y horas
- Solo `java.time`. Prohibido `java.util.Date`/`Calendar`.
- `DATE` → `LocalDate`. `TIMESTAMPTZ` → `OffsetDateTime` (o `Instant`).
- **Zona horaria de negocio: `America/Santiago`.** El proceso del día 1 y la resolución del día de facturación se calculan en esa zona. Las marcas de tiempo se almacenan en UTC y se convierten para mostrar.
- Regla del día en meses cortos (día 31 en febrero → último día del mes) se implementa con `YearMonth`/`LocalDate.lengthOfMonth()`.

### 3.7 DTOs y validación
- DTOs como `record` de Java, separados para entrada (`...SolicitudDto`) y salida (`...RespuestaDto`).
- Validación con Jakarta Bean Validation en los DTOs de entrada (`@NotNull`, `@NotBlank`, `@Positive`, `@Size`).
- La conversión entidad↔DTO se hace en la capa de servicio (o un `mapper` explícito), no en el controlador.

```java
public record CrearProyectoSolicitudDto(
    @NotNull Long clienteId,
    @NotBlank String nombre,
    @NotNull @Positive BigDecimal precioBaseNeto,
    @NotNull Moneda monedaPrecio,
    @NotNull Periodicidad periodicidad,
    @Min(1) @Max(31) int diaFacturacion,
    @NotNull LocalDate fechaInicio
) {}
```

### 3.8 API REST
- Prefijo y versión: `/api/v1/...`.
- Recursos en **sustantivo plural, en español**: `/clientes`, `/proyectos`, `/propuestas`, `/facturas`, `/importaciones`, `/informes`.
- Subrecursos: `/proyectos/{id}/acuerdos`.
- Verbos HTTP estándar (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`) y códigos correctos (`200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `422`).
- Listados paginados con `page`/`size` (Spring `Pageable`) y respuesta que incluya el total.
- Fechas en JSON en formato ISO-8601 (`AAAA-MM-DD`). Montos CLP como número entero; UF como número con decimales.
- **Orden por defecto:** todo listado paginado que representa eventos o registros temporales
  (facturas, propuestas, importaciones, ejecuciones de ciclo, etc.) ordena por su
  fecha/timestamp de negocio —o, en su defecto, período— **descendente** por defecto (más
  reciente primero), sin que el cliente tenga que pedir `sort` explícito. El backend lo aplica
  siempre, nunca el frontend por su cuenta:
  - En repositorios con método derivado simple (`findByEmpresaId(...)`), el orden va en el
    nombre del método: `findByEmpresaIdOrderByEjecutadoEnDesc(...)`.
  - En listados con `Specification` (`findAll(Specification, Pageable)`), el orden por
    defecto se inyecta en el `Pageable` solo si el que llega no trae ya un orden —
    `OrdenPorDefecto.aplicar(pageable, ordenPorDefecto)` (`comun/util/OrdenPorDefecto.java`) —
    para no pisar nunca un `sort` explícito pedido por el cliente. El frontend puede seguir
    ofreciendo override de orden donde ya lo hace.

### 3.9 Manejo de errores
- Un `@RestControllerAdvice` global traduce excepciones a respuestas `application/problem+json` (RFC 7807).
- Excepciones de dominio propias y semánticas: `RecursoNoEncontradoException` (→ 404), `ReglaNegocioException` (→ 409/422), `SolicitudInvalidaException` (→ 400).
- Cuerpo de error consistente:

```json
{
  "type": "https://.../errores/regla-negocio",
  "title": "Regla de negocio no cumplida",
  "status": 409,
  "detail": "El proyecto ya tiene una propuesta para el período 2026-01.",
  "codigo": "PROPUESTA_DUPLICADA",
  "errores": []
}
```
- Nunca exponer *stack traces* ni detalles internos al cliente.

### 3.10 Transacciones
- `@Transactional` en la capa de **servicio**, no en controladores ni repositorios.
- Lectura con `@Transactional(readOnly = true)` cuando corresponda.
- El proceso del día 1 usa un **lock distribuido** (Redis) y se apoya en las restricciones de unicidad de la base para garantizar idempotencia.

### 3.11 Seguridad
- Backend como **OAuth2 Resource Server**; valida el JWT emitido por Keycloak.
- Autorización por rol con `@PreAuthorize("hasRole('ADMINISTRADOR')")` a nivel de método.
- Los roles se leen del token; la tabla `usuario` es solo espejo local.
- `empresa_id` se resuelve mediante el componente `ContextoEmpresa` (hoy fijo en Helpcom); ninguna consulta de negocio lo omite.

### 3.12 Logging
- `SLF4J` (`private static final Logger log = LoggerFactory.getLogger(...)`).
- Sin datos sensibles en los logs (tokens, secretos).
- Niveles: `ERROR` (fallo que requiere atención), `WARN` (situación recuperable, p. ej. UF no disponible), `INFO` (hitos: inicio/fin de ciclo, importación), `DEBUG` (detalle de desarrollo).
- Cada ejecución del ciclo deja además su traza en `ejecucion_ciclo`.

### 3.13 Configuración y secretos
- `application.yml` con perfiles `local`, `dev`, `prod`.
- Secretos (BD, Keycloak, API CMF si se usa, Object Storage) **solo** por variables de entorno; nunca versionados.
- Valores parametrizables de negocio (p. ej. `tasa_iva`) viven en `parametro_sistema`, no en configuración de aplicación.

### 3.14 Pruebas
- `JUnit 5` + `AssertJ`.
- Nombres de prueba descriptivos en español: `deberiaCalcularNetoConDescuentoPorcentual()`.
- Estructura *given / when / then*.
- **Cobertura obligatoria** de la `CalculadoraFacturacion` (todas las combinaciones de la tabla §5 del Modelo) y de la resolución de períodos del ciclo (mensual/anual, primer cobro, meses cortos).
- Pruebas de integración con **Testcontainers (PostgreSQL 16)** para repositorios y migraciones.

### 3.15 Estilo
- Inyección por constructor; clases y campos `final` cuando sea posible.
- Métodos cortos y con una responsabilidad.
- `Optional` para retornos que pueden no existir; no para parámetros ni campos.

---

## 4. Migraciones Flyway

- Ubicación: `src/main/resources/db/migration`.
- Nomenclatura: `V###__descripcion_en_espanol.sql` (p. ej. `V004__crear_proyecto.sql`). Correlativas, sin saltos. **La primera es `V001`.**
- **Una migración = un cambio lógico.** Un cambio ya fusionado en la rama principal es **inmutable**: nunca se edita, se crea una nueva.
- El SQL respeta las convenciones de nombres del *Modelo de Datos* (español, `snake_case`).
- Solo DDL y datos semilla controlados; los datos de negocio no se cargan por migración.
- Migraciones repetibles (`R__...`) solo para objetos recreables (vistas, funciones), si se necesitan.

---

## 5. Frontend — Next.js 16 (React 19) + TypeScript + Tailwind

### 5.1 Estructura (App Router)
```
app/            // rutas y páginas (Server Components por defecto)
components/     // componentes reutilizables
lib/            // cliente API, auth, utilidades, formateadores
types/          // tipos de dominio compartidos
```

### 5.2 Nombres y TypeScript
- **`strict: true`** en `tsconfig`. Prohibido `any` salvo justificación puntual.
- Archivos de componentes en `PascalCase.tsx`; hooks en `useAlgo.ts`.
- Nombres de dominio en español (`obtenerClientes`, `PropuestaFacturacion`); las APIs de React/Next permanecen en inglés.

### 5.3 Tipos de dominio
Los tipos reflejan los DTOs del backend, en español:
```ts
export type Moneda = "UF" | "CLP";
export type EstadoPropuesta = "PENDIENTE" | "PENDIENTE_UF" | "FACTURADA" | "ANULADA";

export interface PropuestaFacturacion {
  id: number;
  clienteId: number;
  periodoAnio: number;
  periodoMes: number;
  netoClp: number;
  ivaClp: number;
  totalClp: number;
  estado: EstadoPropuesta;
}
```

### 5.4 Cliente API y autenticación
- Cliente HTTP centralizado en `lib/`, con una base compartida (`clienteApi.ts`: normaliza errores `problem+json`) y dos entradas — `clienteApiServidor.ts` (Server Components/Route Handlers, token vía `auth()`) y `clienteApiCliente.ts` (Client Components, token vía `useSession()`/`getSession()`) — porque cada contexto resuelve el token OIDC de forma distinta. Detalle en `frontend.md` §2.2.
- Autenticación OIDC contra Keycloak (flujo *Authorization Code + PKCE*) con Auth.js v5 (`frontend.md` §2.1).
- No duplicar llamadas `fetch` sueltas por la app.

### 5.5 Formato de dinero y fechas
- Locale de presentación: **`es-CL`**.
- CLP con `Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 })`.
- UF con separador de miles y coma decimal (`es-CL`).
- Fechas mostradas en `es-CL` (`DD-MM-AAAA`); se envían al backend en ISO-8601.
- Los formateadores viven en `lib/` y se reutilizan; no se formatea *ad hoc* en cada componente.

### 5.6 Estilos (Tailwind)
- Utilidades de Tailwind; sin CSS en línea salvo casos excepcionales.
- Clases largas y repetidas se extraen a componentes, no a `@apply` disperso.
- Diseño responsivo *mobile-first*.

### 5.7 Componentes servidor vs cliente
- **Server Components por defecto**; `"use client"` solo cuando haya interactividad o estado del navegador.
- La obtención de datos de solo lectura se hace preferentemente en el servidor.

---

## 6. Git, commits y ramas

- **Commits:** formato *Conventional Commits* con descripción en español. Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`.
  - Ejemplo: `feat(facturacion): calcular ciclo mensual con acuerdos de precio`
- **Ramas:** `feature/<breve-descripcion>`, `fix/<breve-descripcion>`.
- Un *pull request* por cambio lógico, con descripción de qué y por qué.

---

## 7. Herramientas de formato y análisis

- **Backend:** formateo automático (p. ej. Spotless) + análisis estático (Checkstyle/SonarLint). El formato no se discute en revisión: lo aplica la herramienta.
- **Frontend:** ESLint + Prettier. Configuración versionada en el repositorio.
- El formato se valida en el pipeline antes de fusionar.

---

## 8. Convenciones para trabajar con Claude Code

Todo prompt dirigido a Claude Code debe:
1. Indicar que **lea `CLAUDE.md` primero** (y los documentos de `docs/` relevantes).
2. Indicar explícitamente el **número de la migración siguiente** cuando el cambio toque la base de datos.
3. Indicar que, al terminar, **actualice el documento de `docs/` correspondiente** (arquitectura, modelo de datos o estándares) si el cambio lo afecta.
4. Respetar las convenciones de este documento: dominio en español, dinero en `BigDecimal`, fechas en `java.time`, migraciones Flyway correlativas.

---

## 9. Definición de "terminado" (checklist)

Una tarea se considera terminada cuando:
- [ ] El código sigue las convenciones de este documento (nombres, capas, dominio en español).
- [ ] El dinero usa `BigDecimal` con la escala y redondeo correctos; las fechas usan `java.time` en zona `America/Santiago` donde corresponde.
- [ ] Hay migración Flyway correlativa si cambió el esquema.
- [ ] Hay pruebas para la lógica nueva (obligatorio para cálculo y ciclo).
- [ ] El formateador/linter pasa sin errores.
- [ ] El documento de `docs/` correspondiente quedó actualizado.

---

*Fin del documento — Estándares de Código (Etapa 1).*
