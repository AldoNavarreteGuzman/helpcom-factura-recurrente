import { formatearClp } from "./formato";
import type { ImportacionPreviewFila } from "@/types/importacionCsv";

/**
 * El backend no expone un estado de propuesta explícito en la fila de previsualización — solo
 * agrega este mensaje fijo cuando el cálculo quedó sin UF disponible
 * (`ServicioImportacionCsv.aPreviewDto`: "...la propuesta quedará en estado PENDIENTE_UF.").
 * Es la única señal disponible para distinguir una advertencia por "sin UF" (monto en 0, no
 * real) de cualquier otra advertencia (p. ej. fecha/período discordante, que sí trae un monto
 * real ya calculado).
 */
export function esSinUf(fila: ImportacionPreviewFila): boolean {
  return fila.mensajes.some((mensaje) => mensaje.includes("PENDIENTE_UF"));
}

/** "— (sin UF)" en vez del 0 real — mismo criterio que `lib/propuestas.ts::formatearMontoClpOAusente`. */
export function formatearMontoFilaCsv(monto: number, fila: ImportacionPreviewFila): string {
  return esSinUf(fila) ? "— (sin UF)" : formatearClp(monto);
}

/**
 * Si todas las filas importables (no `ERROR`) comparten el mismo período `AAAA-MM`, lo
 * retorna — para enlazar directo a `/facturacion?origen=CSV&periodoAnio=X&periodoMes=Y` tras
 * confirmar. Un CSV típico (una carga mensual) cae en este caso. Si el archivo mezcla varios
 * períodos, retorna `null` y el enlace queda sin acotar por período.
 */
export function periodoUnicoImportable(
  filas: ImportacionPreviewFila[],
): { anio: number; mes: number } | null {
  const periodos = new Set(
    filas.filter((fila) => fila.estado !== "ERROR" && fila.periodo).map((fila) => fila.periodo),
  );
  if (periodos.size !== 1) {
    return null;
  }
  const periodo = Array.from(periodos)[0];
  const coincidencia = /^(\d{4})-(\d{2})$/.exec(periodo ?? "");
  if (!coincidencia) {
    return null;
  }
  return { anio: Number(coincidencia[1]), mes: Number(coincidencia[2]) };
}
