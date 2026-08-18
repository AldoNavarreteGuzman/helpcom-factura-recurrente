import Link from "next/link";
import { Alerta } from "@/components/ui/Alerta";
import { contarPendienteUf } from "@/lib/dashboardCalculos";
import { obtenerMensajeError } from "@/lib/errores";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

export interface PropiedadesCalloutPendienteUf {
  propuestas: PropuestaFacturacion[] | null;
  cargando: boolean;
  error: unknown;
}

/**
 * Callout de propuestas `PENDIENTE_UF` (docs/frontend.md R9), mismo criterio que
 * `ResumenInforme` (R5): se cuentan APARTE, nunca sumadas a ningún total del dashboard — todas
 * las demás tarjetas ya las excluyen vía {@link import("@/lib/propuestas").esCalculable}. Si es
 * 0, no se renderiza nada (ni siquiera un "0 pendientes" — mismo criterio que el informe).
 */
export function CalloutPendienteUf({ propuestas, cargando, error }: PropiedadesCalloutPendienteUf) {
  if (cargando) {
    return null;
  }
  if (error) {
    return <Alerta variante="error">{obtenerMensajeError(error)}</Alerta>;
  }
  const cantidad = propuestas ? contarPendienteUf(propuestas) : 0;
  if (cantidad === 0) {
    return null;
  }

  return (
    <Alerta variante="advertencia">
      {cantidad} propuesta{cantidad === 1 ? "" : "s"} sin valor UF, no incluida
      {cantidad === 1 ? "" : "s"} en ninguna de las tarjetas de arriba; se valorizará
      {cantidad === 1 ? "" : "n"} al reprocesarse.{" "}
      <Link href="/facturacion?estado=PENDIENTE_UF" className="font-medium underline">
        Ver en Propuestas →
      </Link>
    </Alerta>
  );
}
