import { forwardRef, type InputHTMLAttributes } from "react";
import { combinarClases } from "@/lib/estilos";

export interface PropiedadesEntrada extends InputHTMLAttributes<HTMLInputElement> {
  invalida?: boolean;
}

/** Input de texto base, pensado para usarse dentro de {@link CampoFormulario}. */
export const Entrada = forwardRef<HTMLInputElement, PropiedadesEntrada>(function Entrada(
  { invalida = false, className, ...resto },
  ref,
) {
  return (
    <input
      ref={ref}
      aria-invalid={invalida}
      className={combinarClases(
        "min-h-11 rounded border px-3 py-2 text-sm text-tinta outline-none transition-colors",
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
