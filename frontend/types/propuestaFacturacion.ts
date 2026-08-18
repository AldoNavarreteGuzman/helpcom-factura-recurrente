import type { EstadoPropuesta, Moneda, OrigenPropuesta, TipoAcuerdo } from "./dominio";

/**
 * Espeja `PropuestaFacturacionRespuestaDto` (backend facturacion/dto/PropuestaFacturacionRespuestaDto.java).
 * Solo lectura: las propuestas no se crean a mano desde el frontend (las genera el ciclo o la
 * importación CSV), así que no hay un `PropuestaFacturacionSolicitud`.
 */
export interface PropuestaFacturacion {
  id: number;
  clienteId: number;
  clienteRazonSocial: string;
  proyectoId: number | null;
  proyectoNombre: string | null;
  origen: OrigenPropuesta;
  periodoAnio: number;
  periodoMes: number;
  /** ISO-8601 (AAAA-MM-DD). */
  fechaFacturacion: string;
  descripcion: string;
  monedaOrigen: Moneda;
  precioBaseNeto: number;
  acuerdoTipo: TipoAcuerdo | null;
  acuerdoValor: number | null;
  /** `null` cuando `acuerdoTipo === "DESCUENTO_PORCENTAJE"` (o cuando no hay acuerdo). */
  acuerdoMoneda: Moneda | null;
  /**
   * `null` cuando el cálculo no requirió UF, o cuando quedó `PENDIENTE_UF` — pero, como TODA
   * la API (`jackson.default-property-inclusion: non_null`, `application.yml`), un `null` no
   * viaja como `"valorUf":null`, se OMITE, así que tras `JSON.parse` vale `undefined`, no
   * `null` (docs/deuda-tecnica.md ítem 5). Comparar con `== null`/`!= null`, nunca
   * `===`/`!==`, en cualquier lugar que lo use.
   */
  valorUf: number | null | undefined;
  fechaValorUf: string | null;
  /** En `PENDIENTE_UF` viene en 0 — no es un monto real, ver `lib/propuestas.ts`. */
  netoClp: number;
  tasaIva: number;
  ivaClp: number;
  totalClp: number;
  estado: EstadoPropuesta;
  /** `null` cuando la propuesta no está `FACTURADA`. */
  numeroFactura: string | null;
  /** ISO-8601 (AAAA-MM-DD); `null` cuando la propuesta no está `FACTURADA`. */
  fechaFactura: string | null;
}
