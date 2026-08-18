import { combinarClases } from "@/lib/estilos";

export interface PropiedadesTarjetaEstadistica {
  etiqueta: string;
  valor: string | number;
  className?: string;
}

/**
 * Tarjeta simple de una estadística (etiqueta + valor destacado) — extraída tras el segundo
 * uso (resumen de Importación CSV, totales del Informe de facturación) para no duplicar el
 * mismo bloque de marcado en cada pantalla que necesita mostrar números resumidos.
 */
export function TarjetaEstadistica({ etiqueta, valor, className }: PropiedadesTarjetaEstadistica) {
  return (
    <div className="rounded border border-linea bg-white px-3 py-2 shadow-suave">
      <div className="text-xs text-sutil">{etiqueta}</div>
      <div className={combinarClases("text-lg font-semibold tabular-nums", className)}>{valor}</div>
    </div>
  );
}
