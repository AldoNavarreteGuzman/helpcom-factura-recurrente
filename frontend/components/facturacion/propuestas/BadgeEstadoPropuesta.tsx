import { combinarClases } from "@/lib/estilos";
import type { EstadoPropuesta } from "@/types/dominio";

/**
 * Semántica de color (documentada en docs/frontend.md): `PENDIENTE` neutro (azul — falta
 * facturarse, nada anómalo); `PENDIENTE_UF` advertencia (ámbar — sin monto real todavía, ver
 * `lib/propuestas.ts::esMontoAusente`); `FACTURADA` éxito (verde — ya se facturó);
 * `ANULADA` inactivo (gris — no cuenta para nada). Reutilizable en el informe (10g).
 */
const ETIQUETAS: Record<EstadoPropuesta, string> = {
  PENDIENTE: "Pendiente",
  PENDIENTE_UF: "Pendiente UF",
  FACTURADA: "Facturada",
  ANULADA: "Anulada",
};

const CLASES: Record<EstadoPropuesta, string> = {
  PENDIENTE: "bg-estado-pendiente/10 text-estado-pendiente",
  PENDIENTE_UF: "bg-estado-sin-uf/10 text-estado-sin-uf",
  FACTURADA: "bg-estado-facturada/10 text-estado-facturada",
  ANULADA: "bg-estado-anulada/10 text-estado-anulada",
};

export interface PropiedadesBadgeEstadoPropuesta {
  estado: EstadoPropuesta;
  className?: string;
}

export function BadgeEstadoPropuesta({ estado, className }: PropiedadesBadgeEstadoPropuesta) {
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
