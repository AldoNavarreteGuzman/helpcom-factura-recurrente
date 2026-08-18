import { ChevronDown } from "lucide-react";
import { forwardRef, type SelectHTMLAttributes } from "react";
import { combinarClases } from "@/lib/estilos";

export type PropiedadesSeleccion = SelectHTMLAttributes<HTMLSelectElement>;

/**
 * Select base, con el mismo lenguaje visual que {@link Entrada} — sigue siendo un `<select>`
 * nativo (navegación por teclado, buscar-al-escribir, todo el comportamiento accesible de
 * fábrica del sistema operativo/navegador); solo se le oculta la flecha nativa
 * (`appearance-none`) para dibujar la propia con un ícono de lucide encima, puramente
 * decorativo (`aria-hidden`, `pointer-events-none` para no interceptar el clic).
 * `className` (usado por varias pantallas para controlar el ancho, p. ej. `"w-full"`) se aplica
 * al contenedor — el `<select>` interno siempre ocupa el 100% de ese contenedor.
 */
export const Seleccion = forwardRef<HTMLSelectElement, PropiedadesSeleccion>(function Seleccion(
  { className, children, ...resto },
  ref,
) {
  return (
    <div className={combinarClases("relative inline-block", className)}>
      <select
        ref={ref}
        className={combinarClases(
          "min-h-11 w-full appearance-none rounded border border-linea bg-white py-2 pl-3 pr-9 text-sm text-tinta outline-none transition-colors",
          "focus:border-marca-azul focus:ring-2 focus:ring-marca-azul/20 disabled:cursor-not-allowed disabled:bg-fondo",
        )}
        {...resto}
      >
        {children}
      </select>
      <ChevronDown
        className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-sutil"
        aria-hidden
      />
    </div>
  );
});
