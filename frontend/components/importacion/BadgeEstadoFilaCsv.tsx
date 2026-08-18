import { combinarClases } from "@/lib/estilos";
import type { EstadoFilaCsv } from "@/types/dominio";

/**
 * Mismo lenguaje de color que `BadgeEstadoPropuesta`/`BadgeEstadoEjecucionCiclo`: verde=éxito,
 * ámbar=advertencia (se importa igual), rojo=error (no se importa).
 */
const ETIQUETAS: Record<EstadoFilaCsv, string> = {
  OK: "OK",
  ADVERTENCIA: "Advertencia",
  ERROR: "Error",
};

const CLASES: Record<EstadoFilaCsv, string> = {
  OK: "bg-estado-facturada/10 text-estado-facturada",
  ADVERTENCIA: "bg-estado-sin-uf/10 text-estado-sin-uf",
  ERROR: "bg-estado-error/10 text-estado-error",
};

export interface PropiedadesBadgeEstadoFilaCsv {
  estado: EstadoFilaCsv;
  className?: string;
}

export function BadgeEstadoFilaCsv({ estado, className }: PropiedadesBadgeEstadoFilaCsv) {
  return (
    <span
      className={combinarClases(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        CLASES[estado],
        className,
      )}
    >
      {ETIQUETAS[estado]}
    </span>
  );
}
