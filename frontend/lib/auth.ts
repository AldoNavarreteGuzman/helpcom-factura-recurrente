import NextAuth from "next-auth";
import type { JWT } from "next-auth/jwt";
import Keycloak from "next-auth/providers/keycloak";
import { authConfig } from "./auth.config";
import { decodificarPayloadJwt } from "./jwt";
import { extraerRolesValidos, type Rol } from "./roles";

/** Margen de seguridad: se renueva el access token un poco antes de que expire realmente. */
const MARGEN_EXPIRACION_MS = 30_000;

/**
 * `AUTH_KEYCLOAK_ISSUER_INTERNO` es opcional y solo hace falta quedar detrás de un despliegue
 * en contenedores (docs/despliegue.md D2): ahí, `AUTH_KEYCLOAK_ISSUER` público (el que arma el
 * link al que se redirige al NAVEGADOR) apunta al puerto publicado hacia afuera, que el
 * propio contenedor del frontend no puede alcanzar consigo mismo bajo ese nombre — así que las
 * llamadas del SERVIDOR Node (descubrimiento OIDC, intercambio/renovación de token, JWKS) usan
 * en su lugar el nombre interno de Compose. En desarrollo local de un único desarrollador
 * (docs/frontend.md §1.1) no hace falta definirla: cae de vuelta al mismo valor público, que
 * ahí es igual de alcanzable desde el navegador y desde el propio proceso Node.
 */
const EMISOR_KEYCLOAK_INTERNO = process.env.AUTH_KEYCLOAK_ISSUER_INTERNO ?? process.env.AUTH_KEYCLOAK_ISSUER;

function extraerRolesDeAccessToken(accessToken: string): Rol[] {
  const payload = decodificarPayloadJwt(accessToken);
  const realmAccess = payload.realm_access as { roles?: unknown } | undefined;
  return extraerRolesValidos(realmAccess?.roles);
}

/**
 * Pide un access token nuevo con el refresh token contra el endpoint de token de Keycloak
 * (arquitectura-tecnica.md §7). Si falla (refresh token vencido o revocado), deja
 * `error: "RefreshAccessTokenError"` en el token para que `clienteApi` y el resto de la app
 * lo detecten y fuercen un re-login, en vez de seguir usando un access token inválido.
 */
async function renovarAccessToken(token: JWT): Promise<JWT> {
  if (!token.refreshToken) {
    return { ...token, error: "RefreshAccessTokenError" };
  }
  try {
    const url = `${EMISOR_KEYCLOAK_INTERNO}/protocol/openid-connect/token`;
    const respuesta = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "refresh_token",
        client_id: process.env.AUTH_KEYCLOAK_ID ?? "",
        client_secret: process.env.AUTH_KEYCLOAK_SECRET ?? "",
        refresh_token: token.refreshToken,
      }),
    });
    const datosRenovados = (await respuesta.json()) as {
      access_token?: string;
      refresh_token?: string;
      expires_in?: number;
      error?: string;
    };
    if (!respuesta.ok || !datosRenovados.access_token) {
      throw new Error(datosRenovados.error ?? "No se pudo renovar el access token.");
    }

    return {
      ...token,
      accessToken: datosRenovados.access_token,
      accessTokenExpires: Date.now() + (datosRenovados.expires_in ?? 60) * 1000,
      refreshToken: datosRenovados.refresh_token ?? token.refreshToken,
      roles: extraerRolesDeAccessToken(datosRenovados.access_token),
      error: undefined,
    };
  } catch (error) {
    console.error("No se pudo renovar el access token de Keycloak:", error);
    return { ...token, error: "RefreshAccessTokenError" };
  }
}

export const {
  handlers: { GET, POST },
  auth,
  signIn,
  signOut,
} = NextAuth({
  ...authConfig,
  providers: [
    Keycloak({
      clientId: process.env.AUTH_KEYCLOAK_ID,
      clientSecret: process.env.AUTH_KEYCLOAK_SECRET,
      // El descubrimiento OIDC (y de ahí, token/userinfo/jwks) usa el emisor INTERNO — ver la
      // nota de EMISOR_KEYCLOAK_INTERNO más arriba. `authorization` se pisa aparte con el
      // emisor PÚBLICO porque es el único endpoint al que se redirige al navegador
      // directamente, no una llamada del servidor.
      issuer: EMISOR_KEYCLOAK_INTERNO,
      authorization: `${process.env.AUTH_KEYCLOAK_ISSUER}/protocol/openid-connect/auth`,
    }),
  ],
  session: {
    strategy: "jwt",
  },
  callbacks: {
    ...authConfig.callbacks,
    async jwt({ token, account }) {
      // Login inicial: Auth.js entrega `account` con los tokens crudos de Keycloak.
      if (account?.access_token) {
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          accessTokenExpires: account.expires_at ? account.expires_at * 1000 : Date.now() + 55_000,
          roles: extraerRolesDeAccessToken(account.access_token),
          error: undefined,
        };
      }

      const expira = token.accessTokenExpires ?? 0;
      if (Date.now() < expira - MARGEN_EXPIRACION_MS) {
        return token;
      }

      return renovarAccessToken(token);
    },
    async session({ session, token }) {
      session.accessToken = token.accessToken;
      session.roles = token.roles ?? [];
      session.error = token.error;
      return session;
    },
  },
});
