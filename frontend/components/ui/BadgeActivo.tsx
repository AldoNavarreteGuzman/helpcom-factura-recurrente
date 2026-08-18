import { combinarClases } from "@/lib/estilos";

export interface PropiedadesBadgeActivo {
  activo: boolean;
  className?: string;
}

/**
 * Badge para el campo "activo" de cualquier entidad (Cliente, Tipo de servicio, Proyecto...) —
 * mismo lenguaje visual que los badges de estado de Propuesta/Ciclo/CSV (docs/frontend.md §12):
 * `estado.facturada` (verde) para "Activo", `estado.anulada` (gris) para "Inactivo". Reemplaza
 * el texto de color plano (`text-emerald-700`/`text-slate-500`) que usaban `ListaClientes` y
 * `ListaTiposServicio` antes de R3.
 */
export function BadgeActivo({ activo, className }: PropiedadesBadgeActivo) {
  return (
    <span
      className={combinarClases(
        "inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium",
        activo ? "bg-estado-facturada/10 text-estado-facturada" : "bg-estado-anulada/10 text-estado-anulada",
        className,
      )}
    >
      {activo ? "Activo" : "Inactivo"}
    </span>
  );
}
