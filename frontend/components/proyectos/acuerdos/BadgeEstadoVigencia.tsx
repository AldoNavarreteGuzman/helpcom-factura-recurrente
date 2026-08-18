import { combinarClases } from "@/lib/estilos";
import type { EstadoVigencia } from "@/lib/vigencia";

/** Mismo lenguaje de badge que `BadgeEstadoPropuesta`/`BadgeActivo` (docs/frontend.md §12/§14). */
const ETIQUETAS: Record<EstadoVigencia, string> = {
  VIGENTE: "Vigente",
  FUTURO: "Futuro",
  PASADO: "Pasado",
};

const CLASES: Record<EstadoVigencia, string> = {
  VIGENTE: "bg-estado-facturada/10 text-estado-facturada",
  FUTURO: "bg-estado-pendiente/10 text-estado-pendiente",
  PASADO: "bg-estado-anulada/10 text-estado-anulada",
};

export interface PropiedadesBadgeEstadoVigencia {
  estado: EstadoVigencia;
  className?: string;
}

export function BadgeEstadoVigencia({ estado, className }: PropiedadesBadgeEstadoVigencia) {
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
