import type { Moneda } from "@/types/dominio";

const LOCALE = "es-CL";
const ZONA_NEGOCIO = "America/Santiago";

const formateadorClp = new Intl.NumberFormat(LOCALE, {
  style: "currency",
  currency: "CLP",
  maximumFractionDigits: 0,
});

const formateadorUf = new Intl.NumberFormat(LOCALE, {
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

const formateadorFecha = new Intl.DateTimeFormat(LOCALE, {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
});

const formateadorFechaHora = new Intl.DateTimeFormat(LOCALE, {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const formateadorAnioMesSantiago = new Intl.DateTimeFormat("en-CA", {
  timeZone: ZONA_NEGOCIO,
  year: "numeric",
  month: "2-digit",
});

export function formatearClp(monto: number): string {
  return formateadorClp.format(monto);
}

export function formatearUf(valor: number): string {
  return `${formateadorUf.format(valor)} UF`;
}

/** Espera una fecha en formato ISO-8601 (AAAA-MM-DD), como la recibida desde el backend. */
export function formatearFecha(fechaIso: string): string {
  return formateadorFecha.format(new Date(`${fechaIso}T00:00:00`));
}

/** Espera un instante ISO-8601 con offset (p. ej. `OffsetDateTime` del backend). */
export function formatearFechaHora(fechaHoraIso: string): string {
  return formateadorFechaHora.format(new Date(fechaHoraIso));
}

/** Neto/monto en la moneda que corresponda: CLP sin decimales, UF con decimales. */
export function formatearMontoEnMoneda(monto: number, moneda: Moneda): string {
  return moneda === "UF" ? formatearUf(monto) : formatearClp(monto);
}

/**
 * Año y mes actuales en la zona de negocio `America/Santiago` (arquitectura-tecnica.md —
 * ciclo del día 1), no en la zona local del navegador — para que el período por defecto del
 * formulario de "ejecutar ciclo" coincida con el que usaría el propio backend si se omitiera.
 */
export function obtenerAnioMesSantiago(): { anio: number; mes: number } {
  const partes = formateadorAnioMesSantiago.formatToParts(new Date());
  const anio = Number(partes.find((parte) => parte.type === "year")?.value);
  const mes = Number(partes.find((parte) => parte.type === "month")?.value);
  return { anio, mes };
}
