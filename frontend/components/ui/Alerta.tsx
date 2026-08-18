import { AlertCircle, AlertTriangle, Info } from "lucide-react";
import type { ReactNode } from "react";
import { combinarClases } from "@/lib/estilos";

export type VarianteAlerta = "error" | "advertencia" | "info";

export interface PropiedadesAlerta {
  variante?: VarianteAlerta;
  children: ReactNode;
  className?: string;
}

const ICONO_POR_VARIANTE: Record<VarianteAlerta, typeof AlertCircle> = {
  error: AlertCircle,
  advertencia: AlertTriangle,
  info: Info,
};

const CLASES_POR_VARIANTE: Record<VarianteAlerta, string> = {
  error: "border-estado-error/30 bg-estado-error/10 text-estado-error",
  advertencia: "border-estado-sin-uf/30 bg-estado-sin-uf/10 text-estado-sin-uf",
  info: "border-marca-azul/30 bg-marca-azul-50 text-marca-azul-700",
};

/**
 * Banner inline reutilizable (a diferencia de un toast, queda visible hasta que la condición
 * que lo generó cambia) — reemplaza los banners de error `problem+json` que antes duplicaban
 * este mismo marcado en `PanelListado`/`FormularioDialogo` (docs/frontend.md §12).
 */
export function Alerta({ variante = "error", children, className }: PropiedadesAlerta) {
  const Icono = ICONO_POR_VARIANTE[variante];
  return (
    <div
      role="alert"
      className={combinarClases(
        "flex items-start gap-2 rounded border px-3 py-2 text-sm",
        CLASES_POR_VARIANTE[variante],
        className,
      )}
    >
      <Icono className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <span>{children}</span>
    </div>
  );
}
