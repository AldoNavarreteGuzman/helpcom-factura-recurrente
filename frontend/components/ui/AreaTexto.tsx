import { forwardRef, type TextareaHTMLAttributes } from "react";
import { combinarClases } from "@/lib/estilos";

export interface PropiedadesAreaTexto extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalida?: boolean;
}

/** Textarea base, mismo lenguaje visual que {@link Entrada}. */
export const AreaTexto = forwardRef<HTMLTextAreaElement, PropiedadesAreaTexto>(function AreaTexto(
  { invalida = false, className, rows = 3, ...resto },
  ref,
) {
  return (
    <textarea
      ref={ref}
      rows={rows}
      aria-invalid={invalida}
      className={combinarClases(
        "rounded border px-3 py-2 text-sm text-tinta outline-none transition-colors",
        "placeholder:text-tenue focus:ring-2 focus:ring-offset-0 disabled:cursor-not-allowed disabled:bg-fondo",
        invalida
          ? "border-estado-error focus:border-estado-error focus:ring-estado-error/20"
          : "border-linea focus:border-marca-azul focus:ring-marca-azul/20",
        className,
      )}
      {...resto}
    />
  );
});
