import type { EstadoPropuesta } from "./dominio";

/**
 * Espeja `PropuestaResumenDto` (backend facturacion/dto/PropuestaResumenDto.java) — la
 * propuesta tal como aparece dentro del detalle de una factura (subconjunto de
 * `PropuestaFacturacion`, sin los datos de cliente/proyecto/snapshot completo).
 */
export interface PropuestaResumenFactura {
  id: number;
  periodoAnio: number;
  periodoMes: number;
  /** ISO-8601 (AAAA-MM-DD). */
  fechaFacturacion: string;
  descripcion: string;
  netoClp: number;
  ivaClp: number;
  totalClp: number;
  estado: EstadoPropuesta;
}

/** Espeja `FacturaRespuestaDto` (backend facturacion/dto/FacturaRespuestaDto.java). */
export interface Factura {
  id: number;
  numeroFactura: string;
  /** ISO-8601 (AAAA-MM-DD). */
  fechaFactura: string;
  observacion: string | null;
  /** `null` solo si la factura no tiene ninguna propuesta asociada (no ocurre en la práctica). */
  clienteId: number | null;
  clienteRazonSocial: string | null;
  tienePdf: boolean;
  nombreArchivoPdf: string | null;
  propuestas: PropuestaResumenFactura[];
}

/**
 * Espeja `FacturaSolicitudDto`. Una factura se crea asociando propuestas `PENDIENTE` ya
 * existentes; no hay `FacturaSolicitud` de edición porque el backend no expone ningún
 * endpoint para editar una factura ya creada (solo crear, subir/reemplazar PDF).
 */
export interface FacturaSolicitud {
  numeroFactura: string;
  /** ISO-8601 (AAAA-MM-DD). */
  fechaFactura: string;
  observacion: string | null;
  propuestaIds: number[];
}
