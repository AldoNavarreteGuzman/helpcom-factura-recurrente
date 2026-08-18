import { formatearClp } from "./formato";
import type { EstadoPropuesta } from "@/types/dominio";

/**
 * `PENDIENTE_UF` no tiene un monto real todavía — el ciclo (o la importación CSV) la generó
 * igual, pero `neto_clp`/`iva_clp`/`total_clp` quedaron en 0 porque no había un valor UF
 * disponible para su fecha (arquitectura-tecnica.md §9: "nunca se inventan cifras"). Mostrar
 * ese 0 tal cual en una columna de monto se leería como "esto se va a facturar en $0", que es
 * engañoso — no es que valga cero, es que todavía no se sabe cuánto vale.
 */
export function esMontoAusente(estado: EstadoPropuesta): boolean {
  return estado === "PENDIENTE_UF";
}

/** "— (sin UF)" en vez del 0 real cuando el estado es `PENDIENTE_UF` (ver {@link esMontoAusente}). */
export function formatearMontoClpOAusente(monto: number, estado: EstadoPropuesta): string {
  return esMontoAusente(estado) ? "— (sin UF)" : formatearClp(monto);
}

/** Mismas dos estados que acepta `ServicioPropuestaFacturacion.anular` en el backend. */
export function esAnulable(estado: EstadoPropuesta): boolean {
  return estado === "PENDIENTE" || estado === "PENDIENTE_UF";
}

/** Único estado que acepta `ServicioFactura.buscarPropuestaFacturableOLanzar` en el backend. */
export function esFacturable(estado: EstadoPropuesta): boolean {
  return estado === "PENDIENTE";
}
