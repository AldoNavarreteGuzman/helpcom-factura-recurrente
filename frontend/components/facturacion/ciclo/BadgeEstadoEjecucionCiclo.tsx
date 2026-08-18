import { combinarClases } from "@/lib/estilos";
import type { EstadoEjecucionCiclo } from "@/types/dominio";

/** Mismo lenguaje de color que `BadgeEstadoPropuesta`: verde=éxito, ámbar=advertencia, rojo=error. */
const ETIQUETAS: Record<EstadoEjecucionCiclo, string> = {
  EXITOSA: "Exitosa",
  CON_ADVERTENCIAS: "Con advertencias",
  ERROR: "Error",
};

const CLASES: Record<EstadoEjecucionCiclo, string> = {
  EXITOSA: "bg-estado-facturada/10 text-estado-facturada",
  CON_ADVERTENCIAS: "bg-estado-sin-uf/10 text-estado-sin-uf",
  ERROR: "bg-estado-error/10 text-estado-error",
};

export interface PropiedadesBadgeEstadoEjecucionCiclo {
  estado: EstadoEjecucionCiclo;
  className?: string;
}

export function BadgeEstadoEjecucionCiclo({
  estado,
  className,
}: PropiedadesBadgeEstadoEjecucionCiclo) {
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
