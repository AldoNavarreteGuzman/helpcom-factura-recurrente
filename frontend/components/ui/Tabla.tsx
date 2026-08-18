import type { ReactNode } from "react";
import { combinarClases } from "@/lib/estilos";
import { EstadoVacio } from "./EstadoVacio";

export interface ColumnaTabla<T> {
  encabezado: string;
  renderizar: (fila: T) => ReactNode;
  /** Para columnas numéricas/monetarias — alinea a la derecha con cifras tabulares. Opcional: por defecto queda a la izquierda, igual que antes. */
  alineacion?: "derecha";
}

export interface PropiedadesTabla<T> {
  columnas: ColumnaTabla<T>[];
  filas: T[];
  obtenerClave: (fila: T) => string | number;
  mensajeVacio?: string;
}

/**
 * Tabla genérica: cada página define sus columnas con `renderizar`, sin duplicar el marcado
 * (docs/frontend.md §12). Responsividad transversal (docs/plan-rediseno.md R2 §7): por debajo
 * de `md`, la MISMA tabla (un solo árbol DOM — a propósito, ver nota abajo) se colapsa a
 * tarjetas apiladas solo con CSS (`display: block` en `tr`/`td` + una etiqueta generada con
 * `::before content: attr(data-label)`), nunca scroll horizontal con datos ocultos.
 *
 * Se descartó a propósito una segunda versión "de tarjetas" con su propio JSX en paralelo
 * (oculta con `hidden md:block` / `flex md:hidden`): en un navegador real ambas nunca
 * coexisten (el navegador respeta `display:none`), pero en las pruebas (jsdom, sin CSS
 * aplicado) las DOS quedarían "visibles" a la vez, duplicando cada botón/checkbox/badge de
 * cada fila y rompiendo `getByRole`/`getByText` (que exigen un único match) en prácticamente
 * todos los listados de la app. Con un solo árbol DOM que solo cambia de `display` por CSS,
 * las pruebas siguen viendo exactamente la misma estructura que antes de R2.
 */
export function Tabla<T>({
  columnas,
  filas,
  obtenerClave,
  mensajeVacio = "No hay datos para mostrar.",
}: PropiedadesTabla<T>) {
  if (filas.length === 0) {
    return (
      <div className="rounded border border-linea bg-white">
        <EstadoVacio titulo={mensajeVacio} />
      </div>
    );
  }

  return (
    <div className="overflow-x-auto rounded border border-linea">
      <table className="block w-full text-sm md:table md:min-w-full md:divide-y md:divide-linea">
        <thead className="hidden bg-fondo md:table-header-group">
          <tr>
            {columnas.map((columna) => (
              <th
                key={columna.encabezado}
                scope="col"
                className={combinarClases(
                  "px-4 py-2 text-xs font-semibold uppercase tracking-wide text-sutil",
                  columna.alineacion === "derecha" ? "text-right" : "text-left",
                )}
              >
                {columna.encabezado}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="block divide-y divide-linea bg-white md:table-row-group">
          {filas.map((fila) => (
            <tr
              key={obtenerClave(fila)}
              className="block space-y-1.5 p-4 md:table-row md:space-y-0 md:p-0 md:hover:bg-marca-azul-50"
            >
              {columnas.map((columna) => (
                <td
                  key={columna.encabezado}
                  data-label={columna.encabezado || undefined}
                  className={combinarClases(
                    "flex items-baseline justify-between gap-3 text-texto",
                    "before:font-medium before:text-sutil before:content-[attr(data-label)]",
                    "md:table-cell md:px-4 md:py-2 md:before:content-none",
                    columna.alineacion === "derecha"
                      ? "md:text-right md:tabular-nums"
                      : "md:text-left",
                    !columna.encabezado && "justify-end md:table-cell",
                  )}
                >
                  {columna.renderizar(fila)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
