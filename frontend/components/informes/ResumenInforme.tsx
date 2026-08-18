import Link from "next/link";
import { BadgeEstadoPropuesta } from "@/components/facturacion/propuestas/BadgeEstadoPropuesta";
import { Alerta } from "@/components/ui/Alerta";
import { TarjetaEstadistica } from "@/components/ui/TarjetaEstadistica";
import { formatearClp } from "@/lib/formato";
import { construirQueryString } from "@/lib/query";
import type { EstadoPropuesta } from "@/types/dominio";
import type { InformeFacturacionResumen } from "@/types/informeFacturacion";

const ESTADOS: EstadoPropuesta[] = ["PENDIENTE", "PENDIENTE_UF", "FACTURADA", "ANULADA"];

export interface FiltrosParaEnlacePropuestas {
  periodoAnio?: number;
  periodoMes?: number;
  clienteId?: number | null;
  origen?: string;
}

export interface PropiedadesResumenInforme {
  resumen: InformeFacturacionResumen;
  /** Subconjunto de los filtros activos que `ListaPropuestas` sabe interpretar (no soporta rango de períodos ni `facturada`). */
  filtrosParaPropuestas: FiltrosParaEnlacePropuestas;
}

/**
 * Resumen del informe (arquitectura-tecnica.md §11): totales de lo FACTURABLE (con la
 * exclusión explícita a la vista, nunca en letra chica), desglose de cantidades por estado
 * (mismo lenguaje visual que Propuestas vía `BadgeEstadoPropuesta`), y la cantidad de
 * `PENDIENTE_UF` destacada como advertencia — nunca invisible ni sumada como si fuera 0.
 */
export function ResumenInforme({ resumen, filtrosParaPropuestas }: PropiedadesResumenInforme) {
  const enlacePendientesUf =
    "/facturacion" +
    construirQueryString({
      estado: "PENDIENTE_UF",
      periodoAnio: filtrosParaPropuestas.periodoAnio,
      periodoMes: filtrosParaPropuestas.periodoMes,
      clienteId: filtrosParaPropuestas.clienteId,
      origen: filtrosParaPropuestas.origen,
    });

  return (
    <div className="space-y-4">
      <div className="space-y-2 rounded-md border border-linea p-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-sm font-semibold text-tinta">Totales</h2>
          <span className="text-xs text-sutil">
            Solo Pendiente + Facturada — excluye Pendiente UF y Anulada
          </span>
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <TarjetaEstadistica etiqueta="Neto" valor={formatearClp(resumen.netoClp)} className="text-tinta" />
          <TarjetaEstadistica etiqueta="IVA" valor={formatearClp(resumen.ivaClp)} className="text-tinta" />
          <TarjetaEstadistica
            etiqueta="Total"
            valor={formatearClp(resumen.totalClp)}
            className="text-estado-facturada"
          />
        </div>
      </div>

      <div className="space-y-2 rounded-md border border-linea p-4">
        <h2 className="text-sm font-semibold text-tinta">
          Cantidad de propuestas por estado ({resumen.cantidadTotal} en total)
        </h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {ESTADOS.map((estado) => (
            <div
              key={estado}
              className="flex items-center justify-between rounded-md border border-linea px-3 py-2"
            >
              <BadgeEstadoPropuesta estado={estado} />
              <span className="text-lg font-semibold text-tinta">
                {resumen.cantidadPorEstado[estado] ?? 0}
              </span>
            </div>
          ))}
        </div>
      </div>

      {resumen.cantidadPendienteUf > 0 ? (
        <Alerta variante="advertencia">
          {resumen.cantidadPendienteUf} propuesta{resumen.cantidadPendienteUf === 1 ? "" : "s"} sin
          valor UF, no incluida{resumen.cantidadPendienteUf === 1 ? "" : "s"} en los totales; se
          valorizará{resumen.cantidadPendienteUf === 1 ? "" : "n"} al resolver la UF.{" "}
          <Link href={enlacePendientesUf} className="font-medium underline">
            Ver en Propuestas →
          </Link>
        </Alerta>
      ) : null}
    </div>
  );
}
