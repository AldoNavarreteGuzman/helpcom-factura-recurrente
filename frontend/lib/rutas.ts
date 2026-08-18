/**
 * Rutas accesibles sin sesión. Todo lo demás lo protege el middleware (ver `lib/auth.config.ts`
 * y `middleware.ts`). `/api/auth/*` no está acá porque el `matcher` del middleware ya lo
 * excluye directamente (NextAuth necesita poder completar el login sin que el propio
 * middleware lo bloquee).
 */
const RUTAS_PUBLICAS = ["/login"];

export function esRutaPublica(pathname: string): boolean {
  return RUTAS_PUBLICAS.some((ruta) => pathname === ruta || pathname.startsWith(`${ruta}/`));
}
