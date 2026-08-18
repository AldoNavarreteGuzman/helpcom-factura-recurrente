import type { Moneda, Periodicidad } from "./dominio";

/** Espeja `ProyectoRespuestaDto` (backend proyectos/dto/ProyectoRespuestaDto.java). */
export interface Proyecto {
  id: number;
  clienteId: number;
  clienteRazonSocial: string;
  tipoServicioId: number | null;
  tipoServicioNombre: string | null;
  codigo: string | null;
  nombre: string;
  descripcion: string | null;
  /** Neto, sin IVA, en `monedaPrecio`. */
  precioBaseNeto: number;
  monedaPrecio: Moneda;
  periodicidad: Periodicidad;
  diaFacturacion: number;
  /** ISO-8601 (AAAA-MM-DD). */
  fechaInicio: string;
  fechaTermino: string | null;
  activo: boolean;
}

/** Espeja `ProyectoSolicitudDto`. */
export interface ProyectoSolicitud {
  clienteId: number;
  tipoServicioId: number | null;
  codigo: string | null;
  nombre: string;
  descripcion: string | null;
  precioBaseNeto: number;
  monedaPrecio: Moneda;
  periodicidad: Periodicidad;
  diaFacturacion: number;
  fechaInicio: string;
  fechaTermino: string | null;
  activo: boolean;
}
