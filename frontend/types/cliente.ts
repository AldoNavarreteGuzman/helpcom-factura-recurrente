/** Espeja `ClienteRespuestaDto` (backend clientes/dto/ClienteRespuestaDto.java). */
export interface Cliente {
  id: number;
  /** Formato canónico `NNNNNNNN-D` (mismo que normaliza el backend). */
  rut: string;
  razonSocial: string;
  nombreFantasia: string | null;
  giro: string | null;
  email: string | null;
  telefono: string | null;
  direccion: string | null;
  activo: boolean;
}

/** Espeja `ClienteSolicitudDto`. `rut` va normalizado a `NNNNNNNN-D` antes de enviar (lib/rut.ts). */
export interface ClienteSolicitud {
  rut: string;
  razonSocial: string;
  nombreFantasia: string | null;
  giro: string | null;
  email: string | null;
  telefono: string | null;
  direccion: string | null;
  activo: boolean;
}
