import type { Session } from "next-auth";
import type { NextAuthConfig } from "next-auth";
import { esRutaPublica } from "./rutas";

/**
 * Decisión de autorización del middleware, separada del callback de Auth.js para poder
 * probarla sin levantar `NextAuth(...)`: rutas públicas siempre pasan; el resto exige una
 * sesión con usuario. Si retorna `false`, Auth.js redirige a `pages.signIn` conservando la
 * URL original (`callbackUrl`) — así se cumple "si no hay sesión, redirige a login".
 */
export function estaAutorizado(pathname: string, sesion: Session | null): boolean {
  if (esRutaPublica(pathname)) {
    return true;
  }
  return Boolean(sesion?.user);
}

/**
 * Configuración "liviana" de Auth.js: sin el provider de Keycloak (necesita el client
 * secret) ni los callbacks `jwt`/`session` que renuevan el token contra Keycloak — nada de
 * eso corre en el runtime Edge que usa el middleware. `lib/auth.ts` la extiende con eso para
 * las Route Handlers y Server Components, que sí corren en Node.
 */
export const authConfig = {
  pages: {
    signIn: "/login",
  },
  providers: [],
  callbacks: {
    authorized({ auth, request }) {
      return estaAutorizado(request.nextUrl.pathname, auth);
    },
  },
  trustHost: true,
} satisfies NextAuthConfig;
