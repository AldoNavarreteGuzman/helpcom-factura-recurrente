import { Check } from "lucide-react";
import { forwardRef, type InputHTMLAttributes } from "react";
import { combinarClases } from "@/lib/estilos";

export interface PropiedadesCasilla extends Omit<InputHTMLAttributes<HTMLInputElement>, "type"> {
  /**
   * Texto visible junto a la casilla (p. ej. el nombre del estado en el filtro de Informes). Se
   * omite cuando la casilla ya tiene su nombre accesible por `aria-label` (p. ej. una casilla
   * por fila en un listado, `NuevaFactura`) — en ese caso solo se muestra el cuadro.
   */
  etiqueta?: string;
}

/**
 * Casilla de selección múltiple re-estilizada — DISTINTA del interruptor
 * (`components/ui/Interruptor.tsx`, para campos on/off de una sola entidad como "Activo"): esta
 * sigue siendo una CASILLA, semántica y visualmente (`role="checkbox"` implícito, sin
 * `role="switch"`), para filtros de varios valores a la vez (docs/frontend.md §13.3) o selección
 * de filas. Por dentro sigue siendo un `<input type="checkbox">` real: mismo `checked`/
 * `onChange`, mismo `disabled`, mismo teclado (Espacio) — solo se le reemplaza el cuadrado
 * nativo por uno propio (borde `linea` sin marcar, relleno `marca.azul` con un tick blanco de
 * lucide al marcar), igual que ya se hizo con `Interruptor`.
 *
 * `title` se aplica también al `<label>` que envuelve la casilla (no solo al `<input>`): el
 * input real queda `sr-only` (visualmente oculto, pero presente en el árbol de accesibilidad y
 * recibe el clic que le reenvía el `<label>`), así que un `title` solo en el input nunca se
 * vería al pasar el mouse sobre el cuadro visible — hay que ponerlo también donde el usuario de
 * verdad posiciona el cursor (`NuevaFactura` lo usa para explicar por qué una fila no es
 * seleccionable, mismo patrón que `AccionesFila`).
 */
export const Casilla = forwardRef<HTMLInputElement, PropiedadesCasilla>(function Casilla(
  { etiqueta, id, className, title, ...resto },
  ref,
) {
  const cuadro = (
    <span className="relative inline-flex h-5 w-5 shrink-0 items-center justify-center">
      <input
        ref={ref}
        id={id}
        type="checkbox"
        title={title}
        className={combinarClases("peer sr-only", className)}
        {...resto}
      />
      <span
        aria-hidden
        className={combinarClases(
          "h-5 w-5 rounded border border-linea bg-white transition-colors",
          "peer-checked:border-marca-azul peer-checked:bg-marca-azul",
          "peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-marca-azul",
          "peer-disabled:cursor-not-allowed peer-disabled:opacity-50",
        )}
      />
      <Check
        aria-hidden
        className="pointer-events-none absolute h-3.5 w-3.5 text-white opacity-0 transition-opacity peer-checked:opacity-100"
      />
    </span>
  );

  if (!etiqueta) {
    return (
      <label title={title} className="inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center">
        {cuadro}
      </label>
    );
  }

  return (
    <label htmlFor={id} title={title} className="inline-flex min-h-11 cursor-pointer items-center gap-2 text-sm text-texto">
      {cuadro}
      {etiqueta}
    </label>
  );
});
