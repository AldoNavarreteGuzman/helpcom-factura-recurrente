# Imagen del backend (Spring Boot 4 / Java 21) para el despliegue local de Helpcom en Docker
# Compose — docs/despliegue.md (D2). Multi-stage: la etapa de build nunca llega a la imagen
# final, así que el resultado no carga Maven ni el JDK completo, solo el JRE y el jar.

# ---------------------------------------------------------------------------------------
# Etapa 1 — build (Maven + JDK 21 completo, se descarta al final)
# ---------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copia SOLO el pom y resuelve dependencias primero: mientras el pom no cambie, Docker
# reutiliza esta capa (con todo el árbol de dependencias ya descargado) aunque el código
# fuente sí cambie — evita volver a bajar medio Maven Central en cada build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -Dmaven.test.skip=true

# ---------------------------------------------------------------------------------------
# Etapa 2 — runtime (solo JRE, sin Maven ni JDK completo)
# ---------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuario no-root dedicado — la imagen base parte como root por defecto.
RUN addgroup -S facturacion && adduser -S facturacion -G facturacion

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar

# Directorio para los PDF cuando app.almacenamiento.tipo=local (el valor por defecto de esta
# etapa, sin OCI) — se monta como volumen persistente desde docker-compose.yml. Se crea y se
# entrega al usuario no-root ANTES del USER, porque después de ese punto el proceso ya no
# tiene permiso para crear directorios fuera de lo que ya le pertenece.
RUN mkdir -p /app/almacenamiento-local && chown -R facturacion:facturacion /app
VOLUME /app/almacenamiento-local

USER facturacion

EXPOSE 8080

# /actuator/health es público (SeguridadConfig) — el healthcheck no necesita token. El
# formato mínimo de respuesta (sin management.endpoint.health.show-details) es
# {"status":"UP"}; alcanza con confirmar que contiene "UP". 127.0.0.1 explícito, NO
# "localhost": en Alpine/musl "localhost" resuelve primero a ::1 (IPv6) y la app solo escucha
# en IPv4, así que wget contra "localhost" da "Connection refused" pese a que el proceso esté
# sano — mismo defecto verificado y corregido en el Dockerfile del frontend.
HEALTHCHECK --interval=10s --timeout=5s --retries=10 --start-period=45s \
  CMD wget -q -O- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
