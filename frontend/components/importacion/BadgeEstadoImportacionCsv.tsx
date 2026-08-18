import { combinarClases } from "@/lib/estilos";
import type { EstadoImportacionCsv } from "@/types/dominio";

/** Mismo lenguaje de color que `BadgeEstadoEjecucionCiclo`: PROCESADA≈EXITOSA, PARCIAL≈CON_ADVERTENCIAS, RECHAZADA≈ERROR. */
const ETIQUETAS: Record<EstadoImportacionCsv, string> = {
  PROCESADA: "Procesada",
  PARCIAL: "Parcial",
  RECHAZADA: "Rechazada",
};

const CLASES: Record<EstadoImportacionCsv, string> = {
  PROCESADA: "bg-estado-facturada/10 text-estado-facturada",
  PARCIAL: "bg-estado-sin-uf/10 text-estado-sin-uf",
  RECHAZADA: "bg-estado-error/10 text-estado-error",
};

export interface PropiedadesBadgeEstadoImportacionCsv {
  estado: EstadoImportacionCsv;
  className?: string;
}

export function BadgeEstadoImportacionCsv({
  estado,
  className,
}: PropiedadesBadgeEstadoImportacionCsv) {
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
