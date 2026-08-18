import { TarjetaDashboard } from "./TarjetaDashboard";
import { TarjetaEstadistica } from "@/components/ui/TarjetaEstadistica";
import { calcularKpiDescuentos } from "@/lib/dashboardCalculos";
import { formatearClp } from "@/lib/formato";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

export interface PropiedadesTarjetaKpiDescuentos {
  propuestas: PropuestaFacturacion[] | null;
  cargando: boolean;
  error: unknown;
}

/**
 * KPI de descuentos realizados (docs/frontend.md R9): los 3 tipos de acuerdo, con
 * `DESCUENTO_PORCENTAJE` + `DESCUENTO_MONTO` sumados en un total, y `PRECIO_PACTADO` en su
 * propia línea, en ámbar (mismo token `estado-sin-uf` que usa el resto de la app para "esto es
 * distinto, no lo mezcles") — decisión de negocio ya tomada: el pactado NO se suma al total,
 * porque conceptualmente reemplaza el precio base en vez de rebajarlo.
 */
export function TarjetaKpiDescuentos({
  propuestas,
  cargando,
  error,
}: PropiedadesTarjetaKpiDescuentos) {
  const kpi = propuestas ? calcularKpiDescuentos(propuestas) : null;

  return (
    <TarjetaDashboard
      titulo="Descuentos realizados"
      descripcion="Precio de lista menos lo efectivamente facturado — solo propuestas Pendiente/Facturada."
      cargando={cargando}
      error={error}
      alturaContenido="min-h-[160px]"
    >
      {kpi ? (
        <div className="space-y-3">
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <TarjetaEstadistica etiqueta="Descuento %" valor={formatearClp(kpi.porcentaje)} />
            <TarjetaEstadistica etiqueta="Descuento monto" valor={formatearClp(kpi.monto)} />
            <TarjetaEstadistica
              etiqueta="Total (% + monto)"
              valor={formatearClp(kpi.totalPorcentajeYMonto)}
              className="text-marca-azul"
            />
          </div>
          <div className="flex items-center justify-between rounded-md border border-estado-sin-uf/30 bg-estado-sin-uf/10 px-3 py-2 text-sm text-estado-sin-uf">
            <span>Precio pactado — aparte, no sumado al total de arriba</span>
            <span className="font-semibold tabular-nums">{formatearClp(kpi.pactado)}</span>
          </div>
        </div>
      ) : null}
    </TarjetaDashboard>
  );
}
