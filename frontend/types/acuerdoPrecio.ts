import type { Moneda, TipoAcuerdo } from "./dominio";

/** Espeja `AcuerdoPrecioRespuestaDto` (backend proyectos/dto/AcuerdoPrecioRespuestaDto.java). */
export interface AcuerdoPrecio {
  id: number;
  proyectoId: number;
  tipo: TipoAcuerdo;
  valor: number;
  /** `null` solo cuando `tipo === "DESCUENTO_PORCENTAJE"`. */
  moneda: Moneda | null;
  fechaInicio: string;
  fechaTermino: string;
  /** Presente solo si la vigencia se cargó en modo "meses pactados". */
  mesesPactados: number | null;
  observacion: string | null;
}

/**
 * Espeja `AcuerdoPrecioSolicitudDto`. Vigencia: exactamente una de `fechaTermino` o
 * `mesesPactados` debe venir (el backend rechaza ambas o ninguna con 400
 * `ACUERDO_VIGENCIA_INVALIDA`).
 */
export interface AcuerdoPrecioSolicitud {
  tipo: TipoAcuerdo;
  valor: number;
  moneda: Moneda | null;
  fechaInicio: string;
  fechaTermino: string | null;
  mesesPactados: number | null;
  observacion: string | null;
}
