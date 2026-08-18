package cl.helpcom.facturacion.importacion.dominio;

/**
 * Estado de una fila del CSV tras validarla (modelo-de-datos.md §6). {@code OK} y
 * {@code ADVERTENCIA} se importan al confirmar; {@code ERROR} no.
 */
public enum EstadoFilaCsv {
    OK,
    ADVERTENCIA,
    ERROR
}
