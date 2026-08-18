import { BadgeEstadoPropuesta } from "@/components/facturacion/propuestas/BadgeEstadoPropuesta";
import { TarjetaDashboard } from "./TarjetaDashboard";
import { calcularPorCliente } from "@/lib/dashboardCalculos";
import { formatearClp } from "@/lib/formato";
import type { EstadoPropuesta } from "@/types/dominio";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

export interface PropiedadesTarjetaPorCliente {
  propuestas: PropuestaFacturacion[] | null;
  cargando: boolean;
  error: unknown;
}

const ESTADOS: EstadoPropuesta[] = ["PENDIENTE", "PENDIENTE_UF", "FACTURADA", "ANULADA"];

/**
 * Neto calculable por cliente (docs/frontend.md R9), con el desglose de cantidad por estado
 * como contexto (incluye `PENDIENTE_UF`/`ANULADA` en el conteo, aunque no aporten al monto) —
 * con solo 2 clientes reales hoy, una lista con más detalle por fila lee mejor que un gráfico
 * disperso.
 */
export function TarjetaPorCliente({ propuestas, cargando, error }: PropiedadesTarjetaPorCliente) {
  const filas = propuestas ? calcularPorCliente(propuestas) : null;
  const sinDatos = filas != null && filas.length === 0;

  return (
    <TarjetaDashboard
      titulo="Por cliente"
      descripcion="Neto calculable (Pendiente + Facturada) y cantidad de propuestas por estado."
      cargando={cargando}
      error={error}
      alturaContenido="min-h-[220px]"
    >
      {sinDatos ? (
        <div className="flex flex-1 items-center justify-center text-sm text-sutil">
          Todavía no hay clientes con propuestas.
        </div>
      ) : filas ? (
        <ul className="space-y-3">
          {filas.map((fila) => (
            <li key={fila.clienteId} className="rounded-md border border-linea p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <span className="font-medium text-tinta">{fila.nombre}</span>
                <span className="text-lg font-semibold tabular-nums text-marca-azul">
                  {formatearClp(fila.netoClp)}
                </span>
              </div>
              <div className="mt-2 flex flex-wrap gap-2">
                {ESTADOS.filter((estado) => fila.cantidadPorEstado[estado] > 0).map((estado) => (
                  <span key={estado} className="inline-flex items-center gap-1">
                    <BadgeEstadoPropuesta estado={estado} />
                    <span className="text-xs text-sutil">×{fila.cantidadPorEstado[estado]}</span>
                  </span>
                ))}
              </div>
            </li>
          ))}
        </ul>
      ) : null}
    </TarjetaDashboard>
  );
}
