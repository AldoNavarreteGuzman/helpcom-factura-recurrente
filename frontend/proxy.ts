import NextAuth from "next-auth";
import { authConfig } from "@/lib/auth.config";

/**
 * Instancia de Auth.js dedicada a este proxy (`middleware.ts` hasta Next 15 — Next 16 renombró
 * la convención de archivo a `proxy.ts`, mismo mecanismo, solo el nombre; docs/frontend.md
 * §9.4/§19): usa `authConfig` (sin el provider de Keycloak ni los callbacks de renovación, que
 * necesitan el runtime Node) porque este proxy corre en el runtime Edge. Solo evalúa
 * `callbacks.authorized` para decidir si deja pasar la petición o redirige a `/login`.
 */
export default NextAuth(authConfig).auth;

export const config = {
  // Todo excepto los assets estáticos y las rutas propias de Auth.js (necesitan ejecutarse
  // sin sesión para poder completar el login). `favicon.ico` era la única excepción de archivo
  // suelto hasta R1 (docs/plan-rediseno.md) — con el logo de Helpcom sirviéndose desde
  // `public/` (usado incluso en `/login`, sin sesión), cualquier archivo con extensión
  // (`.png`, `.svg`, etc.) queda excluido en general, no solo el favicon: bug real encontrado
  // al verificar R1 — sin este patrón, el propio logo del login quedaba atrapado por el guard
  // de sesión y Next intentaba "optimizarlo" a partir de la redirección HTML a `/login`
  // resultante ("The requested resource isn't a valid image").
  // "api/proxy": solo existe en modo desarrollo (next.config.mjs, docs/frontend.md) — es un
  // canal de infraestructura hacia el backend real, no una página; el propio backend ya exige
  // su Bearer token en cada llamada, así que no necesita (ni le sirve) el guard de sesión de
  // páginas del middleware.
  matcher: ["/((?!api/auth|api/proxy|_next/static|_next/image|favicon.ico|.*\\..*).*)"],
};
