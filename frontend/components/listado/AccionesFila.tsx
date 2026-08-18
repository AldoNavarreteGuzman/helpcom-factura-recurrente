import { Pencil, Power, Trash2 } from "lucide-react";
import { combinarClases } from "@/lib/estilos";

export interface PropiedadesAccionesFila {
  puedeEditar: boolean;
  onEditar: () => void;
  /** Si se omite (junto con `activo`), no se renderiza el botón Activar/Desactivar (p. ej. acuerdos de precio, que no tienen ese concepto). */
  activo?: boolean;
  onActivarDesactivar?: () => void;
  /** Si se omite, no se renderiza el botón Eliminar (p. ej. proyectos, que hoy solo se editan/desactivan). */
  puedeEliminar?: boolean;
  onEliminar?: () => void;
  /** Por qué está deshabilitado, para el `title` del botón (accesibilidad + descubribilidad). */
  motivoDeshabilitado?: string;
}

const CLASE_BOTON =
  "flex min-h-11 items-center gap-1 rounded px-2 text-sm font-medium transition-colors " +
  "disabled:cursor-not-allowed disabled:opacity-40 " +
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-marca-azul";

/**
 * Acciones estándar por fila (editar, activar/desactivar, eliminar — las dos últimas
 * opcionales, ver props). `puedeEditar`/`puedeEliminar` deben venir de
 * `lib/useRoles.ts::useTieneAlgunRol` — se DESHABILITAN (no se ocultan) para que quede claro
 * que la acción existe pero el rol actual no la tiene permitida (arquitectura-tecnica.md §7:
 * el backend es la autoridad, la UI no debe ofrecer lo que va a dar 403).
 */
export function AccionesFila({
  puedeEditar,
  onEditar,
  activo,
  onActivarDesactivar,
  puedeEliminar,
  onEliminar,
  motivoDeshabilitado = "Requiere el rol ADMINISTRADOR.",
}: PropiedadesAccionesFila) {
  return (
    <div className="flex justify-end gap-3">
      <button
        type="button"
        onClick={onEditar}
        disabled={!puedeEditar}
        title={puedeEditar ? undefined : motivoDeshabilitado}
        className={combinarClases(CLASE_BOTON, "text-sutil hover:text-marca-azul")}
      >
        <Pencil className="h-3.5 w-3.5" aria-hidden />
        Editar
      </button>
      {onActivarDesactivar ? (
        <button
          type="button"
          onClick={onActivarDesactivar}
          disabled={!puedeEditar}
          title={puedeEditar ? undefined : motivoDeshabilitado}
          className={combinarClases(CLASE_BOTON, "text-sutil hover:text-marca-azul")}
        >
          <Power className="h-3.5 w-3.5" aria-hidden />
          {activo ? "Desactivar" : "Activar"}
        </button>
      ) : null}
      {onEliminar ? (
        <button
          type="button"
          onClick={onEliminar}
          disabled={!puedeEliminar}
          title={puedeEliminar ? undefined : motivoDeshabilitado}
          className={combinarClases(CLASE_BOTON, "text-estado-error hover:text-estado-error/80")}
        >
          <Trash2 className="h-3.5 w-3.5" aria-hidden />
          Eliminar
        </button>
      ) : null}
    </div>
  );
}
