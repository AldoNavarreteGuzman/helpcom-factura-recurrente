"use client";

import { useSession } from "next-auth/react";
import { tieneAlgunRol, type Rol } from "./roles";

/**
 * Roles de la sesión actual en un Client Component (el equivalente para Server Components es
 * `const sesion = await auth(); sesion?.roles`, ver `docs/frontend.md` §2.1). Vive separado
 * de `lib/roles.ts` porque ese archivo es puro y lo importa también `lib/auth.ts`
 * (runtime Node/Edge); mezclar `useSession` ahí forzaría `"use client"` en un módulo que no
 * debe tenerlo.
 */
export function useRoles(): Rol[] {
  const { data: sesion } = useSession();
  return sesion?.roles ?? [];
}

/**
 * Para gatear acciones (crear/editar/eliminar) según el rol: el backend es la autoridad
 * final, pero la UI no debe ofrecer una acción que va a terminar en 403 — deshabilita o
 * oculta el control según el resultado de este hook.
 */
export function useTieneAlgunRol(rolesPermitidos: Rol[]): boolean {
  const roles = useRoles();
  return tieneAlgunRol(roles, rolesPermitidos);
}
