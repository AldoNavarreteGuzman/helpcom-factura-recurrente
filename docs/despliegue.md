# Despliegue

**Objetivo del despliegue:** servidor **local**, físicamente en la oficina de Helpcom, con
**acceso remoto**, todo orquestado con **Docker Compose**. No es una nube administrada — es una
máquina que Helpcom controla directamente, con los contenedores como unidad de despliegue y
operación.

Este documento se construye por etapas (D1, D2, ...); cada una agrega una pieza sin rehacer las
anteriores. **Este documento cubre D1, D2 y D3** — el sistema completo (Postgres, Redis,
Keycloak con su realm/roles/clientes/usuarios ya configurados, backend y frontend) arranca de
punta a punta en Docker Compose con el login OIDC y la autorización por rol funcionando
end-to-end.

| Etapa | Qué agrega | Estado |
|---|---|---|
| D1 | Base de datos e identidad: PostgreSQL, Redis, Keycloak — en Docker Compose, sanos y persistentes. | **Hecho** |
| D2 | Backend y frontend contenerizados, integrados a la base de D1; migraciones Flyway aplicadas contra el Postgres real. | **Hecho** |
| D3 | Realm de Keycloak como código (roles `ADMINISTRADOR`/`OPERADOR`, clientes OIDC, usuarios de prueba); login y autorización por rol verificados de punta a punta contra Keycloak real. | **Hecho — este documento** |
| D4 | Reverse proxy con HTTPS — único puerto expuesto al exterior; dominio y certificados reales. | Pendiente |
| D5 | Copias de respaldo, monitoreo/observabilidad de infraestructura, runbook operativo; **reemplazar el RUT placeholder de Helpcom** (`XX.XXX.XXX-X`, sembrado por `V011`) por el RUT real; **crear los usuarios reales de Helpcom** (reemplazando `admin.prueba`/`operador.prueba`). | Pendiente |

---

## 1. Qué hace este stack (D1 + D2 + D3)

Cinco servicios, en `deploy/docker-compose.yml`:

| Servicio | Imagen | Rol |
|---|---|---|
| **postgres** | `postgres:16` | Base de datos de la aplicación (`facturacion_recurrente`) **y** base de datos de Keycloak (`keycloak`, separada, mismo motor) — arquitectura-tecnica.md §12. |
| **redis** | `redis:7` | Caché del valor UF y lock del ciclo mensual (arquitectura-tecnica.md §8, §13). **Degradable a propósito**: si Redis no está disponible, el backend sigue funcionando (pierde el caché y el lock distribuido, no dato de negocio) — ver `CLAUDE.md` y `LockCiclo`/`CacheUf` en el backend. |
| **keycloak** | `quay.io/keycloak/keycloak:26.0` | Identidad OAuth2/OIDC (arquitectura-tecnica.md §7). Corre contra su base en `postgres`. Desde D3, importa automáticamente `deploy/keycloak/realm-helpcom.json` al arrancar sobre una base vacía: realm `helpcom`, roles, clientes OIDC y usuarios de prueba ya configurados — nada de clics manuales en la consola (§5.7). |
| **backend** | construida desde `backend/Dockerfile` | La API (Spring Boot). Aplica las migraciones Flyway contra `postgres` al arrancar, sirve `/api/v1/**`, valida los JWT de Keycloak y guarda los PDF de las facturas en un volumen local. |
| **frontend** | construida desde `frontend/Dockerfile` | La aplicación web (Next.js), servida en su forma `standalone`. Login OIDC (Authorization Code + PKCE) contra Keycloak vía Auth.js. |

**Explícitamente fuera de D1+D2+D3** (por diseño, no un olvido):
- El **dominio real y el certificado HTTPS** son de D4. Hasta entonces, `backend`, `frontend` y
  `keycloak` publican un puerto cada uno TEMPORALMENTE para poder verificar el sistema desde un
  navegador (§4) — D4 los reemplaza por el único puerto del reverse proxy, y con eso desaparece
  la necesidad de los puentes `extra_hosts` de §5.6 (ahí se explica por qué existen).
- Los **usuarios de prueba** (`admin.prueba`/`operador.prueba`, sembrados por
  `deploy/keycloak/realm-helpcom.json`) son exactamente eso — de prueba, con contraseñas
  temporales conocidas y versionadas en el repo. Los usuarios reales de Helpcom se crean en D5,
  a mano en la consola de Keycloak (§5.7 tiene el procedimiento) — nunca en el archivo de realm
  versionado.
- El **RUT placeholder de Helpcom** (`XX.XXX.XXX-X`, sembrado por la migración `V011`) sigue sin
  tocar a propósito — se reemplaza por el RUT real recién en D5, junto con el resto de las
  tareas de puesta en producción real.

---

## 2. Requisitos previos

- Docker y Docker Compose (plugin `docker compose`, no el viejo `docker-compose` standalone)
  instalados en el servidor. Verifica con `docker compose version`.
- **No hace falta Maven, JDK, Node ni npm instalados en el servidor** — `docker compose build`
  compila el backend y el frontend DENTRO de contenedores de build descartables
  (`backend/Dockerfile`, `frontend/Dockerfile`, ambos multi-stage, §5.4/§5.5); la imagen final
  no lleva ni Maven ni las `devDependencies` de Node.
- Que los puertos que este stack publica a la máquina host (8080 backend, 3000 frontend, 8081
  Keycloak — todos temporales hasta D4, ver §4) **no** estén siendo usados por otro proceso de
  la misma máquina. Si lo están, la forma más simple de resolverlo es cambiar el lado
  IZQUIERDO del mapeo (`"<puerto-host>:<puerto-contenedor>"`) en `docker-compose.yml` para ese
  servicio — el puerto interno no necesita cambiar.

---

## 3. Cómo levantar el sistema completo

```bash
cd deploy
cp .env.example .env
```

Edita `.env` y reemplaza **todos** los `cambiar-esta-clave-...`/`cambiar-este-secreto-...` por
valores reales. Para generar una clave robusta:

```bash
openssl rand -base64 32
```

(mismo mecanismo que ya usa `AUTH_SECRET` del frontend, ver `docs/frontend.md` §1.3.)

Revisa también `NEXT_PUBLIC_API_BASE_URL`, `AUTH_URL` y `KEYCLOAK_PUBLIC_URL`: por defecto
asumen que vas a probar el sistema desde el propio servidor (`localhost`/`keycloak.localhost`,
ver §5.6 sobre por qué `KEYCLOAK_PUBLIC_URL` usa ese host en vez de `localhost` a secas). Si vas
a acceder desde otra máquina de la oficina, cambia el host por la IP LAN del servidor en las
**tres** — incluida `KEYCLOAK_PUBLIC_URL`: `keycloak.localhost` es loopback **solo en la propia
máquina** que lo resuelve (por eso funciona igual de bien dentro del servidor y en el navegador
de quien prueba desde ahí mismo), pero un navegador en OTRA máquina de la LAN resolvería
`keycloak.localhost` contra SU PROPIO loopback, no el del servidor — completamente roto. Con una
IP LAN real en `KEYCLOAK_PUBLIC_URL`, el puente `extra_hosts` de §5.6 deja de hacer falta para el
navegador (una IP LAN ya es alcanzable tal cual); si el propio contenedor del frontend/backend
también logra alcanzarse a sí mismo por esa IP LAN publicada (asunto no verificado en este
despliegue, de un solo servidor sin otras máquinas en la LAN a mano) no debería hacer falta
tocar nada más — si no, el mismo mecanismo de `extra_hosts` de §5.6 sirve de referencia para
apuntar esa IP LAN al gateway de Docker. Ver §5.5 sobre por qué `NEXT_PUBLIC_API_BASE_URL`
necesita además **reconstruir la imagen** si la cambias después.

Luego:

```bash
docker compose up -d --build
```

`--build` construye las imágenes de `backend` y `frontend` si no existen todavía (o si cambió su
Dockerfile/código — Compose no reconstruye solo porque sí). Para reconstruir explícitamente más
adelante: `docker compose build backend frontend`.

Esto, en orden (por las condiciones de `depends_on`, §6):
1. Crea la red interna `facturacion_interna` (todos los servicios se ven entre sí por su nombre
   de servicio — `postgres`, `redis`, `keycloak`, `backend`, `frontend` — no por IP).
2. Crea los volúmenes con nombre si no existen (`facturacion_postgres_datos`,
   `facturacion_redis_datos`, `facturacion_keycloak_datos`, `facturacion_backend_almacenamiento`
   — este último para los PDF de las facturas, §5.4). Sobreviven a `docker compose down` y a
   recrear los contenedores; solo desaparecen con `docker compose down -v` (**destructivo** — no
   lo uses en el servidor real sin saber que quieres borrar los datos).
3. Levanta `postgres`. La **primera vez** que el volumen de datos está vacío, además de crear la
   base `facturacion_recurrente` (vía las variables estándar `POSTGRES_*` de la imagen oficial),
   corre `deploy/postgres-init/crear-base-keycloak.sh`, que crea la base `keycloak` y su usuario
   separado — ver §5.1. Ese script **no vuelve a correr** en reinicios posteriores.
4. Levanta `redis`, con contraseña (`--requirepass`).
5. Levanta `keycloak` **una vez que `postgres` está `healthy`** — se conecta a la base
   `keycloak`, corre sus propias migraciones internas (Liquibase, automático) y, **solo la
   primera vez que esa base está vacía**, importa `deploy/keycloak/realm-helpcom.json`: realm
   `helpcom`, roles `ADMINISTRADOR`/`OPERADOR`, los dos clientes OIDC y los usuarios de prueba
   quedan listos sin ningún paso manual (§5.7).
6. Levanta `backend` **una vez que `postgres` y `redis` están `healthy`** — aplica las 11
   migraciones Flyway contra el Postgres real (nunca contra H2 ni Testcontainers, a diferencia
   de las pruebas del backend) y arranca con `ddl-auto=validate`.
7. Levanta `frontend` **una vez que `backend` está `healthy`**.

---

## 4. Puertos: qué queda publicado, y por qué (todo temporal hasta D4)

`postgres` y `redis` **no** publican puertos a la máquina host — nunca los necesita nadie de
afuera de la red interna `facturacion_interna`, así que no hay razón para exponerlos por
defecto.

`backend`, `frontend` y `keycloak` **sí** publican un puerto cada uno, y es una decisión
consciente de esta etapa, no una relajación de la regla de D1 ("nada expuesto salvo que se
necesite"): sin reverse proxy todavía (D4), esos tres SÍ se necesitan alcanzables desde un
navegador para que el sistema sea usable/verificable en absoluto —

| Servicio | Puerto host | Para qué |
|---|---|---|
| `frontend` | `3000` | Es la aplicación en sí — sin esto, nadie puede abrir el sistema. |
| `backend` | `8080` | El bundle de JavaScript del frontend llama a la API **directo desde el navegador del usuario** (`NEXT_PUBLIC_API_BASE_URL`, horneado en el build — §5.5): el backend tiene que ser alcanzable desde ese mismo navegador, no solo desde `frontend` dentro de la red interna. |
| `keycloak` | `8081` | El login OIDC redirige al navegador directo a Keycloak (§5.6) — mismo motivo que el backend. Puerto 8081 a propósito: coincide con el Keycloak de desarrollo local de un desarrollador individual (`docs/frontend.md` §1.1). |

**Qué cambia en D4:** el reverse proxy pasa a ser el único puerto expuesto al exterior (probablemente
443/HTTPS); `frontend`, `backend` y `keycloak` dejan de publicar puerto propio (el proxy les
llega por la red interna, igual que hoy le llega a `postgres`/`redis`), y `KEYCLOAK_PUBLIC_URL`/
`NEXT_PUBLIC_API_BASE_URL` pasan a apuntar al dominio público real en vez de `localhost`/la IP
del servidor.

**Acceso puntual a `postgres`/`redis`** (para administrar a mano — `psql`, `redis-cli` desde tu
propio cliente): usa el archivo de ejemplo `deploy/docker-compose.override.example.yml`:

```bash
cd deploy
cp docker-compose.override.example.yml docker-compose.override.yml
docker compose up -d
```

Docker Compose aplica `docker-compose.override.yml` automáticamente junto al archivo base — no
hace falta pasarlo con `-f`. Publica en loopback (`127.0.0.1:<puerto>:<puerto>`), así que ni
siquiera queda visible en la red local, solo desde la propia máquina del servidor. Cuando
termines, borra `docker-compose.override.yml` (no se versiona, ver `deploy/.gitignore`).

---

## 5. Detalle por servicio

### 5.1 PostgreSQL

- **Un solo contenedor Postgres, dos bases de datos separadas** (`facturacion_recurrente` para
  la app, `keycloak` para Keycloak), cada una con su propio usuario dueño — simplicidad
  operacional para un servidor único (un solo motor que respaldar/monitorear/actualizar), sin
  mezclar el esquema de la app con el de Keycloak. Ver `deploy/postgres-init/crear-base-keycloak.sh`.
- **Volumen persistente con nombre:** `facturacion_postgres_datos`, montado en
  `/var/lib/postgresql/data`. Sobrevive a recrear el contenedor.
- **`btree_gist`:** la migración `V001` del backend hace `CREATE EXTENSION IF NOT EXISTS
  btree_gist` (la necesita la restricción de exclusión de `acuerdo_precio`, `V005`). La imagen
  oficial `postgres:16` la trae disponible sin configuración adicional — verificado en este
  despliegue (§7).
- **Healthcheck:** `pg_isready -U <usuario> -d <base>` — los servicios que dependen de Postgres
  (Keycloak, y en D2 el backend) esperan a que este healthcheck pase antes de arrancar.
- **Puerto:** no publicado por defecto (§4).

### 5.2 Redis

- Rol: caché del valor UF (permanente por fecha, arquitectura-tecnica.md §8) y lock distribuido
  del ciclo mensual (arquitectura-tecnica.md §13) — **optimización, no dato crítico**: el
  backend sigue funcionando si Redis no responde (`ServicioUfImpl` cae a Postgres/la fuente
  externa; `LockCiclo` registra un `WARN` y continúa sin lock, la idempotencia real la garantiza
  la base de datos — ver `CLAUDE.md`).
- **Con contraseña** (`requirepass`, variable `REDIS_PASSWORD`) porque el servidor va a tener
  acceso remoto — aunque el dato en sí sea de bajo riesgo, no tiene sentido dejarlo abierto.
- **Volumen persistente con nombre:** `facturacion_redis_datos`, montado en `/data`. Redis
  guarda snapshots (RDB) periódicos ahí por defecto — es "mejor esfuerzo", coherente con que el
  dato sea descartable; no se activó `appendonly` (más overhead, innecesario para un caché).
- **Healthcheck:** `redis-cli -a <clave> ping`, esperando `PONG`.
- **Puerto:** no publicado por defecto (§4).

### 5.3 Keycloak

- **Versión fijada explícitamente:** `quay.io/keycloak/keycloak:26.0` (no `latest` — para que
  una actualización de Keycloak sea una decisión explícita, no un cambio silencioso en el
  próximo `docker compose pull`).
- **Base de datos:** Postgres (base `keycloak`, usuario separado, mismo contenedor que la app —
  §5.1), nunca la H2 embebida (`KC_DB=postgres` + `KC_DB_URL_HOST/PORT/DATABASE/USERNAME/PASSWORD`).
- **Modo de arranque:** `start` (modo producción de Keycloak), no `start-dev` — `start-dev` usa
  H2 en memoria por defecto y relaja validaciones pensadas justamente para no perder datos; no
  es apto para un servidor que debe persistir usuarios y sesiones reales. (Distinto del
  Keycloak de un desarrollador en su propio computador, `docs/frontend.md` §1.1, que sí usa
  `start-dev` porque ahí no importa perder los datos entre reinicios.)
- **Administrador inicial:** `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` (las
  variables actuales de Keycloak 26 para el usuario del realm `master`; el par histórico
  `KEYCLOAK_ADMIN`/`KEYCLOAK_ADMIN_PASSWORD` de versiones anteriores ya no aplica a esta
  versión). Solo se usa la primera vez que arranca contra una base vacía.
- **Preparado para el reverse proxy de D4** (arquitectura-tecnica.md §7 y esta tarea):
  - `KC_HOSTNAME`: **vacío, a propósito**, hasta D4 (`KEYCLOAK_HOSTNAME` sin valor en
    `.env.example`) — no es un simple "provisional sin rellenar", es una decisión activa
    explicada en detalle en §5.6 (bug real encontrado en D3: un `KC_HOSTNAME` fijo hace que
    Keycloak se autoidentifique con ESE hostname incluso en las llamadas que le hacen los
    propios `backend`/`frontend` por la red interna, lo que rompe la validación de emisor de
    Auth.js). D4 sí le pone un valor fijo (el dominio público real), porque ahí ya no existe la
    distinción navegador-vs-red-interna que motiva dejarlo vacío hoy.
  - `KC_HOSTNAME_STRICT=false`: deja que Keycloak acepte y se autoidentifique según CUALQUIER
    dirección con la que lo contacten (necesario mientras `KC_HOSTNAME` está vacío — ver punto
    anterior y §5.6).
  - `KC_HTTP_ENABLED=true`: Keycloak sirve HTTP plano puertas adentro — el proxy de D4 es quien
    termina TLS, no Keycloak.
  - `KC_PROXY_HEADERS=xforwarded`: para que Keycloak confíe en los encabezados
    `X-Forwarded-*` que pondrá el proxy de D4 (protocolo/host originales), en vez de ver
    siempre HTTP plano viniendo del proxy y construir URLs mal.
  - **Qué falta para D4:** el dominio público real en `KC_HOSTNAME`, volver a
    `KC_HOSTNAME_STRICT=true`, retirar los puentes `extra_hosts` de `backend`/`frontend` (§5.6,
    ya no hacen falta con todo detrás del mismo proxy) y la configuración del proxy en sí
    (certificado, reglas de reenvío).
- **Volumen persistente con nombre:** `facturacion_keycloak_datos`, montado en
  `/opt/keycloak/data` (temas/proveedores personalizados si se agregan a futuro; los datos de
  negocio de Keycloak — realms, usuarios, sesiones — viven en Postgres, no acá).
- **Healthcheck:** consulta `GET /health/ready` en el puerto de administración interno (9000,
  habilitado con `KC_HEALTH_ENABLED=true`) y confirma `"status": "UP"`.
- **Puerto:** `8081` publicado por defecto desde D2, temporalmente hasta D4 (§4) — lo necesita
  el navegador para completar el login OIDC. La consola de administración (`/admin`) queda
  igual de alcanzable ahí mientras tanto.
- **Realm, roles, clientes OIDC y usuarios:** desde D3, `--import-realm` los siembra
  automáticamente la primera vez que arranca sobre una base vacía, a partir de
  `deploy/keycloak/realm-helpcom.json` — realm como código, nada de clics manuales. Detalle
  completo en §5.7.

### 5.4 Backend

- **Imagen:** construida desde `backend/Dockerfile`, multi-stage — etapa 1 compila el jar con
  Maven + JDK 21 completo (`maven:3.9-eclipse-temurin-21`); etapa 2 (la que realmente se
  publica/ejecuta) parte de `eclipse-temurin:21-jre-alpine`, solo el JRE, sin Maven ni el jar
  fuente. Resultado: **385 MB** (verificado en este despliegue, §7) — mucho menor que si la
  imagen final arrastrara la etapa de build.
- **Cache de capas:** copia primero `pom.xml` y corre `mvn dependency:go-offline` **antes** de
  copiar `src/` — mientras el `pom.xml` no cambie, un cambio de código fuente reutiliza la capa
  con el árbol de dependencias ya descargado, en vez de volver a bajar medio Maven Central en
  cada build.
- **Usuario no-root:** corre como `facturacion` (creado explícitamente en la imagen; la base
  parte como `root` por defecto), con el directorio de almacenamiento ya entregado a ese usuario
  antes del `USER` (después de esa línea el proceso ya no puede crear directorios fuera de lo
  que ya le pertenece).
- **Configuración 100% por variables de entorno**, perfil `prod` (`SPRING_PROFILES_ACTIVE=prod`,
  `application-prod.yml`): `DB_*`/`REDIS_*` apuntan a los servicios `postgres`/`redis` por
  nombre de servicio (red interna, nunca por IP ni por el puerto publicado a la máquina host);
  `ALMACENAMIENTO_TIPO=local` + `ALMACENAMIENTO_LOCAL_RUTA=/app/almacenamiento-local`.
  `KEYCLOAK_ISSUER_URI` es la dirección **pública** de Keycloak
  (`${KEYCLOAK_PUBLIC_URL}/realms/${KEYCLOAK_REALM}`), **no** el nombre interno de Compose —
  contraintuitivo para un servicio que solo habla servidor-a-servidor, pero necesario: bug real
  encontrado en D3, ver §5.6 (los tokens que el backend recibe de verdad, obtenidos por el
  frontend a través del navegador, llevan grabado como emisor la dirección pública). El
  contenedor del backend alcanza esa dirección pública gracias al mismo puente `extra_hosts`
  que usa el frontend (§5.6).
- **CORS — bug real encontrado y corregido:** `SeguridadConfig` no tenía ningún
  `CorsConfigurationSource`; un preflight `OPTIONS` real devolvía 401 sin ningún header
  `Access-Control-Allow-*`, así que el navegador bloqueaba **toda** llamada del frontend con
  header `Authorization` antes de que llegara al backend — afecta a cualquier despliegue donde
  el frontend y el backend queden en orígenes distintos (el caso normal aquí, ver §4).
  `CORS_ORIGENES_PERMITIDOS` (`app.cors.origenes-permitidos`) reutiliza **la misma** `AUTH_URL`
  del frontend (mismo patrón que `KEYCLOAK_ISSUER_URI`/`KEYCLOAK_PUBLIC_URL` arriba) — **un
  único origen, estricto**, exactamente como está aquí — vacío por defecto fuera del perfil
  `local` (CORS cerrado hasta declararlo explícito), nunca un wildcard `*`. Detalle completo
  del diagnóstico y la verificación contra el contenedor real: `docs/deuda-tecnica.md` ítem 4.
  **Solo el perfil `local`** (`mvn spring-boot:run`, para desarrollo fuera de este stack
  Docker) amplía la lista por defecto a `http://localhost:3000,http://localhost:3001,
  http://localhost:3002` (`backend/.../application-local.yml`) — cubre que `npm run dev`
  (`frontend/package.json`, sin `-p` fijo) salte al siguiente puerto libre si el 3000 ya está
  ocupado por otro proceso, sin tener que exportar nada a mano en el caso común. Si tu puerto
  de dev cae fuera de ese rango, sobrescribe `CORS_ORIGENES_PERMITIDOS` (lista separada por
  comas; espacios alrededor de cada origen se recortan, `SeguridadConfig#parsearOrigenes`) en
  vez de editar `application-local.yml`. Este despliegue Docker (D2/D3, perfil `prod`) **no
  usa el perfil `local`** — sigue con el único origen estricto de `${AUTH_URL}` de arriba, sin
  cambios por este ajuste.
- **Almacenamiento de PDF:** `app.almacenamiento.tipo=local` (no OCI, es despliegue local) →
  un **volumen persistente con nombre**, `facturacion_backend_almacenamiento`, montado en
  `/app/almacenamiento-local`. Sobrevive a recrear el contenedor igual que los volúmenes de
  Postgres/Redis/Keycloak.
- **Espera a sus dependencias:** `depends_on: postgres (service_healthy), redis
  (service_healthy)` — no intenta migrar/arrancar contra una base que todavía no acepta
  conexiones.
- **Migraciones Flyway:** corren automáticamente al arrancar (`spring.flyway.enabled=true`,
  ya así antes de D2) — **contra el Postgres real del contenedor**, no H2 ni Testcontainers.
  Hibernate usa `ddl-auto=validate`: si Flyway no dejó el esquema exactamente como las entidades
  JPA esperan, el arranque falla ruidosamente, nunca genera DDL por su cuenta. Ver §7 para el
  resultado real verificado (las 11 migraciones, `V001`-`V011`).
- **El aprendizaje de Boot 4 sobre Flyway** (`CLAUDE.md` — "Spring Boot 4 — cambios que
  muerden") sigue aplicando igual dentro del contenedor: sin el starter dedicado
  `spring-boot-flyway` (ya está en `pom.xml`, no se tocó al armar la imagen), Boot 4 no
  autoconfigura el `FlywayMigrationInitializer` y Hibernate falla con un "missing table"
  engañoso. El Dockerfile no cambia el `pom.xml`, así que este punto ya viene resuelto.
- **Healthcheck:** `GET /actuator/health` (público, no pide token — `SeguridadConfig`),
  confirmando que el cuerpo contiene `"status":"UP"`. Usa `127.0.0.1` explícito, **no**
  `localhost` — en esta imagen Alpine/musl, `localhost` resuelve primero a `::1` (IPv6) y la
  app solo escucha en IPv4, así que un healthcheck contra `localhost` falla con "Connection
  refused" pese a que el proceso esté sano (bug real, encontrado y corregido al verificar este
  despliegue — mismo defecto en el frontend, §5.5).
- **Puerto:** `8080` publicado por defecto desde D2, temporalmente hasta D4 (§4) — el bundle de
  JavaScript del frontend llama a la API directo desde el navegador del usuario.

### 5.5 Frontend

- **Imagen:** construida desde `frontend/Dockerfile`, multi-stage — etapa 1 (`node:20-alpine`)
  con `npm ci` completo (incluye `devDependencies`) y `next build`; etapa 2 (la publicada) parte
  de la misma base pero copia **solo** el output `standalone` de Next
  (`next.config.mjs`: `output: "standalone"`, activado para este despliegue) — un `server.js`
  autocontenido más el `node_modules` mínimo que de verdad usa en producción, trazado por
  `@vercel/nft`, no el `node_modules` completo de desarrollo. Resultado: **156 MB** (verificado,
  §7).
- **Cache de capas:** copia `package.json`+`package-lock.json` y corre `npm ci` **antes** de
  copiar el resto del código — mismo principio que el `pom.xml` del backend.
- **Usuario no-root:** corre como `facturacion`, igual que el backend.
- **La distinción build-time vs. runtime de las variables de Next (la fuente clásica de
  errores de este Dockerfile):**
  - `NEXT_PUBLIC_API_BASE_URL` es la **única** variable de build (`ARG` +
    `docker compose build --build-arg` / `build.args` en `docker-compose.yml`). Next.js la
    reemplaza **directo en el bundle de JavaScript** que se manda al navegador, en el momento de
    `next build` — no existe como variable de entorno leída en runtime dentro del contenedor.
    Por eso:
    - Un cambio en `.env` a esta variable **no tiene ningún efecto** hasta reconstruir la imagen
      (`docker compose build frontend`) — llegar tarde a un proceso que ya terminó de compilar
      no sirve de nada.
    - **No es secreta** (aunque el nombre empiece con `NEXT_PUBLIC_` justamente para dejarlo
      claro): cualquiera que abra las herramientas de desarrollo del navegador puede leerla en
      el bundle igual, esté o no en el repo.
    - Tiene que ser una dirección que el **navegador** pueda resolver de verdad (el puerto
      publicado del backend, `KEYCLOAK_PUBLIC_URL`-style) — nunca el nombre interno de Compose
      (`backend`), que no existe fuera de `facturacion_interna`.
  - `AUTH_SECRET`, `AUTH_KEYCLOAK_ID`, `AUTH_KEYCLOAK_SECRET`, `AUTH_URL` y `AUTH_KEYCLOAK_ISSUER`
    son exactamente lo opuesto: Auth.js/Next.js los leen con `process.env.*` **del lado del
    servidor Node, en cada request** — nunca llegan al navegador. Por eso van como
    `environment:` del contenedor en **runtime**, nunca como `--build-arg`: si se hornearan en
    el build, el secreto quedaría escrito dentro de las capas de la imagen (visible con
    `docker history`/`docker save` + inspección), y la imagen ya no sería reutilizable entre
    entornos (dev/staging/prod) sin reconstruir solo para cambiar un secreto.
- **`AUTH_URL` — bug real encontrado y corregido en D3** (no era necesaria en D2 porque nunca se
  había completado un login real hasta entonces): el `server.js` que genera el output
  `standalone` de Next arma el *origin* de cada request —de donde Auth.js saca el `redirect_uri`
  que le manda a Keycloak— a partir del hostname/puerto de **bind** del propio proceso
  (`HOSTNAME=0.0.0.0`/`PORT=3000`, ver más arriba), no del header `Host` real de la petición del
  navegador. Sin `AUTH_URL`, el `redirect_uri` terminaba siendo literalmente
  `http://0.0.0.0:3000/api/auth/callback/keycloak` — una URL que Keycloak rechaza siempre, por
  no calzar con ninguna Valid Redirect URI real. `AUTH_URL` es la variable oficial de Auth.js
  para pisar esa detección: fuerza el *origin* a la dirección pública real del propio frontend
  (`http://localhost:3000` por defecto — ver `.env.example`, análoga a `NEXT_PUBLIC_API_BASE_URL`
  del backend o `KEYCLOAK_PUBLIC_URL` de Keycloak: la dirección que un navegador real puede
  resolver). Se intentó primero `next.config.mjs`'s `experimental.trustHostHeader` (la opción
  que en teoría resuelve esto a nivel de Next) y **no sirvió**: en Next.js 14.2 esa clave no
  existe en el schema de configuración validado — se descarta con un warning de build
  ("Unrecognized key(s) in object: 'trustHostHeader'") sin cambiar nada en runtime.
- **Healthcheck:** `GET /login` (pública, no depende de que el backend/Keycloak estén arriba —
  evita falsos negativos del healthcheck del frontend por una caída de un servicio externo).
  Mismo fix de `127.0.0.1` explícito que el backend (§5.4) — el mismo bug de Alpine/musl
  resolviendo `localhost` a IPv6 se encontró también acá, primero.
- **Puerto:** `3000` publicado por defecto desde D2, temporalmente hasta D4 (§4) — es la propia
  aplicación.
- **Depende de:** `backend (service_healthy)` — no tiene sentido levantar la app antes de que la
  API esté lista, aunque Next no falle duro si el backend tarda (las llamadas fallarían recién
  cuando un usuario interactúe).

### 5.6 El emisor de Keycloak: navegador y contenedores deben ver la MISMA dirección (bug real)

Esta es la fuente de errores más profunda de todo el despliegue, y se dio en dos capas
sucesivas: la primera versión del arreglo (D2) resolvía la mitad del problema; verificar el
login de punta a punta de verdad (D3, no solo hasta que Keycloak mostrara una URL bien formada)
destapó la otra mitad. Documentada completa acá para que nadie tenga que volver a
redescubrirla.

**El problema de fondo:** el proveedor `Keycloak` de Auth.js (`type: "oidc"`), y también el
`spring.security.oauth2.resourceserver.jwt.issuer-uri` del backend, usan el **mismo** valor de
`issuer` para dos cosas a la vez: (a) descubrir/validar contra Keycloak (`issuer` debe coincidir
EXACTO con el `"issuer"` que el propio Keycloak reporta en su documento de descubrimiento y en
el claim `iss` de cada token/redirección que emite), y (b) ser una dirección de red que el
contenedor pueda de verdad alcanzar. El navegador solo puede alcanzar Keycloak por el puerto
publicado (`keycloak.localhost:8081` — ver más abajo el porqué de ese nombre en vez de
`localhost` a secas); un contenedor de Compose, por defecto, solo puede alcanzarlo por el nombre
interno de red (`keycloak:8080`). Son dos direcciones de red distintas — pero como veremos,
Keycloak además se **autoidentifica de forma distinta según por cuál lo contacten**, lo que hace
que ni siquiera baste con "elegir una de las dos y ya".

**Primera capa (D2): `AUTH_URL`.** El `server.js` del output `standalone` de Next arma el
*origin* de cada request a partir del hostname/puerto de *bind* del propio proceso
(`HOSTNAME=0.0.0.0`, frontend/Dockerfile), no del header `Host` real — sin `AUTH_URL`, Auth.js
mandaba a Keycloak un `redirect_uri` literalmente `http://0.0.0.0:3000/...`, que Keycloak
rechaza siempre. Detalle completo en §5.5. Corregido esto, el navegador SÍ llegaba a una
pantalla de login real de Keycloak — pero el login todavía no cerraba del todo (ver la segunda
capa).

**Segunda capa (D3): el parámetro `iss`, y por qué `KC_HOSTNAME` fijo rompe todo.** Con
`KC_HOSTNAME` fijo en un valor (p. ej. `localhost`, como tenía D1/D2), Keycloak fuerza ESE
hostname en TODO lo que autogenera — incluido el `"issuer"` que ve el propio **contenedor**
del frontend/backend al contactarlo por la red interna (`keycloak:8080`): el documento de
descubrimiento devuelto ahí decía `"issuer": "http://localhost:8080/realms/helpcom"` — una URL
que ni siquiera es alcanzable desde ese contenedor (nada escucha ahí), y que además no coincide
con la dirección real usada para el fetch (`http://keycloak:8080/...`) ni con la que ve el
navegador (`http://localhost:8081/...`, puerto publicado). Auth.js rechaza esto de inmediato:
`CallbackRouteError: unexpected "iss" (issuer) response parameter value` — Keycloak agrega un
parámetro `iss` a la respuesta de autorización (RFC 9207, `authorization_response_iss_parameter
_supported` en su documento de descubrimiento) con la dirección con la que fue contactado en
CADA request, y Auth.js exige que ese `iss` coincida exacto con su `issuer` configurado. Con
`KC_HOSTNAME` fijo, la dirección que ve el navegador (`iss` del redirect) y la dirección que
Auth.js necesita para su propio descubrimiento interno **nunca pueden ser la misma string**, sin
importar qué combinación de variables se pruebe del lado del frontend — el problema está en
cómo Keycloak se autoidentifica, no en la configuración de Auth.js.

**El arreglo — tres piezas que van juntas:**
1. **`KC_HOSTNAME` vacío** (`docker-compose.yml`, servicio `keycloak`) + `KC_HOSTNAME_STRICT=
   false`: sin un hostname fijo, Keycloak se autoidentifica según la dirección con la que fue
   contactado en CADA request — verificado con
   `docker exec facturacion-frontend wget http://keycloak:8080/realms/helpcom/.well-known/openid-configuration`
   → `"issuer": "http://keycloak:8080/realms/helpcom"` (coincide con la dirección del fetch) y,
   por separado, `curl http://localhost:8081/realms/helpcom/.well-known/openid-configuration`
   (desde el host) → `"issuer": "http://localhost:8081/realms/helpcom"` — cada audiencia ve un
   emisor consistente **consigo misma**.
2. **Una sola dirección pública para TODO, incluidos los contenedores** (`AUTH_KEYCLOAK_ISSUER`
   del frontend y `KEYCLOAK_ISSUER_URI` del backend, ambas = `${KEYCLOAK_PUBLIC_URL}/realms/
   ${KEYCLOAK_REALM}`): dado el punto 1, el emisor que de verdad importa (el que queda grabado
   en el claim `iss` de los tokens y en el parámetro `iss` del redirect) es SIEMPRE la dirección
   pública, porque esa es la que usó el navegador para completar el login — así que backend y
   frontend deben validar contra esa MISMA dirección pública, no la interna de Compose. (Esto
   reemplaza el diseño de D2, que separaba `AUTH_KEYCLOAK_ISSUER`/`AUTH_KEYCLOAK_ISSUER_INTERNO`
   en dos valores — con `KC_HOSTNAME` vacío, ya no hace falta esa separación: los dos casos de
   uso convergen en una sola dirección. `AUTH_KEYCLOAK_ISSUER_INTERNO` sigue existiendo como
   variable opcional en `frontend/lib/auth.ts`, con su mismo mecanismo de *fallback*, por si
   algún despliegue futuro no puede usar el puente del punto 3 — hoy no se define.)
3. **Puente `extra_hosts: keycloak.localhost:host-gateway`** en `backend` y `frontend`
   (`docker-compose.yml`): para que el punto 2 funcione, esos contenedores necesitan poder
   ALCANZAR la dirección pública desde dentro de sí mismos — no solo que el `issuer` configurado
   sea correcto. `host-gateway` es la resolución especial de Compose hacia la IP del host desde
   dentro del contenedor (soportada en Docker Desktop y en Docker Engine 20.10+ en Linux), la
   misma ruta de red por la que entra el navegador al puerto publicado. El nombre elegido es
   **`keycloak.localhost`, no `localhost` a secas**: Alpine/musl (la base de ambas imágenes) ya
   trae "localhost" predefinido apuntando a `::1` en `/etc/hosts` (mismo defecto de las
   `HEALTHCHECK` de este despliegue — §5.4/§5.5); `extra_hosts` AGREGA una entrada, no reemplaza
   la existente, así que pisar "localhost" a secas deja dos entradas en pugna y musl sigue
   prefiriendo la `::1` original — verificado que el puente NO tenía efecto hasta cambiar el
   nombre. `keycloak.localhost` es un nombre nuevo sin entrada previa, sin ambigüedad — y
   cualquier navegador lo resuelve solo, sin configuración, porque cualquier subdominio bajo
   `.localhost` es loopback automático por convención (RFC 6761), soportado de fábrica por
   Chrome/Firefox/Edge y por el propio sistema operativo.

**Verificado de punta a punta contra Keycloak real** (§7.2): con las tres piezas del arreglo,
login completo (Authorization Code + PKCE) para `admin.prueba` y `operador.prueba`, tokens reales
con `realm_access.roles` correcto, backend acepta esos tokens y aplica la autorización por rol
(`403` para `OPERADOR` en `POST /api/v1/ciclos/ejecutar`, `200` para `ADMINISTRADOR`), y
renovación de token silenciosa antes de que expire el access token — sin ninguna de las tres
piezas, cada intento fallaba en un punto distinto de la cadena (redirect roto, o "Client not
found", o `error=Configuration`, o `401` en el backend pese a un login exitoso).

### 5.7 Keycloak como código: `deploy/keycloak/realm-helpcom.json`

Todo lo que hasta D2 había que configurar a mano en la consola de Keycloak (arquitectura-
tecnica.md §7, y el equivalente de desarrollo local en `docs/frontend.md` §1.1) desde D3 vive
versionado en este archivo, e importado automáticamente por `--import-realm` (§5.3). Nada de
esto se configura por la consola web sin que quede plasmado acá.

**Realm:** `helpcom` — coincide con la convención ya usada en `docs/frontend.md` §1.1.
`sslRequired: "none"` (Keycloak sirve HTTP plano puertas adentro hasta D4, igual que
`KC_HTTP_ENABLED`) y `accessTokenLifespan: 300` (5 minutos — corto a propósito, para poder
verificar la renovación silenciosa de token sin esperar demasiado).

**Roles de realm:** `ADMINISTRADOR` y `OPERADOR`, exactamente esos nombres en mayúsculas —
`SeguridadConfig` del backend los mapea a autoridades `ROLE_ADMINISTRADOR`/`ROLE_OPERADOR` desde
el claim `realm_access.roles` (arquitectura-tecnica.md §7). El scope de cliente por defecto
`roles` (sembrado automáticamente por Keycloak en cualquier realm nuevo) ya incluye el mapper
que expone ese claim — no hizo falta agregar ningún mapper adicional.

**Clientes OIDC:**
- `facturacion-recurrente-frontend` — confidential (`clientAuthenticatorType: client-secret`),
  Authorization Code + PKCE (`standardFlowEnabled: true`, `attributes.pkce.code.challenge.method:
  "S256"` para EXIGIR PKCE, no solo aceptarlo si el cliente lo manda). `directAccessGrants
  Enabled: false` y `serviceAccountsEnabled: false` — este cliente solo inicia sesión vía
  navegador, nunca por contraseña directa ni como service account. Redirect URI registrada:
  `http://localhost:3000/api/auth/callback/keycloak` (el puerto convencional del frontend, §4) —
  si accedes desde otra máquina de la oficina (IP LAN en vez de `localhost`, §3), agrega esa
  URI adicional a mano en la consola (Clients → facturacion-recurrente-frontend → Valid redirect
  URIs) o reimporta el realm con ese valor agregado; el archivo versionado no puede anticipar
  cada IP LAN posible de cada despliegue.
- `facturacion-recurrente-backend` — `bearerOnly: true`, sin ningún flujo de login habilitado.
  Existe como registro informativo/reservado para el día en que se agregue restricción de
  `audience` a la validación del backend; **hoy no hace falta y no se usa**: el backend valida
  JWT solo por `issuer`/JWKS (§5.4), sin comprobar contra qué cliente se emitió el token.

**Usuarios de prueba** (para arrancar y para QA — coinciden con los que asume
`docs/guion-qa-manual.md`): `admin.prueba` (rol `ADMINISTRADOR`) y `operador.prueba` (rol
`OPERADOR`), contraseñas `cambiar-clave-admin-prueba`/`cambiar-clave-operador-prueba` —
placeholders de ejemplo, versionados y por lo tanto públicos, **nunca contraseñas reales**.
Cada credencial tiene `"temporary": true` **y** `"requiredActions": ["UPDATE_PASSWORD"]` en el
usuario — ambas son necesarias: `temporary` por sí solo, al importar un realm (a diferencia de
resetear una contraseña a mano desde la consola, que sí agrega la acción requerida
automáticamente), **no** agrega la acción requerida al usuario — sin `requiredActions`
explícito, el login habría completado con la contraseña placeholder sin pedir cambiarla nunca
(bug real encontrado y corregido en D3: verificado que la primera versión del archivo, solo con
`temporary: true`, dejaba iniciar sesión directo sin pantalla de cambio de contraseña; con
`requiredActions` explícito, el submit de credenciales redirige a
`.../login-actions/required-action?execution=UPDATE_PASSWORD` antes de completar el login,
verificado en este despliegue).

**El client secret del frontend vive en DOS lugares que deben coincidir** (`deploy/.env.example`
tiene la nota completa): el que ve el frontend (`AUTH_KEYCLOAK_SECRET` en `.env`) y el que
Keycloak realmente exige (sembrado por el import, campo `secret` del cliente en el JSON). El
placeholder de ambos es el mismo string a propósito (`cambiar-este-secreto-cliente-keycloak`),
así que el login funciona "de fábrica" apenas se copia `.env.example` — pero sigue siendo un
placeholder público, versionado en el repo. **Antes de cualquier uso real:** genera un secreto
nuevo (`openssl rand -base64 32`), ponlo en `.env`, y regenera el client secret en Keycloak para
que coincida (Admin Console → Clients → facturacion-recurrente-frontend → Credentials →
Regenerate, pegar el mismo valor en `.env`) — reimportar el realm NO sirve para esto, porque
`--import-realm` **no reimporta un realm que ya existe** (§5.3); hay que editarlo en la consola
o vía la API de administración de Keycloak directamente.

**Crear los usuarios reales de Helpcom (D5):** nunca en `realm-helpcom.json` (ese archivo es
público, versionado, pensado para arrancar el sistema y para QA — no para credenciales reales).
Procedimiento: consola de Keycloak → realm `helpcom` → Users → Add user, asignar el rol
`ADMINISTRADOR` u `OPERADOR` que corresponda (Role mapping), y en Credentials asignar una
contraseña con "Temporary" activado (ahí la consola SÍ agrega la acción requerida
automáticamente, a diferencia del import — ver más arriba) para forzar que la persona la cambie
en su primer ingreso. Los usuarios `admin.prueba`/`operador.prueba` pueden desactivarse
(`enabled: false` vía la consola) una vez que existan usuarios reales, o dejarse para QA futura
— decisión operativa de Helpcom, no técnica.

---

## 6. Operación

- **`depends_on` con condición `service_healthy`:** Keycloak espera a Postgres, el backend
  espera a Postgres y Redis, y el frontend espera al backend — todos por healthcheck real ("está
  listo para responder"), no solo "el contenedor arrancó" — evita el típico fallo de arranque
  por conectarse/migrar demasiado pronto.
- **`restart: unless-stopped`** en los cinco servicios: si el servidor de la oficina se reinicia
  solo (p. ej. tras un corte de energía) y Docker arranca al iniciar el sistema operativo, el
  stack completo vuelve a levantarse sin intervención manual — salvo que alguien lo haya
  detenido a propósito (`docker compose stop`), en cuyo caso se respeta esa decisión.
- **Apagar sin perder datos:** `docker compose down` (detiene y elimina los contenedores, deja
  los volúmenes intactos). **Nunca** `docker compose down -v` salvo que la intención sea borrar
  todo — verificado en este despliegue que los datos sobreviven un `down` + `up` normal (§7).
- **Ver logs:** `docker compose logs -f <servicio>` (`postgres`, `redis`, `keycloak`, `backend`
  o `frontend`).
- **Actualizar una imagen de terceros** (`postgres`, `redis`, `keycloak`): cambia la versión
  fijada en `docker-compose.yml` (nunca uses `latest`), luego
  `docker compose pull <servicio> && docker compose up -d <servicio>`.
- **Reconstruir backend/frontend tras un cambio de código:**
  ```bash
  docker compose build backend frontend   # o solo uno de los dos
  docker compose up -d backend frontend
  ```
  Compose **no** reconstruye automáticamente solo porque el código cambió — `docker compose up`
  sin `--build` sigue usando la imagen ya construida aunque el `src/` de atrás haya cambiado.
  Para el frontend, recuerda §5.5: si lo que cambió es `NEXT_PUBLIC_API_BASE_URL` en `.env`, hace
  falta reconstruir la imagen para que tenga efecto, un simple reinicio del contenedor no basta.
- **Reintentar solo las migraciones Flyway** (p. ej. tras arreglar una migración que falló):
  no hace falta nada especial — Flyway corre automáticamente cada vez que arranca el backend
  (`docker compose restart backend` o recrear el contenedor), y es idempotente: las migraciones
  ya aplicadas (registradas en `flyway_schema_history`) no se repiten.
- **El import del realm de Keycloak (`--import-realm`, §5.3/§5.7) es "una sola vez", no
  idempotente-y-repetible:** a diferencia de Flyway, si el realm `helpcom` ya existe, Keycloak
  simplemente NO reimporta nada en el siguiente arranque (ni pisa cambios manuales posteriores,
  ni falla) — así que **editar `realm-helpcom.json` después del primer arranque no tiene efecto
  por sí solo**. Para aplicar un cambio al archivo contra un despliegue ya inicializado: o lo
  replicás a mano en la consola de Keycloak, o exportás/borrás el realm existente y dejás que
  `--import-realm` lo vuelva a crear desde el archivo actualizado (esto último borra cualquier
  usuario/cambio hecho a mano en ese realm — pensalo dos veces en un servidor con usuarios
  reales, D5).

---

## 7. Verificación

Esto es infraestructura — la "prueba" es que el stack arranca limpio y queda sano, no una suite
de JUnit. Comandos y qué esperar (ejecutados y confirmados al construir esta base):

```bash
cd deploy
docker compose up -d
docker compose ps
```

**Estado esperado** (puede tardar hasta ~30-40 s la primera vez, mientras Postgres inicializa y
Keycloak corre sus migraciones internas):

```
NAME                   STATUS
facturacion-postgres   Up ... (healthy)
facturacion-redis      Up ... (healthy)
facturacion-keycloak   Up ... (healthy)
```

Verificaciones puntuales adicionales:

```bash
# Postgres acepta conexiones y tiene las dos bases con sus dueños correctos.
docker exec facturacion-postgres psql -U "$POSTGRES_USER" -d postgres -c \
  "SELECT datname, pg_catalog.pg_get_userbyid(datdba) AS owner FROM pg_database
   WHERE datname IN ('facturacion_recurrente','keycloak');"

# btree_gist funciona en la base de la app (lo que necesitará V001 al correr las migraciones).
docker exec facturacion-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "CREATE EXTENSION IF NOT EXISTS btree_gist;"

# Redis responde con la clave configurada.
docker exec facturacion-redis redis-cli -a "$REDIS_PASSWORD" ping   # → PONG

# Keycloak arrancó contra Postgres y su endpoint de salud está en verde.
docker compose logs keycloak | grep "started in"
```

**Verificación de persistencia** (confirma que los volúmenes realmente sobreviven, no solo que
existen):

```bash
docker compose down        # detiene y quita los contenedores, NO los volúmenes
docker compose up -d       # los vuelve a crear sobre los mismos volúmenes
docker compose ps          # deben quedar los tres "healthy" de nuevo
```

Si el segundo arranque de Keycloak es notablemente más rápido que el primero y sus logs **no**
muestran de nuevo "Created temporary admin user" ni la corrida de las 148 migraciones internas,
es la confirmación de que reutilizó los datos ya persistidos en Postgres, en vez de partir de
cero.

### 7.1 Verificación de D2 (backend + frontend, de punta a punta)

Con el stack completo arriba (`docker compose up -d --build`), `docker compose ps` debe agregar
`facturacion-backend` y `facturacion-frontend`, ambos eventualmente `healthy` (el backend puede
tardar más que Postgres/Redis/Keycloak porque además corre las 11 migraciones Flyway al
arrancar).

**Migraciones Flyway aplicadas contra el Postgres real** (no H2, no Testcontainers):

```bash
docker exec facturacion-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Resultado verificado: **11 filas, `V001` a `V011`, todas con `success = t`** — el esquema
completo del modelo de datos, incluida la semilla de `V011`.

**Backend con `ddl-auto=validate` sin errores** — arrancó limpio (si Flyway hubiera dejado el
esquema desalineado de las entidades JPA, el arranque habría fallado ruidosamente en este punto,
no en silencio).

**Semilla de `V011` presente:**

```bash
docker exec facturacion-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT id, rut, razon_social, activa FROM empresa;"
# 1 | XX.XXX.XXX-X | Helpcom Ltda. | t

docker exec facturacion-postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT clave, valor FROM parametro_sistema;"
# tasa_iva | 0.19
```

El RUT sigue siendo el placeholder `XX.XXX.XXX-X` **a propósito** — recordatorio para D5 (tabla
de cabecera y §1).

**Backend responde en su endpoint de salud, y rechaza sin token:**

```bash
curl http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}

curl -i http://localhost:8080/api/v1/clientes
# HTTP/1.1 401
```

El 401 (en vez de un error de conexión o un 200 inesperado) confirma que el resource server de
Spring Security ya está validando JWT — aunque el realm `helpcom` real todavía no exista (D3),
la ausencia total de token ya es motivo suficiente de rechazo.

**Frontend carga y redirige a login:**

```bash
curl -i http://localhost:3000/
# HTTP/1.1 307, Location: /login?callbackUrl=...

curl -i http://localhost:3000/login
# HTTP/1.1 200, cuerpo contiene "Keycloak"
```

**Flujo de login redirige de verdad a Keycloak** (probado como lo haría un navegador: primero
un `GET /api/auth/csrf` para obtener un token CSRF real, luego el `POST` de sign-in con ese
token — un `curl` directo al endpoint de sign-in sin CSRF da un falso negativo,
`error=Configuration`/`UnknownAction`, que no es el bug real):

```
POST /api/auth/signin/keycloak → HTTP 302
Location: http://localhost:8081/realms/master/protocol/openid-connect/auth?
  scope=openid+profile+email&response_type=code&client_id=facturacion-recurrente-frontend&
  redirect_uri=...&code_challenge=...&code_challenge_method=S256
```

Siguiendo esa URL, Keycloak responde `HTTP 400 "Client not found"` — **esperado y correcto**:
el cliente OIDC `facturacion-recurrente-frontend` todavía no existe en el realm `master` (se
crea recién en D3, en el realm real `helpcom`). Esto confirma el mecanismo de redirección
completo, hasta exactamente el límite de alcance de D2 — el login end-to-end se cierra en D3.

Antes de llegar a este resultado, la primera versión del flujo fallaba con
`error=Configuration` incluso probándolo correctamente (con CSRF) — el bug real de §5.6
(`AUTH_KEYCLOAK_ISSUER` público inalcanzable desde el propio contenedor del frontend). Ese
arreglo de D2 resolvía solo la primera mitad del problema; §7.2 documenta el resto, destapado
al completar el login de verdad contra un realm real en D3.

**Tamaño de las imágenes** (multi-stage cumpliendo su objetivo): `facturacion-backend:local` ≈
385 MB, `facturacion-frontend:local` ≈ 156 MB — ninguna arrastra su etapa de build (Maven+JDK
completo / `devDependencies` de Node).

**Nota sobre puertos usados en la verificación:** en la máquina donde se verificó este
despliegue, los puertos 8080 y 3000 ya estaban en uso por procesos locales no relacionados
(anteriores a esta tarea); la verificación de arriba se hizo remapeando temporalmente esos
puertos en el lado del host, y el archivo entregado quedó con los puertos convencionales
(8080/3000) — el mapeo de puertos es un detalle puramente del lado del host, no afecta ninguno
de los mecanismos verificados (todo lo demás — red interna, healthchecks, Flyway, OIDC — usa
nombres de servicio o el puerto interno del contenedor, sin relación con qué puerto del host se
publique).

### 7.2 Verificación de D3 (login OIDC y autorización por rol, de punta a punta, con Keycloak real)

Con el stack completo levantado **desde cero** (`docker compose down -v` + `docker compose up
-d --build`, para probar el import del realm contra una base realmente vacía, no una ya
sembrada), verificado paso a paso — sin atajos, sin mocks, contra el Keycloak real del
contenedor:

**El realm se importó solo, al primer arranque:**

```
docker compose logs keycloak | grep "helpcom' imported"
# Realm 'helpcom' imported
# KC-SERVICES0032: Import finished successfully
```

**Roles, clientes y usuarios quedaron exactamente como especifica `realm-helpcom.json`**
(confirmado vía la API de administración de Keycloak, no asumido):
- Roles de realm: `ADMINISTRADOR`, `OPERADOR` (más los técnicos por defecto de cualquier realm
  — `default-roles-helpcom`, `offline_access`, `uma_authorization`).
- Clientes: `facturacion-recurrente-frontend` (`bearerOnly: false`, `redirectUris` con el
  callback del frontend) y `facturacion-recurrente-backend` (`bearerOnly: true`).
- Usuarios: `admin.prueba` con rol `ADMINISTRADOR`, `operador.prueba` con rol `OPERADOR`.

**El "Client not found" de D2 desapareció.** El flujo de login (`GET /api/auth/csrf` → `POST
/api/auth/signin/keycloak` con el token CSRF, igual que en D2) ahora redirige a una pantalla de
login REAL del realm `helpcom` (título "Sign in to Helpcom - Facturación Recurrente"), no a un
error.

**Cambio de contraseña obligatorio en el primer ingreso** (§5.7): al enviar las credenciales de
`admin.prueba` con su contraseña placeholder, Keycloak redirige a
`.../login-actions/required-action?execution=UPDATE_PASSWORD` en vez de completar el login
directo — confirma que `requiredActions: ["UPDATE_PASSWORD"]` funciona como se espera.

**Login completo (Authorization Code + PKCE) contra Keycloak real, para los dos roles** —
verificado con la sesión de Auth.js ya establecida (`GET /api/auth/session`) y el access token
real decodificado:

```
admin.prueba  → roles: ["ADMINISTRADOR"], JWT iss: http://keycloak.localhost:8081/realms/helpcom
operador.prueba → roles: ["OPERADOR"],    JWT iss: http://keycloak.localhost:8081/realms/helpcom
```

Ambos tokens con `realm_access.roles` correcto y `preferred_username` coincidiendo con el
usuario que inició sesión — el mapeo `realm_access.roles → ROLE_*` de `SeguridadConfig`
(arquitectura-tecnica.md §7) recibe exactamente lo que Keycloak emite de verdad, no un `jwt()`
simulado de test.

**Autorización por rol end-to-end, contra el backend real, con esos tokens reales:**

```
# admin.prueba
GET  /api/v1/ciclos            → 200
GET  /api/v1/clientes           → 200
POST /api/v1/ciclos/ejecutar    → 200 (ejecución real del ciclo contra el Postgres real)

# operador.prueba
GET  /api/v1/ciclos            → 200
GET  /api/v1/clientes           → 200
POST /api/v1/ciclos/ejecutar    → 403 {"detail":"No tiene permisos para realizar esta operación.",
                                        "status":403,"title":"Acceso denegado", ...}
```

Confirma que `hasRole('ADMINISTRADOR')`/`hasAnyRole('ADMINISTRADOR','OPERADOR')`
(`CicloControlador`, arquitectura-tecnica.md §7) funcionan con roles que vienen de un Keycloak
real — no solo con la suite E2E del backend (que simula el JWT con `SoporteE2E.jwt()...`,
`docs/qa.md`).

**Renovación de token silenciosa:** con `accessTokenLifespan: 300` (5 minutos, §5.7), se esperó
hasta ~20 segundos antes del vencimiento del access token de `admin.prueba` y se volvió a
consultar `/api/auth/session` con la misma cookie de sesión — el token devuelto tenía un `iat`
nuevo (renovado) y un `exp` extendido otros 5 minutos, sin ningún error (`session.error`
`undefined`) y sin que la sesión se cayera. Confirma `renovarAccessToken` en
`frontend/lib/auth.ts`.

**Los tres bugs reales de esta etapa** (`AUTH_URL`/origin del frontend, el emisor de Keycloak
navegador-vs-contenedores, y `requiredActions` del import) están documentados con su causa raíz
completa en §5.5, §5.6 y §5.7 respectivamente — acá solo el resultado de la verificación final,
ya con los tres arreglos aplicados.

**Nota metodológica:** la verificación completa del flujo de login se hizo simulando un
navegador con `curl` (cookies, redirecciones y el intercambio de código a mano) — sin un
navegador real disponible en este entorno. Un detalle específico de curl (no del despliegue):
su motor de cookies solo exceptúa de la regla "una cookie `Secure` no viaja por HTTP plano" al
host literal `localhost`, no a subdominios de `.localhost` como `keycloak.localhost` — un
navegador real SÍ implementa esa excepción para cualquier `.localhost` (Secure Contexts / RFC
6761), así que se reenviaron esas cookies a mano para completar la verificación. No afecta la
validez de lo verificado: es una particularidad del cliente de prueba, no del comportamiento que
vería un usuario real en su navegador.

---

## 8. Seguridad — recordatorios de esta etapa

- **Cero secretos en el repositorio:** `deploy/.env` nunca se versiona (`deploy/.gitignore` +
  la regla `.env` del `.gitignore` raíz). Solo `deploy/.env.example`, con placeholders, se
  versiona. Esto incluye los secretos de Auth.js (`AUTH_SECRET`, `AUTH_KEYCLOAK_SECRET`) — nunca
  como `--build-arg` del frontend (§5.5), porque quedarían escritos en las capas de la imagen
  aunque el `.env` en sí esté bien protegido.
- **`deploy/keycloak/realm-helpcom.json` SÍ se versiona, y contiene placeholders, no secretos
  reales** (§5.7): el client secret del cliente frontend y las contraseñas de los usuarios de
  prueba son valores de ejemplo, iguales a los de `.env.example` a propósito — cualquiera con
  acceso al repo los conoce. Ambos deben cambiarse antes de cualquier uso real (§5.7 tiene el
  procedimiento), y los usuarios de prueba nunca deben llevar datos ni permisos que importen de
  verdad — son para arrancar el sistema y para QA, no para producción.
- **Publicar un puerto es una decisión explícita, documentada y — hoy — temporal:** `backend`,
  `frontend` y `keycloak` publican puerto desde D2 porque D2/D3 los necesitan alcanzables desde
  un navegador (§4); `postgres`/`redis` siguen sin publicar nada. Ninguno de los tres es
  "definitivo" — D4 los reemplaza por el único puerto del reverse proxy.
- **Claves generadas, no triviales:** usa `openssl rand -base64 32` (§3) para cada
  `..._PASSWORD`/`..._SECRET` de `.env` — evita reutilizar la misma clave entre servicios o
  entre entornos.
- **El volumen de PDF (`facturacion_backend_almacenamiento`) contiene documentos de facturación
  reales una vez en uso** — mismo nivel de cuidado que el volumen de Postgres al pensar en
  respaldos (D5): perder ese volumen sin respaldo pierde los PDF ya emitidos, no solo datos
  regenerables.
- **`KC_HOSTNAME_STRICT=false` con `KC_HOSTNAME` vacío (§5.6) es una relajación deliberada,
  acotada a esta etapa:** Keycloak acepta autoidentificarse según cualquier dirección con la que
  lo contacten, en vez de validar contra un hostname fijo — aceptable hoy porque nada de esto
  está expuesto más allá de `backend`/`frontend`/el navegador de quien prueba desde la propia
  LAN de la oficina (§4), pero es exactamente el tipo de ajuste que D4 debe revertir
  (`KC_HOSTNAME_STRICT=true` con el dominio público real) antes de que este sistema quede
  accesible desde fuera de esa LAN.

---

## 9. Qué sigue

- **D2 — hecho:** backend y frontend contenerizados e integrados a la base de D1; las 11
  migraciones Flyway aplicadas contra el Postgres real; sistema completo verificado de punta a
  punta (§7.1).
- **D3 — hecho:** realm `helpcom` como código (`deploy/keycloak/realm-helpcom.json`, §5.7),
  roles `ADMINISTRADOR`/`OPERADOR`, clientes OIDC, usuarios de prueba; login (Authorization Code
  + PKCE) y autorización por rol verificados de punta a punta contra Keycloak real (§7.2).
- **D4:** reverse proxy con HTTPS — dominio real, certificado, `KC_HOSTNAME` definitivo (fijo y
  con `KC_HOSTNAME_STRICT=true`, revirtiendo la relajación de §5.6/§8); `backend`, `frontend` y
  `keycloak` dejan de publicar puerto propio (§4) y pierden los puentes `extra_hosts` de §5.6
  (ya no hacen falta con todo detrás del mismo proxy); `KEYCLOAK_PUBLIC_URL`/`AUTH_URL`/
  `NEXT_PUBLIC_API_BASE_URL` pasan a apuntar al dominio público real.
- **D5:** respaldos (incluido el volumen de PDF, §8), monitoreo/observabilidad de
  infraestructura, runbook operativo, **crear los usuarios reales de Helpcom** (§5.7 — nunca en
  `realm-helpcom.json`) y **reemplazar el RUT placeholder de Helpcom**
  (`XX.XXX.XXX-X`, sembrado por `V011`) por el RUT real.
