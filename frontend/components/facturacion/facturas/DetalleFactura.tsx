"use client";

import { useEffect, useState } from "react";
import { BadgeEstadoPropuesta } from "@/components/facturacion/propuestas/BadgeEstadoPropuesta";
import { Alerta } from "@/components/ui/Alerta";
import { EncabezadoDetalle } from "@/components/detalle/EncabezadoDetalle";
import { Tabla, type ColumnaTabla } from "@/components/ui/Tabla";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerMensajeError } from "@/lib/errores";
import { formatearPeriodo } from "@/lib/etiquetas";
import { formatearClp, formatearFecha } from "@/lib/formato";
import type { Factura, PropuestaResumenFactura } from "@/types/factura";
import { SeccionPdfFactura } from "./SeccionPdfFactura";

export interface PropiedadesDetalleFactura {
  facturaId: number;
}

const COLUMNAS: ColumnaTabla<PropuestaResumenFactura>[] = [
  { encabezado: "Período", renderizar: (p) => formatearPeriodo(p.periodoAnio, p.periodoMes) },
  { encabezado: "Descripción", renderizar: (p) => p.descripcion },
  { encabezado: "Neto", renderizar: (p) => formatearClp(p.netoClp) },
  { encabezado: "IVA", renderizar: (p) => formatearClp(p.ivaClp) },
  { encabezado: "Total", renderizar: (p) => formatearClp(p.totalClp) },
  { encabezado: "Estado", renderizar: (p) => <BadgeEstadoPropuesta estado={p.estado} /> },
];

/**
 * Detalle de solo lectura de la factura + gestión del PDF de respaldo (`SeccionPdfFactura`).
 * El total de la factura se suma en el cliente a partir de `propuestas[].totalClp` — el
 * backend (`FacturaRespuestaDto`) no trae un total agregado propio, así que no hay una fuente
 * de verdad distinta para sumar.
 */
export function DetalleFactura({ facturaId }: PropiedadesDetalleFactura) {
  const [factura, setFactura] = useState<Factura | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);
    clienteApiCliente
      .obtener<Factura>(`/facturas/${facturaId}`)
      .then((respuesta) => {
        if (!cancelado) {
          setFactura(respuesta);
        }
      })
      .catch((error: unknown) => {
        if (!cancelado) {
          setError(error);
        }
      })
      .finally(() => {
        if (!cancelado) {
          setCargando(false);
        }
      });
    return () => {
      cancelado = true;
    };
  }, [facturaId]);

  const total = factura
    ? factura.propuestas.reduce((acumulado, propuesta) => acumulado + propuesta.totalClp, 0)
    : 0;

  if (error) {
    return <Alerta variante="error">{obtenerMensajeError(error)}</Alerta>;
  }

  if (cargando || !factura) {
    return <p className="py-10 text-center text-sm text-sutil">Cargando…</p>;
  }

  return (
    <div className="max-w-4xl space-y-6">
      <EncabezadoDetalle
        volverA="/facturacion/facturas"
        volverEtiqueta="Volver a facturas"
        titulo={`Factura ${factura.numeroFactura}`}
      />
      <div className="-mt-2">
        <p className="text-sm text-sutil">
          {formatearFecha(factura.fechaFactura)} — {factura.clienteRazonSocial ?? "—"}
        </p>
        {factura.observacion ? <p className="mt-1 text-sm text-sutil">{factura.observacion}</p> : null}
      </div>

      <div className="space-y-2">
        <h2 className="text-sm font-semibold text-tinta">Propuestas asociadas</h2>
        <Tabla
          columnas={COLUMNAS}
          filas={factura.propuestas}
          obtenerClave={(propuesta) => propuesta.id}
          mensajeVacio="Esta factura no tiene propuestas asociadas."
        />
        <p className="text-right text-sm font-semibold text-tinta">
          Total factura: {formatearClp(total)}
        </p>
      </div>

      <SeccionPdfFactura factura={factura} onActualizada={setFactura} />
    </div>
  );
}
