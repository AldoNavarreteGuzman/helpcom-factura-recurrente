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

/**
 * Único estado que acepta `ServicioPropuestaFacturacion.reprocesarUf` en el backend
 * (deuda-tecnica.md ítem 8/9) — el guard duro del endpoint rechaza cualquier otro con `409
 * PROPUESTA_NO_REPROCESABLE`. Deliberadamente NO hay una variante "solo si la fecha ya pasó":
 * la UF de Chile se publica con antelación (el Banco Central anuncia el mes calendario
 * completo por adelantado), así que una `PENDIENTE_UF` de fecha nominalmente "futura" puede
 * tener su UF ya disponible — un chequeo de fecha en el cliente adivinaría mal en ese caso. El
 * botón de reprocesar se ofrece igual; el resultado real (`estado` en la respuesta) es quien
 * decide el mensaje, no una suposición de fecha (`components/facturacion/propuestas/
 * ListaPropuestas.tsx`).
 */
export function esReprocesableUf(estado: EstadoPropuesta): boolean {
  return estado === "PENDIENTE_UF";
}

/**
 * Mismo criterio de "lo que efectivamente se va a facturar o ya se facturó" que
 * `InformeFacturacionResumenDto` (backend, arquitectura-tecnica.md §11): solo `PENDIENTE` y
 * `FACTURADA` tienen un monto real. `PENDIENTE_UF` queda en 0 hasta poder recalcularse
 * ({@link esMontoAusente}) y `ANULADA` no se factura — ninguna de las dos cuenta en un total o
 * agregación (dashboard incluido, `docs/frontend.md` R9).
 */
export function esCalculable(estado: EstadoPropuesta): boolean {
  return estado === "PENDIENTE" || estado === "FACTURADA";
}
