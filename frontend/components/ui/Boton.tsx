import { forwardRef, type ButtonHTMLAttributes } from "react";
import { combinarClases } from "@/lib/estilos";

export type VarianteBoton = "primario" | "secundario" | "peligro";

export interface PropiedadesBoton extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: VarianteBoton;
  cargando?: boolean;
}

const CLASES_BASE =
  "inline-flex min-h-11 items-center justify-center gap-2 rounded px-4 py-2 text-sm font-medium " +
  "transition-colors disabled:cursor-not-allowed disabled:opacity-60 " +
  "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-marca-azul focus-visible:ring-offset-2";

const CLASES_POR_VARIANTE: Record<VarianteBoton, string> = {
  primario: "bg-marca-azul text-white hover:bg-marca-azul-700",
  secundario: "border border-marca-azul bg-white text-marca-azul hover:bg-marca-azul-50",
  peligro: "bg-estado-error text-white hover:bg-estado-error/90",
};

/** Botón base reutilizable (arquitectura de componentes documentada en docs/frontend.md). */
export const Boton = forwardRef<HTMLButtonElement, PropiedadesBoton>(function Boton(
  {
    variante = "primario",
    cargando = false,
    disabled,
    className,
    children,
    type = "button",
    ...resto
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || cargando}
      className={combinarClases(CLASES_BASE, CLASES_POR_VARIANTE[variante], className)}
      aria-busy={cargando}
      {...resto}
    >
      {cargando && (
        <span
          className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent"
          aria-hidden
        />
      )}
      {children}
    </button>
  );
});
