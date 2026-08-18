import type {
  DisparoCiclo,
  EstadoPropuesta,
  OrigenPropuesta,
  Periodicidad,
  TipoAcuerdo,
} from "@/types/dominio";

export const ETIQUETAS_TIPO_ACUERDO: Record<TipoAcuerdo, string> = {
  DESCUENTO_PORCENTAJE: "Descuento porcentual",
  DESCUENTO_MONTO: "Descuento por monto",
  PRECIO_PACTADO: "Precio pactado",
};

/** Extraída tras el tercer uso (ListaPropuestas, NuevaFactura, InformeFacturacion) — antes duplicada local a cada pantalla. */
export const ETIQUETAS_ESTADO_PROPUESTA: Record<EstadoPropuesta, string> = {
  PENDIENTE: "Pendiente",
  PENDIENTE_UF: "Pendiente UF",
  FACTURADA: "Facturada",
  ANULADA: "Anulada",
};

export const ETIQUETAS_PERIODICIDAD: Record<Periodicidad, string> = {
  MENSUAL: "Mensual",
  ANUAL: "Anual",
};

export const ETIQUETAS_ORIGEN_PROPUESTA: Record<OrigenPropuesta, string> = {
  CICLO: "Ciclo automático",
  CSV: "Importación CSV",
};

export const ETIQUETAS_DISPARO_CICLO: Record<DisparoCiclo, string> = {
  MANUAL: "Manual",
  AUTOMATICO: "Automático",
};

/** Índice 0 = enero, igual que `periodoMes` (1-12) menos 1. */
export const NOMBRES_MES = [
  "Enero",
  "Febrero",
  "Marzo",
  "Abril",
  "Mayo",
  "Junio",
  "Julio",
  "Agosto",
  "Septiembre",
  "Octubre",
  "Noviembre",
  "Diciembre",
] as const;

/** `1-12` → "MM-AAAA" (sin nombre del mes; para columnas de tabla compactas). */
export function formatearPeriodo(anio: number, mes: number): string {
  return `${String(mes).padStart(2, "0")}-${anio}`;
}
