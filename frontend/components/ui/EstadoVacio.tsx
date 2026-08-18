import type { LucideIcon } from "lucide-react";
import { Inbox } from "lucide-react";
import type { ReactNode } from "react";

export interface PropiedadesEstadoVacio {
  icono?: LucideIcon;
  titulo: string;
  descripcion?: string;
  /** Típicamente un {@link import("./Boton").Boton} que invita a crear el primer registro. */
  accion?: ReactNode;
}

/**
 * Estado vacío reutilizable (sistema de diseño "Confianza", docs/plan-rediseno.md R2): ícono +
 * título + descripción + acción opcional, en vez de una pantalla en blanco. `Tabla` usa una
 * versión mínima (solo ícono + `mensajeVacio` como título) para su fila vacía; las pantallas
 * que quieran el patrón completo (con descripción y una acción, p. ej. "+ Nuevo cliente") pueden
 * usar este componente directamente donde corresponda.
 */
export function EstadoVacio({ icono: Icono = Inbox, titulo, descripcion, accion }: PropiedadesEstadoVacio) {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-12 text-center">
      <Icono className="h-10 w-10 text-tenue" aria-hidden />
      <p className="text-sm font-medium text-tinta">{titulo}</p>
      {descripcion && <p className="max-w-sm text-sm text-sutil">{descripcion}</p>}
      {accion && <div className="mt-2">{accion}</div>}
    </div>
  );
}
