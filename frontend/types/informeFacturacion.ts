import type { EstadoPropuesta, Moneda, OrigenPropuesta } from "./dominio";
import type { PaginaRespuesta } from "./api";

/** Espeja `InformeFacturacionClienteResumenDto` — subtotal por cliente (no se muestra en esta pantalla; se mantiene por completitud del espejo). */
export interface InformeFacturacionClienteResumen {
  clienteId: number | null;
  clienteRazonSocial: string | null;
  cantidad: number;
  netoClp: number;
  ivaClp: number;
  totalClp: number;
}

/**
 * Espeja `InformeFacturacionResumenDto` (backend informes/dto). Política de totales
 * (arquitectura-tecnica.md §11): `netoClp`/`ivaClp`/`totalClp` suman SOLO las propuestas
 * `PENDIENTE` o `FACTURADA` — "lo que se va a facturar o ya se facturó". Se EXCLUYEN por
 * completo `PENDIENTE_UF` (sin monto real todavía) y `ANULADA` (no se factura).
 * `cantidadPendienteUf` reporta esa cantidad APARTE, nunca sumada a los totales ni oculta.
 * `cantidadPorEstado` sí incluye los 4 estados (0 si no hay), para que el desglose sea
 * transparente aunque los totales no los sumen a todos.
 */
export interface InformeFacturacionResumen {
  cantidadTotal: number;
  cantidadPorEstado: Record<EstadoPropuesta, number>;
  cantidadPendienteUf: number;
  netoClp: number;
  ivaClp: number;
  totalClp: number;
  porCliente: InformeFacturacionClienteResumen[];
}

/** Espeja `InformeFacturacionDetalleDto`. */
export interface InformeFacturacionDetalleFila {
  id: number;
  clienteId: number;
  clienteRazonSocial: string;
  proyectoId: number | null;
  proyectoNombre: string | null;
  descripcion: string;
  periodoAnio: number;
  periodoMes: number;
  /** ISO-8601 (AAAA-MM-DD). */
  fechaFacturacion: string;
  monedaOrigen: Moneda;
  /**
   * `null` cuando el cálculo no requirió UF o quedó `PENDIENTE_UF` — pero, como TODA la API
   * (`jackson.default-property-inclusion: non_null`), un `null` se OMITE del JSON en vez de
   * viajar como `"valorUf":null`, así que tras `JSON.parse` vale `undefined`, no `null`
   * (docs/deuda-tecnica.md ítem 5). Comparar con `== null`/`!= null`, nunca `===`/`!==`.
   */
  valorUf: number | null | undefined;
  netoClp: number;
  ivaClp: number;
  totalClp: number;
  estado: EstadoPropuesta;
  origen: OrigenPropuesta;
  facturaId: number | null;
  numeroFactura: string | null;
  /** ISO-8601 (AAAA-MM-DD); `null` si la propuesta no está `FACTURADA`. */
  fechaFactura: string | null;
}

/** Espeja `InformeFacturacionRespuestaDto` (respuesta de `GET /informes/facturacion`): resumen + detalle paginado, mismos filtros. */
export interface InformeFacturacionRespuesta {
  resumen: InformeFacturacionResumen;
  detalle: PaginaRespuesta<InformeFacturacionDetalleFila>;
}
