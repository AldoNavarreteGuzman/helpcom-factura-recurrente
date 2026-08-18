import {
  BarChart3,
  Briefcase,
  LayoutDashboard,
  Receipt,
  Upload,
  Users,
  type LucideIcon,
} from "lucide-react";
import { tieneAlgunRol, type Rol } from "./roles";

export interface EnlaceNav {
  href: string;
  etiqueta: string;
  /** Vacío = visible para cualquier rol autenticado. */
  rolesPermitidos: Rol[];
  /**
   * Opcional para no forzar un ícono en fixtures de prueba que no lo necesitan
   * (`navegacion.test.ts`) — `BarraLateral`/`BarraInferior` siempre lo reciben en los datos
   * reales de `ENLACES_NAV`.
   */
  icono?: LucideIcon;
}

/**
 * Regla provisional (arquitectura-tecnica.md §7): todos los módulos son visibles para
 * ADMINISTRADOR y OPERADOR por ahora; las acciones sensibles dentro de cada pantalla se
 * restringirán una por una cuando existan. `rolesPermitidos` ya queda listo para eso.
 *
 * "Panel" (antes "Inicio") es el futuro dashboard de docs/plan-rediseno.md R9 — en esta etapa
 * (R1) sigue apuntando al mismo placeholder de `/` sin construir el dashboard todavía.
 */
export const ENLACES_NAV: EnlaceNav[] = [
  { href: "/", etiqueta: "Panel", rolesPermitidos: ["ADMINISTRADOR", "OPERADOR"], icono: LayoutDashboard },
  { href: "/clientes", etiqueta: "Clientes", rolesPermitidos: ["ADMINISTRADOR", "OPERADOR"], icono: Users },
  { href: "/proyectos", etiqueta: "Proyectos", rolesPermitidos: ["ADMINISTRADOR", "OPERADOR"], icono: Briefcase },
  {
    href: "/facturacion",
    etiqueta: "Facturación",
    rolesPermitidos: ["ADMINISTRADOR", "OPERADOR"],
    icono: Receipt,
  },
  {
    href: "/importacion",
    etiqueta: "Importación",
    rolesPermitidos: ["ADMINISTRADOR", "OPERADOR"],
    icono: Upload,
  },
  { href: "/informes", etiqueta: "Informes", rolesPermitidos: ["ADMINISTRADOR", "OPERADOR"], icono: BarChart3 },
];

export function enlacesVisibles(enlaces: EnlaceNav[], rolesUsuario: Rol[]): EnlaceNav[] {
  return enlaces.filter((enlace) => tieneAlgunRol(rolesUsuario, enlace.rolesPermitidos));
}
