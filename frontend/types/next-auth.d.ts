import type { DefaultSession } from "next-auth";
import type { Rol } from "@/lib/roles";

/**
 * Amplía los tipos de Auth.js con lo que agregan los callbacks `jwt`/`session` de
 * `lib/auth.ts`: el access token para llamar al backend, los roles de negocio y el error de
 * renovación (para forzar re-login cuando el refresh token falla).
 */
declare module "next-auth" {
  interface Session extends DefaultSession {
    accessToken?: string;
    roles: Rol[];
    error?: "RefreshAccessTokenError";
  }
}

declare module "next-auth/jwt" {
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    /** Instante (epoch ms) en que expira `accessToken`. */
    accessTokenExpires?: number;
    roles: Rol[];
    error?: "RefreshAccessTokenError";
  }
}
