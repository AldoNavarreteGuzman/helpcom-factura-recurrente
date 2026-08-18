"use client";

import { ETIQUETAS_PERIODICIDAD } from "@/lib/etiquetas";
import { formatearFecha, formatearMontoEnMoneda } from "@/lib/formato";
import { useProyectoDetalle } from "./ContextoProyectoDetalle";

/**
 * Pestaña "Datos" del detalle de proyecto (docs/frontend.md §15) — solo lectura; el botón
 * "Editar" vive en la cabecera compartida (`LayoutDetalleProyecto`), visible también desde
 * "Descuentos". El proyecto viene de `useProyectoDetalle()` (compartido con la cabecera), no de
 * un fetch propio — así ambos quedan sincronizados tras una edición.
 */
export function DatosProyecto() {
  const { proyecto } = useProyectoDetalle();

  const campos: Array<[string, string]> = [
    ["Cliente", proyecto.clienteRazonSocial],
    ["Tipo de servicio", proyecto.tipoServicioNombre ?? "—"],
    ["Código", proyecto.codigo ?? "—"],
    ["Descripción", proyecto.descripcion ?? "—"],
    ["Precio base neto", formatearMontoEnMoneda(proyecto.precioBaseNeto, proyecto.monedaPrecio)],
    ["Periodicidad", ETIQUETAS_PERIODICIDAD[proyecto.periodicidad]],
    ["Día de facturación", String(proyecto.diaFacturacion)],
    ["Fecha de inicio", formatearFecha(proyecto.fechaInicio)],
    ["Fecha de término", proyecto.fechaTermino ? formatearFecha(proyecto.fechaTermino) : "—"],
  ];

  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-4 rounded border border-linea bg-white p-4 sm:grid-cols-2">
      {campos.map(([etiqueta, valor]) => (
        <div key={etiqueta}>
          <dt className="text-xs font-semibold uppercase tracking-wide text-sutil">{etiqueta}</dt>
          <dd className="mt-0.5 text-sm text-texto">{valor}</dd>
        </div>
      ))}
    </dl>
  );
}
