/** @type {import('next').NextConfig} */
const nextConfig = {
  // Imagen de runtime mínima en Docker (deploy/frontend.Dockerfile, docs/despliegue.md):
  // copia solo el servidor Next mínimo + las dependencias que de verdad usa, en vez de
  // node_modules completo.
  output: "standalone",
  // NO uses experimental.trustHostHeader para el problema de origin detrás de Docker (bug
  // real encontrado en D3, docs/despliegue.md): en Next.js 14.2 esa clave NO existe en el
  // schema de configuración validado — Next la descarta con un warning de build
  // ("Unrecognized key(s) in object: 'trustHostHeader' at experimental") y no cambia nada en
  // runtime. El arreglo real es la variable de entorno AUTH_URL (frontend/lib/auth.ts la lee
  // indirectamente vía next-auth — ver la nota larga en docker-compose.yml).

  /**
   * Proxy SOLO para desarrollo local (`npm run dev` en un puerto distinto al backend real,
   * ver docs/frontend.md "Modo desarrollo contra el stack Docker"). RESUELTO (docs/deuda-tecnica.md
   * ítem 4): el backend (`SeguridadConfig`) ya tiene un `CorsConfigurationSource`/`.cors(...)`
   * configurado, con el rango de puertos típico de `npm run dev` permitido por defecto en el
   * perfil `local` (`application-local.yml`, 3000-3002) — un `fetch` cross-origin directo al
   * backend (sin este rewrite) ya funciona para esos puertos. Este proxy sigue existiendo como
   * COMODIDAD, no como workaround de un defecto abierto: evita depender de que tu puerto de
   * dev caiga dentro del rango permitido (o de tener que exportar `CORS_ORIGENES_PERMITIDOS`
   * si cae fuera) y evita el viaje de preflight en cada llamada. Con este rewrite,
   * `NEXT_PUBLIC_API_BASE_URL` apunta a `/api/proxy` (mismo origen que la propia app en dev), y
   * es ESTE servidor Next quien reenvía la petición server-a-server al backend real — el
   * navegador nunca cruza de origen, así que nunca dispara un preflight. Inerte en el build de
   * Docker: ahí `NEXT_PUBLIC_API_BASE_URL` sigue apuntando directo al backend
   * (docs/despliegue.md), con un único origen estricto vía `${AUTH_URL}` en el compose — así
   * que ninguna ruta construye una URL bajo `/api/proxy` y esta regla nunca hace match.
   */
  async rewrites() {
    return [
      {
        source: "/api/proxy/:ruta*",
        destination: `${process.env.BACKEND_PROXY_DESTINO ?? "http://localhost:8080/api/v1"}/:ruta*`,
      },
    ];
  },
};

export default nextConfig;
