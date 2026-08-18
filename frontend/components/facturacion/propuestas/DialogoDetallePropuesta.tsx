"use client";

import type { ReactNode } from "react";
import { BadgeEstadoPropuesta } from "./BadgeEstadoPropuesta";
import { Dialogo } from "@/components/ui/Dialogo";
import {
  ETIQUETAS_ORIGEN_PROPUESTA,
  ETIQUETAS_TIPO_ACUERDO,
  formatearPeriodo,
} from "@/lib/etiquetas";
import { formatearFecha, formatearMontoEnMoneda } from "@/lib/formato";
import { esMontoAusente, formatearMontoClpOAusente } from "@/lib/propuestas";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

export interface PropiedadesDialogoDetallePropuesta {
  propuesta: PropuestaFacturacion | null;
  onCerrar: () => void;
}

function Fila({ etiqueta, valor }: { etiqueta: string; valor: ReactNode }) {
  return (
    <div className="border-b border-linea-2 py-2">
      <dt className="text-xs font-medium uppercase tracking-wide text-sutil">{etiqueta}</dt>
      <dd className="mt-0.5 text-sm text-texto">{valor}</dd>
    </div>
  );
}

/**
 * Snapshot completo del cálculo (arquitectura-tecnica.md §9: la propuesta guarda el snapshot
 * inmutable), para que sea auditable de un vistazo — solo lectura, sin acciones acá (Anular
 * vive en la fila del listado, no en el detalle).
 */
export function DialogoDetallePropuesta({
  propuesta,
  onCerrar,
}: PropiedadesDialogoDetallePropuesta) {
  return (
    <Dialogo abierto={propuesta !== null} titulo="Detalle de la propuesta" onCerrar={onCerrar}>
      {propuesta ? (
        <dl className="grid grid-cols-2 gap-x-6">
          <Fila etiqueta="Cliente" valor={propuesta.clienteRazonSocial} />
          <Fila etiqueta="Proyecto" valor={propuesta.proyectoNombre ?? "—"} />
          <Fila etiqueta="Descripción" valor={propuesta.descripcion} />
          <Fila
            etiqueta="Período"
            valor={formatearPeriodo(propuesta.periodoAnio, propuesta.periodoMes)}
          />
          <Fila
            etiqueta="Fecha de facturación"
            valor={formatearFecha(propuesta.fechaFacturacion)}
          />
          <Fila etiqueta="Origen" valor={ETIQUETAS_ORIGEN_PROPUESTA[propuesta.origen]} />
          <Fila etiqueta="Estado" valor={<BadgeEstadoPropuesta estado={propuesta.estado} />} />
          <Fila
            etiqueta="Precio base neto"
            valor={formatearMontoEnMoneda(propuesta.precioBaseNeto, propuesta.monedaOrigen)}
          />
          <Fila
            etiqueta="Acuerdo aplicado"
            valor={
              propuesta.acuerdoTipo
                ? `${ETIQUETAS_TIPO_ACUERDO[propuesta.acuerdoTipo]}: ${propuesta.acuerdoValor}${
                    propuesta.acuerdoMoneda ? ` ${propuesta.acuerdoMoneda}` : "%"
                  }`
                : "Sin acuerdo (precio base)"
            }
          />
          <Fila
            etiqueta="Valor UF"
            valor={
              // `!= null` (no `!==`) por si acaso: hoy `ArmadorPropuesta` siempre pone
              // valorUf/fechaValorUf en null JUNTOS (nunca uno sin el otro), así que el
              // `&& propuesta.fechaValorUf` de al lado ya protege este caso aunque
              // `valorUf` venga `undefined` (omitido por Jackson) en vez de `null` —
              // pero el campo SÍ es nullable/omitible (docs/deuda-tecnica.md ítem 5),
              // así que se corrige igual, por defensa, no porque hoy se vea el bug acá.
              propuesta.valorUf != null && propuesta.fechaValorUf
                ? `${formatearMontoEnMoneda(propuesta.valorUf, "UF")} (${formatearFecha(propuesta.fechaValorUf)})`
                : "— (sin UF)"
            }
          />
          <Fila
            etiqueta="Neto"
            valor={formatearMontoClpOAusente(propuesta.netoClp, propuesta.estado)}
          />
          <Fila
            etiqueta="Tasa IVA"
            valor={
              esMontoAusente(propuesta.estado) ? "—" : `${(propuesta.tasaIva * 100).toFixed(0)}%`
            }
          />
          <Fila
            etiqueta="IVA"
            valor={formatearMontoClpOAusente(propuesta.ivaClp, propuesta.estado)}
          />
          <Fila
            etiqueta="Total"
            valor={formatearMontoClpOAusente(propuesta.totalClp, propuesta.estado)}
          />
        </dl>
      ) : null}
    </Dialogo>
  );
}
