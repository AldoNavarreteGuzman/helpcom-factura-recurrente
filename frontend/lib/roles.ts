/**
 * Roles de negocio (arquitectura-tecnica.md §7): los mismos que el backend mapea desde el
 * claim `realm_access.roles` del token de Keycloak. Se validan aquí para no confiar
 * ciegamente en el contenido del token al construirlo en `lib/auth.ts`.
 */
export const ROLES = ["ADMINISTRADOR", "OPERADOR"] as const;

export type Rol = (typeof ROLES)[number];

export function esRolValido(valor: string): valor is Rol {
  return (ROLES as readonly string[]).includes(valor);
}

/** Filtra una lista sin tipar (p. ej. `realm_access.roles` del token) a solo roles conocidos. */
export function extraerRolesValidos(valores: unknown): Rol[] {
  if (!Array.isArray(valores)) {
    return [];
  }
  return valores.filter((valor): valor is Rol => typeof valor === "string" && esRolValido(valor));
}

/** {@code rolesPermitidos} vacío significa "sin restricción": visible para cualquier rol. */
export function tieneAlgunRol(rolesUsuario: Rol[], rolesPermitidos: Rol[]): boolean {
  if (rolesPermitidos.length === 0) {
    return true;
  }
  return rolesPermitidos.some((rol) => rolesUsuario.includes(rol));
}
