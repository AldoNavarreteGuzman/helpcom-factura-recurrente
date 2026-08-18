import { forwardRef, type InputHTMLAttributes } from "react";
import { combinarClases } from "@/lib/estilos";

export interface PropiedadesInterruptor extends Omit<InputHTMLAttributes<HTMLInputElement>, "type"> {
  /** Texto visible junto al interruptor (p. ej. "Activo") — no reemplaza la etiqueta del campo. */
  etiqueta: string;
}

/**
 * Interruptor on/off (switch) para campos booleanos de estado — "Activo" en Cliente/Proyecto/
 * TipoServicio (docs/frontend.md §13). Sigue siendo, por dentro, un `<input type="checkbox">`
 * real: teclado (Espacio para alternar), envío de formulario, `checked`/`onChange` nativos —
 * nada de eso cambia. Solo gana `role="switch"` (para que un lector de pantalla lo anuncie como
 * interruptor, no como casilla) y el input en sí queda visualmente reemplazado por la pista +
 * el círculo deslizante (`sr-only`, no `hidden`: sigue en el árbol de accesibilidad y el `<label>`
 * que lo envuelve le reenvía el clic igual que a cualquier checkbox nativo).
 *
 * NO usar para checkboxes de selección múltiple (filtros de estado, filas a facturar) — esos
 * siguen siendo casillas normales, ver `docs/frontend.md` §13.
 */
export const Interruptor = forwardRef<HTMLInputElement, PropiedadesInterruptor>(function Interruptor(
  { etiqueta, id, className, ...resto },
  ref,
) {
  return (
    <label htmlFor={id} className="inline-flex min-h-11 cursor-pointer items-center gap-2 text-sm text-texto">
      <span className="relative inline-flex h-6 w-11 shrink-0 items-center">
        <input ref={ref} id={id} type="checkbox" role="switch" className={combinarClases("peer sr-only", className)} {...resto} />
        <span
          aria-hidden
          className="h-6 w-11 rounded-full bg-tenue transition-colors peer-checked:bg-marca-azul peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-marca-azul"
        />
        <span
          aria-hidden
          className="absolute left-0.5 h-5 w-5 rounded-full bg-white shadow-suave transition-transform peer-checked:translate-x-5"
        />
      </span>
      {etiqueta}
    </label>
  );
});
