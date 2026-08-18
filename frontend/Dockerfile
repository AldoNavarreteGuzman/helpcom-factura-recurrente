# Imagen del frontend (Next.js 14, output "standalone") para el despliegue local de Helpcom
# en Docker Compose — docs/despliegue.md (D2).
#
# OJO con las variables (la fuente clásica de errores de este Dockerfile, documentada también
# en docs/despliegue.md):
# - Las `NEXT_PUBLIC_*` (acá, NEXT_PUBLIC_API_BASE_URL) las hornea Next.js EN EL BUILD: el
#   compilador las reemplaza directo en el bundle de JavaScript que corre en el NAVEGADOR del
#   usuario, así que deben llegar como `--build-arg` de `docker build`/`build.args` de Compose,
#   nunca como variable de entorno del contenedor en ejecución (llegar tarde no sirve — ya no
#   hay nada que recompilar en runtime). Por eso NO son secretas: cualquiera que abra el
#   navegador puede leerlas en el bundle igual.
# - `AUTH_SECRET`/`AUTH_KEYCLOAK_SECRET`/`AUTH_KEYCLOAK_ID`/`AUTH_KEYCLOAK_ISSUER` son
#   EXACTAMENTE lo opuesto: Auth.js los lee con `process.env.*` del lado del SERVIDOR Node
#   (nunca llegan al navegador), en cada request — por eso deben ser variables de entorno del
#   contenedor en RUNTIME (`environment:` en docker-compose.yml), nunca `--build-arg`: si se
#   hornearan en el build, el secreto quedaría escrito dentro de la imagen (visible con
#   `docker history`/capas), y además esta imagen sería reconstruible solo para ese entorno.

# ---------------------------------------------------------------------------------------
# Etapa 1 — build (Node completo + devDependencies, se descarta al final)
# ---------------------------------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /build

# Copia SOLO los manifiestos de paquetes primero — igual que el pom.xml del backend, así un
# cambio de código fuente no invalida la capa de `npm ci` (la más lenta).
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Única variable de build: pública a propósito (ver nota de arriba). Con default para que
# `docker build` funcione sin argumentos explícitos en un pull rápido de verificación local.
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}

RUN npm run build

# ---------------------------------------------------------------------------------------
# Etapa 2 — runtime (solo el output "standalone": servidor mínimo + node_modules trazado)
# ---------------------------------------------------------------------------------------
FROM node:20-alpine AS runtime

RUN addgroup -S facturacion && adduser -S facturacion -G facturacion

WORKDIR /app
ENV NODE_ENV=production

# El output "standalone" de Next ya trae su propio node_modules mínimo (trazado con
# @vercel/nft, no el node_modules completo de desarrollo) y un server.js autocontenido —
# no hace falta `npm install` en esta etapa.
COPY --from=build --chown=facturacion:facturacion /build/public ./public
COPY --from=build --chown=facturacion:facturacion /build/.next/standalone ./
COPY --from=build --chown=facturacion:facturacion /build/.next/static ./.next/static

USER facturacion

EXPOSE 3000
ENV PORT=3000 HOSTNAME=0.0.0.0

# /login es pública (middleware, lib/rutas.ts) y no depende de que el backend/Keycloak estén
# arriba — sirve como healthcheck del propio proceso Next sin falsos negativos por servicios
# externos caídos. 127.0.0.1 explícito, NO "localhost": en esta imagen (alpine/musl) "localhost"
# resuelve primero a ::1 (IPv6) y el servidor Next solo escucha en IPv4 (0.0.0.0), así que
# wget contra "localhost" da "Connection refused" aunque el proceso esté sano — verificado al
# construir esta imagen.
HEALTHCHECK --interval=10s --timeout=5s --retries=10 --start-period=20s \
  CMD wget -q -O- http://127.0.0.1:3000/login >/dev/null || exit 1

CMD ["node", "server.js"]
