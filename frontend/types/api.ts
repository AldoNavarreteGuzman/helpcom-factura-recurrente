/** Espeja `PaginaRespuestaDto<T>` del backend (comun/dto/PaginaRespuestaDto.java). */
export interface PaginaRespuesta<T> {
  contenido: T[];
  total: number;
  pagina: number;
  tamano: number;
}
